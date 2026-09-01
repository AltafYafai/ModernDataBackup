package com.xayah.core.work
import android.content.Context
import androidx.startup.Initializer
import androidx.work.Configuration
class WorkManagerInitializer : Initializer<androidx.work.WorkManager> {
    override fun create(context: Context): androidx.work.WorkManager {
        androidx.work.WorkManager.initialize(context, Configuration.Builder().build())
        return androidx.work.WorkManager.getInstance(context)
    }
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
