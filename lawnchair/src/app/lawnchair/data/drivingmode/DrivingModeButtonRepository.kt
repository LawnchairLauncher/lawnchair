package app.lawnchair.data.drivingmode

import android.content.Context
import app.lawnchair.data.AppDatabase
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/** Position key for a driving mode button slot. */
data class DrivingModeSlot(val page: Int, val row: Int, val col: Int)

@LauncherAppSingleton
class DrivingModeButtonRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {

    private val scope = MainScope() + CoroutineName("DrivingModeButtonRepository")
    private val dao = AppDatabase.INSTANCE.get(context).drivingModeButtonDao()

    private val _assignments = MutableStateFlow<Map<DrivingModeSlot, DrivingModeButtonAssignment>>(emptyMap())
    val assignments = _assignments.asStateFlow()

    init {
        scope.launch {
            dao.observeAll()
                .collect { list ->
                    _assignments.value = list.associateBy { DrivingModeSlot(it.page, it.row, it.col) }
                }
        }
    }

    suspend fun setAssignment(slot: DrivingModeSlot, targetType: String, targetValue: String) {
        val item = DrivingModeButtonAssignment(slot.page, slot.row, slot.col, targetType, targetValue)
        dao.insert(item)
        _assignments.value = _assignments.value + (slot to item)
    }

    suspend fun removeAssignment(slot: DrivingModeSlot) {
        dao.delete(slot.page, slot.row, slot.col)
        _assignments.value = _assignments.value - slot
    }

    /** One-time seeding of the default button layout - only runs if nothing has ever been assigned. */
    suspend fun seedDefaultsIfEmpty(defaults: List<Triple<DrivingModeSlot, String, String>>) {
        if (dao.getAll().isNotEmpty()) return
        defaults.forEach { (slot, type, value) -> setAssignment(slot, type, value) }
    }

    override fun close() {
        scope.cancel()
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getDrivingModeButtonRepository)
    }
}
