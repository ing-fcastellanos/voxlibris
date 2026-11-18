package com.fcastellanos.voxlibris.tts

/**
 * Ritmo real de la voz: segundos de audio por carácter a velocidad 1×, promediado sobre lo que el
 * motor va sintetizando. Sirve para estimar cuánto falta para terminar un libro.
 */
object SpeechRate {

    const val DEFAULT_SECONDS_PER_CHAR = 0.075

    @Volatile
    var secondsPerCharAtUnitSpeed: Double = DEFAULT_SECONDS_PER_CHAR
        private set

    private var samples = 0

    @Synchronized
    fun record(chars: Int, audioSeconds: Double, synthSpeed: Double) {
        if (chars < MIN_CHARS || audioSeconds <= 0.0 || synthSpeed <= 0.0) return
        val sample = audioSeconds * synthSpeed / chars
        secondsPerCharAtUnitSpeed = if (samples == 0) sample else secondsPerCharAtUnitSpeed * (1 - WEIGHT) + sample * WEIGHT
        samples++
    }

    private const val MIN_CHARS = 10
    private const val WEIGHT = 0.1
}
