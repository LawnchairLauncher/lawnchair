/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.settings;

import static com.android.launcher3.settings.SettingsActivity.EXTRA_FRAGMENT_ARG_KEY;
import static com.android.launcher3.settings.SettingsActivity.EXTRA_SHOW_FRAGMENT_ARGS;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.R;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.util.SecureSettingsObserver;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * A {@link Preference} for indicating notification dots status.
 * Also has utility methods for updating UI based on dots status changes.
 */
public class NotificationDotsPreference extends Preference
        implements SecureSettingsObserver.OnChangeListener {

    private boolean mWidgetFrameVisible = false;

    /** Hidden field Settings.Secure.ENABLED_NOTIFICATION_LISTENERS */
    private static final String NOTIFICATION_ENABLED_LISTENERS = "enabled_notification_listeners";

    public NotificationDotsPreference(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public NotificationDotsPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public NotificationDotsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NotificationDotsPreference(Context context) {
        super(context);
    }

    private void setWidgetFrameVisible(boolean isVisible) {
        if (mWidgetFrameVisible != isVisible) {
            mWidgetFrameVisible = isVisible;
            notifyChanged();
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        View widgetFrame = holder.findViewById(android.R.id.widget_frame);
        if (widgetFrame != null) {
            widgetFrame.setVisibility(mWidgetFrameVisible ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onSettingsChanged(boolean enabled) {
        int summary = enabled
                ? R.string.notification_dots_desc_on
                : R.string.notification_dots_desc_off;

        boolean serviceEnabled = true;
        if (enabled) {
            // Check if the listener is enabled or not.
            String enabledListeners = Settings.Secure.getString(
                    getContext().getContentResolver(), NOTIFICATION_ENABLED_LISTENERS);
            ComponentName myListener =
                    new ComponentName(getContext(), NotificationListener.class);
            serviceEnabled = enabledListeners != null &&
                    (enabledListeners.contains(myListener.flattenToString()) ||
                            enabledListeners.contains(myListener.flattenToShortString()));
            if (!serviceEnabled) {
                summary = R.string.title_missing_notification_access;
            }
        }
        setWidgetFrameVisible(!serviceEnabled);
        setFragment(serviceEnabled ? null : NotificationAccessConfirmation.class.getName());
        setSummary(summary);
    }

    public static class NotificationAccessConfirmation
            extends DialogFragment implements DialogInterface.OnClickListener {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            String msg = context.getString(R.string.msg_missing_notification_access,
                    context.getString(R.string.derived_app_name));
            return new AlertDialog.Builder(context)
                    .setTitle(R.string.title_missing_notification_access)
                    .setMessage(msg)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.title_change_settings, this)
                    .create();
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            ComponentName cn = new ComponentName(getActivity(), NotificationListener.class);
            Bundle showFragmentArgs = new Bundle();
            showFragmentArgs.putString(EXTRA_FRAGMENT_ARG_KEY, cn.flattenToString());

            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_FRAGMENT_ARG_KEY, cn.flattenToString())
                    .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, showFragmentArgs);
            getActivity().startActivity(intent);
        }
    }
}
