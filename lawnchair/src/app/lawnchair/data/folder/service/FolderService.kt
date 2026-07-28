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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@LauncherAppSingleton
class FolderService @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val folderDao = AppDatabase.INSTANCE.get(context).folderDao()

    fun getFoldersFlow(): Flow<List<FolderEntry>> {
        return folderDao.getAllFoldersWithItems().map { list ->
            list.map { it.toFolderEntry() }
        }
    }

    suspend fun updateFolderWithItems(folderInfoId: Int, title: String, componentKeys: List<String>) = withContext(Dispatchers.IO) {
        val items = componentKeys.mapIndexed { index, componentKey ->
            FolderItemEntity(
                folderId = folderInfoId,
                rank = index,
                componentKey = componentKey,
            )
        }
        folderDao.replaceFolderItems(folderInfoId, title, items)
    }

    suspend fun saveFolderInfo(title: String) = withContext(Dispatchers.IO) {
        folderDao.insertFolder(FolderInfoEntity(title = title))
    }

    suspend fun renameFolderInfo(folderId: Int, title: String) = withContext(Dispatchers.IO) {
        folderDao.updateFolderTitle(folderId, title)
    }

    suspend fun deleteFolderInfo(id: Int) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(id)
    }

    private fun FolderWithItems.toFolderEntry() = FolderEntry(
        id = folder.id,
        title = folder.title,
        hide = folder.hide,
        itemComponentKeys = items
            .sortedBy { it.rank }
            .mapNotNull { it.componentKey },
    )

    override fun close() {
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getFolderService)
    }
}
