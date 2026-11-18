package com.fcastellanos.voxlibris.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {

    @Query("SELECT * FROM highlights WHERE book_id = :bookId ORDER BY total_progression ASC, created_at ASC")
    fun observeForBook(bookId: Long): Flow<List<HighlightEntity>>

    @Insert
    suspend fun insert(highlight: HighlightEntity): Long

    @Query("UPDATE highlights SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun delete(id: Long)
}
