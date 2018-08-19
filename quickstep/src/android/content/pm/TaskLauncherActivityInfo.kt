package android.content.pm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import com.android.systemui.shared.recents.model.Task

class TaskLauncherActivityInfo(private val task: Task, private val context: Context) : LauncherActivityInfo() {

    override fun getComponentName(): ComponentName {
        val cname = task.key.component
        val pm = context.packageManager
        val res = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(task.key.packageName), 0)
        return if (res.isEmpty()) cname else {
            ComponentName.createRelative(res[0].activityInfo.packageName, res[0].activityInfo.name)
        }
    }

    override fun getUser(): UserHandle = Process.myUserHandle()

    override fun getLabel(): CharSequence = task.title

    override fun getIcon(density: Int): Drawable = task.icon

    override fun getApplicationInfo(): ApplicationInfo = TaskApplicationInfo(task)
}