package com.sidongletter.car.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 음성 답장 받아쓰기. Pleos STT를 붙이기 전까지는 정해진 문장을 돌려주는 가짜를 쓴다. */
interface SpeechInput {
    fun listen(onResult: (String) -> Unit)
    fun cancel()
}

class FakeSpeechInput(
    private val scope: CoroutineScope,
    private val sentence: String,
    private val delayMs: Long = 2_000,
) : SpeechInput {
    private var job: Job? = null
    override fun listen(onResult: (String) -> Unit) {
        cancel()
        job = scope.launch { delay(delayMs); onResult(sentence) }
    }
    override fun cancel() { job?.cancel(); job = null }
}
