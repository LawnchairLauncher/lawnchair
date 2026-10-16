package app.lawnchair.data.folder.service

import android.content.Context
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.folder.FolderInfoEntity
import app.lawnchair.data.toEntity
import app.lawnchair.omni.experience.ExperienceStore
import app.lawnchair.omni.experience.FolderRule
import app.lawnchair.omni.experience.OmniAppInventory
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class FolderService(val context: Context) : SafeCloseable {
    private val folderDao = AppDatabase.INSTANCE.get(context).folderDao()
    private val inventory by lazy { OmniAppInventory.get(context) }
    private val experience by lazy { ExperienceStore.get(context) }

    fun getFoldersFlow(): Flow<List<FolderInfo>> = combine(
        folderDao.getAllFolders(),
        experience.state.filterNotNull(),
        inventory.revision,
        PreferenceManager2.getInstance(context).hiddenApps.get(),
    ) { folders, state, _, _ ->
        val apps = inventory.visible()
        val byKey = apps.associateBy { it.toComponentKey().toString() }
        folders.mapNotNull { folder ->
            folderDao.getFolderWithItems(folder.id)?.let { mapFolder(it, true, apps, byKey, state.rules) }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun updateFolderWithItems(folderInfoId: Int, title: String, appInfos: List<AppInfo>) = withContext(Dispatchers.IO) {
        folderDao.insertFolderWithItems(
            FolderInfoEntity(id = folderInfoId, title = title),
            appInfos.mapIndexed { index, app -> app.toEntity(folderInfoId).copy(rank = index) },
        )
    }

    suspend fun saveFolderInfo(folderInfo: FolderInfo) = withContext(Dispatchers.IO) {
        folderDao.insertFolder(FolderInfoEntity(title = folderInfo.title.toString()))
    }

    suspend fun createRuleFolder(title: String, rule: FolderRule): Int = withContext(Dispatchers.IO) {
        require(title.isNotBlank() && title.length <= 80)
        val id = folderDao.insertFolder(FolderInfoEntity(title = title)).toInt()
        experience.update { it.copy(rules = it.rules + rule.copy(folderId = id)) }
        id
    }

    suspend fun updateFolderInfo(folderInfo: FolderInfo, hide: Boolean = false) = withContext(Dispatchers.IO) {
        folderDao.updateFolderInfo(folderInfo.id, folderInfo.title.toString(), hide)
    }

    suspend fun deleteFolderInfo(id: Int) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(id)
        experience.update { it.copy(rules = it.rules.filterNot { rule -> rule.folderId == id }) }
    }

    suspend fun getFolderInfo(folderId: Int, hasId: Boolean = false): FolderInfo? = withContext(Dispatchers.IO) {
        val apps = inventory.visible()
        folderDao.getFolderWithItems(folderId)?.let {
            mapFolder(it, hasId, apps, apps.associateBy { app -> app.toComponentKey().toString() }, experience.read().rules)
        }
    }

    private fun mapFolder(
        folder: FolderWithItems,
        hasId: Boolean,
        apps: List<AppInfo>,
        byKey: Map<String, AppInfo>,
        rules: List<FolderRule>,
    ): FolderInfo {
        val rule = rules.firstOrNull { it.folderId == folder.folder.id }
        val manual = folder.items.sortedBy { it.rank }.mapNotNull { byKey[it.componentKey] }
        val automatic = if (rule == null) {
            emptyList()
        } else {
            apps.filter { app ->
                rule.matches(app.toComponentKey().toString(), app.title.toString(), app.targetPackage.orEmpty(), inventory.category(app))
            }
        }
        return FolderInfo().apply {
            if (hasId) id = folder.folder.id
            title = folder.folder.title
            (manual + automatic).distinctBy { it.toComponentKey() }
                .filter { it.toComponentKey().toString() !in rule?.exclude.orEmpty() }
                .forEach { add(it, false) }
        }
    }

    suspend fun getAllFolders(): List<FolderInfo> = withContext(Dispatchers.IO) { getFoldersFlow().first() }
    override fun close() = Unit

    companion object {
        @JvmField val INSTANCE = MainThreadInitializedObject(::FolderService)
    }
}
