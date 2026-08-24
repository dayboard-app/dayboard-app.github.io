package io.github.dayboard.data.audio

import io.github.dayboard.domain.repository.Chime

/**
 * The completion beep, synthesised rather than played from a file.
 *
 * Two short sine tones, the second a fifth above the first, which is why it reads
 * as "finished" rather than "something is wrong". Generating it costs no download
 * and no asset to keep in the bundle.
 *
 * One context is created on the first beep and kept. The original builds a new one
 * every time and never closes it; browsers cap how many a page may have, so after a
 * handful of completed sessions the chime there simply stops working. Reusing one
 * has no such ceiling.
 */
class WebAudioChime : Chime {

    private var context: AudioContext? = null

    override fun play(volumePercent: Int) {
        // Audio is the least important thing this app does. A browser without Web
        // Audio, or one that has not yet been allowed to make noise, gets a timer
        // that ends quietly rather than a timer that throws.
        try {
            val audio = context ?: AudioContext().also { context = it }

            // A context is suspended until the page is allowed to play sound, and
            // again whenever the tab is hidden.
            audio.resume()

            val gain = audio.createGain()
            gain.gain.value = volumePercent.coerceIn(0, MAX_VOLUME).toDouble() / MAX_VOLUME
            gain.connect(audio.destination)

            BEEP_FREQUENCIES.forEachIndexed { index, frequency ->
                val startAt = audio.currentTime + index * BEEP_INTERVAL_SECONDS
                audio.beep(frequency, gain, startAt)
            }
        } catch (error: Throwable) {
            console.warn("the completion chime could not play", error)
        }
    }

    private fun AudioContext.beep(frequency: Double, through: GainNode, startAt: Double) {
        val oscillator = createOscillator()
        oscillator.type = "sine"
        oscillator.frequency.value = frequency
        oscillator.connect(through)
        oscillator.start(startAt)
        oscillator.stop(startAt + BEEP_LENGTH_SECONDS)
    }

    private companion object {
        /** A above middle C, then the E above it. */
        val BEEP_FREQUENCIES = listOf(440.0, 660.0)

        const val BEEP_LENGTH_SECONDS = 0.2
        const val BEEP_INTERVAL_SECONDS = 0.25
        const val MAX_VOLUME = 100
    }
}
