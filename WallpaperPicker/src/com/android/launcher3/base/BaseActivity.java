package com.android.launcher3.base;

import android.app.Activity;
import android.content.Context;

/**
 * A wrapper over {@link Activity} which allows to override some methods.
 * The base implementation can change from an Activity to a Fragment (or any other custom
 * implementation), Callers should not assume that the base class extends Context, instead use
 * either {@link #getContext} or {@link #getActivity}
 */
public class BaseActivity extends Activity {

    public Context getContext() {
        return this;
    }

    public Activity getActivity() {
        return this;
    }
}
