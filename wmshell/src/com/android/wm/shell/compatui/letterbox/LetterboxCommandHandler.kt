/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.content.Context
import android.graphics.Color
import com.android.internal.protolog.ProtoLog
import com.android.window.flags.Flags
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_APP_COMPAT
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellCommandHandler.ShellCommandActionHandler
import com.android.wm.shell.sysui.ShellInit
import java.io.PrintWriter
import javax.inject.Inject

/**
 * Handles the shell commands for the CompatUI.
 *
 * <p> Use with [adb shell wm shell letterbox &lt;command&gt;].
 */
@WMSingleton
class LetterboxCommandHandler @Inject constructor(
    private val context: Context,
    shellInit: ShellInit,
    shellCommandHandler: ShellCommandHandler,
    private val letterboxConfiguration: LetterboxConfiguration
) : ShellCommandActionHandler {

    companion object {
        @JvmStatic
        private val TAG = "LetterboxCommandHandler"
    }

    init {
        if (Flags.appCompatRefactoring()) {
            ProtoLog.v(
                WM_SHELL_APP_COMPAT,
                "%s: %s",
                TAG,
                "Initializing LetterboxCommandHandler"
            )
            shellInit.addInitCallback({
                shellCommandHandler.addCommandCallback("letterbox", this, this)
            }, this)
        }
    }

    override fun onShellCommand(args: Array<out String>?, pw: PrintWriter?): Boolean {
        if (args == null || pw == null) {
            pw!!.println("Missing arguments.")
            return false
        }
        return when (args.size) {
            1 -> onNoParamsCommand(args[0], pw)
            2 -> onSingleParamCommand(args[0], args[1], pw)
            else -> {
                pw.println("Invalid command: " + args[0])
                return false
            }
        }
    }

    override fun printShellCommandHelp(pw: PrintWriter?, prefix: String?) {
        pw?.println(
            """
                    $prefix backgroundColor color"
                    $prefix      Color of letterbox which is to be used when letterbox background
                    $prefix      type is 'solid-color'. See Color#parseColor for allowed color
                    $prefix      formats (#RRGGBB and some colors by name, e.g. magenta or olive).
                    $prefix backgroundColorResource resource_name"
                    $prefix      Color resource name of letterbox background which is used when
                    $prefix      background type is 'solid-color'. Parameter is a color resource
                    $prefix      name, for example, @android:color/system_accent2_50.
                    $prefix backgroundColorReset"
                    $prefix      Resets the background color to the default value."
                    $prefix cornerRadius"
                    $prefix      Corners radius (in pixels) for activities in the letterbox mode."
                    $prefix      If cornerRadius < 0, it will be ignored and corners of the"
                    $prefix      activity won't be rounded."
                    $prefix cornerRadiusReset"
                    $prefix      Resets the rounded corners radius to the default value."
                """.trimIndent()
        )
    }

    private fun onSingleParamCommand(command: String, value: String, pw: PrintWriter): Boolean {
        when (command) {
            "backgroundColor" -> {
                return invokeWhenValid(
                    pw,
                    value,
                    ::strToColor,
                    { color ->
                        letterboxConfiguration.setLetterboxBackgroundColor(color)
                    },
                    { c -> "$c is not a valid color." }
                )
            }

            "backgroundColorResource" -> return invokeWhenValid(
                pw,
                value,
                ::nameToColorId,
                { color ->
                    letterboxConfiguration.setLetterboxBackgroundColorResourceId(color)
                },
                { c ->
                    "$c is not a valid resource. Color in '@android:color/resource_name'" +
                            " format should be provided as an argument."
                }
            )

            "cornerRadius" -> return invokeWhenValid(
                pw,
                value,
                ::strToInt{ it >= 0 },
                { radius ->
                    letterboxConfiguration.setLetterboxActivityCornersRadius(radius)
                },
                { r ->
                    "$r is not a valid radius. It must be an integer >= 0."
                }
            )

            else -> {
                pw.println("Invalid command: $value")
                return false
            }
        }
    }

    private fun onNoParamsCommand(command: String, pw: PrintWriter): Boolean {
        when (command) {
            "backgroundColor" -> {
                pw.println(
                    "    Background color: " + Integer.toHexString(
                        letterboxConfiguration.getLetterboxBackgroundColor()
                            .toArgb()
                    )
                )
                return true
            }

            "backgroundColorReset" -> {
                letterboxConfiguration.resetLetterboxBackgroundColor()
                return true
            }

            "cornerRadius" -> {
                pw.println(
                    "    Rounded corners radius: " +
                        "${letterboxConfiguration.getLetterboxActivityCornersRadius()} px."
                )
                return true
            }

            "cornerRadiusReset" -> {
                letterboxConfiguration.resetLetterboxActivityCornersRadius()
                return true
            }

            else -> {
                pw.println("Invalid command: $command")
                return false
            }
        }
    }

    private fun <T> invokeWhenValid(
        pw: PrintWriter,
        input: String,
        converter: (String) -> T?,
        consumer: (T) -> Unit,
        errorMessage: (String) -> String = { value -> " Wrong input value: $value." }
    ): Boolean {
        converter(input)?.let {
            consumer(it)
            return true
        }
        pw.println(errorMessage(input))
        return false
    }

    // Converts a String to Color if possible or it returns null otherwise.
    private fun strToColor(str: String): Color? =
        try {
            Color.valueOf(Color.parseColor(str))
        } catch (e: IllegalArgumentException) {
            null
        }

    // Converts a resource id to Color if possible or it returns null otherwise.
    private fun nameToColorId(str: String): Int? =
        try {
            context.resources.getIdentifier(str, "color", "com.android.internal")
        } catch (e: IllegalArgumentException) {
            null
        }

    // Converts a String to Int which if possible or it returns null otherwise.
    // If a predicate is set, it also returns [null] if the predicate evaluate to [false].
    private fun strToInt(predicate: (Int) -> Boolean = { _ -> true }): (String) -> Int? = { str ->
        try {
            val value = str.toInt()
            if (predicate(value)) value else null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
