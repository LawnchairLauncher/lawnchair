package app.lawnchair.wallpaper.service

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallpapers")
data class Wallpaper(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imagePath: String,
    val rank: Int,
    val timestamp: Long,
)
