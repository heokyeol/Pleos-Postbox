package com.pleospostbox.car.data

import com.pleospostbox.letter.Letter
import com.pleospostbox.letter.LetterJson
import com.pleospostbox.letter.Reply

/** 편지를 가져오고 읽음·답장을 기록한다. 지금은 더미, 나중에 Supabase 구현으로 바꾼다. */
interface LetterRepository {
    /** 이 차에 온 모든 편지. 읽은 편지는 openedAt이 채워져 있다. */
    fun letters(): List<Letter>
    fun markOpened(id: String, at: Long)
    fun sendReply(reply: Reply)
    /** 시연용: 이 기기에서 읽은 표시를 지운다. */
    fun resetOpened()
}

/**
 * assets/letters.json(Letter 배열)을 읽는다. JSON의 opened_at이 null인 편지가 새 편지.
 * 읽음은 기기에만 저장한다. 더미 그림을 바꾸려면 letters.json만 바꾸면 된다.
 */
class DummyLetterRepository(
    private val loadJson: () -> String,
    private val store: KeyValueStore,
) : LetterRepository {
    private val all: List<Letter> by lazy { LetterJson.decodeLetters(loadJson()) }

    override fun letters(): List<Letter> = all.map { l ->
        val local = store.getLong(OPENED + l.id)
        if (l.openedAt == null && local != null) l.copy(openedAt = local) else l
    }

    override fun markOpened(id: String, at: Long) {
        if (store.getLong(OPENED + id) == null) store.putLong(OPENED + id, at)
    }

    override fun sendReply(reply: Reply) {
        store.putString(REPLY + reply.letterId, LetterJson.encodeReply(reply))
    }

    override fun resetOpened() {
        store.keys().filter { it.startsWith(OPENED) }.forEach(store::remove)
    }

    private companion object {
        const val OPENED = "opened."
        const val REPLY = "reply."
    }
}

/** 가족 코드와 연결 확인 여부. 코드는 처음 한 번 만들어 계속 쓴다. */
class PairingStore(private val store: KeyValueStore) {
    val familyCode: String
        get() = store.getString(CODE) ?: FamilyCode.generate().also { store.putString(CODE, it) }

    var paired: Boolean
        get() = store.getString(PAIRED) == "1"
        set(v) = store.putString(PAIRED, if (v) "1" else "0")

    private companion object {
        const val CODE = "family.code"
        const val PAIRED = "family.paired"
    }
}
