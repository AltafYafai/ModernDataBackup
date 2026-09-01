package com.xayah.core.data.repository
import com.xayah.core.database.dao.AppDao
import com.xayah.core.database.entity.AppEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class AppsRepo @Inject constructor(private val appDao: AppDao) {
    fun getAllApps(): Flow<List<AppEntity>> = flow { emit(appDao.getAll()) }
    suspend fun insertApp(app: AppEntity) = appDao.insert(app)
    suspend fun insertAll(apps: List<AppEntity>) = appDao.insertAll(apps)
    suspend fun clearAll() = appDao.clearAll()
}
