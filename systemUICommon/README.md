# SystemUICommon

`SystemUICommon` is a module within SystemUI that hosts standalone helper libraries. It is intended to be used by other modules, and therefore should not have other SystemUI dependencies to avoid circular dependencies.

To maintain the structure of this module, please refrain from adding components at the top level. Instead, add them to specific sub-packages, such as `systemui/common/buffer/`. This will help to keep the module organized and easy to navigate.
