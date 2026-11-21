package com.basem.qiyam

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Refreshes everything in the background every few hours (when there's internet), so charts
 * get a point every day and the app opens up to date. Each source fails on its own without
 * stopping the others.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        syncAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "istiqamah-sync"

        fun schedule(ctx: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }

        /** Everything a sync does, shared by the background worker and opening the app. */
        suspend fun syncAll(ctx: Context, onlyIfStaleMinutes: Long = 0) {
            val p = Prefs(ctx)
            val now = System.currentTimeMillis()
            fun stale(last: Long) = now - last > onlyIfStaleMinutes * 60_000L
            runCatching { QiyamLink.sync(ctx) }
            runCatching { Health.sync(ctx) }
            if (p.lyftaKey.isNotBlank() && stale(p.lyftaSyncedAt)) runCatching { Lyfta.sync(ctx) }
            if (GitHub.user(p).isNotBlank() && stale(p.getLong("ghSynced"))) runCatching { GitHub.sync(ctx) }
            val plats = Wealth.platforms(p)
            if (p.getStr("t212Key").isNotBlank() && stale(plats.firstOrNull { it.id == "t212" }?.syncedAt ?: 0L)) runCatching { Wealth.syncTrading212(ctx) }
            if (p.getStr("etoroUserKey").isNotBlank() && stale(plats.firstOrNull { it.id == "etoro" }?.syncedAt ?: 0L)) runCatching { Wealth.syncEtoro(ctx) }
        }
    }
}
