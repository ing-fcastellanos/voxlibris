package com.fcastellanos.voxlibris.data.db

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Las migraciones automáticas tienen que conservar la biblioteca y los resaltados:
 * 1→2 crea la tabla de resaltados y 2→3 le agrega la columna de nota.
 * Trabaja sobre una base propia (`migration-test.db`), nunca sobre la de la app.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath("migration-test.db"),
        driver = AndroidSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    @Test
    fun migrate1To3_keepsBooksAndAddsHighlights() = runBlocking {
        helper.createDatabase(1).use { db ->
            db.execSQL(
                "INSERT INTO books (created_at, file_name, title, author, media_type, progression) " +
                    "VALUES (1, 'rayuela.epub', 'Rayuela', 'Julio Cortázar', 'application/epub+zip', '{\"href\":\"/c1.xhtml\"}')",
            )
        }

        helper.runMigrationsAndValidate(3).use { db ->
            db.prepare("SELECT title, progression FROM books").use { row ->
                assertTrue(row.step())
                assertEquals("Rayuela", row.getText(0))
                assertEquals("{\"href\":\"/c1.xhtml\"}", row.getText(1))
                assertFalse(row.step())
            }
            assertEquals(0L, db.count("highlights"))

            // Un resaltado se guarda y se va con su libro.
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL(
                "INSERT INTO highlights (book_id, created_at, href, locator, text, color, total_progression) " +
                    "VALUES (1, 2, '/c1.xhtml', '{}', 'Un pasaje', 0, 0.5)",
            )
            assertEquals(1L, db.count("highlights"))
            db.execSQL("DELETE FROM books WHERE id = 1")
            assertEquals(0L, db.count("highlights"))
        }
    }

    @Test
    fun migrate2To3_keepsHighlightsAndAddsEmptyNote() = runBlocking {
        helper.createDatabase(2).use { db ->
            db.execSQL(
                "INSERT INTO books (created_at, file_name, title, media_type) " +
                    "VALUES (1, 'rayuela.epub', 'Rayuela', 'application/epub+zip')",
            )
            db.execSQL(
                "INSERT INTO highlights (book_id, created_at, href, locator, text, color, total_progression) " +
                    "VALUES (1, 2, '/c1.xhtml', '{\"href\":\"/c1.xhtml\"}', 'Un pasaje', 7, 0.5)",
            )
        }

        helper.runMigrationsAndValidate(3).use { db ->
            db.prepare("SELECT text, color, locator, note FROM highlights").use { row ->
                assertTrue(row.step())
                assertEquals("Un pasaje", row.getText(0))
                assertEquals(7L, row.getLong(1))
                assertEquals("{\"href\":\"/c1.xhtml\"}", row.getText(2))
                assertTrue(row.isNull(3))
                assertFalse(row.step())
            }
            db.execSQL("UPDATE highlights SET note = 'Me recuerda a Lugano' WHERE id = 1")
            db.prepare("SELECT note FROM highlights WHERE id = 1").use { row ->
                assertTrue(row.step())
                assertEquals("Me recuerda a Lugano", row.getText(0))
            }
        }
    }

    @Test
    fun migrate4To5_keepsBooksAndAddsFinishedAndChapter() = runBlocking {
        helper.createDatabase(4).use { db ->
            db.execSQL(
                "INSERT INTO books (created_at, file_name, title, author, media_type, progression, voice_mode, narrator_voice_id, dialogue_voice_id) " +
                    "VALUES (1, 'niebla.epub', 'Niebla', 'Miguel de Unamuno', 'application/epub+zip', '{}', 'NARRATOR_DIALOGUE', 0, 5)",
            )
        }

        helper.runMigrationsAndValidate(5).use { db ->
            db.prepare("SELECT title, voice_mode, finished_at, chapter_index, chapter_count, chapter_title FROM books").use { row ->
                assertTrue(row.step())
                assertEquals("Niebla", row.getText(0))
                assertEquals("NARRATOR_DIALOGUE", row.getText(1))
                assertTrue(row.isNull(2))
                assertTrue(row.isNull(3))
                assertTrue(row.isNull(4))
                assertTrue(row.isNull(5))
                assertFalse(row.step())
            }
            db.execSQL("UPDATE books SET finished_at = 7, chapter_index = 12, chapter_count = 33, chapter_title = 'XII' WHERE id = 1")
            db.prepare("SELECT finished_at, chapter_index FROM books").use { row ->
                assertTrue(row.step())
                assertEquals(7L, row.getLong(0))
                assertEquals(12L, row.getLong(1))
            }
        }
    }

    private fun SQLiteConnection.count(table: String): Long =
        prepare("SELECT COUNT(*) FROM $table").use { it.step(); it.getLong(0) }
}
