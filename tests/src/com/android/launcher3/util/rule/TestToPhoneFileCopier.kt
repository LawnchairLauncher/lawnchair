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

package com.android.launcher3.util.rule

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Copy a file from the tests assets folder to the phone. */
class TestToPhoneFileCopier(
    val src: String,
    dest: String,
    private val removeOnFinish: Boolean = false
) : TestRule {

    private val dstFile =
        File(InstrumentationRegistry.getInstrumentation().targetContext.dataDir, dest)

    fun getDst() = dstFile.absolutePath

    fun before() =
        dstFile.writeBytes(
            InstrumentationRegistry.getInstrumentation().context.assets.open(src).readBytes()
        )

    fun after() {
        if (removeOnFinish) {
            dstFile.delete()
        }
    }

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                before()
                try {
                    base.evaluate()
                } finally {
                    after()
                }
            }
        }
}
