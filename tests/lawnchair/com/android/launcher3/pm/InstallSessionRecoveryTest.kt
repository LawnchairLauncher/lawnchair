package com.android.launcher3.pm

import android.app.Application
import android.content.Context
import android.content.pm.PackageInstaller.SessionInfo
import android.os.Process
import android.os.UserHandle
import com.android.launcher3.util.PackageUserKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35], application = Application::class)
class InstallSessionRecoveryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val user = Process.myUserHandle()
    private val key = PackageUserKey("example.archived", user)

    @Test
    fun failedUnarchivalRecoversWithoutPromiseIconAfterCreation() {
        val helper = FakeHelper(context, session(true))
        val callback = RecordingCallback()
        val tracker = tracker(helper, callback)
        tracker.onCreated(1)
        helper.sessions.clear() // Finished sessions are no longer available from the installer.
        tracker.onFinished(1, false)
        assertEquals(listOf(key), callback.failures)
        assertEquals(listOf(1), helper.removed)
        tracker.onFinished(1, false)
        assertEquals(1, callback.failures.size)
    }

    @Test
    fun discoversAlreadyActiveUnarchivalWithoutPromiseIcon() {
        val helper = FakeHelper(context, session(true))
        val callback = RecordingCallback()
        val tracker = tracker(helper, callback)
        tracker.onFinished(99, false) // Snapshot active sessions before the installer drops them.
        helper.sessions.clear()
        tracker.onFinished(1, false)
        assertEquals(listOf(key), callback.failures)
    }

    @Test
    fun failedUntrackedOrdinaryInstallDoesNotRunPromiseRecovery() {
        val helper = FakeHelper(context, session(false))
        val callback = RecordingCallback()
        tracker(helper, callback).onFinished(1, false)
        assertEquals(emptyList<PackageUserKey>(), callback.failures)
        assertEquals(emptyList<Int>(), helper.removed)
    }

    @Test
    fun failedTrackedOrdinaryInstallStillRecoversPromiseIcon() {
        val helper = FakeHelper(context, session(false), promiseIcon = true)
        val callback = RecordingCallback()
        tracker(helper, callback).onFinished(1, false)
        assertEquals(listOf(key), callback.failures)
        assertEquals(listOf(1), helper.removed)
    }

    @Test
    fun successfulUnarchivalDoesNotRunFailureRecovery() {
        val helper = FakeHelper(context, session(true))
        val callback = RecordingCallback()
        tracker(helper, callback).onFinished(1, true)
        assertEquals(emptyList<PackageUserKey>(), callback.failures)
        assertEquals(emptyList<Int>(), helper.removed)
    }

    private fun session(unarchival: Boolean) = object : SessionInfo() {
        override fun isUnarchival() = unarchival
    }.apply {
        sessionId = 1
        appPackageName = key.mPackageName
        userId = user.identifier
    }

    private fun tracker(helper: FakeHelper, callback: RecordingCallback) = InstallSessionTracker(
        helper, callback, context.packageManager.packageInstaller, null,
    )

    private class FakeHelper(context: Context, val session: SessionInfo, val promiseIcon: Boolean = false) : InstallSessionHelper(context) {
        val sessions = hashMapOf(PackageUserKey(session.appPackageName, Process.myUserHandle()) to session)
        val removed = mutableListOf<Int>()
        override fun getActiveSessions() = sessions
        override fun getVerifiedSessionInfo(id: Int) = session
        override fun tryQueuePromiseAppIcon(info: SessionInfo?) = Unit
        override fun promiseIconAddedForId(id: Int) = promiseIcon
        override fun removePromiseIconId(id: Int) { removed += id }
    }

    private class RecordingCallback : InstallSessionTracker.Callback {
        val failures = mutableListOf<PackageUserKey>()
        override fun onSessionFailure(packageName: String, user: UserHandle) { failures += PackageUserKey(packageName, user) }
        override fun onUpdateSessionDisplay(key: PackageUserKey, info: SessionInfo) = Unit
        override fun onPackageStateChanged(info: PackageInstallInfo) = Unit
        override fun onInstallSessionCreated(info: PackageInstallInfo) = Unit
    }
}
