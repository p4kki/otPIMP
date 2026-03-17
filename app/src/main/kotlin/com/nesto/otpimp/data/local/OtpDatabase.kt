package com.nesto.otpimp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nesto.otpimp.util.Constants

@Database(
    entities = [OtpEntity::class],
    version = Constants.DATABASE_VERSION,
    exportSchema = true
)
abstract class OtpDatabase : RoomDatabase() {
    
    abstract fun otpDao(): OtpDao
    
    companion object {
        @Volatile
        private var INSTANCE: OtpDatabase? = null
        
        fun getInstance(context: Context): OtpDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): OtpDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                OtpDatabase::class.java,
                Constants.DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
        
        fun destroyInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}