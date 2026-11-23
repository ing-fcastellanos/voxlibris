# Guía de pruebas en dispositivo: POCO X3 Pro

Cubre las cinco fases de VoxLibris con el teléfono conectado por USB. Está pensada para hacerse en orden; cada fase asume que la anterior pasó. Al final hay una tabla para anotar resultados y qué adjuntar al reportar un fallo.

Dispositivo de referencia: POCO X3 Pro (Snapdragon 860, MIUI). El Galaxy S25 sirve para repetir solo las pruebas de rendimiento (sección 6).

---

## 0. Preparación

### En el teléfono

1. Activar opciones de desarrollador: Ajustes → Sobre el teléfono → tocar siete veces "Versión de MIUI".
2. Ajustes → Ajustes adicionales → Opciones de desarrollador, activar:
   - **Depuración USB**
   - **Instalar vía USB** (MIUI puede pedir iniciar sesión con cuenta Mi y tener SIM; sin esto `installDebug` falla con `INSTALL_FAILED_USER_RESTRICTED`)
   - **Depuración USB (ajustes de seguridad)**
3. Para las pruebas de segundo plano (sección 4), Ajustes → Batería → Ahorro de batería por aplicación → VoxLibris → **Sin restricciones**. **Este ajuste se pierde al desinstalar y volver a instalar la app**: sin él, MIUI deshabilita el wake lock y baja el CPU con la pantalla apagada, la síntesis pasa de 0,4 a entre 3 y 8 veces el tiempo real y la lectura suena a tirones (unos segundos de audio, silencio y salto adelante). En recientes, mantener pulsada la tarjeta de VoxLibris y tocar el candado para que MIUI no la cierre.
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
| 1.10 | Primer inicio (o tras borrar los datos de la app) | Pantalla "Antes de empezar" con dos pasos. Paso 1, batería: "Permitir sin restricciones" abre el diálogo del sistema; al aceptarlo el paso queda con tilde "Listo: sin restricciones de batería" (en MIUI, Ajustes → Batería → VoxLibris pasa a "Sin restricciones"). "Abrir ajustes de batería" abre la pantalla de MIUI de la app. |
| 1.11 | En "Antes de empezar", paso 2 → "Descargar voces" | Barra de avance con "Descargando las voces…" y "NN MB de 122 MB"; después "Instalando las voces…" con su porcentaje; al final tilde "Listo: voces instaladas". "Cancelar" vuelve al botón de descarga. "Continuar" lleva a la biblioteca y la pantalla no vuelve a aparecer; la descarga, si quedó en curso, sigue y se ve en Lectores. |

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
| 2.11 | Mantener pulsado un texto → "Resaltar" | Se abre el diálogo "Resaltado guardado" con el pasaje y un campo para una nota personal; "Sin nota" lo cierra y el pasaje queda marcado en verde menta igual. El icono de marcador de la barra superior abre "Resaltados" con la lista: tocar uno lleva al pasaje; el tacho lo borra. Tocar un resaltado en el texto abre un diálogo con el pasaje y "Eliminar resaltado". Los resaltados sobreviven a cerrar y reabrir el libro, y se borran con el libro. |
| 2.12 | Resaltar y escribir una nota → "Guardar"; abrir "Resaltados" | La nota aparece en cursiva debajo del pasaje. El lápiz de la lista y "Editar nota" en el diálogo del texto abren el editor con la nota actual; guardarla vacía la quita. Las notas persisten al reabrir el libro. |
| 2.13 | Mirar en el texto un resaltado con nota | Al final del pasaje aparece un icono de nota en verde profundo, como en Kindle; los resaltados sin nota no lo tienen. Tocar el icono (o el pasaje) abre el diálogo con la nota. Al borrar la nota el icono desaparece. |
| 2.14 | Mantener pulsada una sola palabra (p. ej. «casa», «corrían») | Además del menú, aparece al pie una tarjeta con la palabra, su categoría gramatical y las primeras acepciones del Wikcionario; una conjugación o plural muestra «Forma del verbo…» y debajo las acepciones del lema. Extender la selección a más palabras cierra la tarjeta; tocar fuera también. Sin internet la tarjeta dice que hace falta conexión. "Ver en Wikcionario" abre el navegador. |

---

## 3. Fase 4: voz

Hacer 3.1 a 3.4 en la pantalla "Lectores" (icono en la biblioteca) y el resto dentro del lector.

Verificada en el POCO X3 Pro: 3.18 a 3.20 OK el 18/11/2026 (retomar tras detener, arrancar a mitad de página y tiempo que falta para terminar el libro).

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
| 3.17 | Mantener pulsada una palabra del texto | Aparece el menú de selección con "Leer desde acá", "Copiar" y "Compartir". "Leer desde acá" arranca la lectura en voz alta en esa frase (o la mueve ahí si ya estaba leyendo); "Copiar" deja el texto en el portapapeles; "Compartir" abre el selector de apps. |
| 3.18 | Detener la voz (✕) a mitad de un capítulo y volver a tocar los auriculares sin cambiar de página | Retoma en la misma frase donde se detuvo, no desde el inicio del capítulo. |
| 3.19 | Sin voz activa, ir a una página del medio de un capítulo y tocar los auriculares | Empieza a leer en esa página (o un párrafo antes), no desde el inicio del capítulo. Si se pasa de página antes de tocar, arranca en la página nueva. |
| 3.20 | Arrancar la voz y mirar la píldora de estado; cambiar la velocidad | A los pocos segundos dice "Leyendo · faltan ≈ N h M min" (o "≈ N min"). Es el tiempo para terminar el libro al ritmo real de la voz y la velocidad elegida: subir la velocidad lo baja en proporción, y va bajando a medida que avanza la lectura. En pausa se mantiene; mientras prepara la voz no se muestra. |
| 3.16 | Frases largas con comas | Se oyen como una sola frase; en las comas puede notarse una pausa algo marcada (la frase se sintetiza en trozos cortados solo donde hay puntuación). No debe haber pausas donde el texto no las tiene. |
| 3.14 | Un EPUB en inglés | La voz lee en inglés (`lang=en` en logcat). |

---

## 4. Fase 5: segundo plano

Verificada en el POCO X3 Pro el 17/11/2026: 4.1 a 4.10 OK. La 4.11 salió de esa ronda (la página no seguía a la voz al volver con la lectura en pausa) y quedó corregida.

| ID | Prueba | Resultado esperado |
|---|---|---|
| 4.1 | Primer "Escuchar" en Android 13 o superior | Aparece el permiso de notificaciones. Aceptar. |
| 4.2 | Al empezar a leer, bajar el panel de notificaciones | Notificación multimedia con título y autor del libro, portada y tres controles: frase anterior, ▶/⏸ y frase siguiente. Los tres funcionan; los de frase son comandos propios de VoxLibris (el adaptador de Readium no publica saltos), así que también responden a los botones de auriculares que envíen "anterior/siguiente". |
| 4.3 | Bloquear el teléfono | Sigue leyendo al menos diez minutos con la pantalla apagada, sin cortes ni saltos. Los controles aparecen en la pantalla de bloqueo. Si suena a tirones o se corta, revisar el paso 3 de la preparación (ahorro de batería de MIUI; se reinicia con cada instalación limpia) y repetir. Para confirmarlo desde la PC: `adb shell dumpsys deviceidle whitelist` debe listar la app, y en `adb logcat -s VoxLibris.TTS` el RTF de "Trozo sintetizado" debe seguir por debajo de 1 con la pantalla apagada. |
| 4.4 | Salir del lector con Atrás mientras lee | Sigue sonando. En la biblioteca aparece el mini reproductor con el título, ▶/⏸ y ✕. Tocarlo vuelve al lector con el resaltado en la frase actual. |
| 4.5 | Ir al inicio y tocar la notificación | Abre el lector del libro que suena. |
| 4.6 | ⏱ → "10 minutos" | El estado muestra "Se detiene en 10 min" y la cuenta baja. A los diez minutos se pausa. "Sin temporizador" cancela. |
| 4.7 | Descartar la app de recientes mientras lee; repetir mientras está en pausa | Leyendo: sigue (servicio en primer plano). En pausa: se detiene y la notificación desaparece. |
| 4.8 | Escuchar cinco minutos con el lector cerrado, detener con ✕ y abrir el libro | Se abre donde quedó la lectura (el progreso se guarda cada tres segundos en segundo plano). |
| 4.9 | Con un libro sonando, abrir otro y tocar los auriculares | El primero se detiene y empieza el segundo; la barra de la biblioteca cambia de título. |
| 4.10 | Auriculares Bluetooth con botón multimedia | Play y pausa desde el auricular funcionan. |
| 4.11 | Escuchar varios minutos con la pantalla bloqueada, pausar desde la pantalla de bloqueo y volver a la app (con el lector abierto detrás, o desde la biblioteca tocando el mini reproductor) | El lector muestra la página de la frase donde se pausó, con el resaltado visible. Si en cambio se pausó dentro del lector y se hojeó otra página antes de bloquear, al volver se respeta esa página. |

---

## 5. Ajustes

La barra de abajo tiene cuatro ítems: Inicio, Reproduciendo, Agregar libro y Ajustes. «Lectores» ya no existe como pantalla: lo que tenía está repartido entre Ajustes y Voz predeterminada.

| ID | Prueba | Resultado esperado |
|---|---|---|
| 5.1 | Tocar «Ajustes» en la barra | Pantalla con tres grupos: Voz (Voz predeterminada, Velocidad, Alta calidad, Recortar silencios), Lectura (Apariencia del texto, Resaltar la frase que suena) y App (Tema, Voces y almacenamiento, Acerca de VoxLibris). La barra marca Ajustes; Inicio vuelve a la biblioteca. |
| 5.2 | «Voz predeterminada» | Pantalla con el modo (Un lector / Narrador y diálogos), las diez voces y la muestra; sin velocidad ni calidad. La fila de Ajustes resume lo elegido: «Un lector · Lucía» o «Narrador y diálogos · Lucía y Mateo». Un libro con ajustes propios (hoja «¿Quién lee?» del lector) no cambia. |
| 5.3 | «Velocidad» → elegir 1,5× | Menú desplegable con las mismas velocidades que el reproductor. El valor de la fila cambia y, si hay un libro sonando, la frase se reinicia a la velocidad nueva. |
| 5.4 | «Alta calidad» encendido y apagado | Encendido, el logcat muestra `pasos=8` en la siguiente frase; apagado vuelve a Auto (`pasos=4` en el POCO). |
| 5.5 | «Recortar silencios» apagado | Entre frases se nota una pausa algo más larga (el silencio que deja el modelo). Encendido, la lectura encadena como siempre. |
| 5.6 | «Apariencia del texto» | Abre la misma hoja que el lector (tamaño, tipografía, fondo, interlineado, desplazamiento, estilos del editor). Lo que se cambia acá se ve al abrir cualquier libro, y el resumen de la fila («Literata · 110 % · fondo cálido») se actualiza. |
| 5.7 | «Resaltar la frase que suena» apagado y escuchar | El texto no marca la frase en amarillo, pero la página sigue a la voz. Encendido vuelve a marcarla. |
| 5.8 | «Tema» → Oscuro, Claro, Según el sistema | La biblioteca y Ajustes cambian al instante. El lector no cambia: sigue el fondo de lectura elegido en Apariencia. |
| 5.9 | «Voces y almacenamiento» | Tarjeta con el total en uso y la barra repartida entre voces (tomate) y libros (cobalto), con leyenda. La fila del paquete dice «Instalada» en verde (o «Sin instalar»). «N libros» con su peso; «Borrar archivos temporales» con el peso de los restos (normalmente 0,0 MB) y, al tocarlo, aviso «Archivos temporales borrados». |
| 5.10 | «Buscar actualizaciones» | Compara el paquete instalado con el que trae la app: aviso «Las voces están al día» y la fila pasa a decir «Última revisión: hoy». Solo ofrece actualizar cuando la app trae un paquete distinto. |
| 5.11 | «Eliminar las voces» | Diálogo de confirmación con los MB; al confirmar, la fila del paquete pasa a «Sin instalar», la barra queda solo con libros y el reproductor pide descargar las voces al escuchar. |
| 5.12 | «Acerca de VoxLibris» | Diálogo con la versión, qué es la app, con qué está hecha y «Ver el código», que abre el repositorio en el navegador. |
| 5.13 | «Reproduciendo» en la barra | Con un libro sonando abre su lector; sin nada sonando, abre el libro de la tarjeta de Inicio; sin libros, avisa «Todavía no hay nada para escuchar». |

---

## 6. Rendimiento

| Medición | Cómo | Valor esperado en POCO X3 Pro |
|---|---|---|
| RTF calibrado | Línea "Calibración: … RTF=…" del logcat | Medido en el POCO: 0,82 con 8 pasos (2 hilos). Por encima de 0,5, "Auto" pasa a 4 pasos. |
| RTF por frase | Líneas "Frase sintetizada" | Medido en el POCO con 4 pasos y 4 hilos: entre 0,4 y 0,6 (frases cortas de un par de palabras dan valores altos pero irrelevantes). El valor `lead` resultante entre 3,5 y 6 s. |
| Memoria | `adb shell dumpsys meminfo com.fcastellanos.voxlibris \| head -30` durante la lectura | Entre 300 y 450 MB de PSS. |
| Batería | Anotar el porcentaje antes y después de 30 minutos de lectura con pantalla apagada | Para registrar; no hay objetivo todavía. |

Repetir la fila de RTF en el S25 para tener el techo.

---

## 7. Registro de resultados

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
