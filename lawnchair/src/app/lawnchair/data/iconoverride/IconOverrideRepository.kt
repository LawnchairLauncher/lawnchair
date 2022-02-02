package app.lawnchair.data.iconoverride

import android.content.Context
import app.lawnchair.data.AppDatabase
import app.lawnchair.icons.IconPickerItem
import com.android.launcher3.LauncherAppState
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.MainThreadInitializedObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn

class IconOverrideRepository(private val context: Context) {

    private val scope = MainScope() + CoroutineName("IconOverrideRepository")
    private val dao = AppDatabase.INSTANCE.get(context).iconOverrideDao()
    private var _overridesMap = mapOf<ComponentKey, IconPickerItem>()
    val overridesMap get() = _overridesMap

    init {
        scope.launch {
            dao.observeAll()
                .flowOn(Dispatchers.Main)
                .collect { overrides ->
                    _overridesMap = overrides.associateBy(
                        keySelector = { it.target },
                        valueTransform = { it.iconPickerItem }
                    )
                }
        }
    }

    suspend fun setOverride(target: ComponentKey, item: IconPickerItem) {
        dao.insert(IconOverride(target, item))
        reloadIcons()
    }

    suspend fun deleteOverride(target: ComponentKey) {
        dao.delete(target)
        reloadIcons()
    }

    fun observeTarget(target: ComponentKey) = dao.observeTarget(target)

    fun deleteAll() {
        dao.deleteAll()
        reloadIcons()
    }

    private fun reloadIcons() {
        val las = LauncherAppState.getInstance(context)
        val idp = las.invariantDeviceProfile
        idp.onPreferencesChanged(context.applicationContext)
    }

    companion object {
        val INSTANCE = MainThreadInitializedObject(::IconOverrideRepository)
    }
}
