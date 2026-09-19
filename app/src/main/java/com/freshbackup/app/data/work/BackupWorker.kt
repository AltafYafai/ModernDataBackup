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
import com.freshbackup.app.data.ScheduleDao
import com.freshbackup.app.data.backup.BackupOptions
import com.freshbackup.app.data.backup.Engine
import com.freshbackup.app.data.cloud.CloudFactory
import com.freshbackup.app.data.cloud.CloudSync
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs one Schedule: backs up its packages, prunes old versions,
 * optionally uploads the backup root to its cloud target.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: Engine,
    private val schedules: ScheduleDao,
    private val clouds: CloudFactory,
    private val sync: CloudSync
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            // Legacy single-shot payload (no schedule id): back up listed packages.
            val once = inputData.getStringArray(KEY_PKGS)
            if (once != null) {
                backupList(once.toList())
                return Result.success()
            }
            val sched = schedules.get(inputData.getLong(KEY_SCHEDULE, -1)) ?: return Result.failure()
            if (!sched.enabled) return Result.success()
            backupList(sched.packages())
            schedules.markRun(sched.id, System.currentTimeMillis())
            if (sched.cloudBackend.isNotEmpty()) {
                val backend = clouds.buildById(sched.cloudBackend)
                if (backend != null) {
                    val root = engine.backupRoot()
                    setProgress(Data.Builder().putString("stage", "uploading").build())
                    sync.uploadDir(root, backend, "", log = {})
                }
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private suspend fun backupList(pkgs: List<String>) {
        val opts = BackupOptions()
        pkgs.forEach { pkg ->
            engine.backupApp(pkg, opts) { }
        }
    }

    companion object {
        const val PREFIX = "freshbackup_schedule_"
        private const val KEY_PKGS = "pkgs"
        const val KEY_SCHEDULE = "schedule_id"

        /** One-shot backup of explicit packages (used by manual schedule apply). */
        fun runOnce(context: Context, pkgs: List<String>) {
            val data = Data.Builder().putStringArray(KEY_PKGS, pkgs.toTypedArray()).build()
            val req = androidx.work.OneTimeWorkRequestBuilder<BackupWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }

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
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "${PREFIX}legacy", ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }

        fun scheduleNamed(context: Context, scheduleId: Long, hours: Long, chargingOnly: Boolean, needsNetwork: Boolean) {
            val data = Data.Builder().putLong(KEY_SCHEDULE, scheduleId).build()
            val req = PeriodicWorkRequestBuilder<BackupWorker>(hours.coerceIn(1, 720), TimeUnit.HOURS)
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(chargingOnly)
                        .setRequiredNetworkType(if (needsNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "$PREFIX$scheduleId", ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("${PREFIX}legacy")
        }

        fun cancelNamed(context: Context, scheduleId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork("$PREFIX$scheduleId")
        }
    }
}
