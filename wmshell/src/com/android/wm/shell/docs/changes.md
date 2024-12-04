# Making changes in the Shell

---

## Code reviews

In addition to the individual reviewers who are most familiar with the changes you are making,
please also add [wm-code-reviewers@google.com](http://g/wm-code-reviewers) to keep other WM folks
in the loop.

## Adding new code

### Internal Shell utility classes
If the new component is used only within the WMShell library, then there are no special
considerations, go ahead and add it (in the `com.android.wm.shell.common` package for example)
and make sure the appropriate [unit tests](testing.md) are added.

### Internal Shell components
If the new component is to be used by other components/features within the Shell library, then
you can create an appropriate package for this component to add your new code. The current
pattern is to have a single `<Component name>Controller` that handles the initialization of the
component.

As mentioned in the [Dagger usage](dagger.md) docs, you need to determine whether it should go into:
- `WMShellBaseModule` for components that other base & product components will depend on
- or `WMShellModule`, `TvWmShellModule`, etc. for product specific components that no base
  components depend on

### SysUI accessible components
In addition to doing the above, you will also need to provide an interface for calling to SysUI
from the Shell and vice versa.  The current pattern is to have a parallel `Optional<Component name>`
interface that the `<Component name>Controller` implements and handles on the main Shell thread
(see [SysUI/Shell threading](threading.md)).

In addition, because components accessible to SysUI injection are explicitly listed, you'll have to
add an appropriate method in `WMComponent` to get the interface and update the `Builder` in
`SysUIComponent` to take the interface so it can be injected in SysUI code.  The binding between
the two is done in `SystemUIFactory#init()` which will need to be updated as well.

Specifically, to support calling into a controller from an external process (like Launcher):
- Create an implementation of the external interface within the controller
- Have all incoming calls post to the main shell thread (inject @ShellMainThread Executor into the
  controller if needed)
- Note that callbacks into SysUI should take an associated executor to call back on

### Launcher accessible components
Because Launcher is not a part of SystemUI and is a separate process, exposing controllers to
Launcher requires a new AIDL interface to be created and implemented by the controller.  The
implementation of the stub interface in the controller otherwise behaves similar to the interface
to SysUI where it posts the work to the main Shell thread.

Specifically, to support calling into a controller from an external process (like Launcher):
- Create an implementation of the interface binder's `Stub` class within the controller, have it
  extend `ExternalInterfaceBinder` and implement `invalidate()` to ensure it doesn't hold long
  references to the outer controller
- Make the controller implement `RemoteCallable<T>`, and have all incoming calls use one of
  the `executeRemoteCallWithTaskPermission()` calls to verify the caller's identity and ensure the
  call happens on the main shell thread and not the binder thread
- Inject `ShellController` and add the instance of the implementation as external interface
- In Launcher, update `TouchInteractionService` to pass the interface to `SystemUIProxy`, and then
  call the SystemUIProxy method as needed in that code

### Component initialization
To initialize the component:
- On the Shell side, you potentially need to do two things to initialize the component:
  - Inject `ShellInit` into your component and add an init callback
  - Ensure that your component is a part of the dagger dependency graph, either by:
    - Making this component a dependency of an existing component already exposed to SystemUI
    - Explicitly add this component to the WMShellBaseModule @ShellCreateTrigger provider or
      the @ShellCreateTriggerOverride provider for your product module to expose it explicitly 
      if it is a completely independent component
- On the SysUI side, update `WMShell` to setup any bindings for the component that depend on
  SysUI code

To verify that your component is being initialized at startup, you can enable the `WM_SHELL_INIT` 
protolog group and restart the SysUI process:
```shell
adb shell wm logging enable-text WM_SHELL_INIT
adb shell kill `pid com.android.systemui`
adb logcat *:S WindowManagerShell
```

### General Do's & Dont's
Do:
- Add unit tests for all new components
- Keep controllers simple and break them down as needed
- Any SysUI callbacks should also take an associated executor to run the callback on

Don't:
- **Don't** do initialization in the constructor, only do initialization in the init callbacks.
  Otherwise it complicates the building of the dependency graph.
- **Don't** create dependencies from base-module components on specific features (the base module
  is intended for use with all products)
  - Try adding a mechanism to register and listen for changes from the base module component instead
- **Don't** add blocking synchronous calls in the SysUI interface between Shell & SysUI
  - Try adding a push-mechanism to share data, or an async callback to request data

### Exposing shared code for use in Launcher
Launcher doesn't currently build against the Shell library, but needs to have access to some shared
AIDL interfaces and constants.  Currently, all AIDL files, and classes under the
`com.android.wm.shell.util` package are automatically built into the `SystemUISharedLib` that
Launcher uses.

If the new code doesn't fall into those categories, they can be added explicitly in the Shell's
[Android.bp](/libs/WindowManager/Shell/Android.bp) file under the
`wm_shell_util-sources` filegroup.