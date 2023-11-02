package app.lawnchair.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import com.android.systemui.shared.system.ActivityManagerWrapper
import java.io.PrintWriter

class LawnchairLockedStateController private constructor(context: Context) {
    private val mContext: Context = context
    private val mBGThread: HandlerThread =
        HandlerThread("Recents-LawnchairLockedStateController", 10)
    private val mBGThreadHandler: Handler
    private var mLockedListWithUserId: MutableSet<String> = HashSet()
    private var mLockedPackageNameListWithUserId: MutableList<String> = ArrayList()
    private val mSp: SharedPreferences = mContext.getSharedPreferences(TASK_LOCK_STATE, Context.MODE_PRIVATE)

    init {
        mBGThread.start()
        mBGThreadHandler = Handler(mBGThread.looper)
        initPackageNameList(false)
    }

    private fun initPackageNameList(forceInitialization: Boolean) {
        if (mReloaded && forceInitialization) return

        val packageName = mContext.packageName
        if (packageName.isNullOrEmpty() || !packageName.contains("launcher")) return

        mLockedListWithUserId = mSp.getStringSet(TASK_LOCK_LIST_KEY_WITH_USERID, emptySet()) ?: HashSet()
        mLockedPackageNameListWithUserId = ArrayList()

        if (mLockedListWithUserId.isEmpty() && getLockedListFromProvider(ActivityManagerWrapper.getInstance().getCurrentUserId())) {
            buildPkgNameList()
        } else {
            buildPkgNameList()
            mReloaded = true
        }

        writeToProvider()
        if (++time > 5) mReloaded = true
    }


    private fun buildPkgNameList() {
        mLockedListWithUserId.forEach { str ->
            val split = str.split("/")
            val substring = str.substring(str.lastIndexOf("#") + 1)
            appendUserWithoutBrace(split[0], substring.substring(0, substring.length - 1)).let {
                mLockedPackageNameListWithUserId.add(
                    it
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
            appendUserWithBrace?.let { mLockedListWithUserId.add(it) }
            mLockedPackageNameListWithUserId.add(packageNameWithUser)
        } else {
            mLockedListWithUserId.remove(appendUserWithBrace)
            mLockedPackageNameListWithUserId.remove(packageNameWithUser)
        }

        mSp.edit().apply {
            clear()
            putStringSet(TASK_LOCK_LIST_KEY_WITH_USERID, mLockedListWithUserId)
            apply()
        }

        writeToProvider()

        if (!mReloaded) {
            try {
                val recentTaskLockedListBk = Settings.System.getStringForUser(mContext.contentResolver, RECENT_TASK_LOCKED_LIST_BK, ActivityManagerWrapper.getInstance().getCurrentUserId())
                if (recentTaskLockedListBk == null) {
                    Settings.System.putStringForUser(mContext.contentResolver, RECENT_TASK_LOCKED_LIST_BK, "done", ActivityManagerWrapper.getInstance().getCurrentUserId())
                }
            } catch (e: Exception) {
                Log.e(TAG, "setTaskLockState error: ", e)
            }
        }
    }


    fun getTaskLockState(taskIdentifier: String, userId: Int): Boolean {
        return mLockedListWithUserId.contains(appendUserWithBrace(taskIdentifier, userId.toString()))
    }

    // TODO Implement this, when app is uninstalled
    fun removeTaskLockState(taskIdentifier: String, userId: Int) {
        if (userId != -1) {
            mBGThreadHandler.post {
                val user = UserHandle.getUserId(userId)
                val userString = "{$taskIdentifier/"
                Log.d(TAG, "uninstall Lock task , $taskIdentifier, $user")
                val arrayList = ArrayList(mLockedListWithUserId)
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
        return mLockedPackageNameListWithUserId.contains(taskIdentifier)
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
        mLockedListWithUserId.forEach { taskIdentifier ->
            sb.append(taskIdentifier)
        }
        writeLockedListToProvider(sb.toString(), ActivityManagerWrapper.getInstance().getCurrentUserId())
    }

    fun dump(printWriter: PrintWriter) {
        printWriter.println("LOCKED RECENT APP list: ")
        val strArr = mLockedListWithUserId.toTypedArray()
        printWriter.println()
        printWriter.println("with userId: ${strArr.size}")
        for (i in strArr.indices) {
            printWriter.print("  ")
            printWriter.println(strArr[i])
        }
        printWriter.println()
        printWriter.println("with userId: ${mLockedPackageNameListWithUserId.size}")
        for (i in mLockedPackageNameListWithUserId.indices) {
            printWriter.print("  ")
            printWriter.println(mLockedPackageNameListWithUserId[i])
        }
        printWriter.println()
        val currentUserId = ActivityManagerWrapper.getInstance().getCurrentUserId()
        try {
            printWriter.println("RECENT_TASK_LOCKED_LIST: ${Settings.System.getStringForUser(mContext.contentResolver, RECENT_TASK_LOCKED_LIST, currentUserId)}")
        } catch (e: Exception) {
            Log.e(TAG, "dump error: ", e)
        }
        printWriter.println()
    }

    private fun getLockedListFromProvider(userId: Int): Boolean {
        try {
            val str = Settings.System.getStringForUser(mContext.contentResolver, RECENT_TASK_LOCKED_LIST_BK, userId)
            if ("done" == str) {
                mReloaded = true
                return false
            }
            val split = str.split("}")
            mLockedListWithUserId.addAll(split)
            mSp.edit().apply {
                clear()
                putStringSet(TASK_LOCK_LIST_KEY_WITH_USERID, mLockedListWithUserId)
                apply()
            }
            Settings.System.putStringForUser(mContext.contentResolver, RECENT_TASK_LOCKED_LIST_BK, "done", userId)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "getLockedListFromProvider error: ", e)
            return false
        }
    }


    private fun writeLockedListToProvider(str: String, userId: Int) {
        mBGThreadHandler.post {
            try {
                Settings.System.putStringForUser(mContext.contentResolver, RECENT_TASK_LOCKED_LIST, str, userId)
            } catch (e: Exception) {
                Log.e(TAG, "writeLockedListToProvider error: ", e)
            }
        }
    }

    companion object {
        private const val TAG = "LawnchairLockedStateController"

        private const val RECENT_TASK_LOCKED_LIST = "com_android_systemui_recent_task_lockd_list"
        private const val RECENT_TASK_LOCKED_LIST_BK = "com_android_systemui_recent_task_locked_bk"
        private const val TASK_LOCK_LIST_KEY = "task_lock_list"
        const val TASK_LOCK_LIST_KEY_WITH_USERID = "task_lock_list_with_userid"
        const val TASK_LOCK_STATE = "tasklockstate"

        private var mReloaded = false
        private var time = 0

        @SuppressLint("StaticFieldLeak")
        private var sInstance: LawnchairLockedStateController? = null

        fun getInstance(context: Context): LawnchairLockedStateController {
            if (sInstance == null) {
                sInstance = LawnchairLockedStateController(context)
            }
            return sInstance!!
        }
    }
}
