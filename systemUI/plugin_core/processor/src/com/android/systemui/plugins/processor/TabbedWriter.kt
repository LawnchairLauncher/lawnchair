/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.plugins.processor

import java.io.BufferedWriter

interface TabbedWriter {
    val tabCount: Int

    fun line()

    fun line(str: String)

    fun completeLine(str: String)

    fun startLine(str: String)

    fun appendLine(str: String)

    fun braceBlock(str: String = "", write: TabbedWriter.() -> Unit)

    fun parenBlock(str: String = "", write: TabbedWriter.() -> Unit)
}

/**
 * [TabbedWriter] is a convenience class which tracks and writes correctly tabbed lines of text for
 * generating source files. These files don't need to be correctly tabbed as they're ephemeral and
 * not part of the source tree, but correct tabbing makes debugging much easier.
 */
class TabbedWriterImpl(private val target: BufferedWriter) : TabbedWriter {
    private var isInProgress = false
    override var tabCount: Int = 0
        private set

    override fun line() {
        target.newLine()
        isInProgress = false
    }

    override fun line(str: String) {
        if (isInProgress) {
            target.newLine()
        }

        target.append("    ".repeat(tabCount))
        target.append(str)
        target.newLine()
        isInProgress = false
    }

    override fun completeLine(str: String) {
        if (!isInProgress) {
            target.append("    ".repeat(tabCount))
        }

        target.append(str)
        target.newLine()
        isInProgress = false
    }

    override fun startLine(str: String) {
        if (isInProgress) {
            target.newLine()
        }

        target.append("    ".repeat(tabCount))
        target.append(str)
        isInProgress = true
    }

    override fun appendLine(str: String) {
        if (!isInProgress) {
            target.append("    ".repeat(tabCount))
        }

        target.append(str)
        isInProgress = true
    }

    override fun braceBlock(str: String, write: TabbedWriter.() -> Unit) {
        block(str, " {", "}", newLine = true, write)
    }

    override fun parenBlock(str: String, write: TabbedWriter.() -> Unit) {
        block(str, "(", ")", newLine = false, write)
    }

    private fun block(
        str: String,
        start: String,
        end: String,
        newLine: Boolean,
        write: TabbedWriter.() -> Unit,
    ) {
        if (str != "") {
            startLine(str)
        }
        appendLine(start)

        tabCount++
        this.write()
        tabCount--

        if (newLine) {
            completeLine(end)
        } else {
            appendLine(end)
        }
    }
}
