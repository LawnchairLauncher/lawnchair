package app.lawnchair.data

import androidx.room.TypeConverter
import app.lawnchair.data.folder.FolderItemEntity
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ComponentKey

class Converters {

    @TypeConverter
    fun fromComponentKey(value: ComponentKey?) = value?.toString()

    @TypeConverter
    fun toComponentKey(value: String?) = value?.let { ComponentKey.fromString(it) }
}

fun AppInfo.toEntity(folderId: Int): FolderItemEntity {
    return FolderItemEntity(
        folderId = folderId,
        componentKey = Converters().fromComponentKey(this.toComponentKey()),
    )
}
