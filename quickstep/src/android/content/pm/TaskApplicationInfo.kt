package android.content.pm

import com.android.systemui.shared.recents.model.Task

class TaskApplicationInfo(task: Task): ApplicationInfo() {
    init {
        packageName = task.key.packageName
    }
}