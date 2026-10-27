package com.fcastellanos.readapp.data

import android.content.Context
import android.net.Uri
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fcastellanos.readapp.data.db.AppDatabase
import com.fcastellanos.readapp.reading.Readium
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Prueba de extremo a extremo del importador sobre un EPUB real (generado por tools/make_sample_epub.py). */
@RunWith(AndroidJUnit4::class)
class EpubImporterTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var root: File
    private lateinit var storage: LibraryStorage
    private lateinit var importer: EpubImporter
    private lateinit var sample: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).build()
        root = File(context.cacheDir, "importer-test-${UUID.randomUUID()}").also { it.mkdirs() }
        storage = LibraryStorage(filesDir = File(root, "files"), cacheDir = File(root, "cache"))
        importer = EpubImporter(context, Readium(context), storage, db.bookDao())
        sample = File(root, "sample.epub")
        InstrumentationRegistry.getInstrumentation().context.assets.open("sample.epub").use { input ->
            sample.outputStream().use { input.copyTo(it) }
        }
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    @Test
    fun importsMetadataCoverAndFile() = runBlocking {
        val result = importer.import(Uri.fromFile(sample))
        assertTrue("resultado: $result", result is EpubImporter.Result.Success)

        val book = db.bookDao().observeAll().first().single()
        assertEquals("Libro de prueba", book.title)
        assertEquals("Autora de Prueba", book.author)
        assertEquals("es", book.language)
        assertEquals("application/epub+zip", book.mediaType)
        assertTrue(storage.bookFile(book.fileName).length() > 0)
        assertNotNull("la portada debería haberse extraído", book.coverFileName)
        assertTrue(storage.coverFile(book.coverFileName)!!.length() > 0)
        assertTrue("el temporal debe borrarse", storage.importTempDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun rejectsDuplicateByIdentifier() = runBlocking {
        importer.import(Uri.fromFile(sample))
        val second = importer.import(Uri.fromFile(sample))
        assertTrue("resultado: $second", second is EpubImporter.Result.Failure)
        val error = (second as EpubImporter.Result.Failure).error
        assertTrue("error: $error", error is EpubImporter.ImportError.AlreadyInLibrary)
        assertEquals(1, db.bookDao().observeAll().first().size)
    }

    @Test
    fun rejectsNonEpub() = runBlocking {
        val text = File(root, "nota.txt").apply { writeText("esto no es un libro") }
        val result = importer.import(Uri.fromFile(text))
        assertTrue("resultado: $result", result is EpubImporter.Result.Failure)
        val error = (result as EpubImporter.Result.Failure).error
        assertTrue("error: $error", error is EpubImporter.ImportError.NotAnEpub)
        assertEquals(0, db.bookDao().observeAll().first().size)
    }
}
