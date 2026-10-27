package com.fcastellanos.voxlibris.tts

/** Funciones puras del motor de voz, separadas para poder probarlas sin Android. */
object TtsMath {

    /** Recorta el silencio final dejando [keepMs] de cola, para encadenar frases sin pausas largas. */
    fun trimTrailingSilence(samples: FloatArray, sampleRate: Int, threshold: Float = 0.004f, keepMs: Int = 120): FloatArray {
        if (samples.isEmpty()) return samples
        var end = samples.size
        while (end > 0 && kotlin.math.abs(samples[end - 1]) < threshold) end--
        val keep = sampleRate * keepMs / 1000
        val newSize = (end + keep).coerceIn(minOf(1, samples.size), samples.size)
        return if (newSize == samples.size) samples else samples.copyOf(newSize)
    }

    /** Palabra (rango de caracteres, fin exclusivo) que corresponde a una fracción del audio reproducido. */
    fun wordRangeAt(text: String, fraction: Double): IntRange? {
        if (text.isBlank()) return null
        val index = (fraction.coerceIn(0.0, 1.0) * text.length).toInt().coerceIn(0, text.length - 1)
        var start = index
        var end = index
        if (text[index].isWhitespace()) {
            // Entre palabras: avanzamos a la siguiente.
            while (end < text.length && text[end].isWhitespace()) end++
            if (end >= text.length) return null
            start = end
        }
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        while (end < text.length && !text[end].isWhitespace()) end++
        return start until end
    }

    /** Segundos de audio que conviene tener en cola antes de pedir la frase siguiente. */
    fun leadSeconds(rtf: Double): Double = (rtf * 4.0 + 0.3).coerceIn(0.4, 2.5)

    /** Media móvil del factor de tiempo real (segundos de cómputo por segundo de audio). */
    class RtfEstimator(initial: Double) {
        var value: Double = initial
            private set

        fun update(synthSeconds: Double, audioSeconds: Double) {
            if (audioSeconds <= 0.05 || synthSeconds < 0) return
            val sample = synthSeconds / audioSeconds
            value = value * 0.7 + sample * 0.3
        }
    }

    fun numSteps(quality: SherpaTtsQuality, calibratedRtf: Double?): Int = when (quality) {
        SherpaTtsQuality.FAST -> SherpaTtsDefaults.STEPS_FAST
        SherpaTtsQuality.HIGH -> SherpaTtsDefaults.STEPS_HIGH
        SherpaTtsQuality.AUTO ->
            if ((calibratedRtf ?: 0.0) > SherpaTtsDefaults.AUTO_RTF_THRESHOLD) SherpaTtsDefaults.STEPS_FAST
            else SherpaTtsDefaults.STEPS_HIGH
    }
}
