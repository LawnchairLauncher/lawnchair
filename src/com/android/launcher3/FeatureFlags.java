package com.android.launcher3;
// TODO(b/303773055): Remove the annotation after access issue is resolved.
/** @hide */
public interface FeatureFlags {

    boolean enableCursorHoverStates();
    boolean enableExpandingPauseWorkButton();
    boolean enableGridOnlyOverview();

    boolean enableHomeTransitionListener();
    boolean enableLauncherBrMetrics();

    boolean enableOverviewIconMenu();

    boolean enablePrivateSpace();

    boolean enableResponsiveWorkspace();

    boolean enableSplitFromFullscreenWithKeyboardShortcuts();

    boolean enableTabletTwoPanePickerV2();

    boolean enableTaskbarNoRecreate();

    boolean enableTaskbarPinning();

    boolean enableTwolineAllapps();

    boolean enableUnfoldedTwoPanePicker();

    boolean privateSpaceAnimation();
}