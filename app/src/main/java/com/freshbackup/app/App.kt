package com.freshbackup.app

import android.content.Context
import androidx.room.Room
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.freshbackup.app.data.AppDb
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FreshBackupApp : android.app.Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): AppDb =
        Room.databaseBuilder(context, AppDb::class.java, "freshbackup.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideBackupDao(db: AppDb) = db.backups()
}
