package app.lawnchair.data.folder.model

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.data.folder.FolderEntry
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.preferences2.ReloadHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FolderViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository: FolderService = FolderService.INSTANCE.get(application)

    val folders: StateFlow<List<FolderEntry>?> = repository.getFoldersFlow()
        .distinctUntilChanged()
        .catch { exception ->
            Log.e("FolderViewModel", "Error in folders flow", exception)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )

    val allFolderPackages: StateFlow<Set<String>?> = folders
        .map { folderList ->
            folderList?.flatMap { folder -> folder.itemComponentKeys }
                ?.map { key -> key.substringBefore("/") }
                ?.toSet()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )

    private val reloadHelper = ReloadHelper(application)

    fun getFolderFlowForId(folderId: Int): Flow<FolderEntry?> {
        return folders.map { list -> list?.find { it.id == folderId } }
    }

    fun renameFolder(folderId: Int, title: String) {
        viewModelScope.launch {
            repository.renameFolderInfo(folderId, title)
            reloadHelper.reloadGrid()
        }
    }

    fun updateFolderItems(id: Int, title: String, componentKeys: List<String>) {
        viewModelScope.launch {
            repository.updateFolderWithItems(id, title, componentKeys)
            reloadHelper.reloadGrid()
        }
    }

    fun createFolder(title: String) {
        viewModelScope.launch {
            repository.saveFolderInfo(title)
        }
    }

    fun deleteFolder(id: Int) {
        viewModelScope.launch {
            repository.deleteFolderInfo(id)
        }
        reloadHelper.reloadGrid()
    }
}

object FolderOrderUtils {
    private const val DEFAULT_DELIMITER = ","

    fun intListToString(list: List<Int>, delimiter: String = DEFAULT_DELIMITER): String {
        return list.joinToString(delimiter)
    }

    fun stringToIntList(string: String, delimiter: String = DEFAULT_DELIMITER): List<Int> {
        return string.takeIf { it.isNotBlank() }
            ?.split(delimiter)
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: emptyList()
    }
}
