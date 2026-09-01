package com.xayah.core.database
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.xayah.core.database.dao.AppDao
import com.xayah.core.database.dao.FileDao
import com.xayah.core.database.dao.TaskDao
import com.xayah.core.database.entity.AppEntity
import com.xayah.core.database.entity.FileEntity
import com.xayah.core.database.entity.TaskEntity
import com.xayah.core.database.util.Converters
@Database(entities = [TaskEntity::class, AppEntity::class, FileEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun appDao(): AppDao
    abstract fun fileDao(): FileDao
}
