package app.lawnchair.data.wallpaper

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Wallpapers")
data class Wallpaper(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imagePath: String,
    val rank: Int,
    val timestamp: Long,
    val checksum: String? = null,
)
