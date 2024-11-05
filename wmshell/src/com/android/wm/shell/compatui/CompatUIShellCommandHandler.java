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

package com.android.wm.shell.compatui;

import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.sysui.ShellCommandHandler;

import java.io.PrintWriter;
import java.util.function.Consumer;

import javax.inject.Inject;

/**
 * Handles the shell commands for the CompatUX.
 *
 * <p> Use with {@code adb shell dumpsys activity service SystemUIService WMShell compatui
 * &lt;command&gt;}.
 */
@WMSingleton
public final class CompatUIShellCommandHandler implements
        ShellCommandHandler.ShellCommandActionHandler {

    private final CompatUIConfiguration mCompatUIConfiguration;
    private final ShellCommandHandler mShellCommandHandler;

    @Inject
    public CompatUIShellCommandHandler(ShellCommandHandler shellCommandHandler,
            CompatUIConfiguration compatUIConfiguration) {
        mShellCommandHandler = shellCommandHandler;
        mCompatUIConfiguration = compatUIConfiguration;
    }

    void onInit() {
        mShellCommandHandler.addCommandCallback("compatui", this, this);
    }

    @Override
    public boolean onShellCommand(String[] args, PrintWriter pw) {
        if (args.length != 2) {
            pw.println("Invalid command: " + args[0]);
            return false;
        }
        switch (args[0]) {
            case "restartDialogEnabled":
                return invokeOrError(args[1], pw,
                        mCompatUIConfiguration::setIsRestartDialogOverrideEnabled);
            case "reachabilityEducationEnabled":
                return invokeOrError(args[1], pw,
                        mCompatUIConfiguration::setIsReachabilityEducationOverrideEnabled);
            default:
                pw.println("Invalid command: " + args[0]);
                return false;
        }
    }

    @Override
    public void printShellCommandHelp(PrintWriter pw, String prefix) {
        pw.println(prefix + "restartDialogEnabled [0|false|1|true]");
        pw.println(prefix + "  Enable/Disable the restart education dialog for Size Compat Mode");
        pw.println(prefix + "reachabilityEducationEnabled [0|false|1|true]");
        pw.println(prefix
                + "  Enable/Disable the restart education dialog for letterbox reachability");
        pw.println(prefix + "  Disable the restart education dialog for letterbox reachability");
    }

    private static boolean invokeOrError(String input, PrintWriter pw,
            Consumer<Boolean> setter) {
        Boolean asBoolean = strToBoolean(input);
        if (asBoolean == null) {
            pw.println("Error: expected true, 1, false, 0.");
            return false;
        }
        setter.accept(asBoolean);
        return true;
    }

    // Converts a String to boolean if possible or it returns null otherwise
    private static Boolean strToBoolean(String str) {
        switch(str) {
            case "1":
            case "true":
                return true;
            case "0":
            case "false":
                return false;
        }
        return null;
    }
}
