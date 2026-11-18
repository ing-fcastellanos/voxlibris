package com.fcastellanos.voxlibris.tts

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
import com.fcastellanos.voxlibris.reading.OpenBookResult
import com.fcastellanos.voxlibris.reading.ReaderRepository
import com.fcastellanos.voxlibris.reading.Readium
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.html.cssSelector
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content

/** Verifica, con el análisis real de Readium, que una frase del medio de un párrafo se ubica y se cuentan las frases previas. */
@RunWith(AndroidJUnit4::class)
class StartPointResolverTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ReaderRepository
    private lateinit var importer: EpubImporter
    private lateinit var sample: File

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).build()
        val root = File(context.cacheDir, "resolver-test-${UUID.randomUUID()}").also { it.mkdirs() }
        val storage = LibraryStorage(filesDir = File(root, "files"), cacheDir = File(root, "cache"))
        val readium = Readium(context)
        val books = BookRepository(db.bookDao(), storage)
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
    }

    private suspend fun openSample(): Publication {
        val bookId = (importer.import(Uri.fromFile(sample)) as EpubImporter.Result.Success).bookId
        return (repository.open(bookId) as OpenBookResult.Success).session.publication
    }

    @Test
    fun secondSentenceOfParagraphSkipsOne() = runBlocking {
        val publication = openSample()
        val chapter = publication.locatorFromLink(publication.readingOrder[1])!!
        val elements = mutableListOf<Content.TextElement>()
        val iterator = publication.content(chapter)!!.iterator()
        while (iterator.hasNext() && elements.size < 8) (iterator.next() as? Content.TextElement)?.let(elements::add)
        val paragraph = elements.first { element -> element.segments.any { it.text.contains("Eran las 11:45") } }
        val element = paragraph.segments.first().locator
        assertNotNull("el elemento trae cssSelector", element.locations.cssSelector)
        assertNotNull("el elemento trae progresión", element.locations.progression)

        val second = StartPointResolver.resolve(publication, element, "Él sonrió y respondió que, con un buen lector, los capítulos pasan volando.")
        assertEquals(1, second.skipSentences)
        assertEquals(element.locations.cssSelector, second.locator.locations.cssSelector)

        val first = StartPointResolver.resolve(publication, element, "Eran las 11:45 y quedaban 312 páginas.")
        assertEquals(0, first.skipSentences)

        // Desde una posición de página (sin selector) también se encuentra, recorriendo el recurso desde el principio.
        val page = chapter.copy(locations = Locator.Locations(progression = 0.5))
        val fromPage = StartPointResolver.resolve(publication, page, "Él sonrió y respondió")
        assertEquals(1, fromPage.skipSentences)
        assertEquals(element.locations.cssSelector, fromPage.locator.locations.cssSelector)
    }
}
