package com.fcastellanos.voxlibris.reading

import android.content.Context
import android.net.Uri
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fcastellanos.voxlibris.data.BookRepository
import com.fcastellanos.voxlibris.data.EpubImporter
import com.fcastellanos.voxlibris.data.LibraryStorage
import com.fcastellanos.voxlibris.data.db.AppDatabase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@RunWith(AndroidJUnit4::class)
class ReaderRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var root: File
    private lateinit var storage: LibraryStorage
    private lateinit var books: BookRepository
    private lateinit var importer: EpubImporter
    private lateinit var repository: ReaderRepository
    private lateinit var sample: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).build()
        root = File(context.cacheDir, "reader-test-${UUID.randomUUID()}").also { it.mkdirs() }
        storage = LibraryStorage(filesDir = File(root, "files"), cacheDir = File(root, "cache"))
        val readium = Readium(context)
        books = BookRepository(db.bookDao(), storage)
        importer = EpubImporter(context, readium, storage, db.bookDao())
        repository = ReaderRepository(readium, books, storage)
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

    private suspend fun importSample(): Long =
        (importer.import(Uri.fromFile(sample)) as EpubImporter.Result.Success).bookId

    @Test
    fun opensImportedEpubWithTocAndPositions() = runBlocking {
        val id = importSample()
        val result = repository.open(id)
        assertTrue("resultado: $result", result is OpenBookResult.Success)
        val session = (result as OpenBookResult.Success).session
        assertEquals("Libro de prueba", session.title)
        assertEquals(2, session.publication.readingOrder.size)
        assertEquals(listOf("Capítulo 1", "Capítulo 2"), session.publication.tableOfContents.map { it.title })
        assertTrue(session.publication.positions().isNotEmpty())
        assertNull(session.initialLocator)
        assertSame(session, repository[id])
        repository.release(id)
        assertNull(repository[id])
    }

    @Test
    fun restoresSavedProgression() = runBlocking {
        val id = importSample()
        val locator = Locator(
            href = Url("cap2.xhtml")!!,
            mediaType = MediaType.XHTML,
            locations = Locator.Locations(totalProgression = 0.5),
        )
        books.saveProgression(id, locator.toJSON().toString())

        val session = (repository.open(id) as OpenBookResult.Success).session
        assertNotNull(session.initialLocator)
        assertEquals(0.5, session.initialLocator!!.locations.totalProgression!!, 1e-9)
        assertEquals(50, books.books.first().single().progressPercent)
        repository.release(id)
    }

    @Test
    fun failsForUnknownBook() = runBlocking {
        val result = repository.open(999_999L)
        assertTrue(result is OpenBookResult.Failure)
        assertEquals(OpenBookError.BookNotFound, (result as OpenBookResult.Failure).error)
    }
}
