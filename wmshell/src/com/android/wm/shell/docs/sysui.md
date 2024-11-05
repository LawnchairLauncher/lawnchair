# Shell & SystemUI

---

## Setup

The SystemUI of various products depend on and build against the WM Shell library. To ensure
that we don't inadvertently build dependencies between the Shell library and one particular
product (ie. handheld SysUI), we deliberately separate the initialization of the WM Shell
component from the SysUI component when set up through Dagger.

**TLDR**&nbsp; Initialize everything as needed in the WM component scope and export only well
defined interfaces to SysUI.

## Initialization

There are more details in the Dagger docs, but the general overview of the SysUI/Shell
initialization flow is such:

1) SysUI Global scope is initialize (see `GlobalModule` and its included modules)
2) WM Shell scope is initialized, for example
   1) On phones: `WMComponent` includes `WMShellModule` which includes `WMShellBaseModule`
      (common to all SysUI)
   2) On TVs: `TvWMComponent` includes `TvWMShellModule` which includes `WMShellBaseModule`
   3) etc.
3) SysUI explicitly passes interfaces provided from the `WMComponent` to `SysUIComponent` via
   the `SysUIComponent#Builder`, then builds the SysUI scoped components
4) `WMShell` is the SystemUI “service” (in the SysUI scope) that initializes with the app after the
SystemUI part of the dependency graph has been created. It contains the binding code between the
interfaces provided by the Shell and the rest of SystemUI.
5) SysUI can inject the interfaces into its own components

More detail can be found in [go/wm-sysui-dagger](http://go/wm-sysui-dagger).

## Interfaces from SysUI to Shell components

Within the same process, the WM Shell components can be running on a different thread than the main
SysUI thread (disabled on certain products).  This introduces challenges where we have to be
careful about how SysUI calls into the Shell and vice versa.

As a result, we enforce explicit interfaces between SysUI and Shell components, and the
implementations of the interfaces on each side need to post to the right thread before it calls
into other code.

For example, you might have:
1) (Shell) ShellFeature interface to be used from SysUI
2) (Shell) ShellFeatureController handles logic, implements ShellFeature interface and posts to
   main Shell thread
3) SysUI application init injects Optional<ShellFeature> as an interface to SysUI to call
4) (SysUI) SysUIFeature depends on ShellFeature interface
5) (SysUI) SysUIFeature injects Optional<ShellFeature>, and sets up a callback for the Shell to
   call, and the callback posts to the main SysUI thread

Adding an interface to a Shell component may seem like a lot of boiler plate, but is currently
necessary to maintain proper threading and logic isolation.

## Listening for Configuration changes & other SysUI events

Aside from direct calls into Shell controllers for exposed features, the Shell also receives
common event callbacks from SysUI via the `ShellController`.  This includes things like:

- Configuration changes
- Keyguard events
- Shell init
- Shell dumps & commands

For other events which are specific to the Shell feature, then you can add callback methods on
the Shell feature interface.  Any such calls should <u>**never**</u> be synchronous calls as
they will need to post to the Shell main thread to run.

## Shell commands & Dumps

Since the Shell library is a part of the SysUI process, it relies on SysUI to trigger commands
on individual Shell components, or to dump individual shell components.

```shell
# Dump everything
adb shell dumpsys activity service SystemUIService WMShell

# Run a specific command
adb shell dumpsys activity service SystemUIService WMShell help
adb shell dumpsys activity service SystemUIService WMShell <cmd> <args> ...
```

More detail can be found in [Debugging in the Shell](debugging.md) section.