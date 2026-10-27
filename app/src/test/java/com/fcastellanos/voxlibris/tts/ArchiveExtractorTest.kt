package com.fcastellanos.voxlibris.tts

import java.io.File
import java.nio.file.Files
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveExtractorTest {

    private fun tarBz2(vararg entries: Pair<String, String>): File {
        val file = Files.createTempFile("pkg", ".tar.bz2").toFile()
        TarArchiveOutputStream(BZip2CompressorOutputStream(file.outputStream())).use { tar ->
            for ((name, content) in entries) {
                val bytes = content.toByteArray()
                val entry = TarArchiveEntry(name).apply { size = bytes.size.toLong() }
                tar.putArchiveEntry(entry)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
        return file
    }

    @Test
    fun `strips the root directory and keeps nested paths`() {
        val archive = tarBz2(
            "sherpa-onnx-supertonic-3/tts.json" to "{}",
            "sherpa-onnx-supertonic-3/voice.bin" to "abc",
            "sherpa-onnx-supertonic-3/sub/x.txt" to "x",
        )
        val out = Files.createTempDirectory("out").toFile()
        ArchiveExtractor.extractTarBz2(archive, out)
        assertEquals("{}", File(out, "tts.json").readText())
        assertEquals("abc", File(out, "voice.bin").readText())
        assertEquals("x", File(out, "sub/x.txt").readText())
        assertFalse(File(out, "sherpa-onnx-supertonic-3").exists())
    }

    @Test
    fun `rejects entries that escape the target directory`() {
        val archive = tarBz2("pkg/../../evil.txt" to "nope")
        val out = Files.createTempDirectory("out").toFile()
        assertThrows(java.io.IOException::class.java) { ArchiveExtractor.extractTarBz2(archive, out) }
        assertTrue(out.listFiles().isNullOrEmpty())
    }
}
