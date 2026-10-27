package com.fcastellanos.voxlibris.tts

import org.readium.r2.shared.util.Language

/** Paquete de voces Supertonic 3 (int8) tal como lo publica sherpa-onnx. */
object SupertonicModel {
    const val DIR_NAME = "supertonic-3"
    const val ARCHIVE_NAME = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2"
    const val URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$ARCHIVE_NAME"
    const val SHA256 = "82fa96f91c4ef8abaae3a14a3f4153facf88bed821d1f7331cec2700f432c427"
    const val SIZE_BYTES = 128_774_318L
    val SIZE_MB: Int get() = (SIZE_BYTES / 1_048_576L).toInt()

    val FILES = listOf(
        "duration_predictor.int8.onnx",
        "text_encoder.int8.onnx",
        "vector_estimator.int8.onnx",
        "vocoder.int8.onnx",
        "tts.json",
        "unicode_indexer.bin",
        "voice.bin",
    )

    /** Códigos de idioma que acepta el modelo (31). */
    val LANGUAGES = setOf(
        "ar", "bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el", "hi", "hu", "id", "it",
        "ja", "ko", "lv", "lt", "pl", "pt", "ro", "ru", "sk", "sl", "es", "sv", "tr", "uk", "vi",
    )

    /** `voice.bin` ordena los estilos alfabéticamente: F1..F5 son los ids 0..4 y M1..M5 los 5..9. */
    val VOICES: List<SherpaVoice> = listOf(
        SherpaVoice(0, "Lucía", isFemale = true),
        SherpaVoice(1, "Valentina", isFemale = true),
        SherpaVoice(2, "Camila", isFemale = true),
        SherpaVoice(3, "Sofía", isFemale = true),
        SherpaVoice(4, "Martina", isFemale = true),
        SherpaVoice(5, "Mateo", isFemale = false),
        SherpaVoice(6, "Santiago", isFemale = false),
        SherpaVoice(7, "Lucas", isFemale = false),
        SherpaVoice(8, "Benjamín", isFemale = false),
        SherpaVoice(9, "Tomás", isFemale = false),
    )

    fun voice(id: Int): SherpaVoice = VOICES.firstOrNull { it.id == id } ?: VOICES.first()

    /** Reduce una etiqueta BCP 47 al código de dos letras que entiende el modelo, o null si no lo soporta. */
    fun languageCode(language: Language?): String? {
        val code = language?.code?.substringBefore('-')?.substringBefore('_')?.lowercase() ?: return null
        return code.takeIf { it in LANGUAGES }
    }
}
