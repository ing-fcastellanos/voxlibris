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
 * La migración automática 1→2 tiene que conservar la biblioteca y crear la tabla de resaltados.
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
    fun migrate1To2_keepsBooksAndAddsHighlights() = runBlocking {
        helper.createDatabase(1).use { db ->
            db.execSQL(
                "INSERT INTO books (created_at, file_name, title, author, media_type, progression) " +
                    "VALUES (1, 'rayuela.epub', 'Rayuela', 'Julio Cortázar', 'application/epub+zip', '{\"href\":\"/c1.xhtml\"}')",
            )
        }

        helper.runMigrationsAndValidate(2).use { db ->
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

    private fun SQLiteConnection.count(table: String): Long =
        prepare("SELECT COUNT(*) FROM $table").use { it.step(); it.getLong(0) }
}
