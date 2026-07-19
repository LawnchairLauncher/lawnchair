package app.lawnchair.predictions

import android.content.ComponentName
import android.os.Process
import android.os.UserHandle
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.ALL_APPS_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.FOLDER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.HOTSEAT
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.PREDICTED_HOTSEAT_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.PREDICTION_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.SEARCH_RESULT_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.TASK_BAR_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.TASK_SWITCHER_CONTAINER
import com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.WORKSPACE
import com.android.launcher3.logger.LauncherAtom.FolderContainer
import com.android.launcher3.logger.LauncherAtom.ItemInfo
import com.android.launcher3.logger.LauncherAtom.ItemInfo.ItemCase.APPLICATION
import com.android.launcher3.logger.LauncherAtom.ItemInfo.ItemCase.TASK
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.UserIconInfo
import com.android.launcher3.util.UserIconInfo.TYPE_CLONED
import com.android.launcher3.util.UserIconInfo.TYPE_PRIVATE
import com.android.launcher3.util.UserIconInfo.TYPE_WORK
import com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__USER_TYPE__TYPE_CLONED
import com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__USER_TYPE__TYPE_PRIVATE
import com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__USER_TYPE__TYPE_WORK

/**
 * Turn [ItemInfo] into [ResolvedEvent] instances.
 */
class PredictionEventResolver(private val userCache: UserCache) {

    /**
     * @param location Human-readable location string (e.g. "workspace", "all-apps", "folder/hotseat").
     * @param componentName The component that was interacted with, if resolvable.
     * @param user The user profile the event belongs to.
     */
    data class ResolvedEvent(
        val location: String,
        val componentName: ComponentName?,
        val user: UserHandle,
    )

    /**
     * Resolves an [ItemInfo] atom into a [ResolvedEvent], or `null`.
     */
    fun resolve(atomInfo: ItemInfo?): ResolvedEvent? {
        if (atomInfo == null) return null
        return ResolvedEvent(
            location = resolveLocation(atomInfo),
            componentName = resolveComponentName(atomInfo),
            user = resolveUser(atomInfo),
        )
    }

    private fun resolveComponentName(atomInfo: ItemInfo): ComponentName? {
        return when (atomInfo.itemCase) {
            APPLICATION -> {
                val cn = atomInfo.application.componentName
                if (cn.isNullOrEmpty()) null else ComponentName.unflattenFromString(cn)
            }

            TASK -> {
                val cn = atomInfo.task.componentName
                if (cn.isNullOrEmpty()) null else ComponentName.unflattenFromString(cn)
            }

            else -> null
        }
    }

    private fun resolveUser(atomInfo: ItemInfo): UserHandle {
        val userType = when (atomInfo.userType) {
            LAUNCHER_UICHANGED__USER_TYPE__TYPE_WORK -> TYPE_WORK
            LAUNCHER_UICHANGED__USER_TYPE__TYPE_CLONED -> TYPE_CLONED
            LAUNCHER_UICHANGED__USER_TYPE__TYPE_PRIVATE -> TYPE_PRIVATE
            else -> UserIconInfo.TYPE_MAIN
        }

        return userCache.userProfiles.firstOrNull { userCache.getUserInfo(it).type == userType }
            ?: Process.myUserHandle()
    }

    private fun resolveLocation(atomInfo: ItemInfo): String {
        if (!atomInfo.hasContainerInfo()) return ""
        return when (atomInfo.containerInfo.containerCase) {
            WORKSPACE -> "workspace"

            HOTSEAT -> "hotseat"

            FOLDER -> {
                val parent = atomInfo.containerInfo.folder.parentContainerCase
                when (parent) {
                    FolderContainer.ParentContainerCase.WORKSPACE -> "folder/workspace"
                    FolderContainer.ParentContainerCase.HOTSEAT -> "folder/hotseat"
                    else -> "folder"
                }
            }

            ALL_APPS_CONTAINER -> "all-apps"

            PREDICTION_CONTAINER -> "predictions"

            PREDICTED_HOTSEAT_CONTAINER -> "predictions/hotseat"

            TASK_SWITCHER_CONTAINER -> "task-switcher"

            TASK_BAR_CONTAINER -> "taskbar"

            SEARCH_RESULT_CONTAINER -> "search-results"

            else -> ""
        }
    }
}
