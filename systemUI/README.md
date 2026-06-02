# SystemUI Module

This directory contains all the required SystemUI modules, based from
the [upstream source code](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/)
of the SystemUI app.

Some examples of modules include [`common`](#common-submodule), which serves as a helper for other
SystemUI modules, and
`unfold`, which handles devices with hinges.

## `common` submodule

`SystemUICommon` is a module that hosts standalone helper libraries. It is intended
to be used by other modules, and therefore should not have other SystemUI dependencies to avoid
circular dependencies.

To maintain the structure of this module, please refrain from adding components at the top level.
Instead, add them to specific sub-packages, such as `systemui/common/buffer/`.
This will help keep the module organized and easy to navigate.

## `utils` submodule

A stripped-down version to only include the `WindowManagerUtils` code used by `wmshell`.

### `viewcapture` submodule

> [!CAUTION]
> `ViewCapture.java` is **extremely performance sensitive**.
> **Any changes should be carried out with great caution** not to hurt performance.

The following measurements should serve as a performance baseline (as of 02.10.2022):

1. The onDraw() function invocation time in WindowListener within ViewCapture is measured with
   System.nanoTime(). The following scenario was measured:
    - Capturing the notification shade window root view on a freshly rebooted bluejay device (2
      notifications present) -> avg. time = 204237ns (0.2ms)
