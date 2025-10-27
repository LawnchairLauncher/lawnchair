package app.lawnchair.data.folder.service

import android.content.Context
import android.content.pm.LauncherApps
import android.util.Log
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.Converters
import app.lawnchair.data.folder.FolderInfoEntity
import app.lawnchair.data.toEntity
import com.android.launcher3.AppFilter
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@LauncherAppSingleton
class FolderService @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val folderDao = AppDatabase.INSTANCE.get(context).folderDao()
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userCache = UserCache.INSTANCE.get(context)
    private val appFilter = AppFilter(context)
    private val converters = Converters()

    fun getFoldersFlow(): Flow<List<FolderInfo>> {
        return folderDao.getAllFolders().map { folderEntities ->
            folderEntities.mapNotNull { folderEntity ->
                getFolderInfo(folderEntity.id, true)
            }
        }
    }

    suspend fun updateFolderWithItems(folderInfoId: Int, title: String, appInfos: List<AppInfo>) = withContext(Dispatchers.IO) {
        folderDao.insertFolderWithItems(
            FolderInfoEntity(id = folderInfoId, title = title),
            appInfos.map {
                it.toEntity(folderInfoId)
            }.toList(),
        )
    }

    suspend fun saveFolderInfo(folderInfo: FolderInfo) = withContext(Dispatchers.IO) {
        folderDao.insertFolder(FolderInfoEntity(title = folderInfo.title.toString()))
    }

    suspend fun updateFolderInfo(folderInfo: FolderInfo, hide: Boolean = false) = withContext(Dispatchers.IO) {
        folderDao.updateFolderInfo(folderInfo.id, folderInfo.title.toString(), hide)
    }

    suspend fun deleteFolderInfo(id: Int) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(id)
    }

    suspend fun getFolderInfo(folderId: Int, hasId: Boolean = false): FolderInfo? = withContext(Dispatchers.Default) {
        folderDao.getFolderWithItems(folderId)?.let {
            mapToFolderInfo(it, hasId)
        }
    }

    private fun mapToFolderInfo(folderWithItems: FolderWithItems, hasId: Boolean): FolderInfo? {
        return try {
            val domainFolderInfo = FolderInfo().apply {
                // if no id, launcher automatically creates an id for this
                if (hasId) id = folderWithItems.folder.id
                title = folderWithItems.folder.title
            }

            folderWithItems.items.forEach { itemEntity ->
                // Consider caching toItemInfo results if componentKey lookups are slow
                // and items don't change frequently without folder data changing
                toItemInfo(itemEntity.componentKey)?.let { appInfo ->
                    domainFolderInfo.add(appInfo)
                }
            }
            domainFolderInfo
        } catch (e: Exception) {
            Log.e("FolderService", "Failed to map FolderWithItems for id: ${folderWithItems.folder.id}", e)
            null
        }
    }

    private fun toItemInfo(componentKey: String?): AppInfo? {
        if (launcherApps != null) {
            return userCache.userProfiles.asSequence()
                .flatMap { launcherApps.getActivityList(null, it) }
                .filter { appFilter.shouldShowApp(it.componentName) }
                .map { AppInfo(context, it, it.user) }
                .filter { converters.fromComponentKey(it.componentKey) == componentKey }
                .firstOrNull()
        }
        return null
    }

    suspend fun getAllFolders(): List<FolderInfo> = withContext(Dispatchers.Main) {
        try {
            val folderEntities = folderDao.getAllFolders().firstOrNull() ?: emptyList()
            folderEntities.mapNotNull { folderEntity ->
                getFolderInfo(folderEntity.id, true)
            }
        } catch (e: Exception) {
            Log.e("FolderService", "Failed to get all folders", e)
            emptyList()
        }
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getFolderService)
    }
}
