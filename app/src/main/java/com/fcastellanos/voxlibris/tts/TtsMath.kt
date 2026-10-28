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
     * Parte una frase larga en trozos para sintetizarlos por separado. Corta solo donde el texto
     * ya hace una pausa (puntuación fuerte o comas, puntos y comas, dos puntos, rayas): un trozo
     * sintetizado aislado termina con entonación de cierre, y si el corte cae a mitad de una
     * cláusula suena como una coma inventada. Solo se corta en un espacio cuando la frase supera
     * [hardMax] caracteres sin ninguna pausa.
     *
     * El primer trozo admite un límite blando menor ([firstSoftMax]) para que la frase empiece a sonar antes.
     */
    fun splitForSynthesis(
        text: String,
        softMax: Int = DEFAULT_PIECE_CHARS,
        hardMax: Int = DEFAULT_HARD_PIECE_CHARS,
        firstSoftMax: Int = DEFAULT_FIRST_PIECE_CHARS,
    ): List<Piece> {
        val pieces = ArrayList<Piece>()
        val n = text.length
        var start = 0
        while (start < n) {
            while (start < n && text[start].isWhitespace()) start++
            if (start >= n) break
            val soft = if (pieces.isEmpty()) minOf(softMax, firstSoftMax) else softMax
            val hard = maxOf(soft, hardMax)
            var end = n
            if (n - start > soft) {
                val minEnd = start + soft / 3
                var cut = punctuationCut(text, minEnd, minOf(n, start + soft))
                if (cut < 0 && n - start > hard) cut = punctuationCut(text, minEnd, start + hard)
                if (cut < 0 && n - start > hard) cut = spaceCut(text, minEnd, start + hard)
                if (cut < 0 && n - start > hard) cut = start + hard
                if (cut > 0) end = cut
            }
            val piece = text.substring(start, end).trimEnd()
            if (piece.isNotBlank()) pieces.add(Piece(piece, start))
            start = end
        }
        return pieces
    }

    /** Última pausa de puntuación en (minEnd, end]; devuelve el índice siguiente a la puntuación, o -1. */
    private fun punctuationCut(text: String, minEnd: Int, end: Int): Int {
        for (set in PUNCTUATION_SETS) {
            var i = end - 1
            while (i > minEnd) {
                if (set.indexOf(text[i]) >= 0 && (i + 1 >= text.length || !text[i + 1].isLetterOrDigit())) return i + 1
                i--
            }
        }
        return -1
    }

    private fun spaceCut(text: String, minEnd: Int, end: Int): Int {
        var i = minOf(end, text.length) - 1
        while (i > minEnd) {
            if (text[i].isWhitespace()) return i
            i--
        }
        return -1
    }

    private val PUNCTUATION_SETS = listOf(".!?…", ",;:—–)»\"")
    /** Unos 120 caracteres son 6 a 8 s de audio: se sintetizan en 3 o 4 s mientras suena el trozo anterior. */
    const val DEFAULT_PIECE_CHARS = 120
    const val DEFAULT_HARD_PIECE_CHARS = 200
    const val DEFAULT_FIRST_PIECE_CHARS = 100

    /** Recorta el silencio inicial dejando [keepMs]. */
    fun trimLeadingSilence(samples: FloatArray, sampleRate: Int, threshold: Float = 0.004f, keepMs: Int = 10): FloatArray {
        if (samples.isEmpty()) return samples
        var startIdx = 0
        while (startIdx < samples.size && kotlin.math.abs(samples[startIdx]) < threshold) startIdx++
        val keep = sampleRate * keepMs / 1000
        val from = (startIdx - keep).coerceIn(0, samples.size)
        return if (from == 0) samples else samples.copyOfRange(from, samples.size)
    }

    /** Fundido lineal de entrada y salida para que la unión entre trozos no haga clic. */
    fun fadeEdges(samples: FloatArray, sampleRate: Int, fadeInMs: Int, fadeOutMs: Int): FloatArray {
        val fadeIn = minOf(samples.size, sampleRate * fadeInMs / 1000)
        val fadeOut = minOf(samples.size, sampleRate * fadeOutMs / 1000)
        for (i in 0 until fadeIn) samples[i] *= i.toFloat() / fadeIn
        for (i in 0 until fadeOut) samples[samples.size - 1 - i] *= i.toFloat() / fadeOut
        return samples
    }

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
