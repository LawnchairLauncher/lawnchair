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

package com.android.wm.shell.compatui.impl


import android.graphics.Point
import android.testing.AndroidTestingRunner
import android.view.View
import androidx.test.filters.SmallTest
import com.android.wm.shell.compatui.api.CompatUILayout
import com.android.wm.shell.compatui.api.CompatUILifecyclePredicates
import com.android.wm.shell.compatui.api.CompatUIRepository
import com.android.wm.shell.compatui.api.CompatUISpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for {@link DefaultCompatUIRepository}.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:DefaultCompatUIRepositoryTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class DefaultCompatUIRepositoryTest {

    lateinit var repository: CompatUIRepository

    @Before
    fun setUp() {
        repository = DefaultCompatUIRepository()
    }

    @Test(expected = IllegalStateException::class)
    fun `addSpec throws exception with specs with duplicate id`() {
        repository.addSpec(specById("one"))
        repository.addSpec(specById("one"))
    }

    @Test
    fun `iterateOn invokes the consumer`() {
        with(repository) {
            addSpec(specById("one"))
            addSpec(specById("two"))
            addSpec(specById("three"))
            val consumer = object : (CompatUISpec) -> Unit {
                var acc = ""
                override fun invoke(spec: CompatUISpec) {
                    acc += spec.name
                }
            }
            iterateOn(consumer)
            assertEquals("onetwothree", consumer.acc)
        }
    }

    @Test
    fun `findSpec returns existing specs`() {
        with(repository) {
            val one = specById("one")
            val two = specById("two")
            val three = specById("three")
            addSpec(one)
            addSpec(two)
            addSpec(three)
            assertEquals(findSpec("one"), one)
            assertEquals(findSpec("two"), two)
            assertEquals(findSpec("three"), three)
            assertNull(findSpec("abc"))
        }
    }

    private fun specById(name: String): CompatUISpec =
        CompatUISpec(name = name,
            lifecycle = CompatUILifecyclePredicates(
                creationPredicate = { _, _ -> true },
                removalPredicate = { _, _, _ -> true }
            ),
            layout = CompatUILayout(
                viewBuilder = { ctx, _, _ -> View(ctx) },
                positionFactory = { _, _, _, _ -> Point(0, 0) }
            )
        )
}