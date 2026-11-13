package com.pleospostbox.screen

import com.pleospostbox.letter.Letter

enum class DriveState { PARKED, IDLING, DRIVING }

/** 지금 편지를 어느 목록 안에서 보고 있는지. ‹ › 는 그 목록 안에서 움직인다. */
enum class LetterSource {
    /** 이번 시동에 도착한 새 편지(오래된 순) */
    NEW,
    /** 받은 편지함(최신순) */
    ARCHIVE,
}

sealed interface CarScreenState {
    /** 받은 편지가 하나도 없다. */
    data object Empty : CarScreenState
    /** 주차가 아니다. 그림은 보이지 않는다. newCount는 아직 안 본 새 편지 수. */
    data class Locked(val newCount: Int) : CarScreenState
    /** 받은 편지함. 목록은 CarModel.archive. */
    data object Archive : CarScreenState
    /** 편지 한 장을 화면 가득 본다. index는 0부터. reply는 이 편지에 방금 보낸 답장. */
    data class Reading(
        val letter: Letter,
        val source: LetterSource,
        val index: Int,
        val total: Int,
        val reply: String? = null,
    ) : CarScreenState {
        /** 목록 안에서 시간상 더 이전/더 최근 편지의 위치. 새 편지는 오래된 순, 편지함은 최신순이다. */
        private val olderStep get() = if (source == LetterSource.NEW) -1 else 1
        val olderIndex: Int? get() = (index + olderStep).takeIf { it in 0 until total }
        val newerIndex: Int? get() = (index - olderStep).takeIf { it in 0 until total }
    }
    data class Replying(val reading: Reading) : CarScreenState
}

sealed interface CarEvent {
    /** 시동. newLetters는 이번에 보여줄 편지(오래된 순), archived는 이미 읽은 편지. */
    data class LettersReady(val newLetters: List<Letter>, val archived: List<Letter>) : CarEvent
    data class DriveChanged(val state: DriveState) : CarEvent
    /** '목록으로' */
    data object ToArchive : CarEvent
    /** ‹ 시간상 더 이전에 온 편지 */
    data object Prev : CarEvent
    /** › 더 최근에 온 편지 */
    data object Next : CarEvent
    data class Open(val letter: Letter) : CarEvent
    data object ReplyRequested : CarEvent
    data object ReplyCancelled : CarEvent
    data class ReplySent(val text: String) : CarEvent
}

/**
 * batch: 이번 시동에 도착한 새 편지(오래된 순).
 * archive: 받은 편지 전부(새 편지 포함), 최신순.
 * seen: 이번 시동에 화면에 한 번이라도 띄운 편지 id. 띄운 편지는 읽은 것으로 기록된다.
 */
data class CarModel(
    val screen: CarScreenState,
    val batch: List<Letter>,
    val archive: List<Letter>,
    val seen: Set<String>,
    val drive: DriveState,
) {
    val unseenCount: Int get() = batch.count { it.id !in seen }

    companion object {
        fun initial(drive: DriveState) = CarModel(CarScreenState.Empty, emptyList(), emptyList(), emptySet(), drive)
    }
}

/** 그림은 PARKED일 때만 보인다. 주차가 아니면 잠금 화면이고, 그때는 어떤 조작도 받지 않는다. */
fun reduce(model: CarModel, event: CarEvent): CarModel {
    if (event is CarEvent.LettersReady) {
        return model.copy(
            batch = event.newLetters,
            archive = (event.newLetters + event.archived).distinctBy { it.id }.sortedByDescending { it.createdAt },
            seen = emptySet(),
        ).withDefaultScreen()
    }
    if (event is CarEvent.DriveChanged) {
        val wasParked = model.drive == DriveState.PARKED
        val m = model.copy(drive = event.state)
        return if (wasParked && event.state == DriveState.PARKED) m else m.withDefaultScreen()
    }
    if (model.drive != DriveState.PARKED) return model

    val s = model.screen
    return when (event) {
        CarEvent.ToArchive ->
            if (s is CarScreenState.Reading) model.copy(screen = CarScreenState.Archive) else model

        CarEvent.Prev, CarEvent.Next -> {
            if (s !is CarScreenState.Reading) return model
            val i = if (event == CarEvent.Prev) s.olderIndex else s.newerIndex
            if (i == null) model else model.read(s.source, i)
        }

        is CarEvent.Open -> {
            val i = model.archive.indexOfFirst { it.id == event.letter.id }
            if (s is CarScreenState.Archive && i >= 0) model.read(LetterSource.ARCHIVE, i) else model
        }

        CarEvent.ReplyRequested ->
            if (s is CarScreenState.Reading) model.copy(screen = CarScreenState.Replying(s.copy(reply = null))) else model

        CarEvent.ReplyCancelled ->
            if (s is CarScreenState.Replying) model.copy(screen = s.reading) else model

        is CarEvent.ReplySent ->
            if (s is CarScreenState.Replying) model.copy(screen = s.reading.copy(reply = event.text)) else model

        is CarEvent.LettersReady, is CarEvent.DriveChanged -> model
    }
}

private fun CarModel.listOf(source: LetterSource) = if (source == LetterSource.NEW) batch else archive

private fun CarModel.read(source: LetterSource, index: Int): CarModel {
    val list = listOf(source)
    val letter = list[index]
    return copy(
        screen = CarScreenState.Reading(letter, source, index, list.size),
        seen = seen + letter.id,
    )
}

private fun CarModel.withDefaultScreen(): CarModel {
    if (archive.isEmpty()) return copy(screen = CarScreenState.Empty)
    if (drive != DriveState.PARKED) return copy(screen = CarScreenState.Locked(unseenCount))
    val firstUnseen = batch.indexOfFirst { it.id !in seen }
    return if (firstUnseen >= 0) read(LetterSource.NEW, firstUnseen) else copy(screen = CarScreenState.Archive)
}
