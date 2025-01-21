package app.lawnchair.data.folder.service

import android.content.Context
import android.content.pm.LauncherApps
import android.util.Log
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.Converters
import app.lawnchair.data.folder.FolderInfoEntity
import app.lawnchair.data.toEntity
import com.android.launcher3.AppFilter
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class FolderService(val context: Context) : SafeCloseable {

    val folderDao = AppDatabase.INSTANCE.get(context).folderDao()

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

    suspend fun getFolderInfo(folderId: Int, hasId: Boolean = false): FolderInfo? = withContext(Dispatchers.IO) {
        return@withContext try {
            val folderItems = folderDao.getFolderWithItems(folderId)

            folderItems?.let { items ->
                val folderInfo = FolderInfo().apply {
                    if (hasId) id = items.folder.id
                    title = items.folder.title
                }

                items.items.forEach { item ->
                    toItemInfo(item.componentKey)?.let { folderInfo.add(it) }
                }
                folderInfo
            }
        } catch (e: Exception) {
            Log.e("FolderService", "Failed to get folder info for folderId: $folderId", e)
            null
        }
    }

    private fun toItemInfo(componentKey: String?): AppInfo? {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (launcherApps != null) {
            return UserCache.INSTANCE.get(context).userProfiles.asSequence()
                .flatMap { launcherApps.getActivityList(null, it) }
                .filter { AppFilter(context).shouldShowApp(it.componentName) }
                .map { AppInfo(context, it, it.user) }
                .filter { Converters().fromComponentKey(it.componentKey) == componentKey }
                .firstOrNull()
        }
        return null
    }

    suspend fun getAllFolders(): List<FolderInfo> = withContext(Dispatchers.IO) {
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
        val INSTANCE = MainThreadInitializedObject(::FolderService)
    }
}
