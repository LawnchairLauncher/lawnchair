package app.lawnchair.data.folder.service

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Relation
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import app.lawnchair.data.folder.FolderInfoEntity
import app.lawnchair.data.folder.FolderItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderInfoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderItems(items: List<FolderItemEntity>)

    @Query("SELECT id FROM Folders ORDER BY rank ASC")
    suspend fun getAllFolderIds(): List<Int>

    @Query("SELECT * FROM Folders ORDER BY rank ASC")
    @Transaction
    fun getAllFoldersWithItems(): Flow<List<FolderWithItems>>

    @Query("SELECT MAX(rank) FROM Folders")
    suspend fun getMaxFolderRank(): Int?

    @Query("DELETE FROM FolderItems WHERE folderId = :folderId")
    suspend fun deleteFolderItemsByFolderId(folderId: Int)

    @Insert
    suspend fun insertFolderItem(item: FolderItemEntity): Long

    @Query("SELECT COUNT(*) FROM FolderItems WHERE folderId = :folderId AND item_info = :componentKey")
    suspend fun countFolderItem(folderId: Int, componentKey: String): Int

    @Query("SELECT MAX(rank) FROM FolderItems WHERE folderId = :folderId")
    suspend fun getMaxItemRank(folderId: Int): Int?

    @Query("DELETE FROM FolderItems WHERE folderId = :folderId AND item_info = :componentKey")
    suspend fun deleteFolderItem(folderId: Int, componentKey: String)

    @Query("SELECT COUNT(*) FROM Folders WHERE id = :folderId")
    suspend fun countFolder(folderId: Int): Int

    /**
     * Appends [componentKey] at the end of the folder, unless it is already present. Does nothing
     * if the folder no longer exists (e.g. deleted from settings while the Customize sheet showed it).
     */
    @Transaction
    suspend fun addItemToFolder(folderId: Int, componentKey: String) {
        if (countFolder(folderId) == 0) return
        if (countFolderItem(folderId, componentKey) > 0) return
        val rank = (getMaxItemRank(folderId) ?: -1) + 1
        insertFolderItem(
            FolderItemEntity(folderId = folderId, rank = rank, componentKey = componentKey),
        )
    }

    /**
     * Creates a folder at the end of the list containing [componentKey]. Both inserts share one
     * transaction, so a failure never leaves an empty folder behind.
     */
    @Transaction
    suspend fun createFolderWithItem(title: String, componentKey: String): Int {
        val rank = (getMaxFolderRank() ?: -1) + 1
        val folderId = insertFolder(FolderInfoEntity(title = title, rank = rank)).toInt()
        addItemToFolder(folderId, componentKey)
        return folderId
    }

    @Query("UPDATE Folders SET title = :title, timestamp = :timestamp WHERE id = :id")
    suspend fun updateFolderTitle(id: Int, title: String, timestamp: Long = System.currentTimeMillis())

    @Transaction
    suspend fun replaceFolderItems(folderId: Int, title: String, items: List<FolderItemEntity>) {
        updateFolderTitle(folderId, title)
        deleteFolderItemsByFolderId(folderId)
        insertFolderItems(items.map { it.copy(folderId = folderId) })
    }

    @Query(
        value = """
                UPDATE Folders
                SET hide = :hide, timestamp = :timestamp
                WHERE id = :folderId
            """,
    )
    suspend fun setFolderHidden(
        folderId: Int,
        hide: Boolean,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM Folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Int)

    @Query("UPDATE Folders SET rank = :rank WHERE id = :id")
    suspend fun updateFolderRank(id: Int, rank: Int)

    @Transaction
    suspend fun updateFolderRanks(orderedIds: List<Int>) {
        val currentIds = getAllFolderIds().toSet()
        if (orderedIds.size != currentIds.size || orderedIds.toSet() != currentIds) {
            return
        }
        orderedIds.forEachIndexed { index, id ->
            updateFolderRank(id, index)
        }
    }

    @RawQuery
    suspend fun checkpoint(supportSQLiteQuery: SupportSQLiteQuery): Int
}

data class FolderWithItems(
    @Embedded val folder: FolderInfoEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "folderId",
    )
    val items: List<FolderItemEntity>,
)
