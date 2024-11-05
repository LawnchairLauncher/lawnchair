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

package com.android.wm.shell;

import com.android.internal.protolog.LegacyProtoLogImpl;
import com.android.internal.protolog.common.ILogger;
import com.android.internal.protolog.common.IProtoLog;
import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellInit;

import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Controls the {@link ProtoLog} in WMShell via adb shell commands.
 *
 * Use with {@code adb shell dumpsys activity service SystemUIService WMShell protolog ...}.
 */
public class ProtoLogController implements ShellCommandHandler.ShellCommandActionHandler {
    private final ShellCommandHandler mShellCommandHandler;
    private final IProtoLog mShellProtoLog;

    public ProtoLogController(ShellInit shellInit,
            ShellCommandHandler shellCommandHandler) {
        shellInit.addInitCallback(this::onInit, this);
        mShellCommandHandler = shellCommandHandler;
        mShellProtoLog = ProtoLog.getSingleInstance();
    }

    void onInit() {
        mShellCommandHandler.addCommandCallback("protolog", this, this);
    }

    @Override
    public boolean onShellCommand(String[] args, PrintWriter pw) {
        final ILogger logger = pw::println;
        switch (args[0]) {
            case "status": {
                if (android.tracing.Flags.perfettoProtologTracing()) {
                    pw.println("(Deprecated) legacy command. Use Perfetto commands instead.");
                    return false;
                }
                ((LegacyProtoLogImpl) mShellProtoLog).getStatus();
                return true;
            }
            case "start": {
                if (android.tracing.Flags.perfettoProtologTracing()) {
                    pw.println("(Deprecated) legacy command. Use Perfetto commands instead.");
                    return false;
                }
                ((LegacyProtoLogImpl) mShellProtoLog).startProtoLog(pw);
                return true;
            }
            case "stop": {
                if (android.tracing.Flags.perfettoProtologTracing()) {
                    pw.println("(Deprecated) legacy command. Use Perfetto commands instead.");
                    return false;
                }
                ((LegacyProtoLogImpl) mShellProtoLog).stopProtoLog(pw, true);
                return true;
            }
            case "enable-text": {
                String[] groups = Arrays.copyOfRange(args, 1, args.length);
                int result = mShellProtoLog.startLoggingToLogcat(groups, logger);
                if (result == 0) {
                    pw.println("Starting logging on groups: " + Arrays.toString(groups));
                    return true;
                }
                return false;
            }
            case "disable-text": {
                String[] groups = Arrays.copyOfRange(args, 1, args.length);
                int result = mShellProtoLog.stopLoggingToLogcat(groups, logger);
                if (result == 0) {
                    pw.println("Stopping logging on groups: " + Arrays.toString(groups));
                    return true;
                }
                return false;
            }
            case "enable": {
                String[] groups = Arrays.copyOfRange(args, 1, args.length);
                return mShellProtoLog.startLoggingToLogcat(groups, logger) == 0;
            }
            case "disable": {
                String[] groups = Arrays.copyOfRange(args, 1, args.length);
                return mShellProtoLog.stopLoggingToLogcat(groups, logger) == 0;
            }
            case "save-for-bugreport": {
                if (android.tracing.Flags.perfettoProtologTracing()) {
                    pw.println("(Deprecated) legacy command");
                    return false;
                }
                if (!mShellProtoLog.isProtoEnabled()) {
                    pw.println("Logging to proto is not enabled for WMShell.");
                    return false;
                }
                ((LegacyProtoLogImpl) mShellProtoLog).stopProtoLog(pw, true /* writeToFile */);
                ((LegacyProtoLogImpl) mShellProtoLog).startProtoLog(pw);
                return true;
            }
            default: {
                pw.println("Invalid command: " + args[0]);
                printShellCommandHelp(pw, "");
                return false;
            }
        }
    }

    @Override
    public void printShellCommandHelp(PrintWriter pw, String prefix) {
        pw.println(prefix + "status");
        pw.println(prefix + "  Get current ProtoLog status.");
        pw.println(prefix + "start");
        pw.println(prefix + "  Start proto logging.");
        pw.println(prefix + "stop");
        pw.println(prefix + "  Stop proto logging and flush to file.");
        pw.println(prefix + "enable [group...]");
        pw.println(prefix + "  Enable proto logging for given groups.");
        pw.println(prefix + "disable [group...]");
        pw.println(prefix + "  Disable proto logging for given groups.");
        pw.println(prefix + "enable-text [group...]");
        pw.println(prefix + "  Enable logcat logging for given groups.");
        pw.println(prefix + "disable-text [group...]");
        pw.println(prefix + "  Disable logcat logging for given groups.");
        pw.println(prefix + "save-for-bugreport");
        pw.println(prefix + "  Flush proto logging to file, only if it's enabled.");
    }
}
