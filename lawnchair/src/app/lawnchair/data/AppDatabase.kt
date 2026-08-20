package app.lawnchair.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lawnchair.data.folder.FolderInfoEntity
import app.lawnchair.data.folder.FolderItemEntity
import app.lawnchair.data.folder.service.FolderDao
import app.lawnchair.data.iconoverride.IconOverride
import app.lawnchair.data.iconoverride.IconOverrideDao
import app.lawnchair.data.iconoverride.ShortcutBadgeOverride
import app.lawnchair.data.iconoverride.ShortcutBadgeOverrideDao
import app.lawnchair.data.iconoverride.ShortcutIconOverride
import app.lawnchair.data.iconoverride.ShortcutIconOverrideDao
import app.lawnchair.data.wallpaper.Wallpaper
import app.lawnchair.data.wallpaper.service.WallpaperDao
import app.lawnchair.util.MainThreadInitializedObject
import kotlinx.coroutines.runBlocking

@Database(
    entities = [
        IconOverride::class,
        ShortcutIconOverride::class,
        ShortcutBadgeOverride::class,
        Wallpaper::class,
        FolderInfoEntity::class,
        FolderItemEntity::class,
    ],
    version = 5,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun iconOverrideDao(): IconOverrideDao
    abstract fun shortcutIconOverrideDao(): ShortcutIconOverrideDao
    abstract fun shortcutBadgeOverrideDao(): ShortcutBadgeOverrideDao
    abstract fun wallpaperDao(): WallpaperDao
    abstract fun folderDao(): FolderDao

    suspend fun checkpoint() {
        iconOverrideDao().checkpoint(SimpleSQLiteQuery("pragma wal_checkpoint(full)"))
        shortcutIconOverrideDao().checkpoint(SimpleSQLiteQuery("pragma wal_checkpoint(full)"))
        shortcutBadgeOverrideDao().checkpoint(SimpleSQLiteQuery("pragma wal_checkpoint(full)"))
        wallpaperDao().checkpoint(SimpleSQLiteQuery("pragma wal_checkpoint(full)"))
        folderDao().checkpoint(SimpleSQLiteQuery("pragma wal_checkpoint(full)"))
    }

    fun checkpointSync() {
        runBlocking {
            checkpoint()
        }
    }

    companion object {
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `Wallpapers` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `imagePath` TEXT NOT NULL,
                `rank` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `checksum` TEXT
            )
                    """.trimIndent(),
                )

                database.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `Folders` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `hide` INTEGER NOT NULL,
                `rank` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL
            )
                    """.trimIndent(),
                )

                database.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `FolderItems` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `folderId` INTEGER NOT NULL,
                `rank` INTEGER NOT NULL,
                `item_info` TEXT,
                `timestamp` INTEGER NOT NULL,
                FOREIGN KEY(`folderId`) REFERENCES `Folders`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
            )
                    """.trimIndent(),
                )

                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_FolderItems_folderId` ON `FolderItems` (`folderId`)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_FolderItems_folderId ON FolderItems(folderId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `shortcuticonoverride` (
                `target` TEXT NOT NULL,
                `packPackageName` TEXT NOT NULL,
                `drawableName` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                PRIMARY KEY(`target`)
            )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
            CREATE TABLE IF NOT EXISTS `shortcutbadgeoverride` (
                `target` TEXT NOT NULL,
                PRIMARY KEY(`target`)
            )
                    """.trimIndent(),
                )
            }
        }

        val INSTANCE = MainThreadInitializedObject { context ->
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "preferences",
            ).addMigrations(MIGRATION_1_3).addMigrations(MIGRATION_2_3).addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5).build()
        }
    }
}
