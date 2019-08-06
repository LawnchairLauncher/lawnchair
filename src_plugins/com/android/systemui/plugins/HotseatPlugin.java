package com.android.systemui.plugins;

import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Implement this plugin interface to add a sub-view in the Hotseat.
 */
@ProvidesInterface(action = HotseatPlugin.ACTION, version = HotseatPlugin.VERSION)
public interface HotseatPlugin extends Plugin {
    String ACTION = "com.android.systemui.action.PLUGIN_HOTSEAT";
    int VERSION = 1;

    /**
     * Creates a plugin view which will be added to the Hotseat.
     */
    View createView(ViewGroup parent);
}
