/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.common.pip;

import static com.android.wm.shell.common.pip.PipUtils.dpToPx;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.android.wm.shell.R;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.dagger.WMSingleton;

import java.io.PrintWriter;

import javax.inject.Inject;

/**
 * Acts as a source of truth for display related information for PIP.
 */
@WMSingleton
public class PipDisplayLayoutState {
    private static final String TAG = PipDisplayLayoutState.class.getSimpleName();

    private Context mContext;
    private int mDisplayId;
    @NonNull private DisplayLayout mDisplayLayout;

    private Point mScreenEdgeInsets = null;

    @Inject
    public PipDisplayLayoutState(Context context) {
        mContext = context;
        mDisplayLayout = new DisplayLayout();
        reloadResources();
    }

    /** Responds to configuration change. */
    public void onConfigurationChanged() {
        reloadResources();
    }

    private void reloadResources() {
        Resources res = mContext.getResources();

        final String screenEdgeInsetsDpString = res.getString(
                R.string.config_defaultPictureInPictureScreenEdgeInsets);
        final Size screenEdgeInsetsDp = !screenEdgeInsetsDpString.isEmpty()
                ? Size.parseSize(screenEdgeInsetsDpString)
                : null;
        mScreenEdgeInsets = screenEdgeInsetsDp == null ? new Point()
                : new Point(dpToPx(screenEdgeInsetsDp.getWidth(), res.getDisplayMetrics()),
                        dpToPx(screenEdgeInsetsDp.getHeight(), res.getDisplayMetrics()));
    }

    public Point getScreenEdgeInsets() {
        return mScreenEdgeInsets;
    }

    /**
     * Returns the inset bounds the PIP window can be visible in.
     */
    public Rect getInsetBounds() {
        Rect insetBounds = new Rect();
        Rect insets = getDisplayLayout().stableInsets();
        insetBounds.set(insets.left + getScreenEdgeInsets().x,
                insets.top + getScreenEdgeInsets().y,
                getDisplayLayout().width() - insets.right - getScreenEdgeInsets().x,
                getDisplayLayout().height() - insets.bottom - getScreenEdgeInsets().y);
        return insetBounds;
    }

    /** Set the display layout. */
    public void setDisplayLayout(@NonNull DisplayLayout displayLayout) {
        mDisplayLayout.set(displayLayout);
    }

    /** Get a copy of the display layout. */
    @NonNull
    public DisplayLayout getDisplayLayout() {
        return new DisplayLayout(mDisplayLayout);
    }

    /** Get the display bounds */
    @NonNull
    public Rect getDisplayBounds() {
        return new Rect(0, 0, mDisplayLayout.width(), mDisplayLayout.height());
    }

    /**
     * Apply a rotation to this layout and its parameters.
     * @param targetRotation
     */
    public void rotateTo(@Surface.Rotation int targetRotation) {
        mDisplayLayout.rotateTo(mContext.getResources(), targetRotation);
    }

    /** Returns the current display rotation of this layout state. */
    @Surface.Rotation
    public int getRotation() {
        return mDisplayLayout.rotation();
    }

    /** Get the current display id */
    public int getDisplayId() {
        return mDisplayId;
    }

    /** Set the current display id for the associated display layout. */
    public void setDisplayId(int displayId) {
        mDisplayId = displayId;
    }

    /** Dumps internal state. */
    public void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(innerPrefix + "mDisplayId=" + mDisplayId);
        pw.println(innerPrefix + "getDisplayBounds=" + getDisplayBounds());
        pw.println(innerPrefix + "mScreenEdgeInsets=" + mScreenEdgeInsets);
    }
}
