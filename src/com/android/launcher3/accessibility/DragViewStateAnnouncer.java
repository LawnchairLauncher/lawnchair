/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.accessibility;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import com.android.launcher3.Launcher;

/**
 * Periodically sends accessibility events to announce ongoing state changed. Based on the
 * implementation in ProgressBar.
 */
public class DragViewStateAnnouncer implements Runnable {

    private static final int TIMEOUT_SEND_ACCESSIBILITY_EVENT = 200;

    private final View mTargetView;

    private DragViewStateAnnouncer(View view) {
        mTargetView = view;
    }

    public void announce(CharSequence msg) {
        mTargetView.setContentDescription(msg);
        mTargetView.removeCallbacks(this);
        mTargetView.postDelayed(this, TIMEOUT_SEND_ACCESSIBILITY_EVENT);
    }

    public void cancel() {
        mTargetView.removeCallbacks(this);
    }

    @Override
    public void run() {
        mTargetView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    public void completeAction(int announceResId) {
        cancel();
        Launcher launcher = Launcher.getLauncher(mTargetView.getContext());
        launcher.getDragLayer().announceForAccessibility(launcher.getText(announceResId));
    }

    public static DragViewStateAnnouncer createFor(View v) {
        if (((AccessibilityManager) v.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE))
                .isEnabled()) {
            return new DragViewStateAnnouncer(v);
        } else {
            return null;
        }
    }
}
