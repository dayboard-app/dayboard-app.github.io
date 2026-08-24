package io.github.dayboard.data.audio

/**
 * The slice of the Web Audio API the completion beep needs.
 *
 * Declared here because Kotlin's browser stdlib does not carry Web Audio, and kept
 * to the members actually called: an external declaration is a promise about what
 * exists at runtime, and an unchecked promise is worse than no declaration.
 *
 * These are globals rather than module imports, so no `@JsModule`. On a browser old
 * enough to lack `AudioContext` entirely, merely naming it throws - which is why
 * every use is inside a `try`.
 */
external class AudioContext {
    val currentTime: Double
    val destination: AudioNode

    fun createOscillator(): OscillatorNode
    fun createGain(): GainNode

    /**
     * Brings a suspended context back.
     *
     * A browser suspends audio it has not been given permission to play, and again
     * whenever the tab goes to the background. Without this, the first beep after
     * coming back would be silent.
     */
    fun resume()
}

external interface AudioNode {
    fun connect(destination: AudioNode)
}

external interface AudioParam {
    var value: Double
}

external interface GainNode : AudioNode {
    val gain: AudioParam
}

external interface OscillatorNode : AudioNode {
    var type: String
    val frequency: AudioParam

    fun start(whenTime: Double)
    fun stop(whenTime: Double)
}
