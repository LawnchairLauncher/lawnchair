package app.lawnchair.data.iconoverride

import android.content.Context
import app.lawnchair.data.AppDatabase
import app.lawnchair.icons.IconPickerItem
import com.android.launcher3.LauncherAppState
import com.android.launcher3.pm.PackageInstallInfo
import com.android.launcher3.pm.PackageInstallInfo.STATUS_INSTALLED
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.MainThreadInitializedObject
import com.android.launcher3.util.SafeCloseable
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class IconOverrideRepository(private val context: Context) : SafeCloseable {

    private val scope = MainScope() + CoroutineName("IconOverrideRepository")
    private val dao = AppDatabase.INSTANCE.get(context).iconOverrideDao()
    private var _overridesMap = mapOf<ComponentKey, IconPickerItem>()
    val overridesMap get() = _overridesMap

    private val updatePackageQueue = ConcurrentLinkedQueue<ComponentKey>()

    init {
        scope.launch {
            dao.observeAll()
                .flowOn(Dispatchers.Main)
                .collect { overrides ->
                    _overridesMap = overrides.associateBy(
                        keySelector = { it.target },
                        valueTransform = { it.iconPickerItem },
                    )
                    while (updatePackageQueue.isNotEmpty()) {
                        val target = updatePackageQueue.poll() ?: continue
                        updatePackageIcons(target)
                    }
                }
        }
    }

    suspend fun setOverride(target: ComponentKey, item: IconPickerItem) {
        dao.insert(IconOverride(target, item))
        updatePackageQueue.offer(target)
    }

    suspend fun deleteOverride(target: ComponentKey) {
        dao.delete(target)
        updatePackageQueue.offer(target)
    }

    fun observeTarget(target: ComponentKey) = dao.observeTarget(target)

    fun observeCount() = dao.observeCount()

    suspend fun deleteAll() {
        dao.deleteAll()
        LauncherAppState.getInstance(context).reloadIcons()
    }

    private fun updatePackageIcons(target: ComponentKey) {
        val model = LauncherAppState.getInstance(context).model
        model.onPackageStateChanged(
            PackageInstallInfo.fromState(
                STATUS_INSTALLED,
                target.componentName.packageName,
                target.user,
            ),
        )
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField
        val INSTANCE = MainThreadInitializedObject(::IconOverrideRepository)
    }
}
