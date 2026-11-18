package com.jstensix.cardspending

import android.content.Context
import com.jstensix.cardspending.backup.BackupManager
import com.jstensix.cardspending.backup.BackupPrefs
import com.jstensix.cardspending.backup.BackupScheduler
import com.jstensix.cardspending.backup.DriveAuth
import com.jstensix.cardspending.backup.DriveClient
import com.jstensix.cardspending.data.AppDatabase
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.ingest.Ingestor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependencies for the whole app. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase = AppDatabase.create(appContext)
    val repository = SpendingRepository(database) { BackupScheduler.request(appContext) }
    val ingestor = Ingestor(repository)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val backupPrefs = BackupPrefs(appContext)
    val driveClient = DriveClient()
    val driveAuth = DriveAuth(appContext, backupPrefs)
    val backupManager = BackupManager(repository, driveClient, driveAuth, backupPrefs)
}
