# Debugging in the Shell

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

Protolog tool does not yet have support for Kotlin code (see [b/168581922](https://b.corp.google.com/issues/168581922)).
For logging in Kotlin, use the [KtProtoLog](/libs/WindowManager/Shell/src/com/android/wm/shell/util/KtProtoLog.kt)
class which has a similar API to the Java ProtoLog class.

### Enabling ProtoLog command line logging
Run these commands to enable protologs (in logcat) for WM Core ([list of all core tags](/core/java/com/android/internal/protolog/ProtoLogGroup.java)):
```shell
adb shell wm logging enable-text TAG
adb shell wm logging disable-text TAG
```

And these commands to enable protologs (in logcat) for WM Shell ([list of all shell tags](/libs/WindowManager/Shell/src/com/android/wm/shell/protolog/ShellProtoLogGroup.java)):
```shell
adb shell dumpsys activity service SystemUIService WMShell protolog enable-text TAG
adb shell dumpsys activity service SystemUIService WMShell protolog enable-text TAG
```

## Winscope Tracing

The Winscope tool is extremely useful in determining what is happening on-screen in both
WindowManager and SurfaceFlinger.  Follow [go/winscope](http://go/winscope-help) to learn how to
use the tool.  This trace will contain all the information about the windows/activities/surfaces on
screen.

## WindowManager/SurfaceFlinger hierarchy dump

A quick way to view the WindowManager hierarchy without a winscope trace is via the wm dumps:
```shell
adb shell dumpsys activity containers
```

Likewise, the SurfaceFlinger hierarchy can be dumped for inspection by running:
```shell
adb shell dumpsys SurfaceFlinger
# Search output for "Layer Hierarchy"
```

## Tracing global SurfaceControl transaction updates

While Winscope traces are very useful, it sometimes doesn't give you enough information about which
part of the code is initiating the transaction updates.  In such cases, it can be helpful to get
stack traces when specific surface transaction calls are made, which is possible by enabling the
following system properties for example:
```shell
# Enabling
adb shell setprop persist.wm.debug.sc.tx.log_match_call setAlpha  # matches the name of the SurfaceControlTransaction method
adb shell setprop persist.wm.debug.sc.tx.log_match_name com.android.systemui # matches the name of the surface
adb reboot
adb logcat -s "SurfaceControlRegistry"

# Disabling logging
adb shell setprop persist.wm.debug.sc.tx.log_match_call \"\"
adb shell setprop persist.wm.debug.sc.tx.log_match_name \"\"
adb reboot
```

It is not necessary to set both `log_match_call` and `log_match_name`, but note logs can be quite
noisy if unfiltered.

## Tracing activity starts in the app process

It's sometimes useful to know when to see a stack trace of when an activity starts in the app code
(ie. if you are repro'ing a bug related to activity starts). You can enable this system property to
get this trace:
```shell
# Enabling
adb shell setprop persist.wm.debug.start_activity true
adb reboot
adb logcat -s "Instrumentation"

# Disabling
adb shell setprop persist.wm.debug.start_activity \"\"
adb reboot
```

## Dumps

Because the Shell library is built as a part of SystemUI, dumping the state is currently done as a
part of dumping the SystemUI service.  Dumping the Shell specific data can be done by specifying the
WMShell SysUI service:

```shell
adb shell dumpsys activity service SystemUIService WMShell
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
adb shell dumpsys activity service SystemUIService WMShell help

# Run a specific command
adb shell dumpsys activity service SystemUIService WMShell <cmd> <args> ...
```

## Debugging in Android Studio

If you are using the [go/sysui-studio](http://go/sysui-studio) project, then you can debug Shell
code directly from Android Studio like any other app.
