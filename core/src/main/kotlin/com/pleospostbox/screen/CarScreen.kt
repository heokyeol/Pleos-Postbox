package com.pleospostbox.screen

import com.pleospostbox.letter.Letter

enum class DriveState { PARKED, IDLING, DRIVING }

sealed interface CarScreenState {
    /** 받은 편지가 하나도 없다. */
    data object Empty : CarScreenState
    /** 주차가 아니다. 그림은 보이지 않는다. newCount는 이번 시동에 도착한 안 읽은 편지 수. */
    data class Locked(val newCount: Int) : CarScreenState
    /** 이번 시동에 도착한 편지를 다시 그리는 중. index는 1부터. */
    data class Revealing(val letter: Letter, val index: Int, val total: Int) : CarScreenState
    /** 받은 편지함에서 연 편지. */
    data class Viewing(val letter: Letter) : CarScreenState
    data class Replying(val letter: Letter, val fromArchive: Boolean) : CarScreenState
    /** 답장을 보낸 뒤 그 편지가 화면에 남아 있다. */
    data class Replied(val letter: Letter, val reply: String) : CarScreenState
    /** 받은 편지함. 목록은 CarModel.archive. */
    data object Archive : CarScreenState
}

sealed interface CarEvent {
    /** 시동. newLetters는 이번에 보여줄 편지(오래된 순), archived는 이미 읽은 편지. */
    data class LettersReady(val newLetters: List<Letter>, val archived: List<Letter>) : CarEvent
    data class DriveChanged(val state: DriveState) : CarEvent
    data object Back : CarEvent
    data class Open(val letter: Letter) : CarEvent
    data object ReplyRequested : CarEvent
    data object ReplyCancelled : CarEvent
    data class ReplySent(val text: String) : CarEvent
}

/**
 * queue: 이번 시동에 도착해 아직 넘기지 않은 편지(맨 앞이 지금 편지).
 * archive: 읽은 편지, 최신순. queue에서 넘긴 편지는 archive로 옮겨진다.
 */
data class CarModel(
    val screen: CarScreenState,
    val queue: List<Letter>,
    val archive: List<Letter>,
    val drive: DriveState,
    val batchSize: Int,
) {
    companion object {
        fun initial(drive: DriveState) = CarModel(CarScreenState.Empty, emptyList(), emptyList(), drive, 0)
    }
}

/** 그림은 PARKED일 때만 보인다. 주차가 아니면 잠금 화면, 그때는 어떤 조작도 받지 않는다. */
fun reduce(model: CarModel, event: CarEvent): CarModel {
    if (event is CarEvent.LettersReady) {
        return model.copy(
            queue = event.newLetters,
            archive = event.archived.newestFirst(),
            batchSize = event.newLetters.size,
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
        CarEvent.Back -> when (s) {
            is CarScreenState.Revealing -> model.advance().withDefaultScreen()
            is CarScreenState.Viewing, is CarScreenState.Replied -> model.withDefaultScreen()
            else -> model
        }

        is CarEvent.Open ->
            if (s is CarScreenState.Archive) model.copy(screen = CarScreenState.Viewing(event.letter)) else model

        CarEvent.ReplyRequested -> when (s) {
            is CarScreenState.Revealing -> model.copy(screen = CarScreenState.Replying(s.letter, fromArchive = false))
            is CarScreenState.Viewing -> model.copy(screen = CarScreenState.Replying(s.letter, fromArchive = true))
            else -> model
        }

        CarEvent.ReplyCancelled -> when {
            s !is CarScreenState.Replying -> model
            s.fromArchive -> model.copy(screen = CarScreenState.Viewing(s.letter))
            else -> model.withDefaultScreen()
        }

        is CarEvent.ReplySent -> when {
            s !is CarScreenState.Replying -> model
            s.fromArchive -> model.copy(screen = CarScreenState.Replied(s.letter, event.text))
            else -> {
                val m = model.advance()
                if (m.queue.isEmpty()) m.copy(screen = CarScreenState.Replied(s.letter, event.text))
                else m.withDefaultScreen()
            }
        }

        is CarEvent.LettersReady, is CarEvent.DriveChanged -> model
    }
}

/** 지금 편지(queue 맨 앞)를 읽은 편지로 옮긴다. */
private fun CarModel.advance(): CarModel {
    val current = queue.firstOrNull() ?: return this
    return copy(queue = queue.drop(1), archive = (archive + current).newestFirst())
}

private fun List<Letter>.newestFirst() = sortedByDescending { it.createdAt }

private fun CarModel.withDefaultScreen(): CarModel = copy(
    screen = when {
        queue.isEmpty() && archive.isEmpty() -> CarScreenState.Empty
        drive != DriveState.PARKED -> CarScreenState.Locked(queue.size)
        queue.isNotEmpty() -> CarScreenState.Revealing(queue.first(), index = batchSize - queue.size + 1, total = batchSize)
        else -> CarScreenState.Archive
    }
)
