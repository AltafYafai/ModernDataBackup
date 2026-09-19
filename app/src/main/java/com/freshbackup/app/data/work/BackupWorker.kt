package com.freshbackup.app.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.freshbackup.app.data.backup.BackupOptions
import com.freshbackup.app.data.backup.Engine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Recurring device backup. Cloud upload of the same files is the next step. */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: Engine
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val pkgs = inputData.getStringArray(KEY_PKGS) ?: return Result.failure()
            val opts = BackupOptions()
            var failed = 0
            pkgs.forEach { pkg ->
                val r = engine.backupApp(pkg, opts) { }
                if (r.isFailure) failed++
            }
            if (failed == pkgs.size && pkgs.isNotEmpty()) Result.retry() else Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val NAME = "freshbackup_schedule"
        private const val KEY_PKGS = "pkgs"

        fun schedule(context: Context, pkgs: List<String>, hours: Long, chargingOnly: Boolean) {
            val data = Data.Builder().putStringArray(KEY_PKGS, pkgs.toTypedArray()).build()
            val req = PeriodicWorkRequestBuilder<BackupWorker>(hours, TimeUnit.HOURS)
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(chargingOnly)
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
