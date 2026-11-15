package com.pleospostbox.car

import androidx.lifecycle.ViewModel
import com.pleospostbox.car.data.LetterRepository
import com.pleospostbox.car.data.PairingStore
import com.pleospostbox.delivery.ignitionTime
import com.pleospostbox.delivery.lettersToReveal
import com.pleospostbox.letter.Letter
import com.pleospostbox.letter.Reply
import com.pleospostbox.screen.CarEvent
import com.pleospostbox.screen.CarModel
import com.pleospostbox.screen.CarScreenState
import com.pleospostbox.screen.DriveState
import com.pleospostbox.screen.reduce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱이 시작되면 시동이 걸린 것으로 보고, 시동 전에 도착한 안 읽은 편지를 보여준다.
 * 화면 흐름은 core의 reduce가 정하고, 여기서는 읽음(화면에 띄운 편지)·답장을 저장소에 기록한다.
 */
class CarViewModel(
    private val repo: LetterRepository,
    private val pairing: PairingStore,
    private val now: () -> Long,
    private val elapsedSinceBoot: () -> Long,
) : ViewModel() {
    private val _model = MutableStateFlow(CarModel.initial(DriveState.PARKED))
    val model: StateFlow<CarModel> = _model.asStateFlow()

    private val _showPairing = MutableStateFlow(!pairing.paired)
    val showPairing: StateFlow<Boolean> = _showPairing.asStateFlow()

    /** 디버그 패널에서 정한 주행 상태. null이면 차량 신호를 따른다. */
    private val _driveOverride = MutableStateFlow<DriveState?>(null)
    val driveOverride: StateFlow<DriveState?> = _driveOverride.asStateFlow()
    private var systemDrive = DriveState.PARKED

    val familyCode: String get() = pairing.familyCode

    init {
        ignition(ignitionTime(now(), elapsedSinceBoot()))
    }

    fun ignition(at: Long) {
        val all = repo.letters()
        dispatch(CarEvent.LettersReady(lettersToReveal(all, at), all.filter { it.openedAt != null }))
    }

    fun onSystemDrive(s: DriveState) {
        systemDrive = s
        if (_driveOverride.value == null) dispatch(CarEvent.DriveChanged(s))
    }

    fun overrideDrive(s: DriveState?) {
        _driveOverride.value = s
        dispatch(CarEvent.DriveChanged(s ?: systemDrive))
    }

    fun toArchive() = dispatch(CarEvent.ToArchive)
    fun readNew() = dispatch(CarEvent.ReadNew)
    fun later() = dispatch(CarEvent.Later)
    fun prev() = dispatch(CarEvent.Prev)
    fun next() = dispatch(CarEvent.Next)
    fun open(letter: Letter) = dispatch(CarEvent.Open(letter))
    fun requestReply() = dispatch(CarEvent.ReplyRequested)
    fun cancelReply() = dispatch(CarEvent.ReplyCancelled)

    /** preset=true면 원터치 답장, false면 음성으로 받아쓴 문장. */
    fun sendReply(text: String, preset: Boolean) {
        val s = _model.value.screen as? CarScreenState.Replying ?: return
        repo.sendReply(Reply(s.reading.letter.id, preset = text.takeIf { preset }, text = text.takeIf { !preset }))
        dispatch(CarEvent.ReplySent(text))
    }

    fun confirmPairing() {
        pairing.paired = true
        _showPairing.value = false
    }

    fun showPairing() { _showPairing.value = true }

    /** 시연용: 지금 시동을 다시 켠 것처럼. */
    fun restartIgnition() = ignition(now())

    /** 시연용: 이 기기의 읽음 표시를 지우고 시동을 다시 켠다. */
    fun resetRead() {
        repo.resetOpened()
        restartIgnition()
    }

    /** 시연용: 읽음 표시만 지운다. 화면은 다음 시동(앱이 다시 앞으로 올 때)에 바뀐다. */
    fun clearReadMarks() = repo.resetOpened()

    private fun dispatch(event: CarEvent) {
        val before = _model.value
        val after = reduce(before, event)
        // 화면에 띄운 새 편지는 읽은 것으로 기록한다
        (after.seen - before.seen).forEach { repo.markOpened(it, now()) }
        _model.value = after
    }
}
