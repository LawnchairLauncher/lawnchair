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

package com.android.launcher3.widget;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities to compute the enforced the use of rounded corners on App Widgets.
 */
public class RoundedCornerEnforcement {
    // This class is only a namespace and not meant to be instantiated.
    private RoundedCornerEnforcement() {
    }

    /**
     * Find the background view for a widget.
     *
     * @param appWidget the view containing the App Widget (typically the instance of
     * {@link AppWidgetHostView}).
     */
    @Nullable
    public static View findBackground(@NonNull View appWidget) {
        List<View> backgrounds = findViewsWithId(appWidget, android.R.id.background);
        if (backgrounds.size() == 1) {
            return backgrounds.get(0);
        }
        // Really, the argument should contain the widget, so it cannot be the background.
        if (appWidget instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) appWidget;
            if (vg.getChildCount() > 0) {
                return findUndefinedBackground(vg.getChildAt(0));
            }
        }
        return appWidget;
    }

    /**
     * Check whether the app widget has opted out of the enforcement.
     */
    public static boolean hasAppWidgetOptedOut(@NonNull View appWidget, @NonNull View background) {
        return background.getId() == android.R.id.background && background.getClipToOutline();
    }

    /** Check if the app widget is in the deny list. */
    public static boolean isRoundedCornerEnabled() {
        return Utilities.ATLEAST_S && FeatureFlags.ENABLE_ENFORCED_ROUNDED_CORNERS.get();
    }

    /**
     * Computes the rounded rectangle needed for this app widget.
     *
     * @param appWidget View onto which the rounded rectangle will be applied.
     * @param background Background view. This must be either {@code appWidget} or a descendant
     *                  of {@code appWidget}.
     * @param outRect Rectangle set to the rounded rectangle coordinates, in the reference frame
     *                of {@code appWidget}.
     */
    public static void computeRoundedRectangle(@NonNull View appWidget, @NonNull View background,
            @NonNull Rect outRect) {
        outRect.left = 0;
        outRect.right = background.getWidth();
        outRect.top = 0;
        outRect.bottom = background.getHeight();
        while (background != appWidget) {
            outRect.offset(background.getLeft(), background.getTop());
            background = (View) background.getParent();
        }
    }

    /**
     * Computes the radius of the rounded rectangle that should be applied to a widget expanded
     * in the given context.
     */
    public static float computeEnforcedRadius(@NonNull Context context) {
        if (!Utilities.ATLEAST_S) {
            return 0;
        }
        Resources res = context.getResources();
        float systemRadius = res.getDimension(android.R.dimen.system_app_widget_background_radius);
        float defaultRadius = res.getDimension(R.dimen.enforced_rounded_corner_max_radius);
        return Math.min(defaultRadius, systemRadius);
    }

    private static List<View> findViewsWithId(View view, @IdRes int viewId) {
        List<View> output = new ArrayList<>();
        accumulateViewsWithId(view, viewId, output);
        return output;
    }

    // Traverse views. If the predicate returns true, continue on the children, otherwise, don't.
    private static void accumulateViewsWithId(View view, @IdRes int viewId, List<View> output) {
        if (view.getId() == viewId) {
            output.add(view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                accumulateViewsWithId(vg.getChildAt(i), viewId, output);
            }
        }
    }

    private static boolean isViewVisible(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return false;
        }
        return !view.willNotDraw() || view.getForeground() != null || view.getBackground() != null;
    }

    @Nullable
    private static View findUndefinedBackground(View current) {
        if (current.getVisibility() != View.VISIBLE) {
            return null;
        }
        if (isViewVisible(current)) {
            return current;
        }
        View lastVisibleView = null;
        // Find the first view that is either not a ViewGroup, or a ViewGroup which will draw
        // something, or a ViewGroup that contains more than one view.
        if (current instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) current;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View visibleView = findUndefinedBackground(vg.getChildAt(i));
                if (visibleView != null) {
                    if (lastVisibleView != null) {
                        return current; // At least two visible children
                    }
                    lastVisibleView = visibleView;
                }
            }
        }
        return lastVisibleView;
    }
}
