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
import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.config.FeatureFlags;

public class OverviewScreenAccessibilityDelegate extends AccessibilityDelegate {

    private static final int MOVE_BACKWARD = R.id.action_move_screen_backwards;
    private static final int MOVE_FORWARD = R.id.action_move_screen_forwards;

    private final SparseArray<AccessibilityAction> mActions = new SparseArray<>();
    private final Workspace mWorkspace;

    public OverviewScreenAccessibilityDelegate(Workspace workspace) {
        mWorkspace = workspace;

        Context context = mWorkspace.getContext();
        boolean isRtl = Utilities.isRtl(context.getResources());
        mActions.put(MOVE_BACKWARD, new AccessibilityAction(MOVE_BACKWARD,
                context.getText(isRtl ? R.string.action_move_screen_right :
                    R.string.action_move_screen_left)));
        mActions.put(MOVE_FORWARD, new AccessibilityAction(MOVE_FORWARD,
                context.getText(isRtl ? R.string.action_move_screen_left :
                    R.string.action_move_screen_right)));
    }

    @Override
    public boolean performAccessibilityAction(View host, int action, Bundle args) {
        if (host != null) {
            if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS ) {
                int index = mWorkspace.indexOfChild(host);
                mWorkspace.setCurrentPage(index);
            } else if (action == MOVE_FORWARD) {
                movePage(mWorkspace.indexOfChild(host) + 1, host);
                return true;
            } else if (action == MOVE_BACKWARD) {
                movePage(mWorkspace.indexOfChild(host) - 1, host);
                return true;
            }
        }

        return super.performAccessibilityAction(host, action, args);
    }

    private void movePage(int finalIndex, View view) {
        mWorkspace.onStartReordering();
        mWorkspace.removeView(view);
        mWorkspace.addView(view, finalIndex);
        mWorkspace.onEndReordering();
        mWorkspace.announceForAccessibility(mWorkspace.getContext().getText(R.string.screen_moved));

        mWorkspace.updateAccessibilityFlags();
        view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);

        int index = mWorkspace.indexOfChild(host);
        if (index < mWorkspace.getChildCount() - 1) {
            info.addAction(mActions.get(MOVE_FORWARD));
        }

        int startIndex = mWorkspace.numCustomPages() + (FeatureFlags.QSB_ON_FIRST_SCREEN ? 1 : 0);
        if (index > startIndex) {
            info.addAction(mActions.get(MOVE_BACKWARD));
        }
    }
}
