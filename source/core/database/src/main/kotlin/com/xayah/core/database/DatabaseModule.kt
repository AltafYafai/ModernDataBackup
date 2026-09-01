package com.xayah.core.database
import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "databackup.db").build()

    @Provides fun provideTaskDao(db: AppDatabase) = db.taskDao()
    @Provides fun provideAppDao(db: AppDatabase) = db.appDao()
    @Provides fun provideFileDao(db: AppDatabase) = db.fileDao()
}
