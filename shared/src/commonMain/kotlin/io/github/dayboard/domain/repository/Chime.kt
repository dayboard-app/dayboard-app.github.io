package io.github.dayboard.domain.repository

/**
 * The two-note beep that marks the end of a stretch.
 *
 * Behind an interface because sound is the one part of the timer that cannot be
 * tested: a real implementation needs an audio device, and the browser will refuse
 * to make a noise at all until the user has interacted with the page. Keeping it
 * out here means the state machine can be tested without either.
 */
interface Chime {

    /**
     * Plays the completion beep at [volumePercent] of full, from 0 to 100.
     *
     * Never throws. A browser with no audio, or one that has not yet been allowed
     * to make noise, is a timer that ends quietly rather than a timer that breaks.
     */
    fun play(volumePercent: Int)
}
