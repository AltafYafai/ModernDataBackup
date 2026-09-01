package com.xayah.core.database.dao
import androidx.room.*
import com.xayah.core.database.entity.FileEntity
@Dao
interface FileDao {
    @Query("SELECT * FROM fileentity WHERE taskId = :taskId")
    suspend fun getByTaskId(taskId: String): List<FileEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: FileEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<FileEntity>)
    @Delete
    suspend fun delete(file: FileEntity)
    @Query("DELETE FROM fileentity WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: String)
}
