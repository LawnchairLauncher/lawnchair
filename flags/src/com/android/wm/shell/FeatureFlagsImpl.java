package com.android.wm.shell;
// TODO(b/303773055): Remove the annotation after access issue is resolved.

import com.android.quickstep.util.DeviceConfigHelper;

import java.nio.file.Files;
import java.nio.file.Paths;
/** @hide */
public final class FeatureFlagsImpl implements FeatureFlags {
    private static final boolean isReadFromNew = Files.exists(Paths.get("/metadata/aconfig/boot/enable_only_new_storage"));
    private static volatile boolean isCached = false;
    private static volatile boolean multitasking_is_cached = false;
    private static boolean animateBubbleSizeChange = false;
    private static boolean enableAppPairs = true;
    private static boolean enableBubbleAnything = false;
    private static boolean enableBubbleBar = false;
    private static boolean enableBubbleStashing = false;
    private static boolean enableBubblesLongPressNavHandle = false;
    private static boolean enableLeftRightSplitInPortrait = true;
    private static boolean enableNewBubbleAnimations = false;
    private static boolean enableOptionalBubbleOverflow = true;
    private static boolean enablePipUmoExperience = false;
    private static boolean enableRetrievableBubbles = false;
    private static boolean enableSplitContextual = true;
    private static boolean enableTaskbarNavbarUnification = true;
    private static boolean enableTinyTaskbar = false;
    private static boolean onlyReuseBubbledTaskWhenLaunchedFromBubble = false;


    private void init() {
        boolean foundPackage = true;

        animateBubbleSizeChange = foundPackage;


        enableAppPairs = foundPackage;


        enableBubbleAnything = foundPackage;


        enableBubbleBar = foundPackage;


        enableBubbleStashing = foundPackage;


        enableBubblesLongPressNavHandle = foundPackage ;


        enableLeftRightSplitInPortrait = foundPackage;


        enableNewBubbleAnimations = foundPackage;


        enableOptionalBubbleOverflow = foundPackage;



        enablePipUmoExperience = foundPackage;


        enableRetrievableBubbles = foundPackage;


        enableSplitContextual = foundPackage;


        enableTaskbarNavbarUnification = foundPackage;


        enableTinyTaskbar = foundPackage;


        onlyReuseBubbledTaskWhenLaunchedFromBubble = foundPackage ;

        isCached = true;
    }




    private void load_overrides_multitasking() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            animateBubbleSizeChange =
                    properties.getBoolean(Flags.FLAG_ANIMATE_BUBBLE_SIZE_CHANGE, false);
            enableAppPairs =
                    properties.getBoolean(Flags.FLAG_ENABLE_APP_PAIRS, true);
            enableBubbleAnything =
                    properties.getBoolean(Flags.FLAG_ENABLE_BUBBLE_ANYTHING, false);
            enableBubbleBar =
                    properties.getBoolean(Flags.FLAG_ENABLE_BUBBLE_BAR, false);
            enableBubbleStashing =
                    properties.getBoolean(Flags.FLAG_ENABLE_BUBBLE_STASHING, false);
            enableBubblesLongPressNavHandle =
                    properties.getBoolean(Flags.FLAG_ENABLE_BUBBLES_LONG_PRESS_NAV_HANDLE, false);
            enableLeftRightSplitInPortrait =
                    properties.getBoolean(Flags.FLAG_ENABLE_LEFT_RIGHT_SPLIT_IN_PORTRAIT, true);
            enableNewBubbleAnimations =
                    properties.getBoolean(Flags.FLAG_ENABLE_NEW_BUBBLE_ANIMATIONS, false);
            enableOptionalBubbleOverflow =
                    properties.getBoolean(Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW, true);
            enablePipUmoExperience =
                    properties.getBoolean(Flags.FLAG_ENABLE_PIP_UMO_EXPERIENCE, false);
            enableRetrievableBubbles =
                    properties.getBoolean(Flags.FLAG_ENABLE_RETRIEVABLE_BUBBLES, false);
            enableSplitContextual =
                    properties.getBoolean(Flags.FLAG_ENABLE_SPLIT_CONTEXTUAL, true);
            enableTaskbarNavbarUnification =
                    properties.getBoolean(Flags.FLAG_ENABLE_TASKBAR_NAVBAR_UNIFICATION, true);
            enableTinyTaskbar =
                    properties.getBoolean(Flags.FLAG_ENABLE_TINY_TASKBAR, false);
            onlyReuseBubbledTaskWhenLaunchedFromBubble =
                    properties.getBoolean(Flags.FLAG_ONLY_REUSE_BUBBLED_TASK_WHEN_LAUNCHED_FROM_BUBBLE, false);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace multitasking "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        }
        multitasking_is_cached = true;
    }

    @Override
    
    
    public boolean animateBubbleSizeChange() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!multitasking_is_cached) {
                load_overrides_multitasking();
            }
        }
        return animateBubbleSizeChange;

    }

    @Override
    
    
    public boolean enableAppPairs() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!multitasking_is_cached) {
                load_overrides_multitasking();
            }
        }
        return enableAppPairs;

    }

    @Override
    
    
    public boolean enableBubbleAnything() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!multitasking_is_cached) {
                load_overrides_multitasking();
            }
        }
        return enableBubbleAnything;

    }

    @Override
    
    
    public boolean enableBubbleBar() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!multitasking_is_cached) {
                load_overrides_multitasking();
            }
        }
        return enableBubbleBar;

    }

    @Override
    
    
    public boolean enableBubbleStashing() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!multitasking_is_cached) {
                load_overrides_multitasking();
            }
        }
        return enableBubbleStashing;

    }

    @Override
    
    
    public boolean enableBubblesLongPressNavHandle() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!multitasking_is_cached) {
                load_overrides_multitasking();
            }
        }
        return enableBubblesLongPressNavHandle;

    }

    @Override
    
    
    public boolean enableLeftRightSplitInPortrait() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!multitasking_is_cached) {
                load_overrides_multitasking();
            }
        }
        return enableLeftRightSplitInPortrait;

    }

    @Override
    
    
    public boolean enableNewBubbleAnimations() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!multitasking_is_cached) {
                load_overrides_multitasking();
            }
        }
        return enableNewBubbleAnimations;

    }

    @Override
    
    
    public boolean enableOptionalBubbleOverflow() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!multitasking_is_cached) {
                load_overrides_multitasking();
            }
        }
        return enableOptionalBubbleOverflow;

    }

    @Override
    
    
    public boolean enablePip2Implementation() {
        return false;

    }

    @Override
    
    
    public boolean enablePipUmoExperience() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!multitasking_is_cached) {
                load_overrides_multitasking();
            }
        }
        return enablePipUmoExperience;

    }

    @Override
    
    
    public boolean enableRetrievableBubbles() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!multitasking_is_cached) {
                load_overrides_multitasking();
            }
        }
        return enableRetrievableBubbles;

    }

    @Override
    
    
    public boolean enableSplitContextual() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!multitasking_is_cached) {
                load_overrides_multitasking();
            }
        }
        return enableSplitContextual;

    }

    @Override
    
    
    public boolean enableTaskbarNavbarUnification() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!multitasking_is_cached) {
                load_overrides_multitasking();
            }
        }
        return enableTaskbarNavbarUnification;

    }

    @Override
    
    
    public boolean enableTinyTaskbar() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!multitasking_is_cached) {
                load_overrides_multitasking();
            }
        }
        return enableTinyTaskbar;

    }

    @Override
    
    
    public boolean onlyReuseBubbledTaskWhenLaunchedFromBubble() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!multitasking_is_cached) {
                load_overrides_multitasking();
            }
        }
        return onlyReuseBubbledTaskWhenLaunchedFromBubble;

    }

}

