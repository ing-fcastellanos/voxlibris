package com.fcastellanos.voxlibris.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileNamesTest {

    @Test
    fun `replaces separators and drops extension`() {
        assertEquals("mi libro v2", FileNames.titleFromFileName("mi_libro-v2.epub"))
    }

    @Test
    fun `keeps only the last path segment`() {
        assertEquals("Quijote", FileNames.titleFromFileName("Download/libros/Quijote.epub"))
    }

    @Test
    fun `returns null for empty or useless names`() {
        assertNull(FileNames.titleFromFileName(null))
        assertNull(FileNames.titleFromFileName("   "))
        assertNull(FileNames.titleFromFileName("___.epub"))
    }
}
