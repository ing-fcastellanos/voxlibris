package com.fcastellanos.voxlibris.tts

import org.readium.r2.shared.util.tokenizer.Tokenizer

/** Cómo se lee un libro: una sola voz, o una voz que narra y otra que dice los diálogos. */
enum class ReadingMode { SINGLE, NARRATOR_DIALOGUE }

/** Modo y voces de lectura ya resueltos: los valores por defecto o los propios de un libro. */
data class VoiceChoice(
    val mode: ReadingMode,
    val narratorVoiceId: Int,
    val dialogueVoiceId: Int,
)

/** Cómo marca los diálogos un libro: con rayas (lo habitual en español) o con comillas. */
enum class DialogueStyle { DASHES, QUOTES }

/**
 * Separa narración y diálogo dentro de un párrafo, solo por puntuación.
 *
 * Con rayas: el párrafo que empieza con raya es diálogo; una raya pegada a la palabra siguiente
 * («—dijo Juan») abre un inciso del narrador, y la raya de cierre («Juan—.») o una nueva raya de
 * apertura devuelven la palabra al personaje. Un inciso sin cierre llega hasta el final del párrafo.
 * El párrafo que empieza con » continúa un parlamento del párrafo anterior.
 *
 * Con comillas: lo que va entre comillas es diálogo, salvo citas sueltas de pocas palabras.
 */
object DialogueParser {

    /** Tramo `[start, end)` de un texto, dicho por el personaje ([dialogue]) o por el narrador. */
    data class Span(val start: Int, val end: Int, val dialogue: Boolean)

    /** Trozo a sintetizar y quién lo dice. */
    data class VoicedPiece(val piece: TtsMath.Piece, val dialogue: Boolean)

    enum class Kind { DASH, QUOTED, PLAIN }

    /** Qué marca de diálogo trae el párrafo; sirve para decidir el estilo del libro. */
    fun kindOf(paragraph: String): Kind {
        val first = paragraph.firstOrNull { !it.isWhitespace() } ?: return Kind.PLAIN
        return when {
            first in DASHES -> Kind.DASH
            quoteRoles(paragraph)?.any { it } == true -> Kind.QUOTED
            else -> Kind.PLAIN
        }
    }

    /** Marca, carácter por carácter, lo que dice un personaje. */
    fun roles(paragraph: String, style: DialogueStyle): BooleanArray =
        dashRoles(paragraph)
            ?: (if (style == DialogueStyle.QUOTES) quoteRoles(paragraph) else null)
            ?: BooleanArray(paragraph.length)

    fun parse(paragraph: String, style: DialogueStyle): List<Span> = spans(roles(paragraph, style), 0, paragraph.length)

    /** Tramos de `roles[from, to)`, con posiciones relativas a `from`. */
    fun spans(roles: BooleanArray, from: Int, to: Int): List<Span> {
        val out = ArrayList<Span>()
        var start = from
        for (i in from + 1..to) {
            if (i == to || roles[i] != roles[start]) {
                if (i > start) out.add(Span(start - from, i - from, roles[start]))
                start = i
            }
        }
        return out
    }

    /**
     * Trozos de síntesis de una frase, cada uno con su papel. Los tramos sin letras (una raya suelta,
     * un punto) no se sintetizan. Dentro de cada tramo se corta igual que con una sola voz.
     */
    fun splitVoiced(text: String, spans: List<Span>): List<VoicedPiece> {
        val out = ArrayList<VoicedPiece>()
        for (span in spans) {
            val segment = text.substring(span.start, span.end)
            if (segment.none { it.isLetterOrDigit() }) continue
            val firstSoftMax = if (out.isEmpty()) TtsMath.DEFAULT_FIRST_PIECE_CHARS else TtsMath.DEFAULT_PIECE_CHARS
            TtsMath.splitForSynthesis(segment, firstSoftMax = firstSoftMax).forEach { piece ->
                if (piece.text.any { it.isLetterOrDigit() }) {
                    out.add(VoicedPiece(TtsMath.Piece(piece.text, piece.offset + span.start), span.dialogue))
                }
            }
        }
        return out
    }

    private fun dashRoles(text: String): BooleanArray? {
        val n = text.length
        var first = 0
        while (first < n && text[first].isWhitespace()) first++
        if (first >= n || (text[first] !in DASHES && text[first] != CONTINUATION)) return null
        val roles = BooleanArray(n)
        var inDialogue = true
        var i = first + 1
        while (i < n) {
            val ch = text[i]
            if (ch in DASHES) {
                val afterSpace = text[i - 1].isWhitespace()
                val next = text.getOrNull(i + 1)
                val beforeBreak = next == null || next.isWhitespace() || next in CLOSING_PUNCTUATION
                val opening = afterSpace && !beforeBreak
                val closing = !afterSpace && beforeBreak
                if (inDialogue && opening) {
                    inDialogue = false
                    i++
                    continue
                }
                if (!inDialogue && (opening || closing)) {
                    // La puntuación pegada a la raya de cierre («—dijo Juan—.») es del narrador.
                    i++
                    while (i < n && text[i] in CLOSING_PUNCTUATION) i++
                    inDialogue = true
                    continue
                }
            }
            roles[i] = inDialogue
            i++
        }
        return roles
    }

    private fun quoteRoles(text: String): BooleanArray? {
        val n = text.length
        var roles: BooleanArray? = null
        val firstVisible = text.indexOfFirst { !it.isWhitespace() }
        var i = 0
        while (i < n) {
            val close = QUOTES[text[i]]
            if (close == null) {
                i++
                continue
            }
            val end = text.indexOf(close, i + 1).let { if (it < 0) n else it }
            val inner = text.substring(i + 1, end).trim()
            val isSpeech = i == firstVisible || inner.length >= MIN_QUOTED_SPEECH_CHARS || inner.lastOrNull()?.let { it in SPEECH_ENDINGS } == true
            if (isSpeech && inner.any { it.isLetterOrDigit() }) {
                val marked = roles ?: BooleanArray(n).also { roles = it }
                for (k in i + 1 until end) marked[k] = true
            }
            i = end + 1
        }
        return roles
    }

    private const val DASHES = "—–―"
    private const val CONTINUATION = '»'
    private const val CLOSING_PUNCTUATION = ".,;:!?…"
    private const val SPEECH_ENDINGS = ".,!?…"
    private const val MIN_QUOTED_SPEECH_CHARS = 15
    private val QUOTES = mapOf('“' to '”', '«' to '»', '"' to '"')
}

/**
 * Lo que el tokenizador vio de cada párrafo, para que el motor sepa quién dice cada frase. El motor
 * solo recibe la frase suelta, y «Tal vez mañana.» puede ser narración o la continuación de un
 * parlamento según el párrafo donde esté.
 */
class DialogueRegistry {

    private val byUtterance = object : LinkedHashMap<String, List<DialogueParser.Span>>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<DialogueParser.Span>>?): Boolean = size > MAX_UTTERANCES
    }

    @Volatile
    var style: DialogueStyle = DialogueStyle.DASHES
        set(value) {
            if (field == value) return
            field = value
            synchronized(byUtterance) { byUtterance.clear() }
        }

    /** Anota los papeles de cada frase de un párrafo; `ranges` son las frases que devolvió el tokenizador. */
    fun register(paragraph: String, ranges: List<IntRange>) {
        val roles = DialogueParser.roles(paragraph, style)
        synchronized(byUtterance) {
            for (range in ranges) {
                var from = range.first.coerceIn(0, paragraph.length)
                var to = (range.last + 1).coerceIn(from, paragraph.length)
                while (from < to && paragraph[from].isWhitespace()) from++
                while (to > from && paragraph[to - 1].isWhitespace()) to--
                if (to > from) byUtterance[paragraph.substring(from, to)] = DialogueParser.spans(roles, from, to)
            }
        }
    }

    /** Tramos de una frase, con posiciones relativas a `utterance` tal como llega al motor. */
    fun spansFor(utterance: String): List<DialogueParser.Span> {
        val lead = utterance.indexOfFirst { !it.isWhitespace() }
        if (lead < 0) return emptyList()
        val key = utterance.trim()
        val known = synchronized(byUtterance) { byUtterance[key] }
        // Sin registro (no debería pasar), la frase se analiza sola: alcanza si empieza con raya.
        val spans = known ?: DialogueParser.parse(key, style)
        return if (lead == 0) spans else spans.map { it.copy(start = it.start + lead, end = it.end + lead) }
    }

    private companion object {
        const val MAX_UTTERANCES = 800
    }
}

/** Tokenizador de frases de Readium que, de paso, anota en el registro quién dice cada frase. */
class DialogueTokenizer(
    private val sentences: Tokenizer<String, IntRange>,
    private val registry: DialogueRegistry,
) : Tokenizer<String, IntRange> {

    override fun tokenize(data: String): List<IntRange> = sentences.tokenize(data).also { registry.register(data, it) }
}
