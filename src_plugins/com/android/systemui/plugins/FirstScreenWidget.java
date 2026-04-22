package com.android.systemui.plugins;

import android.view.ViewGroup;
import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Implement this interface to wrap the widget on the first home screen, e.g. to add new content.
 */
@ProvidesInterface(action = FirstScreenWidget.ACTION, version = FirstScreenWidget.VERSION)
public interface FirstScreenWidget extends Plugin {
    String ACTION = "com.android.systemui.action.PLUGIN_FIRST_SCREEN_WIDGET";
    int VERSION = 1;

    void onWidgetUpdated(ViewGroup widgetView);
}
