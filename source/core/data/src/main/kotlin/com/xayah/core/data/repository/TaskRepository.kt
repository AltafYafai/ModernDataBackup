package com.xayah.core.data.repository
import com.xayah.core.database.dao.TaskDao
import com.xayah.core.database.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class TaskRepository @Inject constructor(private val taskDao: TaskDao) {
    fun getAllTasks(): Flow<List<TaskEntity>> = flow { emit(taskDao.getAll()) }
    suspend fun insert(task: TaskEntity): Long = taskDao.insert(task)
    suspend fun update(task: TaskEntity) = taskDao.update(task)
    suspend fun delete(task: TaskEntity) = taskDao.delete(task)
}
