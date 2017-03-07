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

package com.android.launcher3.folder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.support.v4.graphics.ColorUtils;
import android.util.Property;
import android.view.View;
import android.view.animation.AnimationUtils;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.util.Themes;

import java.util.List;

/**
 * Manages the opening and closing animations for a {@link Folder}.
 *
 * All of the animations are done in the Folder.
 * ie. When the user taps on the FolderIcon, we immediately hide the FolderIcon and show the Folder
 * in its place before starting the animation.
 */
public class FolderAnimationManager {

    private Folder mFolder;
    private FolderPagedView mContent;
    private GradientDrawable mFolderBackground;

    private FolderIcon mFolderIcon;
    private FolderIcon.PreviewBackground mPreviewBackground;

    private Context mContext;
    private Launcher mLauncher;

    private Animator mRevealAnimator;
    private final TimeInterpolator mOpeningInterpolator;
    private final TimeInterpolator mClosingInterpolator;

    private final FolderIcon.PreviewItemDrawingParams mTmpParams =
            new FolderIcon.PreviewItemDrawingParams(0, 0, 0, 0);

    private static final Property<View, Float> SCALE_PROPERTY =
            new Property<View, Float>(Float.class, "scale") {
                @Override
                public Float get(View view) {
                    return view.getScaleX();
                }

                @Override
                public void set(View view, Float scale) {
                    view.setScaleX(scale);
                    view.setScaleY(scale);
                }
            };

    private static final Property<List<BubbleTextView>, Integer> ITEMS_TEXT_COLOR_PROPERTY =
            new Property<List<BubbleTextView>, Integer>(Integer.class, "textColor") {
                @Override
                public Integer get(List<BubbleTextView> items) {
                    return items.get(0).getCurrentTextColor();
                }

                @Override
                public void set(List<BubbleTextView> items, Integer color) {
                    int size = items.size();

                    for (int i = 0; i < size; ++i) {
                        items.get(i).setTextColor(color);
                    }
                }
            };

    public FolderAnimationManager(Folder folder) {
        mFolder = folder;
        mContent = folder.mContent;
        mFolderBackground = (GradientDrawable) mFolder.getBackground();

        mFolderIcon = folder.mFolderIcon;
        mPreviewBackground = mFolderIcon.mBackground;

        mContext = folder.getContext();
        mLauncher = folder.mLauncher;

        mOpeningInterpolator = AnimationUtils.loadInterpolator(mContext,
                R.interpolator.folder_opening_interpolator);
        mClosingInterpolator = AnimationUtils.loadInterpolator(mContext,
                R.interpolator.folder_closing_interpolator);
    }

    public AnimatorSet getOpeningAnimator() {
        mFolder.setPivotX(0);
        mFolder.setPivotY(0);

        AnimatorSet a = getAnimatorSet(true /* isOpening */);
        a.setInterpolator(mOpeningInterpolator);
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mFolderIcon.setVisibility(View.INVISIBLE);
            }
        });
        return a;
    }

    public AnimatorSet getClosingAnimator() {
        AnimatorSet a = getAnimatorSet(false /* isOpening */);
        a.setInterpolator(mClosingInterpolator);
        return a;
    }

    /**
     * Prepares the Folder for animating between open / closed states.
     *
     * @param isOpening If true, return the animator set for the opening animation.
     */
    private AnimatorSet getAnimatorSet(final boolean isOpening) {
        final DragLayer.LayoutParams lp = (DragLayer.LayoutParams) mFolder.getLayoutParams();
        FolderIcon.PreviewLayoutRule rule = mFolderIcon.getLayoutRule();
        final List<BubbleTextView> itemsInPreview = mFolderIcon.getItemsToDisplay();

        // Match size/scale of icons in the preview
        float previewScale = rule.scaleForItem(0, itemsInPreview.size());
        float previewSize = rule.getIconSize() * previewScale;
        float folderScale = previewSize / itemsInPreview.get(0).getIconSize();

        final float initialScale = folderScale;
        final float finalScale = 1f;
        float scale = isOpening ? initialScale : finalScale;
        mFolder.setScaleX(scale);
        mFolder.setScaleY(scale);

        // Match position of the FolderIcon
        final Rect folderIconPos = new Rect();
        float scaleRelativeToDragLayer = mLauncher.getDragLayer()
                .getDescendantRectRelativeToSelf(mFolderIcon, folderIconPos);
        folderScale *= scaleRelativeToDragLayer;

        // We want to create a small X offset for the preview items, so that they follow their
        // expected path to their final locations. ie. an icon should not move right, if it's final
        // location is to its left. This value is arbitrarily defined.
        final int nudgeOffsetX = (int) (previewSize / 2);

        final int paddingOffsetX = (int) ((mFolder.getPaddingLeft() + mContent.getPaddingLeft())
                * folderScale);
        final int paddingOffsetY = (int) ((mFolder.getPaddingTop() + mContent.getPaddingTop())
                * folderScale);

        int initialX = folderIconPos.left + mFolderIcon.mBackground.getOffsetX() - paddingOffsetX
                - nudgeOffsetX;
        int initialY = folderIconPos.top + mFolderIcon.mBackground.getOffsetY() - paddingOffsetY;
        final float xDistance = initialX - lp.x;
        final float yDistance = initialY - lp.y;

        // Set up the Folder background.
        final int finalColor = Themes.getAttrColor(mContext, android.R.attr.colorPrimary);
        final int initialColor =
                ColorUtils.setAlphaComponent(finalColor, mPreviewBackground.getBackgroundAlpha());
        mFolderBackground.setColor(isOpening ? initialColor : finalColor);

        // Initialize the Folder items' text.
        final List<BubbleTextView> itemsOnCurrentPage = mFolder.getItemsOnCurrentPage();
        final int finalTextColor = Themes.getAttrColor(mContext, android.R.attr.textColorSecondary);
        ITEMS_TEXT_COLOR_PROPERTY.set(itemsOnCurrentPage, isOpening ? Color.TRANSPARENT
                : finalTextColor);

        // Create the animators.
        AnimatorSet a = LauncherAnimUtils.createAnimatorSet();
        a.setDuration(mFolder.mMaterialExpandDuration);

        ObjectAnimator translationX = isOpening
                ? ObjectAnimator.ofFloat(mFolder, View.TRANSLATION_X, xDistance, 0)
                : ObjectAnimator.ofFloat(mFolder, View.TRANSLATION_X, 0, xDistance);
        a.play(translationX);

        ObjectAnimator translationY = isOpening
                ? ObjectAnimator.ofFloat(mFolder, View.TRANSLATION_Y, yDistance, 0)
                : ObjectAnimator.ofFloat(mFolder, View.TRANSLATION_Y, 0, yDistance);
        a.play(translationY);

        ObjectAnimator scaleAnimator = isOpening
                ? ObjectAnimator.ofFloat(mFolder, SCALE_PROPERTY, initialScale, finalScale)
                : ObjectAnimator.ofFloat(mFolder, SCALE_PROPERTY, finalScale, initialScale);
        a.play(scaleAnimator);

        ObjectAnimator itemsTextColor = isOpening
                ? ObjectAnimator.ofArgb(itemsOnCurrentPage, ITEMS_TEXT_COLOR_PROPERTY,
                        Color.TRANSPARENT, finalTextColor)
                : ObjectAnimator.ofArgb(itemsOnCurrentPage, ITEMS_TEXT_COLOR_PROPERTY,
                        finalTextColor, Color.TRANSPARENT);
        a.play(itemsTextColor);

        ObjectAnimator backgroundColor = isOpening
                ? ObjectAnimator.ofArgb(mFolderBackground, "color", initialColor, finalColor)
                : ObjectAnimator.ofArgb(mFolderBackground, "color", finalColor, initialColor);
        a.play(backgroundColor);

        // Set up the reveal animation that clips the Folder.
        float stroke = mPreviewBackground.getStrokeWidth();
        int initialSize = (int) ((mFolderIcon.mBackground.getRadius() * 2 + stroke) / folderScale);
        int totalOffsetX = paddingOffsetX + Math.round(nudgeOffsetX / folderScale);
        int unscaledStroke = (int) Math.floor(stroke / folderScale);
        Rect startRect = new Rect(totalOffsetX + unscaledStroke, unscaledStroke,
                totalOffsetX + initialSize, initialSize);
        Rect endRect = new Rect(0, 0, lp.width, lp.height);
        a.play(getRevealAnimator(isOpening, initialSize / 2f, startRect, endRect));

        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                ITEMS_TEXT_COLOR_PROPERTY.set(itemsOnCurrentPage, finalTextColor);
            }
        });

        addPreviewItemAnimatorsToSet(a, isOpening, folderScale, nudgeOffsetX);
        return a;
    }

    private Animator getRevealAnimator(boolean isOpening, float circleRadius, Rect start,
            Rect end) {
        boolean revealIsRunning = mRevealAnimator != null && mRevealAnimator.isRunning();
        final float finalRadius = revealIsRunning
                ? ((RoundedRectRevealOutlineProvider) mFolder.getOutlineProvider()).getRadius()
                : Utilities.pxFromDp(2, mContext.getResources().getDisplayMetrics());
        if (revealIsRunning) {
            mRevealAnimator.cancel();
        }
        mRevealAnimator = new RoundedRectRevealOutlineProvider(circleRadius, finalRadius,
                start, end).createRevealAnimator(mFolder, !isOpening);
        return mRevealAnimator;
    }

    /**
     * Animate the items that are displayed in the preview.
     */
    private void addPreviewItemAnimatorsToSet(AnimatorSet animatorSet, boolean isOpening,
            final float folderScale, int nudgeOffsetX) {
        FolderIcon.PreviewLayoutRule rule = mFolderIcon.getLayoutRule();
        final List<BubbleTextView> itemsInPreview = mFolderIcon.getItemsToDisplay();
        final int numItemsInPreview = itemsInPreview.size();

        ShortcutAndWidgetContainer cwc = mContent.getPageAt(0).getShortcutsAndWidgets();
        for (int i = 0; i < numItemsInPreview; ++i) {
            final BubbleTextView btv = itemsInPreview.get(i);
            CellLayout.LayoutParams btvLp = (CellLayout.LayoutParams) btv.getLayoutParams();

            // Calculate the final values in the LayoutParams.
            btvLp.isLockedToGrid = true;
            cwc.setupLp(btv);

            // Match scale of icons in the preview.
            float previewScale = rule.scaleForItem(i, numItemsInPreview);
            float previewSize = rule.getIconSize() * previewScale;
            float iconScale = previewSize / itemsInPreview.get(i).getIconSize();

            final float initialScale = iconScale / folderScale;
            final float finalScale = 1f;
            float scale = isOpening ? initialScale : finalScale;
            btv.setScaleX(scale);
            btv.setScaleY(scale);

            // Match positions of the icons in the folder with their positions in the preview
            rule.computePreviewItemDrawingParams(i, numItemsInPreview, mTmpParams);
            // The PreviewLayoutRule assumes that the icon size takes up the entire width so we
            // offset by the actual size.
            int iconOffsetX = (int) ((btvLp.width - btv.getIconSize()) * iconScale) / 2;

            final int previewPosX =
                    (int) ((mTmpParams.transX - iconOffsetX + nudgeOffsetX) / folderScale);
            final int previewPosY = (int) (mTmpParams.transY / folderScale);

            final float xDistance = previewPosX - btvLp.x;
            final float yDistance = previewPosY - btvLp.y;

            ObjectAnimator translationX = isOpening
                    ? ObjectAnimator.ofFloat(btv, View.TRANSLATION_X, xDistance, 0)
                    : ObjectAnimator.ofFloat(btv, View.TRANSLATION_X, 0, xDistance);
            animatorSet.play(translationX);

            ObjectAnimator translationY = isOpening
                    ? ObjectAnimator.ofFloat(btv, View.TRANSLATION_Y, yDistance, 0)
                    : ObjectAnimator.ofFloat(btv, View.TRANSLATION_Y, 0, yDistance);
            animatorSet.play(translationY);

            ObjectAnimator scaleAnimator = isOpening
                    ? ObjectAnimator.ofFloat(btv, SCALE_PROPERTY, initialScale, finalScale)
                    : ObjectAnimator.ofFloat(btv, SCALE_PROPERTY, finalScale, initialScale);
            animatorSet.play(scaleAnimator);

            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    btv.setTranslationX(0.0f);
                    btv.setTranslationY(0.0f);
                    btv.setScaleX(1f);
                    btv.setScaleY(1f);
                }
            });
        }
    }
}
