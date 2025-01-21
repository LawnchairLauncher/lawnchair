package app.lawnchair.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SimpleSQLiteQuery
import app.lawnchair.data.folder.FolderInfoEntity
import app.lawnchair.data.folder.FolderItemEntity
import app.lawnchair.data.folder.service.FolderDao
import app.lawnchair.data.iconoverride.IconOverride
import app.lawnchair.data.iconoverride.IconOverrideDao
import app.lawnchair.data.wallpaper.Wallpaper
import app.lawnchair.data.wallpaper.service.WallpaperDao
import app.lawnchair.util.MainThreadInitializedObject
import kotlinx.coroutines.runBlocking

@Database(entities = [IconOverride::class, Wallpaper::class, FolderInfoEntity::class, FolderItemEntity::class], version = 2)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun iconOverrideDao(): IconOverrideDao
    abstract fun wallpaperDao(): WallpaperDao
    abstract fun folderDao(): FolderDao

    suspend fun checkpoint() {
        iconOverrideDao().checkpoint(SimpleSQLiteQuery("pragma wal_checkpoint(full)"))
        wallpaperDao().checkpoint(SimpleSQLiteQuery("pragma wal_checkpoint(full)"))
        folderDao().checkpoint(SimpleSQLiteQuery("pragma wal_checkpoint(full)"))
    }

    fun checkpointSync() {
        runBlocking {
            checkpoint()
        }
    }

    companion object {
        val INSTANCE = MainThreadInitializedObject { context ->
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "preferences",
            ).fallbackToDestructiveMigration().build()
        }
    }
}
