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
- Cada componente de Compose lleva el mismo nombre que en el sistema: PlayButton, BookCover, BookRow, ContinueCard, MiniPlayer, PlayerPill, NavBar y el resto. Las clases `vx-` del lienzo se corresponden uno a uno con esos componentes.
- Donde Material ya tiene la pieza (ModalBottomSheet, AlertDialog, DropdownMenu, Snackbar, Switch), adaptala con los tokens en lugar de rehacerla.
- `tomato` significa escuchar. `mustard` como fondo de texto marca solo la frase que está sonando. `accent` es solo para agregar libros. La tarjeta del libro actual y el reproductor van siempre en `tomato`.
- El color y la forma de la portada generada de cada libro salen de un hash estable de su identificador. No hace falta guardarlos en la base de datos. Si el EPUB trae portada, se muestra la imagen real.
- El texto del libro lo dibuja Readium en una vista web, no Compose. La tipografía, el fondo y el interlineado van por las preferencias del navegador EPUB de Readium, con Figtree y Literata registradas como fuentes propias. La frase que suena es una decoración de resaltado en `mustard`.
- Las fuentes Bricolage Grotesque, Figtree y Literata tienen licencia OFL y van empaquetadas en `res/font`, no como fuentes descargables, porque la app funciona sin conexión.
- Los textos van en `strings.xml`, en español rioplatense con voseo. Mayúscula solo al inicio de la frase. Números en formato local: «1,25×», «12 %», «≈ 14 min». Sin emoji.
- El tema oscuro usa los valores `dark` de cada token. En oscuro, el reproductor usa `ground` de fondo y deja `tomato` solo en el play y el avance.

### Estructura prevista de la capa de UI

Todavía no existe. Es el plan acordado para aplicar el sistema al código.

```text
design/tokens.json        # copia de project/tokens.json del sistema
tools/gen_tokens.py       # genera ui/theme/Tokens.kt a partir de la copia
app/src/main/res/font/    # Bricolage Grotesque, Figtree y Literata
ui/theme/Tokens.kt        # generado, no se edita a mano
ui/theme/Type.kt          # estilos de texto con los nombres del sistema
ui/theme/Theme.kt         # VoxLibrisTheme: tokens propios y esquema de Material
ui/components/            # un archivo por componente del sistema
```

Orden de trabajo: primero el tema. Después se migran al sistema las pantallas que se hicieron antes que él: la biblioteca y el lector de la fase 3, con sus hojas de índice y de ajustes, que corresponden a Texto completo, Capítulos y Apariencia en el lienzo. Las fases siguientes se construyen directamente con el sistema. La fase 4 usa Reproductor, Elegir voz y Preparando la voz. La fase 5 usa el mini reproductor y el temporizador.

### Pantallas o funciones nuevas

1. Diseñá primero en el lienzo, en la página «VoxLibris · Diseño 6» o en una página nueva por función. Usá las clases `vx-` y los tokens del sistema, que ya está instalado en `project/ds/voxlibris/`.
2. Si falta un componente o un token, agregalo primero al sistema. Un token lleva su nota de uso. Un componente lleva `README.md` y `preview.html`. Después actualizá la copia del lienzo: `tokens.json` y `components/bundle.css` se copian del sistema, `tokens.css` se vuelve a escribir a partir de `tokens.json` y se actualiza el registro `designSystems` de `project/canvas.json`.
3. Pasalo a código: actualizá `design/tokens.json`, regenerá los tokens, sumá el componente en `ui/components/` con el mismo nombre y recién después armá la pantalla.
4. Compará la vista previa de Compose, clara y oscura, con el artboard. Si no coinciden, manda el sistema, salvo que se decida cambiarlo ahí primero.

### Decisiones abiertas

- Las voces Supertonic pesan unos 123 MB. Falta decidir si se descargan la primera vez, como muestra la pantalla «Preparando la voz», o si van dentro del APK.
