# Prueba de escucha: TTS en español on-device (2026-10-26)

Ocho audios del mismo texto (apertura de *Don Quijote* + un párrafo con diálogo, hora y cifras),
sintetizados en CPU con sherpa-onnx 2.0.1, 2 hilos, Intel Core Ultra 7 155H.

| Archivo | Motor / voz | RTF (2 hilos) | RAM pico | Paquete modelo |
|---|---|---|---|---|
| piper-es_ES-sharvard-medium-sid0/1 | Piper es_ES sharvard (2 voces) | 0.13 | 159 MB | 22 MB |
| piper-es_MX-claude-high-sid0 | Piper es_MX claude | 0.14 | ~160 MB | 20 MB |
| kokoro-v1.0-ef_dora / em_alex / em_santa | Kokoro v1.0 multi-lang int8 | 0.70 | 301 MB | 126 MB |
| supertonic3-F1-sid0 / M1-sid5 | Supertonic 3 int8, lang=es | 0.18 | 267 MB | 123 MB |

RTF = segundos de cómputo por segundo de audio (menor es mejor). En un teléfono esperá 3 a 8 veces más.

Notas:
- sherpa-onnx >= 2.0 eliminó espeak-ng: Piper y Kokoro necesitan un fonetizador externo
  (aquí `piper_phonemize`). Supertonic 3 trae su propio frontend de texto y no necesita nada.
- El paquete `kokoro-multi-lang-v1_1` de sherpa-onnx es la variante china (sin español).
  Las voces en español (ef_dora=28, em_alex=29, em_santa=53) están en `kokoro-multi-lang-v1_0`.
- Supertonic: voice.bin ordena las voces alfabéticamente: sid 0-4 = F1..F5, sid 5-9 = M1..M5.

Reproducir el test: crear un venv, `pip install sherpa-onnx soundfile piper_phonemize -f https://k2-fsa.github.io/icefall/piper_phonemize.html`,
descargar los paquetes de https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models en `models/` y correr `python run_test.py`.

## Veredicto de escucha (Frank, 2026-10-26)

Supertonic 3 (F1, M1) es la mejor escucha, Kokoro (ef_dora, em_santa) segunda, Piper malo en general
(solo es_MX-claude tolerable). Decisión: Supertonic 3 como motor principal.

## Supertonic: pasos de difusión vs velocidad (M1, mismo texto, 2 hilos)

| num_steps | RTF | Archivo |
|---|---|---|
| 4 | 0.092 | supertonic3-M1-steps4.wav |
| 6 | 0.132 | — |
| 8 | 0.172 | supertonic3-M1-sid5.wav |

`silence_scale` no afecta a Supertonic (duración idéntica): las pausas largas entre frases son del modelo.
En la app se sintetiza frase por frase, así que se pueden recortar los silencios de cola por frase.
sherpa-onnx v2.0.0 salió el 2026-10-23 y v2.0.1 el 2026-10-26: la API Kotlin ya expone
`OfflineTtsSupertonicModelConfig`, `GenerationConfig(numSteps, extra["lang"], phonemeCodepoints)` y `generateWithCallback`.

Los archivos de audio (.wav) no están en el repositorio por tamaño; se regeneran con `run_test.py`.
