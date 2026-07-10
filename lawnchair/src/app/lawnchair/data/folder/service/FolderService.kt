package app.lawnchair.data.folder.service

import android.content.Context
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.folder.FolderEntry
import app.lawnchair.data.folder.FolderInfoEntity
import app.lawnchair.data.folder.FolderItemEntity
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
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

    fun getFoldersFlow(): Flow<List<FolderEntry>> {
        return folderDao.getAllFolders().map { folderEntities ->
            folderEntities.mapNotNull { folderEntity ->
                getFolderEntry(folderEntity.id)
            }
        }
    }

    suspend fun updateFolderWithItems(folderInfoId: Int, title: String, componentKeys: List<String>) = withContext(Dispatchers.IO) {
        folderDao.deleteFolderItemsByFolderId(folderInfoId)
        folderDao.insertFolderWithItems(
            FolderInfoEntity(id = folderInfoId, title = title),
            componentKeys.mapIndexed { index, componentKey ->
                FolderItemEntity(
                    folderId = folderInfoId,
                    rank = index,
                    componentKey = componentKey,
                )
            },
        )
    }

    suspend fun saveFolderInfo(title: String) = withContext(Dispatchers.IO) {
        folderDao.insertFolder(FolderInfoEntity(title = title))
    }

    suspend fun updateFolderInfo(folderId: Int, title: String, hide: Boolean = false) = withContext(Dispatchers.IO) {
        folderDao.updateFolderInfo(folderId, title, hide)
    }

    suspend fun deleteFolderInfo(id: Int) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(id)
    }

    suspend fun getFolderEntry(folderId: Int): FolderEntry? = withContext(Dispatchers.IO) {
        folderDao.getFolderWithItems(folderId)?.let { folderWithItems ->
            FolderEntry(
                id = folderWithItems.folder.id,
                title = folderWithItems.folder.title,
                hide = folderWithItems.folder.hide,
                itemComponentKeys = folderWithItems.items
                    .sortedBy { it.rank }
                    .mapNotNull { it.componentKey },
            )
        }
    }

    suspend fun getAllFolders(): List<FolderEntry> = withContext(Dispatchers.IO) {
        val folderEntities = folderDao.getAllFolders().firstOrNull() ?: emptyList()
        folderEntities.mapNotNull { folderEntity ->
            getFolderEntry(folderEntity.id)
        }
    }

    override fun close() {
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getFolderService)
    }
}
