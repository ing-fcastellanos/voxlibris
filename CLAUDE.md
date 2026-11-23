# VoxLibris

Lector de EPUB para Android que lee los libros en voz alta con una voz neuronal que corre en el teléfono: Jetpack Compose, Readium, Room y sherpa-onnx con Supertonic 3. El estado de cada fase está en `README.md`.

## Diseño

El diseño vive fuera del repo, en dos artefactos privados de claude.ai. Leelos con la herramienta Artifact, acción `read` y el `path` del archivo.

- **Sistema de diseño VoxLibris:** https://claude.ai/artifact/2yJX24JiybvjUAuGstaAkH
  Tiene los tokens de color claro y oscuro, tipografía, espacios, radios, sombras y tamaños, más 30 componentes con guía y vista previa, y las reglas de tono. Empezá siempre por `project/README.md`. Los valores exactos están en `project/tokens.json`. Las medidas de cada componente están en `project/components/bundle.css` y en `project/components/<Componente>/README.md`.
- **Lienzo VoxLibris — Diseño:** https://claude.ai/artifact/X5Y5V4evy9mVfcdHfK2wYf
  Tiene las 30 pantallas de referencia, ordenadas en filas por función: Primer uso, Biblioteca, Escuchar, Leer, Ajustes y Modo oscuro. Inicio es `project/Main.dc.html` y el resto son `project/app-*.dc.html`. Las notas del lienzo dicen qué pantallas todavía no existen en la app. El índice es `project/canvas.json`.

El sistema de diseño es la fuente de verdad. El lienzo lo usa para dibujar pantallas y el código lo sigue.

### Reglas

- Ningún color, tamaño de letra, radio, espacio ni sombra aparece en el código o en el lienzo si no está en el sistema. Si hace falta uno nuevo, se agrega primero al sistema, con su nota de uso.
- No uses el color dinámico de Material (`dynamicLightColorScheme`, `dynamicDarkColorScheme`). Reemplaza la paleta de la marca por los colores del fondo de pantalla.
- Cada componente de Compose lleva el mismo nombre que en el sistema: PlayButton, BookCover, BookRow, ContinueCard, MiniPlayer, PlayerPill, NavBar y el resto. Si el nombre chocaría con uno de Material, lleva el prefijo `Vox`: VoxButton, VoxIconButton, VoxSnackbar, VoxBottomSheet, VoxMenuItem, VoxConfirmDialog. Las clases `vx-` del lienzo se corresponden uno a uno con esos componentes.
- Donde Material ya tiene la pieza (ModalBottomSheet, AlertDialog, DropdownMenu, Snackbar, Switch), adaptala con los tokens en lugar de rehacerla.
- `tomato` significa escuchar. `mustard` como fondo de texto marca solo la frase que está sonando. `accent` es solo para agregar libros. La tarjeta del libro actual y el reproductor van siempre en `tomato`.
- El color y la forma de la portada generada de cada libro salen de un hash estable de su identificador. No hace falta guardarlos en la base de datos. Si el EPUB trae portada, se muestra la imagen real.
- El texto del libro lo dibuja Readium en una vista web, no Compose. El fondo, el color del texto, la tipografía y el interlineado van por las preferencias del navegador EPUB de Readium. Hay tres fondos de lectura (`ReaderPaper`): Cálido, Blanco y Oscuro, cada uno con sus tokens, y la interfaz del lector usa el tema de ese fondo. La frase que suena va en `reading-now` y los resaltados en `reading-highlight`, opacos y detrás del texto; `withPaperColors` los pinta según el fondo.
- Las fuentes van dentro del APK, nunca como fuentes descargables, porque la app funciona sin conexión. Bricolage Grotesque y Figtree están en `res/font` para Compose, con sus licencias OFL en `assets/licenses/`. Literata y una copia de Figtree, con sus itálicas, están en `assets/fonts/` para el texto del libro, porque la vista web de Readium no lee `res/font`; `ReaderFonts.register` las declara.
- Los textos van en `strings.xml`, en español rioplatense con voseo. Mayúscula solo al inicio de la frase. Números en formato local: «1,25×», «12 %», «≈ 14 min». Sin emoji.
- El tema oscuro usa los valores `dark` de cada token. En oscuro, el reproductor usa `ground` de fondo y deja `tomato` solo en el play y el avance.

### Capa de UI

El tema, la biblioteca, el lector, la pantalla de lectores y el primer inicio ya usan el sistema. El menú de selección de texto del lector es el del sistema operativo y no se estiliza, pero sus acciones, textos y orden son los del componente SelectionToolbar: si cambia uno, cambiá el otro.

```text
design/tokens.json            # copia de project/tokens.json del sistema
tools/gen_tokens.py           # genera Tokens.kt y vx_colors.xml a partir de la copia
app/src/main/res/font/        # Bricolage Grotesque y Figtree, variables
ui/theme/Tokens.kt            # generado: VoxColors, VoxDimens, VoxShadows, VoxTextSpecs, VoxTypography
res/values*/vx_colors.xml     # generado: los colores como recursos, claro y oscuro
ui/theme/Type.kt              # fuentes, estilos de texto y su mapeo a la tipografía de Material
ui/theme/Theme.kt             # VoxLibrisTheme: tokens propios y esquema de Material
ui/components/                # componentes del sistema y ComponentPreviews.kt, con vistas previas claras y oscuras
ui/reader/ReaderPaper.kt      # fondos de lectura, colores de las decoraciones y fuentes de Readium
ui/reader/AppearanceSheet.kt  # hoja de Apariencia, compartida por el lector y Ajustes
ui/MainNavBar.kt              # la barra de cuatro ítems: Inicio, Reproduciendo, Agregar libro y Ajustes
ui/settings/                  # Ajustes y Voces y almacenamiento; ui/voices/DefaultVoiceScreen.kt es Voz predeterminada
ui/player/                    # Reproduciendo: el reproductor a pantalla completa; reutiliza las hojas del lector
ui/voices/PreparingVoiceScreen.kt # «Preparando la voz»: tapa la app mientras se descargan las voces, en MainActivity y en el lector
settings/AppPreferencesStore.kt # tema, resaltado de la frase que suena y revisión del paquete de voces
assets/fonts/                 # Literata y Figtree para el texto del libro
```

- En Compose, los tokens se leen desde `VoxLibrisTheme`: `VoxLibrisTheme.colors.tomato`, `VoxLibrisTheme.typography.label`, `VoxLibrisTheme.dimens.space4`. Los nombres son los del sistema en camelCase.
- Los componentes de Material toman la paleta del mapeo de `Theme.kt`. `primary` es el negro de la marca, `primaryContainer` es `accent`, `secondary` es `tomato` y `secondaryContainer` es `track`. Si un componente de Material no queda como en el lienzo, se reemplaza por el componente del sistema, no se cambia el mapeo para ese caso.
- El color y la forma de cada libro salen de `bookStyleFor(título)`, y el color de cada voz de `voiceTint(id)`. No los guardes ni los elijas a mano.
- Si cambia `design/tokens.json`, corré `python3 tools/gen_tokens.py`. Con `--check` termina con error si los archivos generados quedaron desactualizados. `VoxColorsContrastTest` verifica que las parejas de texto y fondo del sistema pasen 4,5:1 en los dos temas.

Toda la interfaz que existe está migrada. Lo nuevo se construye directamente con el sistema, siguiendo los pasos de abajo.

### Pantallas o funciones nuevas

1. Diseñá primero en el lienzo, en la fila de la función o en una fila nueva. Usá las clases `vx-` y los tokens del sistema, que ya está instalado en `project/ds/voxlibris/`.
2. Si falta un componente o un token, agregalo primero al sistema. Un token lleva su nota de uso. Un componente lleva `README.md` y `preview.html`. Después actualizá la copia del lienzo: `tokens.json` y `components/bundle.css` se copian del sistema, `tokens.css` se vuelve a escribir a partir de `tokens.json` y se actualiza el registro `designSystems` de `project/canvas.json`.
3. Pasalo a código: copiá el `tokens.json` del sistema a `design/tokens.json`, corré `python3 tools/gen_tokens.py`, sumá el componente en `ui/components/` con el mismo nombre y recién después armá la pantalla.
4. Compará la vista previa de Compose, clara y oscura, con el artboard. Si no coinciden, manda el sistema, salvo que se decida cambiarlo ahí primero.

### Cuentas

Los dos artefactos de arriba están en la cuenta de trabajo de Frank. Los de su cuenta personal (el sistema https://claude.ai/artifact/Kq9WW8MgKsE8xbUYmVtufV y el lienzo https://claude.ai/artifact/39BfGSkFznBitvbXSbpmhM) quedaron como copias viejas: no los actualices. La página «Exploración» del lienzo viejo, con las propuestas descartadas, no se pasó.

### Decisiones abiertas

- Las voces Supertonic pesan unos 123 MB. Falta decidir si se descargan la primera vez, como muestra la pantalla «Preparando la voz», o si van dentro del APK.
