package com.fcastellanos.voxlibris.dictionary

/** Entrada del Wikcionario reducida a lo que muestra la tarjeta: acepciones por categoría gramatical. */
data class DictionaryEntry(val word: String, val sections: List<DictionarySection>) {
    /** Lemas a los que remiten las acepciones del tipo «forma de…». */
    val lemmas: List<String> get() = sections.flatMap { section -> section.senses.mapNotNull { it.formOf?.lemma } }.distinct()

    /** Cierto si la palabra es solo una forma flexionada (plural, conjugación) de otra. */
    val isOnlyForms: Boolean get() = sections.isNotEmpty() && sections.all { section -> section.senses.all { it.formOf != null } }
}

data class DictionarySection(val partOfSpeech: String, val senses: List<DictionarySense>)

data class DictionarySense(val label: String?, val text: String, val formOf: FormOf?)

data class FormOf(val lemma: String, val kind: Kind) {
    enum class Kind { VERB, ADJECTIVE, NOUN, OTHER }
}

/**
 * Lee el wikitexto de una página del Wikcionario en español. Se queda con la sección `{{lengua|es}}`,
 * toma como categoría cada encabezado `=== {{sustantivo femenino|es}} ===` y como acepción cada línea `;1: …`.
 */
object WiktionaryParser {
    private val LANGUAGE = Regex("^==\\s*\\{\\{lengua\\|es\\}\\}\\s*==\\s*$", RegexOption.MULTILINE)
    private val NEXT_LANGUAGE = Regex("^==[^=].*$", RegexOption.MULTILINE)
    private val HEADING = Regex("^(={3,6})\\s*(.*?)\\s*\\1$")
    private val HEADING_TEMPLATE = Regex("^\\{\\{([^|}]+)\\|es(?:\\|[^}]*)?\\}\\}$")
    private val SENSE = Regex("^;\\s*\\d+\\s*(?:\\{\\{csem\\|([^}]*)\\}\\})?\\s*:\\s*(.*)$")
    private val FORM_OF = Regex("\\{\\{forma ([^|}]*)\\|([^}]*)\\}\\}")
    private val TEMPLATE = Regex("\\{\\{([^{}]*)\\}\\}")
    private val REF = Regex("<ref[^>]*/>|<ref[^>]*>.*?</ref>", RegexOption.DOT_MATCHES_ALL)
    private val PIPED_LINK = Regex("\\[\\[[^\\]|]*\\|([^\\]]*)\\]\\]")
    private val LINK = Regex("\\[\\[([^\\]]*)\\]\\]")
    private val EXTERNAL_LINK = Regex("\\[https?://\\S*\\s*([^\\]]*)\\]")
    private val TAG = Regex("<[^>]+>")
    private val SPACES = Regex("\\s+")
    private val LANGUAGE_CODE = Regex("^[a-z]{2,3}$")
    private val NOT_A_CATEGORY = listOf(
        "etimología", "traducciones", "véase", "información", "referencias", "locuciones",
        "refranes", "derivados", "conjugación", "pronunciación", "notas",
    )
    private val IGNORED_TEMPLATES = setOf(
        "csem", "sinónimo", "sinónimos", "antónimo", "antónimos", "uso", "ámbito", "relacionado",
        "hipónimo", "hiperónimo", "ejemplo", "cita", "referencia", "etimología", "pron-graf",
    )

    fun parse(word: String, wikitext: String): DictionaryEntry? {
        val spanish = spanishSection(wikitext) ?: return null
        val sections = mutableListOf<DictionarySection>()
        var category: String? = null
        var senses = mutableListOf<DictionarySense>()
        fun flush() {
            category?.takeIf { senses.isNotEmpty() }?.let { sections += DictionarySection(it, senses.toList()) }
            senses = mutableListOf()
        }
        for (raw in spanish.lineSequence()) {
            val line = raw.trim()
            val heading = HEADING.matchEntire(line)
            if (heading != null) {
                flush()
                category = partOfSpeech(heading.groupValues[2])
                continue
            }
            if (category == null) continue
            SENSE.matchEntire(line)?.let { match -> sense(match)?.let { senses += it } }
        }
        flush()
        return DictionaryEntry(word, sections).takeIf { it.sections.isNotEmpty() }
    }

    private fun spanishSection(wikitext: String): String? {
        val start = LANGUAGE.find(wikitext) ?: return null
        val rest = wikitext.substring(start.range.last + 1)
        val end = NEXT_LANGUAGE.find(rest)?.range?.first ?: rest.length
        return rest.substring(0, end)
    }

    private fun partOfSpeech(heading: String): String? {
        val name = HEADING_TEMPLATE.matchEntire(heading)?.groupValues?.get(1)?.trim()
            ?: heading.takeIf { it.isNotBlank() && !it.contains("{{") && NOT_A_CATEGORY.none { word -> it.lowercase().startsWith(word) } }
            ?: return null
        return name.replaceFirstChar { it.uppercase() }
    }

    private fun sense(match: MatchResult): DictionarySense? {
        val label = match.groupValues[1].takeIf { it.isNotBlank() }
            ?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() && '=' !in it }?.joinToString(", ")
        val raw = match.groupValues[2]
        val formOf = FORM_OF.find(raw)?.let { form ->
            val lemma = form.groupValues[2].split('|').map { it.trim() }.firstOrNull { it.isNotEmpty() && '=' !in it }
                ?: return@let null
            val kind = when {
                form.groupValues[1].startsWith("verbo") -> FormOf.Kind.VERB
                form.groupValues[1].startsWith("adjetivo") -> FormOf.Kind.ADJECTIVE
                form.groupValues[1].startsWith("sustantivo") -> FormOf.Kind.NOUN
                else -> FormOf.Kind.OTHER
            }
            FormOf(lemma, kind)
        }
        val text = clean(raw)
        if (text.isEmpty() && formOf == null) return null
        return DictionarySense(label, text, formOf)
    }

    /** Convierte el wikitexto de una acepción en texto plano. */
    internal fun clean(raw: String): String {
        var text = REF.replace(raw, "")
        var previous: String
        do {
            previous = text
            text = TEMPLATE.replace(text) { template(it.groupValues[1]) }
        } while (text != previous)
        text = PIPED_LINK.replace(text, "$1")
        text = LINK.replace(text, "$1")
        text = EXTERNAL_LINK.replace(text, "$1")
        text = text.replace("'''", "").replace("''", "")
        text = TAG.replace(text, "")
        text = SPACES.replace(text, " ").trim().trimStart(',', ';', ':', ' ')
        if (text == ".") return ""
        return text.replaceFirstChar { it.uppercase() }
    }

    private fun template(body: String): String {
        val parts = body.split('|')
        val name = parts.first().trim().lowercase()
        val positional = parts.drop(1).map { it.trim() }.filter { it.isNotEmpty() && '=' !in it }
        return when {
            name == "plm" -> positional.firstOrNull()?.replaceFirstChar { it.uppercase() }.orEmpty()
            name.startsWith("forma ") || name in IGNORED_TEMPLATES -> ""
            else -> positional.firstOrNull { !LANGUAGE_CODE.matches(it) }.orEmpty()
        }
    }
}
