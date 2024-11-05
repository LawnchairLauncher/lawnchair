package com.android.systemui.shared;
// TODO(b/303773055): Remove the annotation after access issue is resolved.

import com.android.quickstep.util.DeviceConfigHelper;

import java.nio.file.Files;
import java.nio.file.Paths;
/** @hide */
public final class FeatureFlagsImpl implements FeatureFlags {
    private static final boolean isReadFromNew = Files.exists(Paths.get("/metadata/aconfig/boot/enable_only_new_storage"));
    private static volatile boolean isCached = false;
    private static volatile boolean biometrics_framework_is_cached = false;
    private static volatile boolean systemui_is_cached = false;
    private static boolean bouncerAreaExclusion = true;
    private static boolean enableHomeDelay = false;
    private static boolean exampleSharedFlag = false;
    private static boolean returnAnimationFrameworkLibrary = false;
    private static boolean shadeAllowBackGesture = false;
    private static boolean sidefpsControllerRefactor = true;


    private void init() {
        boolean foundPackage = true;

        sidefpsControllerRefactor = foundPackage;


        bouncerAreaExclusion = foundPackage;


        enableHomeDelay = foundPackage;


        exampleSharedFlag = foundPackage;


        returnAnimationFrameworkLibrary = foundPackage ;


        shadeAllowBackGesture = foundPackage;

        isCached = true;
    }




    private void load_overrides_biometrics_framework() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            sidefpsControllerRefactor =
                    properties.getBoolean(Flags.FLAG_SIDEFPS_CONTROLLER_REFACTOR, true);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace biometrics_framework "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        }
        biometrics_framework_is_cached = true;
    }

    private void load_overrides_systemui() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            bouncerAreaExclusion =
                    properties.getBoolean(Flags.FLAG_BOUNCER_AREA_EXCLUSION, true);
            enableHomeDelay =
                    properties.getBoolean(Flags.FLAG_ENABLE_HOME_DELAY, false);
            exampleSharedFlag =
                    properties.getBoolean(Flags.FLAG_EXAMPLE_SHARED_FLAG, false);
            returnAnimationFrameworkLibrary =
                    properties.getBoolean(Flags.FLAG_RETURN_ANIMATION_FRAMEWORK_LIBRARY, false);
            shadeAllowBackGesture =
                    properties.getBoolean(Flags.FLAG_SHADE_ALLOW_BACK_GESTURE, false);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace systemui "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        }
        systemui_is_cached = true;
    }

    @Override
    
    
    public boolean bouncerAreaExclusion() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return bouncerAreaExclusion;

    }

    @Override
    
    
    public boolean enableHomeDelay() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return enableHomeDelay;

    }

    @Override
    
    
    public boolean exampleSharedFlag() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return exampleSharedFlag;

    }

    @Override
    
    
    public boolean returnAnimationFrameworkLibrary() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return returnAnimationFrameworkLibrary;

    }

    @Override
    
    
    public boolean shadeAllowBackGesture() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return shadeAllowBackGesture;

    }

    @Override
    
    
    public boolean sidefpsControllerRefactor() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!biometrics_framework_is_cached) {
                load_overrides_biometrics_framework();
            }
        }
        return sidefpsControllerRefactor;

    }

}

