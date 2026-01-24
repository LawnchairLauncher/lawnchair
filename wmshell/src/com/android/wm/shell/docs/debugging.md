# Debugging in the Shell
[Back to home](README.md)

---

## Logging & ProtoLogs

The interactions in the Shell can be pretty complicated, so having good logging is crucial to
debugging problems that arise (especially in dogfood).  The Shell uses the same efficient Protolog
mechanism as WM Core, which can be enabled at runtime on debug devices.

**TLDR**&nbsp; Don’t use Logs or Slogs except for error cases, Protologs are much more flexible,
easy to add and easy to use

### Adding a new ProtoLog
Update `ShellProtoLogGroup` to include a new log group (ie. NEW_FEATURE) for the content you want to
log.  ProtoLog log calls mirror Log.v/d/e(), and take a format message and arguments:
```java
ProtoLog.v(NEW_FEATURE, "Test log w/ params: %d %s", 1, “a”)
```
This code itself will not compile by itself, but the `protologtool` will preprocess the file when
building to check the log state (is enabled) before printing the print format style log.

**Notes**
- ProtoLogs are only fully supported from soong builds (ie. via make/mp). In SysUI-studio it falls
  back to log via Logcat
- Non-text ProtoLogs are not currently supported with the Shell library (you can't view them with
  traces in Winscope)

### Kotlin
Kotlin protologging is supported, but with some differences compared to Java. The ProtoLog tool
currently does not process Kotlin code. This means that while ProtoLogs in Kotlin will still be
traced to Perfetto, they are not pre-processed to extract static strings like in Java. Consequently,
using ProtoLogging in Kotlin does not provide the same memory gains as in Java, and log calls may be
slightly less performant due to additional string interning at runtime.

### Enabling ProtoLog command line logging
Run these commands to enable protologs (in logcat) for WM Core ([list of all core groups](/core/java/com/android/internal/protolog/WmProtoLogGroups.java)) or WM Shell ([list of all shell groups](/libs/WindowManager/Shell/src/com/android/wm/shell/protolog/ShellProtoLogGroup.java)):
```shell
# Note: prior to 25Q2, you may need to use:
#   adb shell dumpsys activity service SystemUIService WMShell protolog enable-text TAG
adb shell cmd protolog_configuration logcat enable <group>
adb shell cmd protolog_configuration logcat disable <group>
```

Use these commands to print protolog groups and their status:
```shell
adb shell cmd protolog_configuration groups list
adb shell cmd protolog_configuration groups status <group>
```

### R8 optimizations & ProtoLog

If the APK that the Shell library is included into has R8 optimizations enabled, then you may need
to update the proguard flags to keep the generated protolog classes (ie. AOSP SystemUI's [proguard.flags](/packages/SystemUI/proguard_common.flags)).

## Winscope Tracing

The Winscope tool is extremely useful in determining what is happening on-screen in both
WindowManager and SurfaceFlinger.  Follow [go/winscope](http://go/winscope-help) to learn how to
use the tool.  This trace will contain all the information about the windows/activities/surfaces on
screen.

## WindowManager/SurfaceFlinger/InputDispatcher information

A quick way to view the WindowManager hierarchy without a winscope trace is via the wm dumps:
```shell
adb shell dumpsys activity containers
# The output lists the containers in the hierarchy from top -> bottom in z-order
```

To get more information about windows on the screen:
```shell
# All windows in WM
adb shell dumpsys window -a
# The windows are listed from top -> bottom in z-order

# Visible windows only
adb shell dumpsys window -a visible
```

Likewise, the SurfaceFlinger hierarchy can be dumped for inspection by running:
```shell
adb shell dumpsys SurfaceFlinger
# Search output for "Layer Hierarchy", the surfaces in the table are listed bottom -> top in z-order
```

And the visible input windows can be dumped via:
```shell
adb shell dumpsys input
# Search output for "Windows:", they are ordered top -> bottom in z-order
```

## Tracing global SurfaceControl transaction updates

While Winscope traces are very useful, it sometimes doesn't give you enough information about which
part of the code is initiating the transaction updates. In such cases, it can be helpful to get
stack traces when specific surface transaction calls are made (regardless of process), which is
possible by enabling the following system properties for example:
```shell
# Enabling
adb shell setprop persist.wm.debug.sc.tx.log_match_call setAlpha,setPosition  # matches the name of the SurfaceControlTransaction methods
adb shell setprop persist.wm.debug.sc.tx.log_match_name com.android.systemui # matches the name of the surface
adb reboot
adb logcat -s "SurfaceControlRegistry"

# Disabling logging
adb shell setprop persist.wm.debug.sc.tx.log_match_call \"\"
adb shell setprop persist.wm.debug.sc.tx.log_match_name \"\"
```

A reboot is required to enable the logging. Once enabled, reboot is not needed to update the
properties.

It is not necessary to set both `log_match_call` and `log_match_name`, but note logs can be quite
noisy if unfiltered.

### Tracing transaction merge & apply

Tracing the method calls on SurfaceControl.Transaction tells you where a change is requested, but
the changes are not actually committed until the transaction itself is applied.  And because
transactions can be passed across processes, or prepared in advance for later application (ie.
when restoring state after a Transition), the ordering of the change logs is not always clear
by itself.

In such cases, you can also enable the "merge" and "apply" calls to get additional information
about how/when transactions are respectively merged/applied:
```shell
# Enabling
adb shell setprop persist.wm.debug.sc.tx.log_match_call setAlpha,merge,apply  # apply will dump logs of each setAlpha or merge call on that tx
adb reboot
adb logcat -s "SurfaceControlRegistry"
```

Using those logs, you can first look at where the desired change is called, note the transaction
id, and then search the logs for where that transaction id is used.  If it is merged into another
transaction, you can continue the search using the merged transaction until you find the final
transaction which is applied.

## Tracing activity starts & finishes in the app process

It's sometimes useful to know when to see a stack trace of when an activity starts in the app code
or via a `WindowContainerTransaction` (ie. if you are repro'ing a bug related to activity starts).
You can enable this system property to get this trace:
```shell
# Enabling
adb shell setprop persist.wm.debug.start_activity true
adb reboot
adb logcat -s "Instrumentation"

# Disabling
adb shell setprop persist.wm.debug.start_activity \"\"
adb reboot
```

Likewise, to trace where a finish() call may be made in the app process, you can enable this system
property:
```shell
# Enabling
adb shell setprop persist.wm.debug.finish_activity true
adb reboot
adb logcat -s "Instrumentation"

# Disabling
adb shell setprop persist.wm.debug.finish_activity \"\"
adb reboot
```

## Tracing transition starts/finishes in the Shell

To trace where a new WM transition is started and finished in the Shell, you can enable these system
properties respectively:
```shell
# Enabling
adb shell setprop persist.wm.debug.start_shell_transition true
adb shell setprop persist.wm.debug.finish_shell_transition true
adb reboot
adb logcat -s "ShellTransitions"

# Disabling
adb shell setprop persist.wm.debug.start_shell_transition \"\"
adb shell setprop persist.wm.debug.finish_shell_transition \"\"
adb reboot
```


## Dumps

Because the Shell library is built as a part of SystemUI, dumping the state is currently done as a
part of dumping the SystemUI service.  Dumping the Shell specific data can be done by specifying the
WMShell SysUI service:

```shell
# Note: prior to 25Q2, you may need to use:
#   adb shell dumpsys activity service SystemUIService WMShell dump
adb shell wm shell dump
```

If information should be added to the dump, either:
- Update `WMShell` if you are dumping SysUI state
- Inject `ShellCommandHandler` into your Shell class, and add a dump callback

## Shell commands

It can be useful to add additional shell commands to drive and test specific interactions.

To add a new command for your feature, inject a `ShellCommandHandler` into your class and add a
shell command handler in your controller.

```shell
# List all available commands
# Note: prior to 25Q2, you may need to use:
#   adb shell dumpsys activity service SystemUIService WMShell help
adb shell wm shell help

# Run a specific command
# Note: prior to 25Q2, you may need to use:
#   adb shell dumpsys activity service SystemUIService WMShell <cmd> <args> ...
adb shell wm shell <cmd> <args> ...
```

## Debugging in Android Studio

If you are using the [go/sysui-studio](http://go/sysui-studio) project, then you can debug Shell
code directly from Android Studio like any other app.
