/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.launcher2;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.OnHierarchyChangeListener;

import java.util.HashMap;

public class HideFromAccessibilityHelper implements OnHierarchyChangeListener {
    private HashMap<View, Integer> mPreviousValues;
    boolean mHide;
    boolean mOnlyAllApps;

    public HideFromAccessibilityHelper() {
        mPreviousValues = new HashMap<View, Integer>();
        mHide = false;
    }

    public void setImportantForAccessibilityToNo(View v, boolean onlyAllApps) {
        mOnlyAllApps = onlyAllApps;
        setImportantForAccessibilityToNoHelper(v);
        mHide = true;
    }

    private void setImportantForAccessibilityToNoHelper(View v) {
        mPreviousValues.put(v, v.getImportantForAccessibility());
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        // Call method on children recursively
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            vg.setOnHierarchyChangeListener(this);
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);

                if (includeView(child)) {
                    setImportantForAccessibilityToNoHelper(child);
                }
            }
        }
    }

    public void restoreImportantForAccessibility(View v) {
        if (mHide) {
            restoreImportantForAccessibilityHelper(v);
        }
        mHide = false;
    }

    private void restoreImportantForAccessibilityHelper(View v) {
        v.setImportantForAccessibility(mPreviousValues.get(v));
        mPreviousValues.remove(v);

        // Call method on children recursively
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;

            // We assume if a class implements OnHierarchyChangeListener, it listens
            // to changes to any of its children (happens to be the case in Launcher)
            if (vg instanceof OnHierarchyChangeListener) {
                vg.setOnHierarchyChangeListener((OnHierarchyChangeListener) vg);
            } else {
                vg.setOnHierarchyChangeListener(null);
            }
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (includeView(child)) {
                    restoreImportantForAccessibilityHelper(child);
                }
            }
        }
    }

    public void onChildViewAdded(View parent, View child) {
        if (mHide && includeView(child)) {
            setImportantForAccessibilityToNoHelper(child);
        }
    }

    public void onChildViewRemoved(View parent, View child) {
        if (mHide && includeView(child)) {
            restoreImportantForAccessibilityHelper(child);
        }
    }

    private boolean includeView(View v) {
        return !hasAncestorOfType(v, Cling.class) &&
                (!mOnlyAllApps || hasAncestorOfType(v, AppsCustomizeTabHost.class));
    }

    private boolean hasAncestorOfType(View v, Class c) {
        return v != null &&
                (v.getClass().equals(c) ||
                 (v.getParent() instanceof ViewGroup &&
                  hasAncestorOfType((ViewGroup) v.getParent(), c)));
    }
}