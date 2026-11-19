package com.jstensix.cardspending.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jstensix.cardspending.model.ExclusionItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The phone already holds a version 1 database; upgrading the app must keep every card and payment. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After fun tearDown() {
        context.deleteDatabase(name)
    }

    // Copied from app/schemas/.../1.json: the schema version 1 apps created.
    private val v1Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `issuer` TEXT NOT NULL, " +
            "`last4` TEXT NOT NULL, `nickname` TEXT NOT NULL, `exclusions` TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `issuer` TEXT NOT NULL, " +
            "`kind` TEXT NOT NULL, `amount` INTEGER NOT NULL, `merchant` TEXT NOT NULL, `mask` TEXT NOT NULL, " +
            "`installment` INTEGER NOT NULL, `approvedAt` TEXT NOT NULL, `cumulative` INTEGER, `source` TEXT NOT NULL, " +
            "`sourcePackage` TEXT, `rawText` TEXT, `manualRecognition` INTEGER, `originalId` INTEGER, " +
            "`deleted` INTEGER NOT NULL, `createdAt` TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_transactions_issuer_mask_approvedAt` ON `transactions` (`issuer`, `mask`, `approvedAt`)",
        "CREATE TABLE IF NOT EXISTS `unparsed` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `contentHash` TEXT NOT NULL, " +
            "`packageName` TEXT NOT NULL, `title` TEXT, `body` TEXT NOT NULL, `postedAt` TEXT NOT NULL, " +
            "`parserVersion` INTEGER NOT NULL, `dismissed` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_unparsed_contentHash` ON `unparsed` (`contentHash`)",
    )

    @Test fun migration1to2_keepsCardsAndAddsEmptyTiers() {
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            v1Schema.forEach(db::execSQL)
            db.execSQL("INSERT INTO cards (issuer, last4, nickname, exclusions) VALUES ('LOTTE', '7512', '텔로', 'TAX')")
            db.execSQL(
                "INSERT INTO transactions (issuer, kind, amount, merchant, mask, installment, approvedAt, source, deleted, createdAt) " +
                    "VALUES ('LOTTE', 'APPROVAL', 11000, '편의점', '7*1*', 0, '2026-11-18T10:54', 'MANUAL', 0, '2026-11-18T10:54')",
            )
            db.version = 1
        }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val card = runBlocking { room.cardDao().get(1) }!!
            assertEquals("텔로", card.nickname)
            assertEquals(setOf(ExclusionItem.TAX), card.exclusions)
            assertEquals(emptyList<Any>(), card.tiers)
            assertEquals(1, runBlocking { room.transactionDao().count() })
        } finally {
            room.close()
        }
    }
}
