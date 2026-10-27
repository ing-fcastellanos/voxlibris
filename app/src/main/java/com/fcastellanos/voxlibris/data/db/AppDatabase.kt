package com.fcastellanos.voxlibris.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase

@Database(entities = [BookEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder<AppDatabase>(context.applicationContext, "voxlibris.db")
                    .build()
                    .also { instance = it }
            }
    }
}
