package com.xayah.core.service
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
abstract class AbstractProcessingService : Service() {
    abstract suspend fun startProcessing(params: Map<String, Any>)
    open fun getProgress(): Flow<Int> = flowOf(0)
    override fun onBind(intent: Intent?): IBinder? = null
}
