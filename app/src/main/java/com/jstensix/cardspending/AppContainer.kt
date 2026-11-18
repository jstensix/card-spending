package com.jstensix.cardspending

import android.content.Context
import com.jstensix.cardspending.data.AppDatabase
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.ingest.Ingestor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependencies for the whole app. */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val repository = SpendingRepository(database) {}
    val ingestor = Ingestor(repository)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
