package app.lawnchair.util

import com.android.quickstep.TaskUtils
import com.android.systemui.shared.recents.model.Task

object TaskIconUtils {

    private val customIconBlacklist = setOf(
        "com.facebook.katana",
        "com.twitter.android",
        "eu.faircode.email",
    )

    @JvmStatic
    fun allowCustomIcon(task: Task): Boolean {
        val componentKey = TaskUtils.getLaunchComponentKeyForTask(task.key)
        val packageName = componentKey.componentName.packageName
        return packageName !in customIconBlacklist
    }
}
