package com.xayah.core.work.workers
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
@HiltWorker
class AppsInitWorker @AssistedInject constructor(@Assisted ctx: Context, @Assisted params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = Result.success()
}
