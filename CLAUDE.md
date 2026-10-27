# VoxLibris

Lector de EPUB para Android que lee los libros en voz alta con una voz neuronal que corre en el teléfono: Jetpack Compose, Readium, Room y sherpa-onnx con Supertonic 3. El estado de cada fase está en `README.md`.

## Diseño

El diseño vive fuera del repo, en dos artefactos privados de claude.ai. Leelos con la herramienta Artifact, acción `read` y el `path` del archivo.

- **Sistema de diseño VoxLibris:** https://claude.ai/artifact/Kq9WW8MgKsE8xbUYmVtufV
  Tiene los tokens de color claro y oscuro, tipografía, espacios, radios, sombras y tamaños, más 24 componentes con guía y vista previa, y las reglas de tono. Empezá siempre por `project/README.md`. Los valores exactos están en `project/tokens.json`. Las medidas de cada componente están en `project/components/bundle.css` y en `project/components/<Componente>/README.md`.
- **Lienzo VoxLibris — Diseño:** https://claude.ai/artifact/39BfGSkFznBitvbXSbpmhM
  La página «VoxLibris · Diseño 6» tiene las 22 pantallas de referencia, en los archivos `project/app-*.dc.html`. La página «Exploración» guarda las propuestas descartadas. El índice del lienzo es `project/canvas.json`.

El sistema de diseño es la fuente de verdad. El lienzo lo usa para dibujar pantallas y el código lo sigue.

### Reglas

- Ningún color, tamaño de letra, radio, espacio ni sombra aparece en el código o en el lienzo si no está en el sistema. Si hace falta uno nuevo, se agrega primero al sistema, con su nota de uso.
- No uses el color dinámico de Material (`dynamicLightColorScheme`, `dynamicDarkColorScheme`). Reemplaza la paleta de la marca por los colores del fondo de pantalla.
- Cada componente de Compose lleva el mismo nombre que en el sistema: PlayButton, BookCover, BookRow, ContinueCard, MiniPlayer, PlayerPill, NavBar y el resto. Si el nombre chocaría con uno de Material, lleva el prefijo `Vox`: VoxButton, VoxIconButton, VoxSnackbar, VoxBottomSheet, VoxMenuItem, VoxConfirmDialog. Las clases `vx-` del lienzo se corresponden uno a uno con esos componentes.
- Donde Material ya tiene la pieza (ModalBottomSheet, AlertDialog, DropdownMenu, Snackbar, Switch), adaptala con los tokens en lugar de rehacerla.
- `tomato` significa escuchar. `mustard` como fondo de texto marca solo la frase que está sonando. `accent` es solo para agregar libros. La tarjeta del libro actual y el reproductor van siempre en `tomato`.
- El color y la forma de la portada generada de cada libro salen de un hash estable de su identificador. No hace falta guardarlos en la base de datos. Si el EPUB trae portada, se muestra la imagen real.
- El texto del libro lo dibuja Readium en una vista web, no Compose. La tipografía, el fondo y el interlineado van por las preferencias del navegador EPUB de Readium, con Figtree y Literata registradas como fuentes propias. La frase que suena es una decoración de resaltado en `mustard`.
- Las fuentes van dentro del APK, nunca como fuentes descargables, porque la app funciona sin conexión. Bricolage Grotesque y Figtree están en `res/font` para Compose, con sus licencias OFL en `assets/licenses/`. Literata, y la copia de Figtree que use el texto del libro, se sirven a Readium desde `assets/fonts/` al migrar el lector, porque su vista web no lee `res/font`.
- Los textos van en `strings.xml`, en español rioplatense con voseo. Mayúscula solo al inicio de la frase. Números en formato local: «1,25×», «12 %», «≈ 14 min». Sin emoji.
- El tema oscuro usa los valores `dark` de cada token. En oscuro, el reproductor usa `ground` de fondo y deja `tomato` solo en el play y el avance.

### Capa de UI

El tema ya aplica el sistema y la biblioteca ya usa sus componentes. El lector y la pantalla de lectores todavía usan Material directo.

```text
design/tokens.json            # copia de project/tokens.json del sistema
tools/gen_tokens.py           # genera Tokens.kt y vx_colors.xml a partir de la copia
app/src/main/res/font/        # Bricolage Grotesque y Figtree, variables
ui/theme/Tokens.kt            # generado: VoxColors, VoxDimens, VoxShadows, VoxTextSpecs, VoxTypography
res/values*/vx_colors.xml     # generado: los colores como recursos, claro y oscuro
ui/theme/Type.kt              # fuentes, estilos de texto y su mapeo a la tipografía de Material
ui/theme/Theme.kt             # VoxLibrisTheme: tokens propios y esquema de Material
ui/components/                # componentes del sistema y ComponentPreviews.kt, con vistas previas claras y oscuras
```

- En Compose, los tokens se leen desde `VoxLibrisTheme`: `VoxLibrisTheme.colors.tomato`, `VoxLibrisTheme.typography.label`, `VoxLibrisTheme.dimens.space4`. Los nombres son los del sistema en camelCase.
- Los componentes de Material toman la paleta del mapeo de `Theme.kt`. `primary` es el negro de la marca, `primaryContainer` es `accent`, `secondary` es `tomato` y `secondaryContainer` es `track`. Si un componente de Material no queda como en el lienzo, se reemplaza por el componente del sistema, no se cambia el mapeo para ese caso.
- El color y la forma de cada libro salen de `bookStyleFor(título)`, y el color de cada voz de `voiceTint(id)`. No los guardes ni los elijas a mano.
- Si cambia `design/tokens.json`, corré `python3 tools/gen_tokens.py`. Con `--check` termina con error si los archivos generados quedaron desactualizados. `VoxColorsContrastTest` verifica que las parejas de texto y fondo del sistema pasen 4,5:1 en los dos temas.

Orden de trabajo: el tema y la biblioteca ya están hechos. Sigue migrar el lector de la fase 3, con sus hojas de índice y de ajustes, que corresponden a Texto completo, Capítulos y Apariencia en el lienzo. Las fases siguientes se construyen directamente con el sistema. La fase 4 usa Reproductor, Elegir voz y Preparando la voz. La fase 5 usa el mini reproductor y el temporizador.

### Pantallas o funciones nuevas

1. Diseñá primero en el lienzo, en la página «VoxLibris · Diseño 6» o en una página nueva por función. Usá las clases `vx-` y los tokens del sistema, que ya está instalado en `project/ds/voxlibris/`.
2. Si falta un componente o un token, agregalo primero al sistema. Un token lleva su nota de uso. Un componente lleva `README.md` y `preview.html`. Después actualizá la copia del lienzo: `tokens.json` y `components/bundle.css` se copian del sistema, `tokens.css` se vuelve a escribir a partir de `tokens.json` y se actualiza el registro `designSystems` de `project/canvas.json`.
3. Pasalo a código: copiá el `tokens.json` del sistema a `design/tokens.json`, corré `python3 tools/gen_tokens.py`, sumá el componente en `ui/components/` con el mismo nombre y recién después armá la pantalla.
4. Compará la vista previa de Compose, clara y oscura, con el artboard. Si no coinciden, manda el sistema, salvo que se decida cambiarlo ahí primero.

### Decisiones abiertas

- Las voces Supertonic pesan unos 123 MB. Falta decidir si se descargan la primera vez, como muestra la pantalla «Preparando la voz», o si van dentro del APK.
