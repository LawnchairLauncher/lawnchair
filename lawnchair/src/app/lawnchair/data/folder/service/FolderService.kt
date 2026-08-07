package app.lawnchair.data.folder.service

import android.content.Context
import androidx.core.content.edit
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.folder.FolderEntry
import app.lawnchair.data.folder.FolderInfoEntity
import app.lawnchair.data.folder.FolderItemEntity
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

@LauncherAppSingleton
class FolderService @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val folderDao = AppDatabase.INSTANCE.get(context).folderDao()
    private val prefs = PreferenceManager.getInstance(context)

    fun getFoldersFlow(): Flow<List<FolderEntry>> {
        return folderDao.getAllFoldersWithItems()
            .onStart { migrateLegacyFolderRanks() }
            .map { list ->
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
        val rank = (folderDao.getMaxFolderRank() ?: -1) + 1
        folderDao.insertFolder(FolderInfoEntity(title = title, rank = rank))
    }

    suspend fun renameFolderInfo(folderId: Int, title: String) = withContext(Dispatchers.IO) {
        folderDao.updateFolderTitle(folderId, title)
    }

    suspend fun deleteFolderInfo(id: Int) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(id)
    }

    suspend fun updateFolderRanks(orderedIds: List<Int>) = withContext(Dispatchers.IO) {
        folderDao.updateFolderRanks(orderedIds)
    }

    private suspend fun migrateLegacyFolderRanks() = withContext(Dispatchers.IO) {
        val legacyOrder = prefs.sp.getString(LEGACY_FOLDER_ORDER_STRING, "") ?: ""
        if (legacyOrder.isNotEmpty()) {
            val legacyIds = stringToIntList(legacyOrder).distinct()
            val currentIds = folderDao.getAllFolderIds()
            val validLegacyIds = legacyIds.filter { it in currentIds }
            val missingCurrentIds = currentIds.filter { it !in validLegacyIds }
            val orderedIds = validLegacyIds + missingCurrentIds

            if (orderedIds.isNotEmpty()) {
                folderDao.updateFolderRanks(orderedIds)
            }
            prefs.sp.edit {
                putString(LEGACY_FOLDER_ORDER_STRING, "")
            }
        }
    }

    private fun stringToIntList(string: String, delimiter: String = ","): List<Int> {
        return string.takeIf { it.isNotBlank() }
            ?.split(delimiter)
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: emptyList()
    }

    private fun FolderWithItems.toFolderEntry() = FolderEntry(
        id = folder.id,
        title = folder.title,
        hide = folder.hide,
        rank = folder.rank,
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

private const val LEGACY_FOLDER_ORDER_STRING = "pref_drawerListOrder"
