package app.lawnchair.data.folder.model

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.ui.preferences.destinations.Action
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FolderViewModel(context: Context) : ViewModel() {

    private val repository = FolderService.INSTANCE.get(context)

    private val _folders = MutableStateFlow<List<FolderInfo>>(emptyList())
    val folders: StateFlow<List<FolderInfo>> = _folders.asStateFlow()

    private val _currentTitle = MutableStateFlow("My Folder")
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    private val _action = MutableStateFlow(Action.DEFAULT)
    val action: StateFlow<Action> = _action.asStateFlow()

    private val _foldersMutable = MutableLiveData<List<FolderInfo>>()
    val foldersMutable: LiveData<List<FolderInfo>> = _foldersMutable

    private val _folderInfo = MutableStateFlow<FolderInfo?>(null)
    val folderInfo = _folderInfo.asStateFlow()
    private var tempTitle: String = ""

    init {
        viewModelScope.launch {
            loadFolders()
        }
    }

    fun setAction(newAction: Action) {
        if (_action.value != newAction) {
            Log.d("FolderPreferences", "Transitioning action from ${action.value} to $newAction")
            _action.value = newAction
        }
    }

    fun refreshFolders() {
        viewModelScope.launch {
            loadFolders()
        }
    }

    fun updateCurrentTitle(title: String) {
        if (action.value == Action.EDIT) {
            tempTitle = title
            _currentTitle.value = title
        } else if (action.value != Action.SETTLE) {
            _currentTitle.value = title
        }
    }

    fun setFolderInfo(folderInfoId: Int, hasId: Boolean) {
        viewModelScope.launch {
            _folderInfo.value = repository.getFolderInfo(folderInfoId, hasId)
        }
    }

    fun updateFolderInfo(folderInfo: FolderInfo, hide: Boolean) {
        viewModelScope.launch {
            repository.updateFolderInfo(folderInfo, hide)
            loadFolders()
        }
    }

    fun saveFolder(folderInfo: FolderInfo) {
        viewModelScope.launch {
            repository.saveFolderInfo(folderInfo)
            loadFolders()
        }
    }

    fun deleteFolderInfo(id: Int) {
        viewModelScope.launch {
            repository.deleteFolderInfo(id)
            loadFolders()
        }
    }

    fun updateFolderWithItems(id: Int, title: String, appInfos: List<AppInfo>) {
        viewModelScope.launch {
            repository.updateFolderWithItems(id, title, appInfos)
        }
    }

    suspend fun loadFolders() {
        val folders = repository.getAllFolders()
        _folders.value = folders
        _foldersMutable.postValue(folders)
    }
}
