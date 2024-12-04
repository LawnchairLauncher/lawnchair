# Testing

---

## Unit tests

New WM Shell unit tests can be added to the
[Shell/tests/unittest](/libs/WindowManager/Shell/tests/unittest) directory, and can
be run via command line using `atest`:
```shell
atest WMShellUnitTests
```

If you use the SysUI Studio project, you can run and debug tests directly in the source files
(click on the little arrows next to the test class or test method).

These unit tests are run as a part of WindowManager presubmit, and the dashboards for these unit
tests tests can be found at [go/wm-tests](http://go/wm-tests).

This [GCL file](http://go/wm-unit-tests-gcl) configures the tests being run on the server.

## Flicker tests

Flicker tests are tests that perform actions and make assertions on the state in Window Manager
and SurfaceFlinger traces captured during the run.

New WM Shell Flicker tests can be added to the
[Shell/tests/flicker](/libs/WindowManager/Shell/tests/flicker) directory, and can be run via command line using `atest`:
```shell
# Bubbles
atest WMShellFlickerTestsBubbles

# PIP
atest WMShellFlickerTestsPip1
atest WMShellFlickerTestsPip2
atest WMShellFlickerTestsPip3
atest WMShellFlickerTestsPipApps
atest WMShellFlickerTestsPipAppsCSuite

# Splitscreen
atest WMShellFlickerTestsSplitScreenGroup1
atest WMShellFlickerTestsSplitScreenGroup2

# Other
atest WMShellFlickerTestsOther
```

**Note**: Currently Flicker tests can only be run from the commandline and not via SysUI Studio

A subset of the flicker tests tests are run as a part of WindowManager presubmit, and the
dashboards for these tests tests can be found at [go/wm-tests-flicker](http://go/wm-tests-flicker).

## CTS tests

Some windowing features also have CTS tests to ensure consistent behavior across OEMs.  For example:
- Picture-in-Picture:
  [PinnedStackTests](cts/tests/framework/base/windowmanager/src/android/server/wm/PinnedStackTests.java)
- etc.

These can also be run via commandline only using `atest`, for example:
```shell
atest PinnedStackTests
```