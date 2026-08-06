/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.util

import android.content.Context
import android.os.UserHandle
import android.os.UserManager
import android.util.LongSparseArray
import com.android.launcher3.EncryptionType
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.logging.FileLog
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.UserIconInfo

/**
 * Remembers the private profile across periods where it is not visible to Launcher.
 *
 * Access to the private profile is granted by `ACCESS_HIDDEN_PROFILES`, an appop permission that is
 * only held while this app holds the home role. Losing the home role - or simply locking the private
 * space - therefore makes the profile disappear from [UserCache] and from `LauncherApps`, which is
 * indistinguishable from the profile having been deleted. Without a record of the profile, the model
 * loader treats every private-space workspace item as belonging to a deleted user or to an
 * uninstalled app, and deletes the rows permanently.
 *
 * The recorded serial number lets those rows be recognised and preserved. It is cleared only on an
 * explicit profile-removed broadcast, never by mere absence, because absence is exactly the
 * ambiguous signal this class exists to work around.
 */
object PrivateProfileTracker {

    private const val TAG = "PrivateProfileTracker"

    const val INVALID_SERIAL = -1L

    const val INVALID_USER_ID = -1

    /**
     * Serial number of the last private profile we were able to observe, or [INVALID_SERIAL].
     *
     * Not backed up: a serial number is only meaningful on the device that issued it, and restoring
     * a foreign one would make this device's rows look like they belong to a private profile.
     */
    @JvmField
    val KNOWN_PRIVATE_PROFILE_SERIAL =
        LauncherPrefs.nonRestorableItem(
            "private_profile_serial",
            INVALID_SERIAL,
            EncryptionType.DEVICE_PROTECTED
        )

    /**
     * User id of the same profile, kept alongside the serial so that a [UserHandle] can be rebuilt
     * while the profile is invisible. Without it the loader cannot resolve the owning user of a
     * private-space row and has to treat it as belonging to a deleted user.
     */
    @JvmField
    val KNOWN_PRIVATE_PROFILE_USER_ID =
        LauncherPrefs.nonRestorableItem(
            "private_profile_user_id",
            INVALID_USER_ID,
            EncryptionType.DEVICE_PROTECTED
        )

    /**
     * Records the private profile's serial number whenever one is visible.
     *
     * Deliberately does nothing when no private profile is visible: that state also occurs while the
     * space is locked or while another launcher holds the home role, and forgetting the serial there
     * would let the loader delete the very items this class protects.
     */
    @JvmStatic
    fun onUserCacheUpdated(context: Context, userCache: UserCache) {
        val user =
            userCache.userProfiles.firstOrNull { userCache.getUserInfo(it).isPrivate } ?: return
        val serial = userCache.getSerialNumberForUser(user)
        val userId = try {
            user.identifier
        } catch (t: Throwable) {
            // Also a hidden API. UserHandle.hashCode returns the same user id, so it is a safe
            // fallback rather than a reason to give up recording the profile.
            user.hashCode()
        }
        val prefs = LauncherPrefs.get(context)
        if (
            prefs.get(KNOWN_PRIVATE_PROFILE_SERIAL) != serial ||
                prefs.get(KNOWN_PRIVATE_PROFILE_USER_ID) != userId
        ) {
            FileLog.d(TAG, "Recording private profile serial=$serial userId=$userId")
            prefs.put(KNOWN_PRIVATE_PROFILE_SERIAL, serial)
            prefs.put(KNOWN_PRIVATE_PROFILE_USER_ID, userId)
        }
    }

    /**
     * Forgets the recorded profile when that profile is genuinely removed. This is the only signal
     * that reliably distinguishes deletion from invisibility, and it is what allows the items of a
     * deleted private space to be cleaned up on the next load.
     */
    @JvmStatic
    fun onProfileRemoved(context: Context, user: UserHandle) {
        val prefs = LauncherPrefs.get(context)
        val known = prefs.get(KNOWN_PRIVATE_PROFILE_SERIAL)
        if (known == INVALID_SERIAL) return
        // UserCache refreshes asynchronously, so the removed user is usually still resolvable here.
        // Its fallback path also reports a user carrying the recorded serial as private, which
        // covers the case where the cache has already dropped it.
        val info = UserCache.getInstance(context).getUserInfo(user)
        if (info.isPrivate || info.userSerial == known) {
            FileLog.d(TAG, "Private profile removed, forgetting serial=$known")
            prefs.put(KNOWN_PRIVATE_PROFILE_SERIAL, INVALID_SERIAL)
            prefs.put(KNOWN_PRIVATE_PROFILE_USER_ID, INVALID_USER_ID)
        }
    }

    /**
     * Whether the profile we recorded still exists on this device.
     *
     * This is the distinction the rest of this class is built around, and it turns out the platform
     * will answer it. [UserManager.getSerialNumberForUser] resolves a user id straight through
     * UserManagerService, which is a different question from whether `LauncherApps` will show us the
     * profile: a private space that is merely locked, or hidden because another launcher holds the
     * home role, still reports its serial, while one that has actually been deleted reports -1.
     *
     * Returns true when the query fails, so nothing is ever deleted on the strength of an error.
     */
    @JvmStatic
    fun isRecordedProfileStillPresent(context: Context): Boolean {
        val serial = LauncherPrefs.get(context).get(KNOWN_PRIVATE_PROFILE_SERIAL)
        if (serial == INVALID_SERIAL) return false
        val user = getKnownPrivateProfileUser(context) ?: return false
        return try {
            context.getSystemService(UserManager::class.java)
                ?.getSerialNumberForUser(user) == serial
        } catch (t: Throwable) {
            true
        }
    }

    /** Drops the recorded identity. Used once the profile is known to be gone. */
    @JvmStatic
    fun forgetRecordedProfile(context: Context) {
        val prefs = LauncherPrefs.get(context)
        if (prefs.get(KNOWN_PRIVATE_PROFILE_SERIAL) == INVALID_SERIAL) return
        FileLog.d(TAG, "Recorded private profile no longer exists; forgetting it")
        prefs.put(KNOWN_PRIVATE_PROFILE_SERIAL, INVALID_SERIAL)
        prefs.put(KNOWN_PRIVATE_PROFILE_USER_ID, INVALID_USER_ID)
    }

    /**
     * The profile to add to a loader's user table when the platform did not enumerate it, or null.
     *
     * A private space that is merely locked, or hidden because another launcher holds the home role,
     * has to stay resolvable or every one of its workspace rows reads as belonging to a deleted user
     * and is removed. When the recorded profile turns out to be genuinely gone the record is dropped
     * here instead, before the workspace is read, so this load clears those leftover rows normally.
     */
    @JvmStatic
    fun getHiddenProfileToInject(context: Context, alreadyKnown: LongSparseArray<UserHandle>):
        UserHandle? {
        val serialNo = getKnownPrivateProfileSerial(context)
        if (serialNo == INVALID_SERIAL || alreadyKnown.get(serialNo) != null) return null
        if (!isRecordedProfileStillPresent(context)) {
            forgetRecordedProfile(context)
            return null
        }
        return getKnownPrivateProfileUser(context)
    }

    /**
     * Resolves a user that [UserCache] does not know about, which happens while a profile is hidden
     * from us - a locked private space, or any private space while another launcher holds the home
     * role.
     *
     * The two-argument [UserIconInfo] constructor derives the serial number from
     * [UserHandle.hashCode], i.e. the user id. That is correct for the main user and wrong for every
     * other profile, so items would silently be attributed to the wrong profileId. Ask [UserManager]
     * for the real serial instead, and recognise a hidden private profile by [knownSerial] so its
     * icons keep their badge.
     */
    @JvmStatic
    fun resolveUnknownUser(context: Context, user: UserHandle, knownSerial: Long): UserIconInfo {
        val serial = try {
            context.getSystemService(UserManager::class.java)?.getSerialNumberForUser(user)
                ?: INVALID_SERIAL
        } catch (t: Throwable) {
            INVALID_SERIAL
        }
        if (serial == INVALID_SERIAL) {
            // Nothing better available; keep the historic behaviour rather than inventing a serial.
            return UserIconInfo(user, UserIconInfo.TYPE_MAIN)
        }
        val type =
            if (serial == knownSerial) UserIconInfo.TYPE_PRIVATE else UserIconInfo.TYPE_MAIN
        return UserIconInfo(user, type, serial)
    }

    /**
     * Whether [serial] identifies a private profile that exists on this device, for deciding whether
     * restored rows carrying that profileId may be kept.
     *
     * Deliberately narrow: matching on the serial alone would let a backup from another device hand
     * its private space items to whichever profile happened to be issued the same serial here. The
     * recorded identity is accepted only when it still resolves, because Lawnchair's own backup
     * copies the preference files verbatim and so imports the other device's record along with them.
     */
    @JvmStatic
    fun isRestorablePrivateProfileSerial(context: Context, serial: Long): Boolean {
        if (serial == INVALID_SERIAL) return false
        val userCache = UserCache.getInstance(context)
        if (userCache.userProfiles.any {
                userCache.getSerialNumberForUser(it) == serial && userCache.getUserInfo(it).isPrivate
            }
        ) {
            return true
        }
        return LauncherPrefs.get(context).get(KNOWN_PRIVATE_PROFILE_SERIAL) == serial &&
            isRecordedProfileStillPresent(context)
    }

    /**
     * The recorded private profile as a [UserHandle], or null when none was ever recorded.
     *
     * Used to keep private-space rows resolvable during a load that happens while the profile is
     * hidden. The handle is rebuilt from the stored user id rather than queried, precisely because
     * the platform will not enumerate the profile for us in that state.
     */
    @JvmStatic
    fun getKnownPrivateProfileUser(context: Context): UserHandle? {
        val prefs = LauncherPrefs.get(context)
        if (prefs.get(KNOWN_PRIVATE_PROFILE_SERIAL) == INVALID_SERIAL) return null
        val userId = prefs.get(KNOWN_PRIVATE_PROFILE_USER_ID)
        if (userId == INVALID_USER_ID) return null
        return try {
            UserHandle.of(userId)
        } catch (t: Throwable) {
            // UserHandle.of is a hidden API. If it is ever unavailable the failure arrives as a
            // LinkageError rather than an Exception, and this runs on every load - letting it
            // escape would take the whole workspace down with it.
            FileLog.e(TAG, "Unable to rebuild private profile handle for userId=$userId, $t")
            null
        }
    }

    /**
     * Whether items stored against [serial] must be preserved rather than deleted when their target
     * cannot be resolved.
     *
     * True when the serial belongs to a private profile that is currently inaccessible - either
     * visible but in quiet mode, or not visible at all while still being the profile we last
     * recorded. False for a visible, unlocked private profile, where a missing app really is
     * missing and the normal cleanup should apply.
     */
    @JvmStatic
    fun isInaccessiblePrivateProfile(context: Context, serial: Long): Boolean {
        if (serial == INVALID_SERIAL) return false
        val userCache = UserCache.getInstance(context)
        val liveUser =
            userCache.userProfiles.firstOrNull {
                userCache.getUserInfo(it).isPrivate && userCache.getSerialNumberForUser(it) == serial
            }
        if (liveUser != null) {
            return try {
                context.getSystemService(UserManager::class.java)?.isQuietModeEnabled(liveUser)
                    ?: false
            } catch (t: Throwable) {
                // Unable to tell; assume inaccessible so that nothing is destroyed.
                true
            }
        }
        return LauncherPrefs.get(context).get(KNOWN_PRIVATE_PROFILE_SERIAL) == serial &&
            isRecordedProfileStillPresent(context)
    }

    /**
     * The [UserHandle] recorded items should be attributed to while the profile is invisible, or
     * null when the profile is currently visible or unknown.
     */
    @JvmStatic
    fun getKnownPrivateProfileSerial(context: Context): Long =
        LauncherPrefs.get(context).get(KNOWN_PRIVATE_PROFILE_SERIAL)
}
