/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.util.rule

import android.content.pm.LauncherApps
import android.content.pm.LauncherUserInfo
import android.os.UserHandle
import android.os.UserManager
import android.os.UserManager.USER_TYPE_PROFILE_CLONE
import android.os.UserManager.USER_TYPE_PROFILE_MANAGED
import android.os.UserManager.USER_TYPE_PROFILE_PRIVATE
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.UserIconInfo
import com.android.launcher3.util.UserIconInfo.Companion.TYPE_CLONED
import com.android.launcher3.util.UserIconInfo.Companion.TYPE_PRIVATE
import com.android.launcher3.util.UserIconInfo.Companion.TYPE_WORK
import com.android.launcher3.util.UserIconInfo.UserType
import com.android.launcher3.util.rule.MockUsersRule.MockUser
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

/**
 * Rule for mocking users during tests. Multiple users can be mocking by specifying multiple
 * [MockUser] annotations. If any [MockUser] annotation is present, it will replace all real users.
 * The first mocked user will always match [android.os.Process.myUserHandle].
 */
class MockUsersRule(private val app: SandboxApplication) : TestRule {

    private val generatedUsers = mutableListOf<UserIconInfo>()

    override fun apply(base: Statement, description: Description): Statement {
        val users = getMockUsers(description)
        if (users.isEmpty()) return base

        return object : Statement() {
            override fun evaluate() {
                setupMockUsers(users)
                base.evaluate()
            }
        }
    }

    fun findUser(predicate: (UserIconInfo) -> Boolean): UserHandle =
        generatedUsers.find(predicate)!!.user

    private fun getMockUsers(description: Description): List<MockUser> =
        description.getAnnotation<MockUsers>()?.value?.toList()
            ?: description.getAnnotation<MockUser>()?.let { listOf(it) }
            ?: emptyList()

    private inline fun <reified T> Description.getAnnotation(): T? where T : Annotation =
        (if (isTest) getAnnotation(T::class.java) else null)
            ?: testClass.getAnnotation(T::class.java)

    private fun setupMockUsers(users: List<MockUser>) {
        val launcherApps = app.spyService(LauncherApps::class.java)
        val userManager = app.spyService(UserManager::class.java)

        val userList = mutableListOf<UserHandle>()
        var startUserId = UserHandle.myUserId()
        users.forEach { mockUser ->
            val user = UserHandle.of(startUserId)
            startUserId++
            val serial = if (mockUser.userSerial == -1) user.hashCode() else mockUser.userSerial

            val launcherUserInfo = mock(LauncherUserInfo::class.java)
            doReturn(serial).whenever(launcherUserInfo).userSerialNumber
            doReturn(
                    when (mockUser.userType) {
                        TYPE_PRIVATE -> USER_TYPE_PROFILE_PRIVATE
                        TYPE_CLONED -> USER_TYPE_PROFILE_CLONE
                        TYPE_WORK -> USER_TYPE_PROFILE_MANAGED
                        else -> ""
                    }
                )
                .whenever(launcherUserInfo)
                .userType
            doReturn(launcherUserInfo).whenever(launcherApps).getLauncherUserInfo(user)
            doReturn(mockUser.preinstalledApps.toList())
                .whenever(launcherApps)
                .getPreInstalledSystemPackages(user)

            doReturn(mockUser.isUserUnlocked).whenever(userManager).isUserUnlocked(user)
            doReturn(mockUser.isQuietModeEnabled).whenever(userManager).isQuietModeEnabled(user)

            generatedUsers.add(
                UserIconInfo(user = user, type = mockUser.userType, userSerial = serial.toLong())
            )
            userList.add(user)
        }

        doReturn(userList).whenever(userManager).userProfiles
    }

    /** Interface to indicate a mock user */
    @Retention(RUNTIME)
    @JvmRepeatable(MockUsers::class)
    @Target(FUNCTION, CLASS)
    annotation class MockUser(
        @UserType val userType: Int,
        val userSerial: Int = -1, // If not specified, userHandle's hashCode is used
        val preinstalledApps: Array<String> = [],
        val isUserUnlocked: Boolean = true,
        val isQuietModeEnabled: Boolean = false,
    )

    @Retention(RUNTIME)
    @Target(FUNCTION, CLASS)
    annotation class MockUsers(vararg val value: MockUser)
}
