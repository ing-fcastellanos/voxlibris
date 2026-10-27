package com.fcastellanos.voxlibris.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY COALESCE(last_opened_at, created_at) DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE identifier = :identifier LIMIT 1")
    suspend fun findByIdentifier(identifier: String): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE books SET progression = :progression, last_opened_at = :openedAt WHERE id = :id")
    suspend fun updateProgression(id: Long, progression: String?, openedAt: Long)
}
