package com.fcastellanos.voxlibris.ui.reader

import android.os.Parcel
import android.os.Parcelable
import android.util.Base64
import androidx.annotation.ColorInt
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.html.HtmlDecorationTemplate

/** Estilo de decoración propio: un icono de nota al final del pasaje resaltado, como en Kindle. */
data class NoteMarkStyle(@ColorInt val tint: Int) : Decoration.Style {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(tint)
    }

    companion object CREATOR : Parcelable.Creator<NoteMarkStyle> {
        override fun createFromParcel(source: Parcel): NoteMarkStyle = NoteMarkStyle(source.readInt())

        override fun newArray(size: Int): Array<NoteMarkStyle?> = arrayOfNulls(size)
    }
}

object NoteMarkDecoration {
    private const val CLASS_NAME = "vox-note-mark"
    private const val ICON_PATH =
        "M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z"

    /**
     * Readium clona el elemento por cada línea que ocupa el pasaje; solo la última muestra el icono,
     * pegado al final del texto. El relleno extra hace que el icono también responda al toque.
     */
    fun template(): HtmlDecorationTemplate = HtmlDecorationTemplate(
        layout = HtmlDecorationTemplate.Layout.BOXES,
        width = HtmlDecorationTemplate.Width.WRAP,
        element = { decoration ->
            val tint = (decoration.style as? NoteMarkStyle)?.tint ?: 0xFF000000.toInt()
            """<div class="$CLASS_NAME" style="--vox-note-icon: url(${iconDataUrl(tint)})"/>"""
        },
        stylesheet = """
            .$CLASS_NAME { box-sizing: content-box; }
            .$CLASS_NAME:last-child {
                padding-right: 22px;
                background: var(--vox-note-icon) no-repeat right 2px center / 16px 16px;
            }
        """.trimIndent(),
    )

    private fun iconDataUrl(@ColorInt tint: Int): String {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path fill="${cssColor(tint)}" d="$ICON_PATH"/></svg>"""
        return "data:image/svg+xml;base64," + Base64.encodeToString(svg.toByteArray(), Base64.NO_WRAP)
    }

    private fun cssColor(@ColorInt color: Int): String = "#%06X".format(color and 0xFFFFFF)
}
