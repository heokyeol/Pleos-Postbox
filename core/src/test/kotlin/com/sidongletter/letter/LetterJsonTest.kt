package com.sidongletter.letter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LetterJsonTest {
    @Test fun decodes_supabase_row() {
        val json = """[{"id":"a1","family_code":"K7Q2MX","sender_name":"민준","created_at":"2026-11-08T12:00:00Z",
          "aspect":0.75,"strokes":[{"color":"#FF5A5F","width":0.01,"points":[{"x":0.1,"y":0.2,"t":0}]}],
          "text":"아빠 힘내세요","opened_at":null}]"""
        val l = LetterJson.decodeLetters(json).single()
        assertEquals(Instant.parse("2026-11-08T12:00:00Z").toEpochMilli(), l.createdAt)
        assertEquals("민준", l.senderName)
        assertEquals("아빠 힘내세요", l.text)
        assertNull(l.openedAt)
        assertEquals(0.1f, l.strokes[0].points[0].x)
    }

    @Test fun decodes_postgres_timestamp_with_offset_and_micros() {
        val json = """[{"id":"a1","family_code":"K","sender_name":"s","aspect":1,
          "created_at":"2026-11-08T21:00:00.123456+09:00","opened_at":"2026-11-09T06:00:00+00:00"}]"""
        val l = LetterJson.decodeLetters(json).single()
        assertEquals(Instant.parse("2026-11-08T12:00:00.123Z").toEpochMilli(), l.createdAt)
        assertEquals(Instant.parse("2026-11-09T06:00:00Z").toEpochMilli(), l.openedAt)
    }

    @Test fun text_only_letter_decodes_with_empty_strokes() {
        val json = """[{"id":"a1","family_code":"K","sender_name":"s","created_at":"2026-11-08T12:00:00Z",
          "aspect":0.75,"strokes":[],"text":"글만"}]"""
        assertTrue(LetterJson.decodeLetters(json).single().strokes.isEmpty())
    }

    @Test fun unknown_keys_are_ignored() {
        val json = """[{"id":"a1","family_code":"K","sender_name":"s","created_at":"2026-11-08T12:00:00Z",
          "aspect":0.75,"strokes":[],"mood":"happy"}]"""
        assertEquals("a1", LetterJson.decodeLetters(json).single().id)
    }

    /** web/app.js가 보내는 모양 + Supabase가 붙이는 id·created_at·opened_at, 그리고 embed된 replies */
    @Test fun web_payload_decodes() {
        val json = """[{"family_code":"K7Q2MX","sender_name":"민준","aspect":0.75,
          "strokes":[{"color":"#E8604C","width":0.012,"points":[{"x":0.1848,"y":0.2416,"t":0},{"x":0.7419,"y":0.3075,"t":2046}]}],
          "text":"아빠 힘내세요","id":"3f1c0a7e-1111-4222-8333-944455556666",
          "created_at":"2026-11-12T00:35:12.345678+00:00","opened_at":null,"replies":[]}]"""
        val l = LetterJson.decodeLetters(json).single()
        assertEquals(2046L, l.strokes[0].points[1].t)
        assertEquals(0.012f, l.strokes[0].width)
    }

    /** 웹 스케치북의 글상자: 위치(왼쪽 위, 0..1), 글자 크기(캔버스 너비 대비), 놓은 시각 t */
    @Test fun text_boxes_decode_and_default_to_empty() {
        val json = """[{"id":"a1","family_code":"K","sender_name":"s","created_at":"2026-11-08T12:00:00Z",
          "aspect":1.7778,"strokes":[],"texts":[{"x":0.1,"y":0.2,"text":"사랑해요","color":"#000000","size":0.045,"t":3200}]},
          {"id":"a2","family_code":"K","sender_name":"s","created_at":"2026-11-08T12:00:00Z","aspect":1.7778}]"""
        val (withText, without) = LetterJson.decodeLetters(json)
        assertEquals(TextBox(0.1f, 0.2f, "사랑해요", "#000000", 0.045f, 3200L), withText.texts.single())
        assertTrue(without.texts.isEmpty())
    }

    @Test fun letters_round_trip_for_cache() {
        val letters = listOf(
            Letter("a1", "K", "민준", 1_000L, 0.75f, listOf(Stroke("#000000", 0.01f, listOf(Point(0.5f, 0.5f, 0)))), "hi", null)
        )
        assertEquals(letters, LetterJson.decodeLetters(LetterJson.encodeLetters(letters)))
    }

    @Test fun encodes_reply_with_snake_case() {
        assertEquals(
            """{"letter_id":"a1","preset":"사랑해","text":null}""",
            LetterJson.encodeReply(Reply("a1", "사랑해", null))
        )
    }
}
