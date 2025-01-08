package app.lawnchair.wallpaper.model

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lawnchair.wallpaper.service.Wallpaper
import app.lawnchair.wallpaper.service.WallpaperDatabase
import kotlinx.coroutines.launch

class WallpaperViewModel(context: Context) : ViewModel() {
    private val dao = WallpaperDatabase.INSTANCE.get(context).wallpaperDao()

    private val _wallpapers = MutableLiveData<List<Wallpaper>>()
    val wallpapers: LiveData<List<Wallpaper>> = _wallpapers

    init {
        loadTopWallpapers()
    }

    private fun loadTopWallpapers() {
        viewModelScope.launch {
            val topWallpapers = dao.getTopWallpapers()
            _wallpapers.postValue(topWallpapers)
        }
    }
}
