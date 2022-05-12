/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.plugins;

import android.app.PendingIntent;
import android.app.smartspace.SmartspaceAction;
import android.app.smartspace.SmartspaceTarget;
import android.app.smartspace.SmartspaceTargetEvent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.android.systemui.plugins.annotations.ProvidesInterface;

import java.util.List;

/**
 * Interface to provide SmartspaceTargets to BcSmartspace.
 */
@ProvidesInterface(action = BcSmartspaceDataPlugin.ACTION, version = BcSmartspaceDataPlugin.VERSION)
public interface BcSmartspaceDataPlugin extends Plugin {
    String ACTION = "com.android.systemui.action.PLUGIN_BC_SMARTSPACE_DATA";
    int VERSION = 1;
    String TAG = "BcSmartspaceDataPlugin";

    /** Register a listener to get Smartspace data. */
    void registerListener(SmartspaceTargetListener listener);

    /** Unregister a listener. */
    void unregisterListener(SmartspaceTargetListener listener);

    /** Register a SmartspaceEventNotifier. */
    default void registerSmartspaceEventNotifier(SmartspaceEventNotifier notifier) {}

    /** Push a SmartspaceTargetEvent to the SmartspaceEventNotifier. */
    default void notifySmartspaceEvent(SmartspaceTargetEvent event) {}

    /** Allows for notifying the SmartspaceSession of SmartspaceTargetEvents. */
    interface SmartspaceEventNotifier {
        /** Pushes a given SmartspaceTargetEvent to the SmartspaceSession. */
        void notifySmartspaceEvent(SmartspaceTargetEvent event);
    }

    /**
     * Create a view to be shown within the parent. Do not add the view, as the parent
     * will be responsible for correctly setting the LayoutParams
     */
    default SmartspaceView getView(ViewGroup parent) {
        return null;
    }

    /**
     * As the smartspace view becomes available, allow listeners to receive an event.
     */
    default void addOnAttachStateChangeListener(View.OnAttachStateChangeListener listener) { }

    /** Updates Smartspace data and propagates it to any listeners. */
    void onTargetsAvailable(List<SmartspaceTarget> targets);

    /** Provides Smartspace data to registered listeners. */
    interface SmartspaceTargetListener {
        /** Each Parcelable is a SmartspaceTarget that represents a card. */
        void onSmartspaceTargetsUpdated(List<? extends Parcelable> targets);
    }

    /** View to which this plugin can be registered, in order to get updates. */
    interface SmartspaceView {
        void registerDataProvider(BcSmartspaceDataPlugin plugin);

        /**
         * Primary color for unprotected text
         */
        void setPrimaryTextColor(int color);

        /**
         * Range [0.0 - 1.0] when transitioning from Lockscreen to/from AOD
         */
        void setDozeAmount(float amount);

        /**
         * Overrides how Intents/PendingIntents gets launched. Mostly to support auth from
         * the lockscreen.
         */
        void setIntentStarter(IntentStarter intentStarter);

        /**
         * Set or clear Do Not Disturb information.
         */
        void setDnd(@Nullable Drawable image, @Nullable String description);

        /**
         * Set or clear next alarm information
         */
        void setNextAlarm(@Nullable Drawable image, @Nullable String description);

        /**
         * Set or clear device media playing
         */
        void setMediaTarget(@Nullable SmartspaceTarget target);
    }

    /** Interface for launching Intents, which can differ on the lockscreen */
    interface IntentStarter {
        default void startFromAction(SmartspaceAction action, View v, boolean showOnLockscreen) {
            try {
                if (action.getIntent() != null) {
                    startIntent(v, action.getIntent(), showOnLockscreen);
                } else if (action.getPendingIntent() != null) {
                    startPendingIntent(action.getPendingIntent(), showOnLockscreen);
                }
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "Could not launch intent for action: " + action, e);
            }
        }

        /** Start the intent */
        void startIntent(View v, Intent i, boolean showOnLockscreen);

        /** Start the PendingIntent */
        void startPendingIntent(PendingIntent pi, boolean showOnLockscreen);
    }
}
