/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.wm.shell.sysui;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_INIT;

import com.android.internal.protolog.common.ProtoLog;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * An entry point into the shell for dumping shell internal state and running adb commands.
 *
 * Use with {@code adb shell dumpsys activity service SystemUIService WMShell ...}.
 */
public final class ShellCommandHandler {
    private static final String TAG = ShellCommandHandler.class.getSimpleName();

    // We're using a TreeMap to keep them sorted by command name
    private final TreeMap<String, BiConsumer<PrintWriter, String>> mDumpables = new TreeMap<>();
    private final TreeMap<String, ShellCommandActionHandler> mCommands = new TreeMap<>();

    public interface ShellCommandActionHandler {
        /**
         * Handles the given command.
         *
         * @param args the arguments starting with the action name, then the action arguments
         * @param pw the write to print output to
         */
        boolean onShellCommand(String[] args, PrintWriter pw);

        /**
         * Prints the help for this class of commands.  Implementations do not need to print the
         * command class.
         */
        void printShellCommandHelp(PrintWriter pw, String prefix);
    }


    /**
     * Adds a callback to run when the Shell is being dumped.
     *
     * @param callback the callback to be made when Shell is dumped, takes the print writer and
     *                 a prefix
     * @param instance used for debugging only
     */
    public <T> void addDumpCallback(BiConsumer<PrintWriter, String> callback, T instance) {
        mDumpables.put(instance.getClass().getSimpleName(), callback);
        ProtoLog.v(WM_SHELL_INIT, "Adding dump callback for %s",
                instance.getClass().getSimpleName());
    }

    /**
     * Adds an action callback to be invoked when the user runs that particular command from adb.
     *
     * @param commandClass the top level class of command to invoke
     * @param actions the interface to callback when an action of this class is invoked
     * @param instance used for debugging only
     */
    public <T> void addCommandCallback(String commandClass, ShellCommandActionHandler actions,
            T instance) {
        mCommands.put(commandClass, actions);
        ProtoLog.v(WM_SHELL_INIT, "Adding command class %s for %s", commandClass,
                instance.getClass().getSimpleName());
    }

    /** Dumps WM Shell internal state. */
    public void dump(PrintWriter pw) {
        for (String key : mDumpables.keySet()) {
            final BiConsumer<PrintWriter, String> r = mDumpables.get(key);
            r.accept(pw, "");
            pw.println();
        }
    }


    /** Returns {@code true} if command was found and executed. */
    public boolean handleCommand(final String[] args, PrintWriter pw) {
        if (args.length < 2) {
            // Argument at position 0 is "WMShell".
            return false;
        }

        final String cmdClass = args[1];
        if (cmdClass.toLowerCase().equals("help")) {
            return runHelp(pw);
        }
        if (!mCommands.containsKey(cmdClass)) {
            return false;
        }

        // Only pass the actions onwards as arguments to the callback
        final ShellCommandActionHandler actions = mCommands.get(args[1]);
        final String[] cmdClassArgs = Arrays.copyOfRange(args, 2, args.length);
        actions.onShellCommand(cmdClassArgs, pw);
        return true;
    }

    private boolean runHelp(PrintWriter pw) {
        pw.println("Window Manager Shell commands:");
        for (String commandClass : mCommands.keySet()) {
            pw.println("  " + commandClass);
            mCommands.get(commandClass).printShellCommandHelp(pw, "    ");
        }
        pw.println("  help");
        pw.println("      Print this help text.");
        pw.println("  <no arguments provided>");
        pw.println("    Dump all Window Manager Shell internal state");
        return true;
    }
}
