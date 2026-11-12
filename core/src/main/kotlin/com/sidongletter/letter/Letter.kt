package com.sidongletter.letter

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime

/** 좌표는 0..1로 정규화. t는 편지 첫 점 기준 ms. */
@Serializable
data class Point(val x: Float, val y: Float, val t: Long)

/**
 * color는 #RRGGBB, width는 캔버스 너비 대비 비율.
 * color가 "#00000000"이면 지우개 획이다. 그 아래 그려진 획을 BlendMode.Clear로 지운다.
 */
@Serializable
data class Stroke(val color: String, val width: Float, val points: List<Point>)

/** 스케치북 위 글상자. x·y는 글의 왼쪽 위(0..1), size는 글자 크기(캔버스 너비 대비), t는 놓은 시각(첫 점 기준 ms). */
@Serializable
data class TextBox(val x: Float, val y: Float, val text: String, val color: String, val size: Float, val t: Long = 0)

/** 시각은 epoch ms. JSON에서는 Supabase의 ISO-8601 문자열. */
@Serializable
data class Letter(
    val id: String,
    @SerialName("family_code") val familyCode: String,
    @SerialName("sender_name") val senderName: String,
    @SerialName("created_at") @Serializable(with = IsoMillis::class) val createdAt: Long,
    val aspect: Float,
    val strokes: List<Stroke> = emptyList(),
    val text: String? = null,
    @SerialName("opened_at") @Serializable(with = IsoMillis::class) val openedAt: Long? = null,
    val texts: List<TextBox> = emptyList(),
)

@Serializable
data class Reply(
    @SerialName("letter_id") val letterId: String,
    val preset: String?,
    val text: String?,
)

/** "2026-11-08T21:00:00.123456+09:00" 같은 Postgres timestamptz ↔ epoch ms */
object IsoMillis : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("IsoMillis", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Long =
        OffsetDateTime.parse(decoder.decodeString()).toInstant().toEpochMilli()
    override fun serialize(encoder: Encoder, value: Long) =
        encoder.encodeString(Instant.ofEpochMilli(value).toString())
}

object LetterJson {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = true }
    private val listSerializer = ListSerializer(Letter.serializer())

    fun decodeLetters(text: String): List<Letter> = json.decodeFromString(listSerializer, text)
    fun encodeLetters(letters: List<Letter>): String = json.encodeToString(listSerializer, letters)
    fun encodeReply(reply: Reply): String = json.encodeToString(Reply.serializer(), reply)
}
