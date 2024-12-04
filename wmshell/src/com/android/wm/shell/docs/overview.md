# What is the WindowManager Shell

---

## Motivation

The primary motivation for the WindowManager Shell (WMShell) library is to effectively scale
WindowManager by making it easy&trade; and safe to create windowing features to fit the needs of
various Android products and form factors.

To achieve this, WindowManager separates the policy of managing windows (WMCore) from the
presentation of surfaces (WMShell) and provides a minimal interface boundary for the two to
communicate.

## Who is using the library?

Currently, the WMShell library is used to drive the windowing experience on handheld
(phones & tablets), TV, Auto, Arc++, and Wear to varying degrees.

## Where does the code live

The core WMShell library code is currently located in the [frameworks/base/libs/WindowManager/Shell](/libs/WindowManager/Shell)
directory and is included as a part dependency of the host SystemUI apk.

## How do I build the Shell library

The library can be built directly by running:
```shell
m WindowManager-Shell
```
But this is mainly useful for inspecting the contents of the library or verifying it builds. The
various targets can be found in the Shell library's [Android.bp](/libs/WindowManager/Shell/Android.bp)
file.

Normally, you would build it as a part of the host SystemUI, for example via commandline:
```shell
# Phone SystemUI variant
m SystemUI
adevice update
```

Or preferably, if you are making WMShell/SysUI only changes (no other framework changes), then
building via [go/sysui-studio](http://go/sysui-studio) allows for very quick iteration (one click
build and push of SysUI in < 30s).

If you are making framework changes and are using `aidegen` to set up your platform IDE, make sure
to include the appropriate directories to build, for example:
```shell
# frameworks/base will include base/libs/WindowManager/Shell and base/packages/SystemUI
aidegen frameworks/base \
    vendor/<oem>/packages/SystemUI \
    ...
```

## Other useful links
- [go/o-o-summit-20](go/o-o-summit-20) (Video presentations from the WM team)
- [go/o-o-summit-21](go/o-o-summit-21)