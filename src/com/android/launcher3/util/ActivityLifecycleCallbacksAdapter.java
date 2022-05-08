package com.android.launcher3.util;

import android.app.Activity;
import android.app.Application.ActivityLifecycleCallbacks;
import android.os.Bundle;

public interface ActivityLifecycleCallbacksAdapter extends ActivityLifecycleCallbacks {

    default void onActivityCreated(Activity activity, Bundle bundle) {
    }

    default void onActivityDestroyed(Activity activity) {
    }

    default void onActivityPaused(Activity activity) {
    }

    default void onActivityResumed(Activity activity) {
    }

    default void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
    }

    default void onActivityStarted(Activity activity) {
    }

    default void onActivityStopped(Activity activity) {
    }
}
