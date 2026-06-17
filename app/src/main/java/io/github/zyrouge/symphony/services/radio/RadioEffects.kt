package io.github.zyrouge.symphony.services.radio

import java.util.Timer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

object RadioEffects {
    enum class FadeCurve { LINEAR, EQUAL_POWER }

    class Fader(
        val options: Options,
        val onUpdate: (Float) -> Unit,
        val onFinish: (Boolean) -> Unit,
    ) {
        data class Options(
            val from: Float,
            val to: Float,
            val duration: Int,
            val interval: Int = DEFAULT_INTERVAL,
            val curve: FadeCurve = FadeCurve.EQUAL_POWER
        ) {
            companion object {
                private const val DEFAULT_INTERVAL = 50
            }
        }

        private var timer: Timer? = null
        private var ended = false
        private var elapsed = 0

        fun start() {
            val isFadeOut = options.to < options.from
            timer = kotlin.concurrent.timer(period = options.interval.toLong()) {
                elapsed += options.interval
                if (elapsed >= options.duration) {
                    onUpdate(options.to)
                    ended = true
                    onFinish(true)
                    destroy()
                } else {
                    val progress = elapsed.toFloat() / options.duration
                    val volume = when (options.curve) {
                        FadeCurve.LINEAR -> options.from + (options.to - options.from) * progress
                        FadeCurve.EQUAL_POWER -> {
                            if (isFadeOut) {
                                options.from * cos(progress * PI / 2).toFloat()
                            } else {
                                options.to * sin(progress * PI / 2).toFloat()
                            }
                        }
                    }
                    onUpdate(volume)
                }
            }
        }

        fun stop() {
            if (!ended) onFinish(false)
            destroy()
        }

        private fun destroy() {
            timer?.cancel()
            timer = null
        }
    }
}