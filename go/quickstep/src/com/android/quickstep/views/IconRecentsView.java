/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.ViewDebug;
import android.widget.FrameLayout;

/**
 * Root view for the icon recents view.
 */
public final class IconRecentsView extends FrameLayout {

    public static final FloatProperty<IconRecentsView> TRANSLATION_Y_FACTOR =
            new FloatProperty<IconRecentsView>("translationYFactor") {

                @Override
                public void setValue(IconRecentsView view, float v) {
                    view.setTranslationYFactor(v);
                }

                @Override
                public Float get(IconRecentsView view) {
                    return view.mTranslationYFactor;
                }
            };

    public static final FloatProperty<IconRecentsView> CONTENT_ALPHA =
            new FloatProperty<IconRecentsView>("contentAlpha") {
                @Override
                public void setValue(IconRecentsView view, float v) {
                    ALPHA.set(view, v);
                }

                @Override
                public Float get(IconRecentsView view) {
                    return ALPHA.get(view);
                }
            };

    /**
     * A ratio representing the view's relative placement within its padded space. For example, 0
     * is top aligned and 0.5 is centered vertically.
     */
    @ViewDebug.ExportedProperty(category = "launcher")
    private float mTranslationYFactor;

    public IconRecentsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setTranslationYFactor(float translationFactor) {
        mTranslationYFactor = translationFactor;
        setTranslationY(computeTranslationYForFactor(mTranslationYFactor));
    }

    private float computeTranslationYForFactor(float translationYFactor) {
        return translationYFactor * (getPaddingBottom() - getPaddingTop());
    }
}
