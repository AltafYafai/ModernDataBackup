package com.xayah.core.database.dao
import androidx.room.*
import com.xayah.core.database.entity.AppEntity
@Dao
interface AppDao {
    @Query("SELECT * FROM appentity")
    suspend fun getAll(): List<AppEntity>
    @Query("SELECT * FROM appentity WHERE packageName = :pkg LIMIT 1")
    suspend fun getByPackage(pkg: String): AppEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: AppEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<AppEntity>)
    @Delete
    suspend fun delete(app: AppEntity)
    @Query("DELETE FROM appentity")
    suspend fun clearAll()
}
