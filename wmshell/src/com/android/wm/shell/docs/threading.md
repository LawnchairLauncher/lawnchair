# Threading

---

## Boundaries

```text
                                    Thread boundary
                                          |
                                WM Shell  | SystemUI
                                          |
                                          |
FeatureController <-> FeatureInterface <--|--> WMShell <-> SysUI
       |          (^post to shell thread) |            (^post to main thread)
      ...                                 |
       |                                  |
 OtherControllers                         |
```

## Threads

We currently have multiple threads in use in the Shell library depending on the configuration by
the product.
- SysUI main thread (standard main thread)
- `ShellMainThread` (only used if the resource `config_enableShellMainThread` is set true
  (ie. phones))
  - This falls back to the SysUI main thread otherwise
  - **Note**:
    - This thread runs with `THREAD_PRIORITY_DISPLAY` priority since so many windowing-critical
      components depend on it
    - This is also the UI thread for almost all UI created by the Shell
    - The Shell main thread Handler (and the Executor that wraps it) is async, so
      messages/runnables used via this Handler are handled immediately if there is no sync
      messages prior to it in the queue.
- `ShellBackgroundThread` (for longer running tasks where we don't want to block the shell main
  thread)
  - This is always another thread even if config_enableShellMainThread is not set true
  - **Note**:
    - This thread runs with `THREAD_PRIORITY_BACKGROUND` priority
- `ShellAnimationThread` (currently only used for Transitions and Splitscreen, but potentially all
  animations could be offloaded here)
- `ShellSplashScreenThread` (only for use with splashscreens)

## Dagger setup

The threading-related components are provided by the [WMShellConcurrencyModule](/libs/WindowManager/Shell/src/com/android/wm/shell/dagger/WMShellConcurrencyModule.java),
for example, the Executors and Handlers for the various threads that are used.  You can request
an executor of the necessary type by using the appropriate annotation for each of the threads (ie.
`@ShellMainThread Executor`) when injecting into your Shell component.

To get the SysUI main thread, you can use the `@Main` annotation.

## Best practices

### Components
- Don't do initialization in the Shell component constructors
  - If the host SysUI is not careful, it may construct the WMComponent dependencies on the main
    thread, and this reduces the likelihood that components will intiailize on the wrong thread
    in such cases
- Be careful of using CountDownLatch and other blocking synchronization mechanisms in Shell code
  - If the Shell main thread is not a separate thread, this will cause a deadlock
- Callbacks, Observers, Listeners to any non-shell component should post onto main Shell thread
  - This includes Binder calls, SysUI calls, BroadcastReceivers, etc. Basically any API that
    takes a runnable should either be registered with the right Executor/Handler or posted to
    the main Shell thread manually
- Since everything in the Shell runs on the main Shell thread, you do **not** need to explicitly
  `synchronize` your code (unless you are trying to prevent reentrantcy, but that can also be
  done in other ways)

### Handlers/Executors
- You generally **never** need to create Handlers explicitly, instead inject `@ShellMainThread
  ShellExecutor` instead
  - This is a common pattern to defer logic in UI code, but the Handler created wraps the Looper
    that is currently running, which can be wrong (see above for initialization vs construction)
- That said, sometimes Handlers are necessary because Framework API only takes Handlers or you
  want to dedupe multiple messages
  - In such cases inject `@ShellMainThread Handler` or use view.getHandler() which should be OK
    assuming that the view root was initialized on the main Shell thread
- <u>**Never</u> use Looper.getMainLooper()**
  - It's likely going to be wrong, you can inject `@Main ShellExecutor` to get the SysUI main thread

### Testing
- You can use a `TestShellExecutor` to control the processing of messages