package io.github.zyrouge.symphony

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ActivityIgnition : ViewModel() {
    private val readyFlow = MutableStateFlow(false)
    val ready = readyFlow.asStateFlow()

    internal fun emitReady() {
        // Overture: Thread-safe atomic update to prevent race conditions during startup
        readyFlow.compareAndSet(expect = false, update = true)
    }
}