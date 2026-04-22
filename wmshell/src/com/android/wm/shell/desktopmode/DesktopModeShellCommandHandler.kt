/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.desktopmode

import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.window.DesktopExperienceFlags
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.UnminimizeReason
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource.ADB_COMMAND
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource.UNKNOWN
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.transition.FocusTransitionObserver
import java.io.PrintWriter

/** Handles the shell commands for the DesktopTasksController. */
class DesktopModeShellCommandHandler(
    private val controller: DesktopTasksController,
    private val focusTransitionObserver: FocusTransitionObserver,
) : ShellCommandHandler.ShellCommandActionHandler {

    override fun onShellCommand(args: Array<String>, pw: PrintWriter): Boolean =
        when (args[0]) {
            "moveTaskToDesk" -> runMoveTaskToDesk(args, pw)
            "moveToNextDisplay" -> runMoveToNextDisplay(args, pw)
            "createDesk" -> runCreateDesk(args, pw)
            "activateDesk" -> runActivateDesk(args, pw)
            "removeDesk" -> runRemoveDesk(args, pw)
            "removeAllDesks" -> runRemoveAllDesks(args, pw)
            "moveTaskToFront" -> runMoveTaskToFront(args, pw)
            "moveTaskOutOfDesk" -> runMoveTaskOutOfDesk(args, pw)
            "canCreateDesk" -> runCanCreateDesk(args, pw)
            "getActiveDeskId" -> runGetActiveDeskId(args, pw)
            else -> {
                pw.println("Invalid command: ${args[0]}")
                false
            }
        }

    private fun runMoveTaskToDesk(args: Array<String>, pw: PrintWriter): Boolean {
        if (args.size < 2) {
            // First argument is the action name.
            pw.println("Error: task id should be provided as arguments")
            return false
        }
        var taskId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: task id should be an integer")
                return false
            }

        if (taskId == 0) {
            taskId = focusTransitionObserver.globallyFocusedTaskId
        }

        if (taskId == INVALID_TASK_ID) {
            pw.println("Error: no appropriate task found")
            return false
        }

        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            return controller.moveTaskToDefaultDeskAndActivate(taskId, transitionSource = UNKNOWN)
        }
        if (args.size < 3) {
            pw.println("Error: desk id should be provided as arguments")
            return false
        }
        val deskId =
            try {
                args[2].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: desk id should be an integer")
                return false
            }
        controller.moveTaskToDesk(taskId = taskId, deskId = deskId, transitionSource = ADB_COMMAND)
        return true
    }

    private fun runMoveToNextDisplay(args: Array<String>, pw: PrintWriter): Boolean {
        var taskId = INVALID_TASK_ID
        if (args.size < 2) {
            taskId = focusTransitionObserver.globallyFocusedTaskId
        } else {
            try {
                taskId = args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: task id should be an integer")
                return false
            }
        }
        if (taskId == INVALID_TASK_ID) {
            pw.println("Error: no appropriate task found")
            return false
        }
        controller.moveToNextDisplay(taskId)
        return true
    }

    private fun runCreateDesk(args: Array<String>, pw: PrintWriter): Boolean {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            pw.println("Not supported.")
            return false
        }
        if (args.size < 2) {
            // First argument is the action name.
            pw.println("Error: desk id should be provided as arguments")
            return false
        }
        val displayId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: display id should be an integer")
                return false
            }
        controller.createDesk(displayId)
        return true
    }

    private fun runActivateDesk(args: Array<String>, pw: PrintWriter): Boolean {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            pw.println("Not supported.")
            return false
        }
        if (args.size < 2) {
            // First argument is the action name.
            pw.println("Error: desk id should be provided as arguments")
            return false
        }
        val deskId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: desk id should be an integer")
                return false
            }
        controller.activateDesk(deskId)
        return true
    }

    private fun runRemoveDesk(args: Array<String>, pw: PrintWriter): Boolean {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            pw.println("Not supported.")
            return false
        }
        if (args.size < 2) {
            // First argument is the action name.
            pw.println("Error: desk id should be provided as arguments")
            return false
        }
        val deskId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: desk id should be an integer")
                return false
            }
        controller.removeDesk(deskId)
        return true
    }

    private fun runRemoveAllDesks(args: Array<String>, pw: PrintWriter): Boolean {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            pw.println("Not supported.")
            return false
        }
        pw.println("Not implemented.")
        return false
    }

    private fun runMoveTaskToFront(args: Array<String>, pw: PrintWriter): Boolean {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            pw.println("Not supported.")
            return false
        }
        if (args.size < 2) {
            // First argument is the action name.
            pw.println("Error: task id should be provided as arguments")
            return false
        }
        val taskId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: task id should be an integer")
                return false
            }
        controller.moveTaskToFront(
            /* taskId= */ taskId,
            /* remoteTransition= */ null,
            /* unminimizeReason= */ UnminimizeReason.UNKNOWN,
        )
        return true
    }

    private fun runMoveTaskOutOfDesk(args: Array<String>, pw: PrintWriter): Boolean {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            pw.println("Not supported.")
            return false
        }
        if (args.size < 2) {
            // First argument is the action name.
            pw.println("Error: task id should be provided as arguments")
            return false
        }
        val taskId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: task id should be an integer")
                return false
            }
        controller.moveToFullscreen(taskId, transitionSource = UNKNOWN)
        return true
    }

    private fun runCanCreateDesk(args: Array<String>, pw: PrintWriter): Boolean {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            pw.println("Not supported.")
            return false
        }
        val displayId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: display id should be an integer")
                return false
            }
        pw.println("Not implemented.")
        return false
    }

    private fun runGetActiveDeskId(args: Array<String>, pw: PrintWriter): Boolean {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            pw.println("Not supported.")
            return false
        }
        if (args.size < 2) {
            // First argument is the action name.
            pw.println("Error: task id should be provided as arguments")
            return false
        }
        val displayId =
            try {
                args[1].toInt()
            } catch (e: NumberFormatException) {
                pw.println("Error: display id should be an integer")
                return false
            }
        pw.println("Not implemented.")
        return false
    }

    override fun printShellCommandHelp(pw: PrintWriter, prefix: String) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            pw.println("$prefix moveTaskToDesk <taskId|0>")
            pw.println(
                "$prefix  Move a task with given id to desktop mode. " +
                    "TaskId 0 means focused task on the default display."
            )
            pw.println("$prefix moveToNextDisplay <taskId> ")
            pw.println("$prefix  Move a task with given id to next display.")
            return
        }
        pw.println("$prefix moveTaskToDesk <taskId|0> <deskId>")
        pw.println(
            "$prefix  Move a task with given id to the given desk and activate it. " +
                "TaskId 0 means focused task on the default display."
        )
        pw.println("$prefix moveToNextDisplay <taskId>")
        pw.println("$prefix  Move a task with given id to next display.")
        pw.println("$prefix createDesk <displayId>")
        pw.println("$prefix  Creates a desk on the given display.")
        pw.println("$prefix activateDesk <deskId>")
        pw.println("$prefix  Activates the given desk.")
        pw.println("$prefix removeDesk <deskId> ")
        pw.println("$prefix  Removes the given desk and all of its windows.")
        pw.println("$prefix removeAllDesks")
        pw.println("$prefix  Removes all the desks and their windows across all displays")
        pw.println("$prefix moveTaskToFront <taskId>")
        pw.println("$prefix  Moves a task in front of its siblings.")
        pw.println("$prefix moveTaskOutOfDesk <taskId>")
        pw.println("$prefix  Moves the given desktop task out of the desk into fullscreen mode.")
        pw.println("$prefix canCreateDesk <displayId>")
        pw.println("$prefix  Whether creating a new desk in the given display is allowed.")
        pw.println("$prefix getActivateDeskId <displayId>")
        pw.println("$prefix  Print the id of the active desk in the given display.")
    }
}
