package com.fcastellanos.voxlibris.util

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Test

class SizesTest {

    @Test
    fun `under ten megabytes keeps one decimal in local format`() {
        assertEquals("0,4", Sizes.megabytes(419_430L))
        assertEquals("9,9", Sizes.megabytes((9.94 * 1_048_576).toLong()))
    }

    @Test
    fun `from ten megabytes up rounds to whole megabytes`() {
        assertEquals("10", Sizes.megabytes(10 * 1_048_576L))
        assertEquals("171", Sizes.megabytes((171.4 * 1_048_576).toLong()))
        assertEquals("1230", Sizes.megabytes(1230L * 1_048_576L).replace(".", ""))
    }

    @Test
    fun `folder size adds every file inside`() {
        val dir = createTempDirectory("sizes").toFile()
        try {
            File(dir, "a.bin").writeBytes(ByteArray(1000))
            File(dir, "sub").mkdirs()
            File(dir, "sub/b.bin").writeBytes(ByteArray(500))
            assertEquals(1500L, Sizes.of(dir))
            assertEquals(0L, Sizes.of(File(dir, "missing")))
            assertEquals(1500L, Sizes.of(listOf(File(dir, "a.bin"), File(dir, "sub"))))
        } finally {
            dir.deleteRecursively()
        }
    }
}
