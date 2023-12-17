package com.android.systemui.plugins;

import com.android.systemui.plugins.annotations.ProvidesInterface;

@ProvidesInterface(action = DozeServicePlugin.ACTION, version = DozeServicePlugin.VERSION)
public interface DozeServicePlugin extends Plugin {
    String ACTION = "com.android.systemui.action.PLUGIN_DOZE";
    int VERSION = 1;

    public interface RequestDoze {
        void onRequestShowDoze();

        void onRequestHideDoze();
    }

    void onDreamingStarted();

    void onDreamingStopped();

    void setDozeRequester(RequestDoze requester);
}
