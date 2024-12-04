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
import android.app.smartspace.uitemplatedata.TapAction;
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
    String UI_SURFACE_LOCK_SCREEN_AOD = "lockscreen";
    String UI_SURFACE_HOME_SCREEN = "home";
    String UI_SURFACE_MEDIA = "media_data_manager";
    String UI_SURFACE_DREAM = "dream";
    String UI_SURFACE_GLANCEABLE_HUB = "glanceable_hub";

    String ACTION = "com.android.systemui.action.PLUGIN_BC_SMARTSPACE_DATA";
    int VERSION = 1;
    String TAG = "BcSmartspaceDataPlugin";

    /** Register a listener to get Smartspace data. */
    default void registerListener(SmartspaceTargetListener listener) {
        throw new UnsupportedOperationException("Not implemented by " + getClass());
    }

    /** Unregister a listener. */
    default void unregisterListener(SmartspaceTargetListener listener) {
        throw new UnsupportedOperationException("Not implemented by " + getClass());
    }

    /** Register a SmartspaceEventNotifier. */
    default void registerSmartspaceEventNotifier(SmartspaceEventNotifier notifier) {
        throw new UnsupportedOperationException("Not implemented by " + getClass());
    }

    /** Push a SmartspaceTargetEvent to the SmartspaceEventNotifier. */
    default void notifySmartspaceEvent(SmartspaceTargetEvent event) {
        throw new UnsupportedOperationException("Not implemented by " + getClass());
    }

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
        throw new UnsupportedOperationException("Not implemented by " + getClass());
    }

    /**
     * As the smartspace view becomes available, allow listeners to receive an event.
     */
    default void addOnAttachStateChangeListener(View.OnAttachStateChangeListener listener) {
        throw new UnsupportedOperationException("Not implemented by " + getClass());
    }

    /** Updates Smartspace data and propagates it to any listeners. */
    default void onTargetsAvailable(List<SmartspaceTarget> targets) {
        throw new UnsupportedOperationException("Not implemented by " + getClass());
    }

    /** Provides Smartspace data to registered listeners. */
    interface SmartspaceTargetListener {
        /** Each Parcelable is a SmartspaceTarget that represents a card. */
        void onSmartspaceTargetsUpdated(List<? extends Parcelable> targets);
    }

    /** View to which this plugin can be registered, in order to get updates. */
    interface SmartspaceView {
        void registerDataProvider(BcSmartspaceDataPlugin plugin);

        /**
         * Sets {@link BcSmartspaceConfigPlugin}.
         */
        default void registerConfigProvider(BcSmartspaceConfigPlugin configProvider) {
            throw new UnsupportedOperationException("Not implemented by " + getClass());
        }

        /**
         * Primary color for unprotected text
         */
        void setPrimaryTextColor(int color);

        /**
         * Set the UI surface for the cards. Should be called immediately after the view is created.
         */
        void setUiSurface(String uiSurface);

        /**
         * Range [0.0 - 1.0] when transitioning from Lockscreen to/from AOD
         */
        void setDozeAmount(float amount);

        /**
         * Set if the screen is on.
         */
        default void setScreenOn(boolean screenOn) {}

        /**
         * Sets a delegate to handle clock event registration. Should be called immediately after
         * the view is created.
         */
        default void setTimeChangedDelegate(TimeChangedDelegate delegate) {}

        /**
         * Set if dozing is true or false
         */
        default void setDozing(boolean dozing) {}

        /**
         * Set if split shade enabled
         */
        default void setSplitShadeEnabled(boolean enabled) {}

        /**
         * Set the current keyguard bypass enabled status.
         */
        default void setKeyguardBypassEnabled(boolean enabled) {}

        /**
         * Overrides how Intents/PendingIntents gets launched. Mostly to support auth from
         * the lockscreen.
         */
        void setIntentStarter(IntentStarter intentStarter);

        /**
         * When on the lockscreen, use the FalsingManager to help detect errant touches
         */
        void setFalsingManager(com.android.systemui.plugins.FalsingManager falsingManager);

        /**
         * Set or clear Do Not Disturb information.
         */
        default void setDnd(@Nullable Drawable image, @Nullable String description) {
            throw new UnsupportedOperationException("Not implemented by " + getClass());
        }

        /**
         * Set or clear next alarm information
         */
        default void setNextAlarm(@Nullable Drawable image, @Nullable String description) {
            throw new UnsupportedOperationException("Not implemented by " + getClass());
        }

        /**
         * Set or clear device media playing
         */
        default void setMediaTarget(@Nullable SmartspaceTarget target) {
            throw new UnsupportedOperationException("Not implemented by " + getClass());
        }

        /**
         * Get the index of the currently selected page.
         */
        default int getSelectedPage() {
            throw new UnsupportedOperationException("Not implemented by " + getClass());
        }

        /**
         * Return the top padding value from the currently visible card, or 0 if there is no current
         * card.
         */
        default int getCurrentCardTopPadding() {
            throw new UnsupportedOperationException("Not implemented by " + getClass());
        }
    }

    /** Interface for launching Intents, which can differ on the lockscreen */
    interface IntentStarter {
        default void startFromAction(SmartspaceAction action, View v, boolean showOnLockscreen) {
            try {
                if (action.getIntent() != null) {
                    startIntent(v, action.getIntent(), showOnLockscreen);
                } else if (action.getPendingIntent() != null) {
                    startPendingIntent(v, action.getPendingIntent(), showOnLockscreen);
                }
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "Could not launch intent for action: " + action, e);
            }
        }

        default void startFromAction(TapAction action, View v, boolean showOnLockscreen) {
            try {
                if (action.getIntent() != null) {
                    startIntent(v, action.getIntent(), showOnLockscreen);
                } else if (action.getPendingIntent() != null) {
                    startPendingIntent(v, action.getPendingIntent(), showOnLockscreen);
                }
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "Could not launch intent for action: " + action, e);
            }
        }

        /** Start the intent */
        void startIntent(View v, Intent i, boolean showOnLockscreen);

        /** Start the PendingIntent */
        void startPendingIntent(View v, PendingIntent pi, boolean showOnLockscreen);
    }

    /** Interface for delegating time updates */
    interface TimeChangedDelegate {
        /** Register the callback to be called when time is updated **/
        void register(Runnable callback);

        /** Unegister the callback **/
        void unregister();
    }
}
