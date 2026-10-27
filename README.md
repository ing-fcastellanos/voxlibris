# ReadApp (nombre provisional)

> Lector de EPUB para Android que lee los libros en voz alta con una IA que corre **dentro del teléfono**. Sin cuentas, sin claves de API, sin nube.
>
> *EPUB reader for Android with fully on-device neural text-to-speech. No accounts, no API keys, no cloud.*

## Por qué

Las apps de lectura que "leen en voz alta" suelen depender de un servicio en la nube, de una suscripción o del motor de voz del sistema. ReadApp apunta a otra cosa: un lector de EPUB normal, con voces neuronales de buena calidad que se ejecutan localmente en el dispositivo y que el usuario elige como quien elige un narrador.

## Estado

El proyecto está en construcción. La exploración técnica ya cerró y la base del proyecto compila.

| Fase | Contenido | Estado |
|---|---|---|
| 0 | Exploración de motores TTS on-device y prueba de escucha en español | Hecha ([detalle](exploracion/tts-test/README.md)) |
| 1 | Entorno: Android SDK, Gradle, matriz de versiones | Hecha |
| 2 | Esqueleto: Compose, Readium, sherpa-onnx, Room, importación de EPUB y biblioteca | Compila y pasan los tests; pendiente de prueba en teléfono |
| 3 | Lector EPUB: navegador de Readium, índice, ajustes de lectura, progreso | Pendiente |
| 4 | Voz: motor `TtsEngine` de Readium sobre sherpa-onnx + Supertonic 3, catálogo de lectores | Pendiente |
| 5 | Reproducción en segundo plano, controles multimedia, temporizador de sueño | Pendiente |
| 6 | Opcional: Kokoro con fonetizador español propio; exponer las voces como motor TTS del sistema | Pendiente |

## Cómo funciona

```
EPUB en el almacenamiento
        │  Storage Access Framework
        ▼
 Readium (streamer)  ──►  metadatos + portada  ──►  Room (biblioteca)
        │
        ▼
 Readium (navigator)  ──►  pantalla de lectura
        │
        ▼
 Readium TtsNavigator  ──►  TtsEngine propio  ──►  sherpa-onnx  ──►  Supertonic 3 (ONNX, int8)
   (frases, resaltado,        (Kotlin)              (C++ / JNI)         modelo descargado bajo demanda
    sincronía de página)
```

Readium se encarga de partir el texto en frases, resaltar la que se está leyendo y pasar de página. La app solo implementa la interfaz `TtsEngine`, que convierte cada frase en audio con sherpa-onnx y lo reproduce.

## Decisiones técnicas

**Motor de voz.** Se compararon los modelos TTS abiertos que corren en un teléfono y tienen español. Resultado de la prueba de escucha (mismo texto, CPU, 2 hilos):

| Modelo | Tamaño | Velocidad relativa (RTF) | Veredicto |
|---|---|---|---|
| Supertonic 3 (int8, 31 idiomas) | 123 MB | 0.18 | **Elegido**: mejor naturalidad, no necesita fonetizador externo |
| Kokoro v1.0 (int8) | 126 MB | 0.70 | Segunda opción; español flojo y requiere fonemas externos |
| Piper es_ES / es_MX (int8) | 20–33 MB | 0.13 | Descartado por calidad de voz |

Gemma quedó descartado para esta función porque no genera audio; sus variantes multimodales solo lo reciben como entrada.

**Stack.** Kotlin con Jetpack Compose y Material 3, Room 3, Readium Kotlin Toolkit 3.4.0, sherpa-onnx 2.0.1. La matriz de versiones (`gradle/libs.versions.toml`) copia la que usa el propio Readium 3.4.0, para construir sobre una combinación probada. AGP 9 con Kotlin integrado.

**Dispositivos.** Desarrollado y probado contra un POCO X3 Pro (piso de rendimiento) y un Galaxy S25 (techo). Solo ABI `arm64-v8a`; `minSdk 26`, `targetSdk 36`, `compileSdk 37`.

**Idioma.** Español primero. El modelo elegido cubre 31 idiomas, así que ampliar es cuestión de interfaz.

## Estructura

```
app/src/main/java/com/fcastellanos/readapp/
├── ReadApp.kt              Application; crea el contenedor de dependencias
├── MainActivity.kt         Única Activity; maneja ACTION_VIEW de EPUB
├── di/AppContainer.kt      Composición manual de dependencias
├── reading/Readium.kt      AssetRetriever + PublicationOpener
├── data/
│   ├── db/                 Room 3: BookEntity, BookDao, AppDatabase
│   ├── EpubImporter.kt     Copia, valida, extrae metadatos y portada, inserta
│   ├── BookRepository.kt   Flujo de libros y borrado
│   └── LibraryStorage.kt   Carpetas privadas (books/, covers/)
└── ui/
    ├── theme/              Material 3 con color dinámico
    └── library/            Pantalla de biblioteca y su ViewModel
app/src/androidTest/        Test instrumentado del importador con un EPUB de muestra
exploracion/                Prueba de escucha de TTS (script, resultados) y EPUB de prueba
tools/make_sample_epub.py   Genera el EPUB de muestra (solo librería estándar de Python)
```

## Compilar

Requisitos: JDK 17 o superior y el Android SDK. Creá `local.properties` con `sdk.dir=/ruta/a/Android/Sdk` si Gradle no lo encuentra solo.

```bash
./gradlew :app:assembleDebug
```

La primera vez, Gradle descarga las dependencias y también el AAR de sherpa-onnx (37 MB) desde sus releases de GitHub, verificando su SHA-256; no está en el repositorio.

Tests unitarios:

```bash
./gradlew :app:testDebugUnitTest
```

Con un teléfono conectado por USB y depuración activada, instalar y correr el test instrumentado del importador:

```bash
./gradlew :app:installDebug :app:connectedDebugAndroidTest
```

Para probar la importación a mano hay un EPUB pequeño en `exploracion/libro-de-prueba.epub`.

## Modelos de voz

No se incluyen en el APK. La app los descargará bajo demanda (fase 4) desde los paquetes que publica sherpa-onnx, por ejemplo `sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2` (123 MB). Los pesos de Supertonic 3 se distribuyen bajo licencia OpenRAIL-M; sherpa-onnx es Apache 2.0; Readium es BSD-3.

## Licencia

Pendiente de definir.

## Créditos

- [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit), de EDRLab: renderizado de EPUB y navegador de TTS.
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), de k2-fsa: inferencia de voz on-device.
- [Supertonic](https://github.com/supertone-inc/supertonic), de Supertone: modelo de voz.
