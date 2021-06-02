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

import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.view.View;
import android.widget.ImageView;

import com.android.launcher3.R;

/** Containing logic for the rotation button in nav bar. */
public class RotationContextButton extends ImageView implements RotationButton {

    private AnimatedVectorDrawable mImageDrawable;

    public RotationContextButton(Context context) {
        super(context);
        setBackgroundResource(R.drawable.taskbar_icon_click_feedback_roundrect);
    }

    @Override
    public void setRotationButtonController(RotationButtonController rotationButtonController) {
        // TODO(b/187754252) UI polish, different icons based on light/dark context, etc
        mImageDrawable = (AnimatedVectorDrawable) getContext()
                .getDrawable(rotationButtonController.getIconResId());
        setImageDrawable(mImageDrawable);
        mImageDrawable.setCallback(this);
    }

    @Override
    public View getCurrentView() {
        return this;
    }

    @Override
    public boolean show() {
        setVisibility(VISIBLE);
        return true;
    }

    @Override
    public boolean hide() {
        setVisibility(GONE);
        return true;
    }

    @Override
    public boolean isVisible() {
        return getVisibility() == VISIBLE;
    }

    @Override
    public void updateIcon(int lightIconColor, int darkIconColor) {
        // TODO(b/187754252): UI Polish
    }

    @Override
    public void setOnClickListener(View.OnClickListener onClickListener) {
        super.setOnClickListener(onClickListener);
    }

    @Override
    public void setOnHoverListener(View.OnHoverListener onHoverListener) {
        super.setOnHoverListener(onHoverListener);
    }

    @Override
    public AnimatedVectorDrawable getImageDrawable() {
        return mImageDrawable;
    }

    @Override
    public void setDarkIntensity(float darkIntensity) {
        // TODO(b/187754252) UI polish
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        if (visibility != View.VISIBLE && mImageDrawable != null) {
            mImageDrawable.clearAnimationCallbacks();
            mImageDrawable.reset();
        }

        // Start the rotation animation once it becomes visible
        if (visibility == View.VISIBLE && mImageDrawable != null) {
            mImageDrawable.reset();
            mImageDrawable.start();
        }
    }

    @Override
    public boolean acceptRotationProposal() {
        return isAttachedToWindow();
    }
}
