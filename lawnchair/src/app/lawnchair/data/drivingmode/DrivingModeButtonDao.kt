package app.lawnchair.data.drivingmode

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface DrivingModeButtonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DrivingModeButtonAssignment)

    @Query("DELETE FROM drivingmodebutton WHERE page = :page AND row = :row AND col = :col")
    suspend fun delete(page: Int, row: Int, col: Int)

    @Query("SELECT * FROM drivingmodebutton")
    fun observeAll(): Flow<List<DrivingModeButtonAssignment>>

    @Query("SELECT * FROM drivingmodebutton")
    suspend fun getAll(): List<DrivingModeButtonAssignment>

    @Query("DELETE FROM drivingmodebutton")
    suspend fun deleteAll()

    @RawQuery
    suspend fun checkpoint(supportSQLiteQuery: SupportSQLiteQuery): Int
}
