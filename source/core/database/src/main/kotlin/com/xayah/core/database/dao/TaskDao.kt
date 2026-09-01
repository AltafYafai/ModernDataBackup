package com.xayah.core.database.dao
import androidx.room.*
import com.xayah.core.database.entity.TaskEntity
@Dao
interface TaskDao {
    @Query("SELECT * FROM taskentity ORDER BY timestamp DESC")
    suspend fun getAll(): List<TaskEntity>
    @Query("SELECT * FROM taskentity WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TaskEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long
    @Update
    suspend fun update(task: TaskEntity)
    @Delete
    suspend fun delete(task: TaskEntity)
    @Query("DELETE FROM taskentity")
    suspend fun clearAll()
}
