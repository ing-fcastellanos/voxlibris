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

    /** Trozo de una frase que se sintetiza por separado; `offset` es su posición en el texto original. */
    data class Piece(val text: String, val offset: Int)

    /**
     * Parte una frase larga en trozos de como mucho [maxChars], cortando preferentemente tras
     * puntuación fuerte, luego tras comas y similares, y en último caso en un espacio.
     * Trozos cortos significan menos latencia tras una acción y menos trabajo desperdiciado al cancelar.
     */
    fun splitForSynthesis(text: String, maxChars: Int = DEFAULT_PIECE_CHARS): List<Piece> {
        val pieces = ArrayList<Piece>()
        val n = text.length
        var start = 0
        while (start < n) {
            while (start < n && text[start].isWhitespace()) start++
            if (start >= n) break
            var end = minOf(n, start + maxChars)
            if (end < n) {
                val minEnd = start + maxChars / 3
                var cut = -1
                for (set in CUT_SETS) {
                    var i = end - 1
                    while (i > minEnd) {
                        if (set.indexOf(text[i]) >= 0) {
                            cut = i + 1
                            break
                        }
                        i--
                    }
                    if (cut > 0) break
                }
                if (cut > 0) end = cut
            }
            val piece = text.substring(start, end).trimEnd()
            if (piece.isNotBlank()) pieces.add(Piece(piece, start))
            start = end
        }
        return pieces
    }

    private val CUT_SETS = listOf(".!?…", ",;:—–)", " ")
    const val DEFAULT_PIECE_CHARS = 150

    /** Palabra (rango de caracteres, fin exclusivo) que corresponde a una fracción del audio reproducido. */
    fun wordRangeAt(text: String, fraction: Double): IntRange? {
        if (text.isBlank()) return null
        return wordRangeAtIndex(text, (fraction.coerceIn(0.0, 1.0) * text.length).toInt())
    }

    /** Palabra que contiene (o sigue a) la posición [charIndex] del texto. */
    fun wordRangeAtIndex(text: String, charIndex: Int): IntRange? {
        if (text.isBlank()) return null
        val index = charIndex.coerceIn(0, text.length - 1)
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

    /**
     * Segundos de audio que conviene tener en cola antes de pedir la frase siguiente: el tiempo
     * estimado de sintetizarla (RTF por una frase típica de unos seis segundos) más un margen.
     */
    fun leadSeconds(rtf: Double): Double = (rtf * 6.0 + 0.5).coerceIn(0.5, MAX_LEAD_SECONDS)

    const val MAX_LEAD_SECONDS = 6.0

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
