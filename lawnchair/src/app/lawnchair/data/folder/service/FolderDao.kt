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

    @Query("SELECT * FROM Folders ORDER BY rank ASC")
    @Transaction
    fun getAllFoldersWithItems(): Flow<List<FolderWithItems>>

    @Query("DELETE FROM FolderItems WHERE folderId = :folderId")
    suspend fun deleteFolderItemsByFolderId(folderId: Int)

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
        orderedIds.forEachIndexed { index, id ->
            updateFolderRank(id, index)
        }
    }

    @Query("SELECT COUNT(*) FROM Folders")
    suspend fun getFolderCount(): Int

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
