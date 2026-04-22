# Usage of Dagger in the Shell library

[Back to home](README.md)

---

## Dependencies

Dagger is not required to use the Shell library, but it has a lot of obvious benefits:

- Not having to worry about how to instantiate all the dependencies of a class, especially as
  dependencies evolve (ie. product controller depends on base controller)
- Can create boundaries within the same app to encourage better code modularity

As such, the Shell also tries to provide some reasonable out-of-the-box modules for use with Dagger.

## Modules

All the Dagger related code in the Shell can be found in the `com.android.wm.shell.dagger` package,
this is intentional as it keeps the "magic" in a single location. The explicit nature of how
components in the shell are provided is as a result a bit more verbose, but it makes it easy for
developers to jump into a few select files and understand how different components are provided
(especially as products override components).

The module dependency tree looks a bit like:

- [WMShellConcurrencyModule](/libs/WindowManager/Shell/src/com/android/wm/shell/dagger/WMShellConcurrencyModule.java)
  (provides threading-related components)
    - [WMShellBaseModule](/libs/WindowManager/Shell/src/com/android/wm/shell/dagger/WMShellBaseModule.java)
      (provides components that are common to many products, ie. DisplayController, Transactions,
      etc.)
        - [WMShellModule](/libs/WindowManager/Shell/src/com/android/wm/shell/dagger/WMShellModule.java)
          (phone/tablet specific components only)
        - [TvPipModule](/libs/WindowManager/Shell/src/com/android/wm/shell/dagger/TvPipModule.java)
          (PIP specific components for TV)
            - [TvWMShellModule](/libs/WindowManager/Shell/src/com/android/wm/shell/dagger/TvWMShellModule.java)
              (TV specific components only)
    - etc.

Ideally features could be abstracted out into their own modules and included as needed by each
product.

## Changing WMShellBaseModule

Because all products will include WMShellBaseModule, we don't want it to provide instances for
features that aren't used across multiple products (ie. Handheld, TV, Auto, Wear). This module
should generally only provide:

- Concrete implementations that are needed for startup
  (see `provideIndependentShellComponentsToCreate()`)
- Things used directly/indirectly by interfaces
  exposed to SysUI
  in [WMComponent.java](/libs/WindowManager/Shell/src/com/android/wm/shell/dagger/WMComponent.java).

For the latter, not every feature will be enabled on the SysUI form factor including the base
module, so the recommendation is to have an `@BindsOptionalOf` for the interface, and have the
actual implementation provided in the form-factor specific module (ie. `WMShellModule`).

## Overriding base components

In some rare cases, there are base components that can change behavior depending on which
product it runs on. If there are hooks that can be added to the component, that is the
preferable approach.

The alternative is to use
the [@DynamicOverride](/libs/WindowManager/Shell/src/com/android/wm/shell/dagger/DynamicOverride.java)
annotation to allow the product module to provide an implementation that the base module can
reference. This is most useful if the existence of the entire component is controlled by the
product and the override implementation is optional (there is a default implementation). More
details can be found in the class's javadoc.

## Starting up Shell components aren't dependencies for other components

With Dagger, objects are created in dependency order and individual components can register with
`ShellInit` (see [Component initialization](changes.md#component-initialization)) to initialize in
dependency order as well. However, if there is code that needs to run on startup but has nothing
dependent on it (imagine a background error detector for example), then
`provideIndependentShellComponentsToCreate()` can serve as the artificial dependent object (itself
a dependency for `ShellInterface`) to trigger creation of such a component.

This can be declared within each module, so if a product includes `WMShellModule`, all the
components in `provideIndependentShellComponentsToCreate()` for both it and `WMShellBaseModule` will
be created.

Note that long term we are looking to move to a `CoreStartable` like infrastructure.