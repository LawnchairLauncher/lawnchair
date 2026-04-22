package app.lawnchair.data.wallpaper.service

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import app.lawnchair.data.wallpaper.Wallpaper

@Dao
interface WallpaperDao {
    @Insert
    suspend fun insert(wallpaper: Wallpaper)

    @Query("SELECT * FROM wallpapers ORDER BY timestamp DESC LIMIT 4")
    suspend fun getTopWallpapers(): List<Wallpaper>

    @Query("UPDATE wallpapers SET rank = rank + 1 WHERE rank >= :rank")
    suspend fun updateRank(rank: Int)

    @Query("UPDATE wallpapers SET rank = :rank, timestamp = :timestamp WHERE id = :id")
    suspend fun updateWallpaper(id: Long, rank: Int, timestamp: Long)

    @Query("DELETE FROM wallpapers WHERE id = :id")
    suspend fun deleteWallpaper(id: Long)

    @RawQuery
    suspend fun checkpoint(supportSQLiteQuery: SupportSQLiteQuery): Int
}
