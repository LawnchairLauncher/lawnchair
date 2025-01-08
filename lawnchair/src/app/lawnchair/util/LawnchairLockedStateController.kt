import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import com.android.systemui.shared.system.ActivityManagerWrapper

@SuppressLint("StaticFieldLeak")
object LawnchairLockedStateController {

    private const val TAG = "LawnchairLockedStateController"
    private const val RECENT_TASK_LOCKED_LIST = "com_android_systemui_recent_task_lockd_list"
    private const val RECENT_TASK_LOCKED_LIST_BK = "com_android_systemui_recent_task_locked_bk"
    private const val TASK_LOCK_LIST_KEY = "task_lock_list"
    const val TASK_LOCK_LIST_KEY_WITH_USERID = "task_lock_list_with_userid"
    const val TASK_LOCK_STATE = "tasklockstate"

    private var isReloaded = false
    private var reloadCount = 0

    private lateinit var applicationContext: Context
    private val backgroundThread: HandlerThread by lazy {
        HandlerThread("Recents-LawnchairLockedStateController", 10)
    }
    private val backgroundThreadHandler: Handler by lazy {
        Handler(backgroundThread.looper)
    }
    private var lockedListWithUserId: MutableSet<String> = HashSet()
    private var lockedPackageNameListWithUserId: MutableList<String> = ArrayList()
    private val sharedPreferences: SharedPreferences by lazy {
        applicationContext.getSharedPreferences(TASK_LOCK_STATE, Context.MODE_PRIVATE)
    }

    fun initialize(context: Context): LawnchairLockedStateController {
        if (!this::applicationContext.isInitialized) {
            this.applicationContext = context
            backgroundThread.start()
            initializePackageNameList(false)
        }
        return this
    }

    private fun initializePackageNameList(forceInitialization: Boolean) {
        if (isReloaded && forceInitialization) return

        val packageName = applicationContext.packageName
        if (packageName.isNullOrEmpty() || !packageName.contains("launcher")) return

        lockedListWithUserId =
            sharedPreferences.getStringSet(TASK_LOCK_LIST_KEY_WITH_USERID, emptySet()) ?: HashSet()
        lockedPackageNameListWithUserId = ArrayList()

        if (lockedListWithUserId.isEmpty() &&
            getLockedListFromProvider(
                ActivityManagerWrapper.getInstance().currentUserId,
            )
        ) {
            buildPackageNameList()
        } else {
            buildPackageNameList()
            isReloaded = true
        }

        writeToProvider()
        if (++reloadCount > 5) isReloaded = true
    }

    private fun buildPackageNameList() {
        lockedListWithUserId.forEach { str ->
            val split = str.split("/")
            val substring = str.substring(str.lastIndexOf("#") + 1)
            appendUserWithoutBrace(split[0], substring.substring(0, substring.length - 1)).let {
                lockedPackageNameListWithUserId.add(
                    it,
                )
            }
        }
    }

    fun setTaskLockState(taskIdentifier: String, lockState: Boolean, userId: Int) {
        val userIdStr = userId.toString()
        val appendUserWithBrace = appendUserWithBrace(taskIdentifier, userIdStr)
        val packageName = taskIdentifier.split("/")[0]
        val packageNameWithUser = appendUserWithoutBrace(packageName, userIdStr)

        if (lockState) {
            appendUserWithBrace.let { lockedListWithUserId.add(it) }
            lockedPackageNameListWithUserId.add(packageNameWithUser)
        } else {
            lockedListWithUserId.remove(appendUserWithBrace)
            lockedPackageNameListWithUserId.remove(packageNameWithUser)
        }

        sharedPreferences.edit().apply {
            clear()
            putStringSet(TASK_LOCK_LIST_KEY_WITH_USERID, lockedListWithUserId)
            apply()
        }

        writeToProvider()

        if (!isReloaded) {
            try {
                val recentTaskLockedListBk =
                    Settings.System.getStringForUser(
                        applicationContext.contentResolver,
                        RECENT_TASK_LOCKED_LIST_BK,
                        ActivityManagerWrapper.getInstance().currentUserId,
                    )
                if (recentTaskLockedListBk == null) {
                    Settings.System.putStringForUser(
                        applicationContext.contentResolver,
                        RECENT_TASK_LOCKED_LIST_BK,
                        "done",
                        ActivityManagerWrapper.getInstance().currentUserId,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "setTaskLockState error: ", e)
            }
        }
    }

    fun getTaskLockState(taskIdentifier: String, userId: Int): Boolean {
        return lockedListWithUserId.contains(appendUserWithBrace(taskIdentifier, userId.toString()))
    }

    // TODO Implement this, when the app is uninstalled
    fun removeTaskLockState(taskIdentifier: String, userId: Int) {
        if (userId != -1) {
            backgroundThreadHandler.post {
                val user = UserHandle.getUserId(userId)
                val userString = "{$taskIdentifier/"
                Log.d(TAG, "uninstall Lock task , $taskIdentifier, $user")
                val arrayList = ArrayList(lockedListWithUserId)
                val size = arrayList.size
                for (i in 0 until size) {
                    val userTask = arrayList[i]
                    if (userTask.startsWith(userString) && userTask.endsWith("$user}")) {
                        setTaskLockState(removeUserWithBrace(userTask), false, user)
                    }
                }
            }
        }
    }

    fun isTaskLocked(taskIdentifier: String): Boolean {
        return lockedPackageNameListWithUserId.contains(taskIdentifier)
    }

    private fun appendUserWithoutBrace(input: String, userId: String): String {
        return input.replace("{", "") + "#$userId"
    }

    private fun appendUserWithBrace(input: String, userId: String): String {
        return input.replace("}", "") + "#$userId}"
    }

    private fun removeUserWithBrace(input: String): String {
        return input.substring(0, input.lastIndexOf("#")) + "}"
    }

    private fun writeToProvider() {
        val sb = StringBuilder()
        lockedListWithUserId.forEach { taskIdentifier ->
            sb.append(taskIdentifier)
        }
        writeLockedListToProvider(
            sb.toString(),
            ActivityManagerWrapper.getInstance().currentUserId,
        )
    }

    private fun getLockedListFromProvider(userId: Int): Boolean {
        try {
            val str = Settings.System.getStringForUser(
                applicationContext.contentResolver,
                RECENT_TASK_LOCKED_LIST_BK,
                userId,
            )
            if ("done" == str) {
                isReloaded = true
                return false
            }
            val split = str.split("}")
            lockedListWithUserId.addAll(split)
            sharedPreferences.edit().apply {
                clear()
                putStringSet(TASK_LOCK_LIST_KEY_WITH_USERID, lockedListWithUserId)
                apply()
            }
            Settings.System.putStringForUser(
                applicationContext.contentResolver,
                RECENT_TASK_LOCKED_LIST_BK,
                "done",
                userId,
            )
            return true
        } catch (e: Exception) {
            Log.e(TAG, "getLockedListFromProvider error: ", e)
            return false
        }
    }

    private fun writeLockedListToProvider(str: String, userId: Int) {
        backgroundThreadHandler.post {
            try {
                Settings.System.putStringForUser(
                    applicationContext.contentResolver,
                    RECENT_TASK_LOCKED_LIST,
                    str,
                    userId,
                )
            } catch (e: Exception) {
                Log.e(TAG, "writeLockedListToProvider error: ", e)
            }
        }
    }
}
