package com.jstensix.cardspending.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jstensix.cardspending.model.Card
import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.UnparsedNotification

@Database(entities = [Card::class, Transaction::class, UnparsedNotification::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun transactionDao(): TransactionDao
    abstract fun unparsedDao(): UnparsedDao

    companion object {
        /** 2: cards gain 실적 구간 (JSON list). Existing cards start with none. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN tiers TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** 3: cards gain a user-chosen order. Seeded from id so the upgrade shows cards exactly as before. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE cards SET sortOrder = id")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "cardspending.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
