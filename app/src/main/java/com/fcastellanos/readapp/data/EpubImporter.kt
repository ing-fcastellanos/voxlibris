package com.fcastellanos.readapp.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.fcastellanos.readapp.data.db.BookDao
import com.fcastellanos.readapp.data.db.BookEntity
import com.fcastellanos.readapp.reading.Readium
import com.fcastellanos.readapp.util.FileNames
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.format.Specification
import org.readium.r2.shared.util.getOrElse

/**
 * Copia un EPUB elegido por el usuario a la carpeta de la app, lo abre con Readium
 * para extraer metadatos y portada, y lo registra en la base de datos.
 */
class EpubImporter(
    private val context: Context,
    private val readium: Readium,
    private val storage: LibraryStorage,
    private val dao: BookDao,
) {

    sealed interface Result {
        data class Success(val bookId: Long, val title: String) : Result
        data class Failure(val error: ImportError) : Result
    }

    sealed interface ImportError {
        data object CannotRead : ImportError
        data class NotAnEpub(val detected: String?) : ImportError
        data class OpenFailed(val message: String) : ImportError
        data class AlreadyInLibrary(val title: String) : ImportError
    }

    private class Extracted(
        val title: String?,
        val author: String?,
        val language: String?,
        val identifier: String?,
        val mediaType: String,
        val coverFileName: String?,
    )

    suspend fun import(uri: Uri): Result = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri)
        val temp = copyToTemp(uri) ?: return@withContext Result.Failure(ImportError.CannotRead)
        try {
            val asset = readium.assetRetriever.retrieve(temp)
                .getOrElse { return@withContext Result.Failure(ImportError.NotAnEpub(null)) }

            if (!asset.format.conformsTo(Specification.Epub)) {
                val detected = asset.format.mediaType.toString()
                asset.close()
                return@withContext Result.Failure(ImportError.NotAnEpub(detected))
            }
            val mediaType = asset.format.mediaType.toString()

            val publication = readium.publicationOpener.open(asset, allowUserInteraction = false)
                .getOrElse { return@withContext Result.Failure(ImportError.OpenFailed(it.message)) }

            // Todo lo que necesite leer el archivo ocurre antes de moverlo.
            val extracted = try {
                val metadata = publication.metadata
                val identifier = metadata.identifier?.takeIf { it.isNotBlank() }
                if (identifier != null) {
                    dao.findByIdentifier(identifier)?.let { existing ->
                        return@withContext Result.Failure(ImportError.AlreadyInLibrary(existing.title))
                    }
                }
                Extracted(
                    title = metadata.title?.takeIf { it.isNotBlank() },
                    author = metadata.authors.map { it.name }.filter { it.isNotBlank() }
                        .joinToString(", ").ifBlank { null },
                    language = metadata.languages.firstOrNull(),
                    identifier = identifier,
                    mediaType = mediaType,
                    coverFileName = storeCover(publication),
                )
            } finally {
                publication.close()
            }

            val fileName = "${UUID.randomUUID()}.epub"
            val target = storage.bookFile(fileName)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
            }

            val entity = BookEntity(
                createdAt = System.currentTimeMillis(),
                fileName = fileName,
                title = extracted.title
                    ?: FileNames.titleFromFileName(displayName)
                    ?: context.getString(com.fcastellanos.readapp.R.string.unknown_title),
                author = extracted.author,
                language = extracted.language,
                identifier = extracted.identifier,
                mediaType = extracted.mediaType,
                coverFileName = extracted.coverFileName,
            )
            val id = try {
                dao.insert(entity)
            } catch (e: Exception) {
                target.delete()
                storage.coverFile(extracted.coverFileName)?.delete()
                throw e
            }
            Result.Success(id, entity.title)
        } finally {
            temp.delete()
        }
    }

    private suspend fun storeCover(publication: Publication): String? {
        val bitmap = publication.cover() ?: return null
        val scaled = scaleToFit(bitmap, maxWidth = 360, maxHeight = 540)
        val fileName = "${UUID.randomUUID()}.png"
        val file = storage.coverFile(fileName) ?: return null
        return try {
            FileOutputStream(file).use { out -> scaled.compress(Bitmap.CompressFormat.PNG, 90, out) }
            fileName
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    private fun scaleToFit(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val ratio = minOf(maxWidth / bitmap.width.toFloat(), maxHeight / bitmap.height.toFloat(), 1f)
        if (ratio >= 1f) return bitmap
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun copyToTemp(uri: Uri): File? {
        val temp = File(storage.importTempDir, "${UUID.randomUUID()}.epub")
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { source -> temp.outputStream().use { sink -> source.copyTo(sink) } }
            temp
        } catch (e: Exception) {
            temp.delete()
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
}
