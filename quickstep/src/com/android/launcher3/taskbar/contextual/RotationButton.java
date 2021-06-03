/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.launcher3.taskbar.contextual;

import android.graphics.drawable.AnimatedVectorDrawable;
import android.view.View;

/**
 * Interface of a rotation button that interacts {@link RotationButtonController}.
 * This interface exists because of the two different styles of rotation button in Sysui,
 * one in contextual for 3 button nav and a floating rotation button for gestural.
 * Keeping the interface for eventual migration of floating button, so some methods are
 * pass through to "super" while others are trivially implemented.
 *
 * Changes:
 *  * Directly use AnimatedVectorDrawable instead of KeyButtonDrawable
 */
public interface RotationButton {
    void setRotationButtonController(RotationButtonController rotationButtonController);
    View getCurrentView();
    boolean show();
    boolean hide();
    boolean isVisible();
    void updateIcon(int lightIconColor, int darkIconColor);
    void setOnClickListener(View.OnClickListener onClickListener);
    void setOnHoverListener(View.OnHoverListener onHoverListener);
    AnimatedVectorDrawable getImageDrawable();
    void setDarkIntensity(float darkIntensity);
    default boolean acceptRotationProposal() {
        return getCurrentView() != null;
    }
}
