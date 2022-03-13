package app.lawnchair.data

import androidx.room.TypeConverter
import com.android.launcher3.util.ComponentKey

class Converters {
    @TypeConverter
    fun fromComponentKey(value: ComponentKey?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toComponentKey(value: String?): ComponentKey? {
        return value?.let { ComponentKey.fromString(it) }
    }
}
