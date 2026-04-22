# WM Shell Test

This contains all tests written for WM (WindowManager) Shell and it's currently
divided into 3 categories

- unittest, tests against individual functions, usually @SmallTest and do not
  require UI automation nor real device to run
- integration, this maybe a mix of functional and integration tests. Contains
  tests verify the WM Shell as a whole, like talking to WM core. This usually
  involves mocking the window manager service or even talking to the real one.
  Due to this nature, test cases in this package is normally annotated as
  @LargeTest and runs with UI automation on real device
- flicker, similar to functional tests with its sole focus on flickerness. See
  [WM Shell Flicker Test Package](http://cs/android/framework/base/libs/WindowManager/Shell/tests/flicker/)
  for more details
