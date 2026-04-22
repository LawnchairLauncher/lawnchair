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
import java.io.Writer

/**
 * [TabbedWriter] is a convience class which tracks and writes correctly tabbed lines for generating
 * source files. These files don't need to be correctly tabbed as they're ephemeral and not part of
 * the source tree, but correct tabbing makes debugging much easier when the build fails.
 */
class TabbedWriter(writer: Writer) : AutoCloseable {
    private val target = BufferedWriter(writer)
    private var isInProgress = false
    var tabCount: Int = 0
        private set

    override fun close() = target.close()

    fun line() {
        target.newLine()
        isInProgress = false
    }

    fun line(str: String) {
        if (isInProgress) {
            target.newLine()
        }

        target.append("    ".repeat(tabCount))
        target.append(str)
        target.newLine()
        isInProgress = false
    }

    fun completeLine(str: String) {
        if (!isInProgress) {
            target.newLine()
            target.append("    ".repeat(tabCount))
        }

        target.append(str)
        target.newLine()
        isInProgress = false
    }

    fun startLine(str: String) {
        if (isInProgress) {
            target.newLine()
        }

        target.append("    ".repeat(tabCount))
        target.append(str)
        isInProgress = true
    }

    fun appendLine(str: String) {
        if (!isInProgress) {
            target.append("    ".repeat(tabCount))
        }

        target.append(str)
        isInProgress = true
    }

    fun braceBlock(str: String = "", write: TabbedWriter.() -> Unit) {
        block(str, " {", "}", true, write)
    }

    fun parenBlock(str: String = "", write: TabbedWriter.() -> Unit) {
        block(str, "(", ")", false, write)
    }

    private fun block(
        str: String,
        start: String,
        end: String,
        newLineForEnd: Boolean,
        write: TabbedWriter.() -> Unit,
    ) {
        appendLine(str)
        completeLine(start)

        tabCount++
        this.write()
        tabCount--

        if (newLineForEnd) {
            line(end)
        } else {
            startLine(end)
        }
    }

    companion object {
        fun writeTo(writer: Writer, write: TabbedWriter.() -> Unit) {
            TabbedWriter(writer).use { it.write() }
        }
    }
}
