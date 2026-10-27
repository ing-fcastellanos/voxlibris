"""Listening test: Spanish TTS candidates via sherpa-onnx 2.0.1 (CPU, 2 threads)."""
import json, os, sys, time
import sherpa_onnx, soundfile as sf
from piper_phonemize import phonemize_espeak

M = "models"; OUT = "results"; os.makedirs(OUT, exist_ok=True)
THREADS = 2

TEXT = (
    "En un lugar de la Mancha, de cuyo nombre no quiero acordarme, no ha mucho tiempo que vivía "
    "un hidalgo de los de lanza en astillero, adarga antigua, rocín flaco y galgo corredor. "
    "Una olla de algo más vaca que carnero, salpicón las más noches, duelos y quebrantos los sábados, "
    "lantejas los viernes, algún palomino de añadidura los domingos, consumían las tres partes de su hacienda. "
    "—¿De verdad vas a leer todo el libro esta noche? —preguntó Marta, sorprendida. "
    "Eran las 11:45 y quedaban 312 páginas. Él sonrió y respondió que, con un buen lector, "
    "los capítulos pasan volando."
)

def model_cfg(**kw):
    return sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
        num_threads=THREADS, provider="cpu", debug=False, **kw))

def run(name, tts, text, sid, lang=None, phonemize_lang=None, num_steps=None):
    g = sherpa_onnx.GenerationConfig(); g.sid = sid; g.speed = 1.0
    if num_steps: g.num_steps = num_steps
    if lang: g.extra["lang"] = lang
    if phonemize_lang:
        sents = phonemize_espeak(text, phonemize_lang)
        g.phoneme_codepoints = [[ord(c) for c in s] for s in sents]
        text_in = ""
    else:
        text_in = text
    t0 = time.time(); a = tts.generate(text_in, g); el = time.time() - t0
    if len(a.samples) == 0:
        print(f"!! {name}: no audio"); return None
    dur = len(a.samples) / a.sample_rate
    path = f"{OUT}/{name}.wav"
    sf.write(path, a.samples, a.sample_rate, subtype="PCM_16")
    r = dict(name=name, sample_rate=a.sample_rate, audio_s=round(dur, 2), synth_s=round(el, 2), rtf=round(el / dur, 3))
    print(json.dumps(r, ensure_ascii=False)); return r

results = []

# --- Piper (VITS) : needs external phonemizer in sherpa-onnx >= 2.0
for d, base, sids in [("vits-piper-es_ES-sharvard-medium-int8", "es_ES-sharvard-medium", [0, 1]),
                      ("vits-piper-es_MX-claude-high-int8", "es_MX-claude-high", [0])]:
    tts = sherpa_onnx.OfflineTts(model_cfg(vits=sherpa_onnx.OfflineTtsVitsModelConfig(
        model=f"{M}/{d}/{base}.onnx", tokens=f"{M}/{d}/tokens.txt")))
    for sid in sids:
        results.append(run(f"piper-{base}-sid{sid}", tts, TEXT, sid, phonemize_lang=tts.lang or "es"))

# --- Kokoro v1.0 multi-lang : Spanish voices 28 ef_dora (f), 29 em_alex (m); external phonemizer
k = f"{M}/kokoro-int8-multi-lang-v1_0"
tts = sherpa_onnx.OfflineTts(model_cfg(kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(
    model=f"{k}/model.int8.onnx", voices=f"{k}/voices.bin", tokens=f"{k}/tokens.txt")))
for sid, vn in [(28, "ef_dora"), (29, "em_alex"), (53, "em_santa")]:
    results.append(run(f"kokoro-v1.0-{vn}-sid{sid}", tts, TEXT, sid, phonemize_lang="es"))

# --- Supertonic 3 : native text frontend, lang=es
s = f"{M}/sherpa-onnx-supertonic-3-tts-int8-2026-05-11"
tts = sherpa_onnx.OfflineTts(model_cfg(supertonic=sherpa_onnx.OfflineTtsSupertonicModelConfig(
    duration_predictor=f"{s}/duration_predictor.int8.onnx", text_encoder=f"{s}/text_encoder.int8.onnx",
    vector_estimator=f"{s}/vector_estimator.int8.onnx", vocoder=f"{s}/vocoder.int8.onnx",
    tts_json=f"{s}/tts.json", unicode_indexer=f"{s}/unicode_indexer.bin", voice_style=f"{s}/voice.bin")))
SUPERTONIC_SIDS = [int(x) for x in os.environ.get("SUPERTONIC_SIDS", "0,5").split(",")]
for sid in SUPERTONIC_SIDS:
    results.append(run(f"supertonic3-sid{sid}", tts, TEXT, sid, lang="es", num_steps=8))

json.dump([r for r in results if r], open(f"{OUT}/results.json", "w"), indent=1, ensure_ascii=False)
