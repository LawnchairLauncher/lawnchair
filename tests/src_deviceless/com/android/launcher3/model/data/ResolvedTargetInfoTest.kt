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

package com.android.launcher3.model.data

import android.content.ComponentName
import android.content.Intent
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.ComponentKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ResolvedTargetInfoTest {

    private val testTargetActivityComponent =
        ComponentName("com.example.target", "com.example.target.TargetActivity")
    private val testComponent = ComponentName("com.example.app", "com.example.app.MainActivity")
    private val dummyComponent = ComponentName("com.example.app", "com.example.app.DummyActivity")
    private val testUser: UserHandle = UserHandle.getUserHandleForUid(0)
    private val testIntent = Intent(Intent.ACTION_MAIN).setComponent(testComponent)
    private val testTargetIntent =
        Intent(Intent.ACTION_MAIN).setComponent(testTargetActivityComponent)
    private val testNullComponentIntent = Intent(Intent.ACTION_MAIN).setComponent(null)
    private val dummyIntent =
        Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName("com.example.app", "com.example.app.DummyActivity"))

    @Test
    fun `getTargetComponentKey targetActivityComponentName isNotNull returnsTargetActivityComponentKey`() {
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)
        val expectedKey = ComponentKey(testTargetActivityComponent, testUser)

        val actualKey = resolvedInfo.getTargetComponentKey()

        assertThat(actualKey).isNotNull()
        assertThat(actualKey!!.componentName).isEqualTo(testTargetActivityComponent)
        assertThat(actualKey).isEqualTo(expectedKey)
        assertThat(actualKey.user).isEqualTo(testUser)
    }

    @Test
    fun `getTargetComponentKey targetActivityComponentName isNull componentName isNotNull returnsComponentKey`() {
        val resolvedInfo = ResolvedTargetInfo(null, testComponent, testUser)
        val expectedKey = ComponentKey(testComponent, testUser)

        val actualKey = resolvedInfo.getTargetComponentKey()

        assertThat(actualKey).isNotNull()
        assertThat(actualKey!!.componentName).isEqualTo(testComponent)
        assertThat(actualKey.user).isEqualTo(testUser)
        assertThat(actualKey).isEqualTo(expectedKey)
    }

    @Test
    fun `getTargetComponentKey both targetActivityComponentName and componentName areNull returnsNull`() {
        val resolvedInfo = ResolvedTargetInfo(null, null, testUser)

        val actualKey = resolvedInfo.getTargetComponentKey()

        assertThat(actualKey).isNull()
    }

    @Test
    fun `matchTaskKey different userId`() {
        // Test matchTaskKey returns false when taskKey.userId is different from
        // ResolvedTargetInfo.user.identifier.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches =
            resolvedInfo.matchTaskKey(testTargetActivityComponent, testIntent, UserHandle.of(1))

        assertThat(matches).isFalse()
    }

    @Test
    fun `matchTaskKey with different user objects but same identifier`() {
        // Test matchTaskKey returns true when taskKey.userId is the same as
        // ResolvedTargetInfo.user.identifier, even if the UserHandle objects are different
        // instances, and other conditions for matching are met.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches =
            resolvedInfo.matchTaskKey(testTargetActivityComponent, testIntent, UserHandle.of(0))

        assertThat(matches).isTrue()
    }

    @Test
    fun `matchTaskKey with baseActivity matching targetActivityComponentName`() {
        // Test matchTaskKey returns true when taskKey.userId matches and taskKey.baseActivity
        // matches ResolvedTargetInfo's targetActivityComponentName (when
        // targetActivityComponentName is not null).
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches =
            resolvedInfo.matchTaskKey(testTargetActivityComponent, dummyIntent, UserHandle.of(0))

        assertThat(matches).isTrue()
    }

    @Test
    fun `matchTaskKey with baseActivity matching componentName`() {
        // Test matchTaskKey returns true when taskKey.userId matches and taskKey.baseActivity
        // matches ResolvedTargetInfo's componentName (when targetActivityComponentName is null and
        // componentName is not null).
        val resolvedInfo = ResolvedTargetInfo(null, testComponent, testUser)

        val matches = resolvedInfo.matchTaskKey(testComponent, dummyIntent, UserHandle.of(0))

        assertThat(matches).isTrue()
    }

    @Test
    fun `matchTaskKey with baseActivity not matching`() {
        // Test matchTaskKey returns false when taskKey.userId matches but taskKey.baseActivity
        // does
        // not match either targetActivityComponentName or componentName.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches = resolvedInfo.matchTaskKey(dummyComponent, dummyIntent, UserHandle.of(0))

        assertThat(matches).isFalse()
    }

    @Test
    fun `matchTaskKey with baseActivity and null targetComponentKey`() {
        // Test matchTaskKey returns false when taskKey.userId matches, taskKey.baseActivity is
        // not
        // null, but getTargetComponentKey() returns null (both targetActivityComponentName and
        // componentName are null).
        val resolvedInfo = ResolvedTargetInfo(null, null, testUser)

        val matches = resolvedInfo.matchTaskKey(dummyComponent, dummyIntent, UserHandle.of(0))

        assertThat(matches).isFalse()
    }

    @Test
    fun `matchTaskKey with null baseActivity and baseIntent component matching componentName`() {
        // Test matchTaskKey returns true when taskKey.userId matches, taskKey.baseActivity is
        // null,
        // and taskKey.baseIntent.component matches ResolvedTargetInfo's componentName.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches = resolvedInfo.matchTaskKey(null, testIntent, UserHandle.of(0))

        assertThat(matches).isTrue()
    }

    @Test
    fun `matchTaskKey with null baseActivity and baseIntent component matching targetActivityComponentName`() {
        // Test matchTaskKey returns true when taskKey.userId matches, taskKey.baseActivity is
        // null,
        // and taskKey.baseIntent.component matches ResolvedTargetInfo's
        // targetActivityComponentName.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches = resolvedInfo.matchTaskKey(null, testTargetIntent, UserHandle.of(0))

        assertThat(matches).isTrue()
    }

    @Test
    fun `matchTaskKey with null baseActivity and baseIntent component not matching`() {
        // Test matchTaskKey returns false when taskKey.userId matches, taskKey.baseActivity is
        // null, and taskKey.baseIntent.component does not match either componentName or
        // targetActivityComponentName.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches = resolvedInfo.matchTaskKey(null, dummyIntent, UserHandle.of(0))

        assertThat(matches).isFalse()
    }

    @Test
    fun `matchTaskKey with null baseActivity and null baseIntent component`() {
        // Test matchTaskKey returns false when taskKey.userId matches, taskKey.baseActivity is
        // null, and taskKey.baseIntent.component is null.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches = resolvedInfo.matchTaskKey(null, testNullComponentIntent, UserHandle.of(0))

        assertThat(matches).isFalse()
    }

    @Test
    fun `matchTaskKey with baseActivity when only targetActivityComponentName is set`() {
        // Test matchTaskKey with taskKey.baseActivity when ResolvedTargetInfo has
        // targetActivityComponentName set but componentName is null.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, null, testUser)

        val matches1 =
            resolvedInfo.matchTaskKey(testTargetActivityComponent, dummyIntent, UserHandle.of(0))
        val matches2 = resolvedInfo.matchTaskKey(testComponent, dummyIntent, UserHandle.of(0))

        assertThat(matches1).isTrue()
        assertThat(matches2).isFalse()
    }

    @Test
    fun `matchTaskKey with baseActivity when only componentName is set`() {
        // Test matchTaskKey with taskKey.baseActivity when ResolvedTargetInfo has componentName
        // set
        // but targetActivityComponentName is null.
        val resolvedInfo = ResolvedTargetInfo(null, testComponent, testUser)

        val matches1 =
            resolvedInfo.matchTaskKey(testTargetActivityComponent, dummyIntent, UserHandle.of(0))
        val matches2 = resolvedInfo.matchTaskKey(testComponent, dummyIntent, UserHandle.of(0))

        assertThat(matches1).isFalse()
        assertThat(matches2).isTrue()
    }

    @Test
    fun `matchTaskKey with null baseActivity  intent component matching componentName  targetActivityComponentName is null`() {
        // Test matchTaskKey when taskKey.baseActivity is null, taskKey.baseIntent.component
        // matches
        // ResolvedTargetInfo.componentName, and ResolvedTargetInfo.targetActivityComponentName is
        // null.
        val resolvedInfo = ResolvedTargetInfo(null, testComponent, testUser)

        val matches1 = resolvedInfo.matchTaskKey(null, testIntent, UserHandle.of(0))
        val matches2 = resolvedInfo.matchTaskKey(null, dummyIntent, UserHandle.of(0))

        assertThat(matches1).isTrue()
        assertThat(matches2).isFalse()
    }

    @Test
    fun `matchTaskKey with null baseActivity  intent component matching targetActivityComponentName  componentName is null`() {
        // Test matchTaskKey when taskKey.baseActivity is null, taskKey.baseIntent.component
        // matches
        // ResolvedTargetInfo.targetActivityComponentName, and ResolvedTargetInfo.componentName is
        // null.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, null, testUser)

        val matches1 = resolvedInfo.matchTaskKey(null, testTargetIntent, UserHandle.of(0))
        val matches2 = resolvedInfo.matchTaskKey(null, dummyIntent, UserHandle.of(0))

        assertThat(matches1).isTrue()
        assertThat(matches2).isFalse()
    }

    @Test
    fun `matchTaskKey with null baseActivity  intent component matches componentName  targetActivityComponentName is different`() {
        // Test matchTaskKey returns true when taskKey.baseActivity is null,
        // taskKey.baseIntent.component matches ResolvedTargetInfo.componentName, even if
        // ResolvedTargetInfo.targetActivityComponentName is set and different.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches = resolvedInfo.matchTaskKey(null, testIntent, UserHandle.of(0))

        assertThat(matches).isTrue()
    }

    @Test
    fun `matchTaskKey with null baseActivity  intent component matches targetActivityComponentName  componentName is different`() {
        // Test matchTaskKey returns true when taskKey.baseActivity is null,
        // taskKey.baseIntent.component matches ResolvedTargetInfo.targetActivityComponentName, even
        // if ResolvedTargetInfo.componentName is set and different.
        val resolvedInfo = ResolvedTargetInfo(testTargetActivityComponent, testComponent, testUser)

        val matches = resolvedInfo.matchTaskKey(null, testTargetIntent, UserHandle.of(0))

        assertThat(matches).isTrue()
    }
}
