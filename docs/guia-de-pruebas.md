# Guía de pruebas en dispositivo: POCO X3 Pro

Cubre las cinco fases de VoxLibris con el teléfono conectado por USB. Está pensada para hacerse en orden; cada fase asume que la anterior pasó. Al final hay una tabla para anotar resultados y qué adjuntar al reportar un fallo.

Dispositivo de referencia: POCO X3 Pro (Snapdragon 860, MIUI). El Galaxy S25 sirve para repetir solo las pruebas de rendimiento (sección 5).

---

## 0. Preparación

### En el teléfono

1. Activar opciones de desarrollador: Ajustes → Sobre el teléfono → tocar siete veces "Versión de MIUI".
2. Ajustes → Ajustes adicionales → Opciones de desarrollador, activar:
   - **Depuración USB**
   - **Instalar vía USB** (MIUI puede pedir iniciar sesión con cuenta Mi y tener SIM; sin esto `installDebug` falla con `INSTALL_FAILED_USER_RESTRICTED`)
   - **Depuración USB (ajustes de seguridad)**
3. Para las pruebas de segundo plano (sección 4), Ajustes → Batería → Ahorro de batería por aplicación → VoxLibris → **Sin restricciones**. En recientes, mantener pulsada la tarjeta de VoxLibris y tocar el candado para que MIUI no la cierre.
4. Conectar por USB en modo "Transferencia de archivos" y aceptar la huella de la PC cuando aparezca.

### En la PC

```bash
adb devices
```

Debe listar el equipo con estado `device`. Si dice `unauthorized`, revisar el diálogo en el teléfono.

Instalación limpia:

```bash
adb uninstall com.fcastellanos.voxlibris; ./gradlew :app:installDebug
```

Tests instrumentados (importador y repositorio de lectura):

```bash
./gradlew :app:connectedDebugAndroidTest
```

El reporte queda en `app/build/reports/androidTests/connected/`.

Libros de prueba:

```bash
adb push exploracion/libro-de-prueba.epub /sdcard/Download/
```

Para las pruebas largas de voz conviene un libro real en español de dominio público, por ejemplo el *Quijote* de Project Gutenberg (https://www.gutenberg.org/ebooks/2000, formato EPUB). Descargarlo en la PC y subirlo con `adb push` a `/sdcard/Download/`.

Logs durante toda la sesión, en otra terminal:

```bash
adb logcat -s VoxLibris.TTS:* AndroidRuntime:E
```

Los mensajes propios de la app salen con la etiqueta `VoxLibris.TTS`: carga del modelo, calibración (RTF), cada frase sintetizada con su tiempo y pasos, cada `play()` y `onDone`, interrupciones, descarga e instalación de voces, inicio y fin de la lectura. Las líneas con "reintento", "lo recreo" o "no responde" indican un atasco de la salida de audio y son siempre un fallo a reportar.

---

## 1. Fases 1 y 2: instalación, importación y biblioteca

| ID | Prueba | Resultado esperado |
|---|---|---|
| 1.1 | Abrir la app recién instalada | Icono "VoxLibris" (libro con ondas). Pantalla "Biblioteca" vacía con el texto de ayuda. Arriba a la derecha, icono de "Lectores". |
| 1.2 | "Agregar libro" → elegir `libro-de-prueba.epub` | Barra "Importando 1 de 1…", luego tarjeta con portada azul, título "Libro de prueba" y autora "Autora de Prueba". Snackbar "Libro agregado a la biblioteca". |
| 1.3 | Importar dos o más EPUB en una sola selección | "Importando x de N…" y al final "N libros agregados a la biblioteca". |
| 1.4 | Volver a importar `libro-de-prueba.epub` | Snackbar "«Libro de prueba» ya está en la biblioteca". No aparece tarjeta nueva. |
| 1.5 | Renombrar un `.txt` a `.epub` e importarlo | Snackbar "El archivo no es un EPUB (detectado: …)". Nada se agrega. |
| 1.6 | Desde el gestor de archivos, tocar un `.epub` → "Abrir con" → VoxLibris | La app se abre y el libro se importa. Repetir con la app ya abierta en segundo plano: también importa. |
| 1.7 | Mantener pulsada una tarjeta → "Eliminar" | La tarjeta desaparece y el snackbar lo confirma. Comprobar que el archivo se borró: `adb shell run-as com.fcastellanos.voxlibris ls files/books`. |
| 1.8 | Rotar la pantalla en la biblioteca; cerrar la app desde recientes y reabrir | Sin crash. Los libros persisten. |
| 1.9 | `./gradlew :app:connectedDebugAndroidTest` | 6 tests en verde: 3 de `EpubImporterTest`, 3 de `ReaderRepositoryTest`. |

---

## 2. Fase 3: lector

Usar el *Quijote* para 2.2 a 2.6; el libro de prueba sirve para lo demás.

| ID | Prueba | Resultado esperado |
|---|---|---|
| 2.1 | Tocar una tarjeta | "Abriendo libro…" bajo la barra y luego el lector: barra superior con el título y barra inferior con "Posición x de N" y porcentaje. |
| 2.2 | Tocar el borde derecho; luego el izquierdo; luego deslizar | Página siguiente, anterior, y deslizar también pasa de página. |
| 2.3 | Tocar el centro de la página dos veces | Primera vez: se ocultan barras de la app y del sistema. Segunda: vuelven. La página no se recoloca ni cambia de tamaño al ocultar o mostrar. |
| 2.4 | Icono de lista (índice) | Hoja con los capítulos; el actual resaltado. Tocar otro: salta y la hoja se cierra. Con el libro de prueba: "Capítulo 1" y "Capítulo 2". |
| 2.5 | Arrastrar el deslizador inferior | Al soltar, salta a esa posición y el texto "Posición x de N" se actualiza. |
| 2.6 | Icono "Aa": tema Sepia y Oscuro; letra − y +; desplazamiento continuo; estilos del editor; interlineado | Cada cambio se aplica al instante. Con Sepia y Oscuro los márgenes alrededor de la página toman el mismo color (sin franjas blancas). El interlineado solo aparece con "Respetar estilos del editor" desactivado. Cerrar y reabrir el lector: los ajustes se conservan. |
| 2.7 | Avanzar varias páginas y salir con Atrás | La tarjeta muestra el porcentaje leído. Reabrir: misma página. Cerrar la app desde recientes y repetir: misma página. |
| 2.8 | Rotar dentro del lector | Mantiene la posición. Sin crash. |
| 2.9 | Opciones de desarrollador → "No conservar actividades"; con el lector abierto ir al inicio y volver | El lector se cierra solo y vuelve a la biblioteca sin crash (es el comportamiento diseñado: tras una muerte de proceso la publicación ya no está abierta). Desactivar la opción al terminar. |
| 2.10 | Con un EPUB que tenga enlaces web, tocar uno | Se abre el navegador. |

---

## 3. Fase 4: voz

Hacer 3.1 a 3.4 en la pantalla "Lectores" (icono en la biblioteca) y el resto dentro del lector.

| ID | Prueba | Resultado esperado |
|---|---|---|
| 3.1 | Abrir "Lectores" sin voces instaladas | Tarjeta "Las voces se descargan una sola vez (122 MB)…" con botón "Descargar voces". Lista de diez voces deshabilitada. Velocidad y calidad visibles. |
| 3.2 | "Descargar voces" con Wi-Fi | Porcentaje creciente, luego "Instalando voces…", luego "Voces instaladas". En logcat: "Descargando voces desde …" y "Voces instaladas en …". Probar también "Cancelar" a mitad: vuelve a "no instaladas" y `adb shell run-as com.fcastellanos.voxlibris ls files/models` no deja restos. |
| 3.3 | Tocar ▶ junto a una voz | La primera vez tarda unos segundos (carga del modelo y calibración; en logcat "Modelo cargado…" y "Calibración: … RTF=…"). Se oye "Hola, soy Lucía…" con el nombre de la voz. Las siguientes muestras salen casi al instante. **Anotar el RTF.** |
| 3.4 | Elegir otra voz, velocidad 1,3× y calidad "Alta" | Las muestras reflejan voz y velocidad. |
| 3.5 | En el lector, tocar los auriculares **sin** voces instaladas (borrarlas antes desde "Lectores") | Diálogo "Descargar voces". Al aceptar, la barra inferior muestra el progreso y al terminar la lectura arranca sola. |
| 3.6 | Tocar los auriculares con voces instaladas | "Preparando la voz…" (uno a cuatro segundos la primera vez) y luego "Leyendo". La frase actual queda resaltada en amarillo, el resaltado avanza frase a frase y la página pasa sola al llegar al final. |
| 3.7 | Escuchar dos minutos seguidos | Sin silencios entre frases salvo, de vez en cuando, uno de menos de un segundo tras una frase muy corta (dos o tres palabras): es una limitación conocida, la frase siguiente no llega a sintetizarse en lo que dura la corta. Cualquier hueco mayor o frecuente se reporta con las líneas "Trozo sintetizado … RTF=… lead=…" del logcat. |
| 3.8 | Pausar y reanudar | La pausa corta el audio al instante. Al reanudar muestra "Preparando la voz…" entre 1 y 4 s (vuelve a sintetizar la frase) y retoma la frase en curso desde su inicio. |
| 3.9 | Frase anterior y frase siguiente | Corta el audio, muestra "Preparando la voz…" entre 1 y 3 s y arranca la frase destino; el resaltado acompaña. |
| 3.10 | Tocar el chip "Lucía · 1,0×" y cambiar voz y velocidad | Readium reinicia la frase en curso con la voz o velocidad nuevas: se oye un corte, "Preparando la voz…" entre 1 y 8 s (según lo larga que fuera la frase que se estaba sintetizando) y sigue desde el principio de esa frase. |
| 3.11 | Con la lectura en marcha, reproducir música en otra app; luego recibir una llamada | En ambos casos la lectura se pausa. No se reanuda sola (es lo esperado). |
| 3.12 | Escuchar el libro de prueba hasta el final | Al terminar el último párrafo la lectura se detiene y la barra de voz desaparece. |
| 3.13 | Comparar calidad "Rápida" y "Alta" con la misma frase | "Rápida" responde antes; "Alta" suena algo más limpia. En "Auto", el logcat muestra `pasos=4` si el RTF calibrado fue mayor que 0,5 y `pasos=8` si no. |
| 3.15 | Velocidad 1,6× a 2,0× | Se entienden todas las palabras y se oye completo el final de cada párrafo. El modelo sintetiza como mucho a 1,3× y el resto lo pone el reproductor estirando el audio sin cambiar el tono. |
| 3.16 | Frases largas con comas | Se oyen como una sola frase; en las comas puede notarse una pausa algo marcada (la frase se sintetiza en trozos cortados solo donde hay puntuación). No debe haber pausas donde el texto no las tiene. |
| 3.14 | Un EPUB en inglés | La voz lee en inglés (`lang=en` en logcat). |

---

## 4. Fase 5: segundo plano

| ID | Prueba | Resultado esperado |
|---|---|---|
| 4.1 | Primer "Escuchar" en Android 13 o superior | Aparece el permiso de notificaciones. Aceptar. |
| 4.2 | Al empezar a leer, bajar el panel de notificaciones | Notificación multimedia con título y autor del libro, portada y tres controles: frase anterior, ▶/⏸ y frase siguiente. Los tres funcionan; los de frase son comandos propios de VoxLibris (el adaptador de Readium no publica saltos), así que también responden a los botones de auriculares que envíen "anterior/siguiente". |
| 4.3 | Bloquear el teléfono | Sigue leyendo al menos diez minutos con la pantalla apagada. Los controles aparecen en la pantalla de bloqueo. Si se corta, revisar el paso 3 de la preparación (ahorro de batería de MIUI) y repetir. |
| 4.4 | Salir del lector con Atrás mientras lee | Sigue sonando. En la biblioteca aparece la barra "Leyendo: <título>" con ▶/⏸, "Abrir" y ✕. "Abrir" vuelve al lector con el resaltado en la frase actual. |
| 4.5 | Ir al inicio y tocar la notificación | Abre el lector del libro que suena. |
| 4.6 | ⏱ → "10 minutos" | El estado muestra "Se detiene en 10 min" y la cuenta baja. A los diez minutos se pausa. "Sin temporizador" cancela. |
| 4.7 | Descartar la app de recientes mientras lee; repetir mientras está en pausa | Leyendo: sigue (servicio en primer plano). En pausa: se detiene y la notificación desaparece. |
| 4.8 | Escuchar cinco minutos con el lector cerrado, detener con ✕ y abrir el libro | Se abre donde quedó la lectura (el progreso se guarda cada tres segundos en segundo plano). |
| 4.9 | Con un libro sonando, abrir otro y tocar los auriculares | El primero se detiene y empieza el segundo; la barra de la biblioteca cambia de título. |
| 4.10 | Auriculares Bluetooth con botón multimedia | Play y pausa desde el auricular funcionan. |

---

## 5. Rendimiento

| Medición | Cómo | Valor esperado en POCO X3 Pro |
|---|---|---|
| RTF calibrado | Línea "Calibración: … RTF=…" del logcat | Medido en el POCO: 0,82 con 8 pasos (2 hilos). Por encima de 0,5, "Auto" pasa a 4 pasos. |
| RTF por frase | Líneas "Frase sintetizada" | Medido en el POCO con 4 pasos y 4 hilos: entre 0,4 y 0,6 (frases cortas de un par de palabras dan valores altos pero irrelevantes). El valor `lead` resultante entre 3,5 y 6 s. |
| Memoria | `adb shell dumpsys meminfo com.fcastellanos.voxlibris \| head -30` durante la lectura | Entre 300 y 450 MB de PSS. |
| Batería | Anotar el porcentaje antes y después de 30 minutos de lectura con pantalla apagada | Para registrar; no hay objetivo todavía. |

Repetir la fila de RTF en el S25 para tener el techo.

---

## 6. Registro de resultados

| ID | OK / FALLA | Observaciones |
|---|---|---|
| 1.1 | | |
| … | | |

Para cada fallo, adjuntar:

- ID y pasos exactos para reproducirlo.
- Captura o video si es algo visual (resaltado, página, barras).
- Logcat del momento:

```bash
adb logcat -d -s VoxLibris.TTS:* AndroidRuntime:E > logcat.txt
```

- Si la app se cerró sola:

```bash
adb logcat -b crash -d > crash.txt
```

## Comandos útiles

```bash
adb shell run-as com.fcastellanos.voxlibris ls -la files files/books files/models/supertonic-3
```

```bash
adb shell am kill com.fcastellanos.voxlibris
```

```bash
adb shell dumpsys media_session | grep -A 12 voxlibris
```
