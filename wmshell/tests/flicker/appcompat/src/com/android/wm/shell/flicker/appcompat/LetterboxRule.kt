/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.flicker.appcompat

import android.app.Instrumentation
import android.system.helpers.CommandsHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** JUnit Rule to handle letterboxStyles and states */
class LetterboxRule(
    private val withLetterboxEducationEnabled: Boolean = false,
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
    private val cmdHelper: CommandsHelper = CommandsHelper.getInstance(instrumentation)
) : TestRule {

    private val execAdb: (String) -> String = { cmd -> cmdHelper.executeShellCommand(cmd) }
    private lateinit var _letterboxStyle: MutableMap<String, String>

    val letterboxStyle: Map<String, String>
        get() {
            if (!::_letterboxStyle.isInitialized) {
                _letterboxStyle = mapLetterboxStyle()
            }
            return _letterboxStyle
        }

    val cornerRadius: Int?
        get() = asInt(letterboxStyle["Corner radius"])

    val hasCornerRadius: Boolean
        get() {
            val radius = cornerRadius
            return radius != null && radius > 0
        }

    val isIgnoreOrientationRequest: Boolean
        get() = execAdb("wm get-ignore-orientation-request")?.contains("true") ?: false

    override fun apply(base: Statement?, description: Description?): Statement {
        resetLetterboxStyle()
        _letterboxStyle = mapLetterboxStyle()
        val isLetterboxEducationEnabled = _letterboxStyle.getValue("Is education enabled")
        if ("$withLetterboxEducationEnabled" != isLetterboxEducationEnabled) {
            execAdb("wm set-letterbox-style --isEducationEnabled " + withLetterboxEducationEnabled)
        }
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                try {
                    base!!.evaluate()
                } finally {
                    resetLetterboxStyle()
                }
            }
        }
    }

    private fun mapLetterboxStyle(): HashMap<String, String> {
        val res = execAdb("wm get-letterbox-style")
        val lines = res.lines()
        val map = HashMap<String, String>()
        for (line in lines) {
            val keyValuePair = line.split(":")
            if (keyValuePair.size == 2) {
                val key = keyValuePair[0].trim()
                map[key] = keyValuePair[1].trim()
            }
        }
        return map
    }

    private fun resetLetterboxStyle() {
        execAdb("wm reset-letterbox-style")
    }

    private fun asInt(str: String?): Int? =
        try {
            str?.toInt()
        } catch (e: NumberFormatException) {
            null
        }
}
