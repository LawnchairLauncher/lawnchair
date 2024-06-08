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

import static android.view.View.ALPHA;

import static com.android.launcher3.BubbleTextView.TEXT_ALPHA_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.Property;
import android.view.View;
import android.view.animation.AnimationUtils;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PropertyResetListener;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.graphics.IconShape;
import com.android.launcher3.graphics.IconShape.ShapeDelegate;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.BaseDragLayer;

import java.util.List;

/**
 * Manages the opening and closing animations for a {@link Folder}.
 *
 * All of the animations are done in the Folder.
 * ie. When the user taps on the FolderIcon, we immediately hide the FolderIcon and show the Folder
 * in its place before starting the animation.
 */
public class FolderAnimationManager {

    private static final int FOLDER_NAME_ALPHA_DURATION = 32;
    private static final int LARGE_FOLDER_FOOTER_DURATION = 128;

    private Folder mFolder;
    private FolderPagedView mContent;
    private GradientDrawable mFolderBackground;

    private FolderIcon mFolderIcon;
    private PreviewBackground mPreviewBackground;

    private Context mContext;

    private final boolean mIsOpening;

    private final int mDuration;
    private final int mDelay;

    private final TimeInterpolator mFolderInterpolator;
    private final TimeInterpolator mLargeFolderPreviewItemOpenInterpolator;
    private final TimeInterpolator mLargeFolderPreviewItemCloseInterpolator;

    private final PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0, 0, 0);
    private final FolderGridOrganizer mPreviewVerifier;

    private ObjectAnimator mBgColorAnimator;

    private DeviceProfile mDeviceProfile;

    public FolderAnimationManager(Folder folder, boolean isOpening) {
        mFolder = folder;
        mContent = folder.mContent;
        mFolderBackground = (GradientDrawable) mFolder.getBackground();

        mFolderIcon = folder.mFolderIcon;
        mPreviewBackground = mFolderIcon.mBackground;

        mContext = folder.getContext();
        mDeviceProfile = folder.mActivityContext.getDeviceProfile();
        mPreviewVerifier = new FolderGridOrganizer(mDeviceProfile);

        mIsOpening = isOpening;

        Resources res = mContent.getResources();
        mDuration = res.getInteger(R.integer.config_materialFolderExpandDuration);
        mDelay = res.getInteger(R.integer.config_folderDelay);

        mFolderInterpolator = AnimationUtils.loadInterpolator(mContext,
                R.interpolator.standard_interpolator);
        mLargeFolderPreviewItemOpenInterpolator = AnimationUtils.loadInterpolator(mContext,
                R.interpolator.large_folder_preview_item_open_interpolator);
        mLargeFolderPreviewItemCloseInterpolator = AnimationUtils.loadInterpolator(mContext,
                R.interpolator.standard_accelerate_interpolator);
    }

    /**
     * Returns the animator that changes the background color.
     */
    public ObjectAnimator getBgColorAnimator() {
        return mBgColorAnimator;
    }

    /**
     * Prepares the Folder for animating between open / closed states.
     */
    public AnimatorSet getAnimator() {
        final BaseDragLayer.LayoutParams lp =
                (BaseDragLayer.LayoutParams) mFolder.getLayoutParams();
        mFolderIcon.getPreviewItemManager().recomputePreviewDrawingParams();
        ClippedFolderIconLayoutRule rule = mFolderIcon.getLayoutRule();
        final List<View> itemsInPreview = getPreviewIconsOnPage(0);

        // Match position of the FolderIcon
        final Rect folderIconPos = new Rect();
        float scaleRelativeToDragLayer = mFolder.mActivityContext.getDragLayer()
                .getDescendantRectRelativeToSelf(mFolderIcon, folderIconPos);
        int scaledRadius = mPreviewBackground.getScaledRadius();
        float initialSize = (scaledRadius * 2) * scaleRelativeToDragLayer;

        // Match size/scale of icons in the preview
        float previewScale = rule.scaleForItem(itemsInPreview.size());
        float previewSize = rule.getIconSize() * previewScale;
        float baseIconSize = getBubbleTextView(itemsInPreview.get(0)).getIconSize();
        float initialScale = previewSize / baseIconSize * scaleRelativeToDragLayer;
        final float finalScale = 1f;
        float scale = mIsOpening ? initialScale : finalScale;
        mFolder.setPivotX(0);
        mFolder.setPivotY(0);

        // Scale the contents of the folder.
        mFolder.mContent.setScaleX(scale);
        mFolder.mContent.setScaleY(scale);
        mFolder.mContent.setPivotX(0);
        mFolder.mContent.setPivotY(0);
        mFolder.mFooter.setScaleX(scale);
        mFolder.mFooter.setScaleY(scale);
        mFolder.mFooter.setPivotX(0);
        mFolder.mFooter.setPivotY(0);

        // We want to create a small X offset for the preview items, so that they follow their
        // expected path to their final locations. ie. an icon should not move right, if it's final
        // location is to its left. This value is arbitrarily defined.
        int previewItemOffsetX = (int) (previewSize / 2);
        if (Utilities.isRtl(mContext.getResources())) {
            previewItemOffsetX = (int) (lp.width * initialScale - initialSize - previewItemOffsetX);
        }

        final int paddingOffsetX = (int) (mContent.getPaddingLeft() * initialScale);
        final int paddingOffsetY = (int) (mContent.getPaddingTop() * initialScale);

        int initialX = folderIconPos.left + mFolder.getPaddingLeft()
                + Math.round(mPreviewBackground.getOffsetX() * scaleRelativeToDragLayer)
                - paddingOffsetX - previewItemOffsetX;
        int initialY = folderIconPos.top + mFolder.getPaddingTop()
                + Math.round(mPreviewBackground.getOffsetY() * scaleRelativeToDragLayer)
                - paddingOffsetY;
        final float xDistance = initialX - lp.x;
        final float yDistance = initialY - lp.y;

        // Set up the Folder background.
        final int initialColor = Themes.getAttrColor(mContext, R.attr.folderPreviewColor);
        final int finalColor = Themes.getAttrColor(mContext, R.attr.folderBackgroundColor);

        mFolderBackground.mutate();
        mFolderBackground.setColor(mIsOpening ? initialColor : finalColor);

        // Set up the reveal animation that clips the Folder.
        int totalOffsetX = paddingOffsetX + previewItemOffsetX;
        Rect startRect = new Rect(totalOffsetX,
                paddingOffsetY,
                Math.round((totalOffsetX + initialSize)),
                Math.round((paddingOffsetY + initialSize)));
        Rect endRect = new Rect(0, 0, lp.width, lp.height);
        float finalRadius = mFolderBackground.getCornerRadius();

        // Create the animators.
        AnimatorSet a = new AnimatorSet();

        // Initialize the Folder items' text.
        PropertyResetListener colorResetListener =
                new PropertyResetListener<>(TEXT_ALPHA_PROPERTY, 1f);
        for (View icon : mFolder.getItemsOnPage(mFolder.mContent.getCurrentPage())) {
            BubbleTextView titleText = getBubbleTextView(icon);
            if (mIsOpening) {
                titleText.setTextVisibility(false);
            }
            ObjectAnimator anim = titleText.createTextAlphaAnimator(mIsOpening);
            anim.addListener(colorResetListener);
            play(a, anim);
        }

        mBgColorAnimator = getAnimator(mFolderBackground, "color", initialColor, finalColor);
        play(a, mBgColorAnimator);
        play(a, getAnimator(mFolder, View.TRANSLATION_X, xDistance, 0f));
        play(a, getAnimator(mFolder, View.TRANSLATION_Y, yDistance, 0f));
        play(a, getAnimator(mFolder.mContent, SCALE_PROPERTY, initialScale, finalScale));
        play(a, getAnimator(mFolder.mFooter, SCALE_PROPERTY, initialScale, finalScale));

        final int footerAlphaDuration;
        final int footerStartDelay;
        if (isLargeFolder()) {
            if (mIsOpening) {
                mFolder.mFooter.setAlpha(0);
                footerAlphaDuration = LARGE_FOLDER_FOOTER_DURATION;
                footerStartDelay = mDuration - footerAlphaDuration;
            } else {
                footerAlphaDuration = 0;
                footerStartDelay = 0;
            }
        } else {
            footerStartDelay = 0;
            footerAlphaDuration = mDuration;
        }
        play(a, getAnimator(mFolder.mFooter, ALPHA, 0, 1f), footerStartDelay, footerAlphaDuration);

        ShapeDelegate shapeDelegate = IconShape.INSTANCE.get(mContext).getShape();
        // Create reveal animator for the folder background
        play(a, shapeDelegate.createRevealAnimator(
                mFolder, startRect, endRect, finalRadius, !mIsOpening));

        // Create reveal animator for the folder content (capture the top 4 icons 2x2)
        int width = mDeviceProfile.folderCellLayoutBorderSpacePx.x
                + mDeviceProfile.folderCellWidthPx * 2;
        int rtlExtraWidth = 0;
        int height = mDeviceProfile.folderCellLayoutBorderSpacePx.y
                + mDeviceProfile.folderCellHeightPx * 2;
        int page = mIsOpening ? mContent.getCurrentPage() : mContent.getDestinationPage();
        // In RTL we want to move to the last 2 columns of icons in the folder.
        if (Utilities.isRtl(mContext.getResources())) {
            page = (mContent.getPageCount() - 1) - page;
            CellLayout clAtPage = mContent.getPageAt(page);
            if (clAtPage != null) {
                int numExtraRows = clAtPage.getCountX() - 2;
                rtlExtraWidth = (int) Math.max(numExtraRows * (mDeviceProfile.folderCellWidthPx
                        + mDeviceProfile.folderCellLayoutBorderSpacePx.x), rtlExtraWidth);
            }
        }
        int left = mContent.getPaddingLeft() + page * lp.width;
        Rect contentStart = new Rect(
                left + rtlExtraWidth,
                0,
                left + width + mContent.getPaddingRight() + rtlExtraWidth,
                height);
        Rect contentEnd = new Rect(left, 0, left + lp.width, lp.height);
        play(a, shapeDelegate.createRevealAnimator(
                mFolder.getContent(), contentStart, contentEnd, finalRadius, !mIsOpening));

        // Fade in the folder name, as the text can overlap the icons when grid size is small.
        mFolder.mFolderName.setAlpha(mIsOpening ? 0f : 1f);
        play(a, getAnimator(mFolder.mFolderName, View.ALPHA, 0, 1),
                mIsOpening ? FOLDER_NAME_ALPHA_DURATION : 0,
                mIsOpening ? mDuration - FOLDER_NAME_ALPHA_DURATION : FOLDER_NAME_ALPHA_DURATION);

        // Translate the footer so that it tracks the bottom of the content.
        float normalHeight = mFolder.getContentAreaHeight();
        float scaledHeight = normalHeight * initialScale;
        float diff = normalHeight - scaledHeight;
        play(a, getAnimator(mFolder.mFooter, View.TRANSLATION_Y, -diff, 0f));

        // Animate the elevation midway so that the shadow is not noticeable in the background.
        int midDuration = mDuration / 2;
        Animator z = getAnimator(mFolder, View.TRANSLATION_Z, -mFolder.getElevation(), 0);
        play(a, z, mIsOpening ? midDuration : 0, midDuration);

        // Store clip variables.
        // Because {@link #onAnimationStart} and {@link #onAnimationEnd} callbacks are sent to
        // message queue and executed on separate frame, we should save states in
        // {@link #onAnimationStart} instead of before creating animator, so that cancelling
        // animation A and restarting animation B allows A to reset states in
        // {@link #onAnimationEnd} before B reads new UI state from {@link #onAnimationStart}.
        a.addListener(new AnimatorListenerAdapter() {
            private CellLayout mCellLayout;

            private boolean mFolderClipChildren;
            private boolean mFolderClipToPadding;
            private boolean mContentClipChildren;
            private boolean mContentClipToPadding;
            private boolean mCellLayoutClipChildren;
            private boolean mCellLayoutClipPadding;

            @Override
            public void onAnimationStart(Animator animator) {
                super.onAnimationStart(animator);
                mCellLayout = mContent.getCurrentCellLayout();
                mFolderClipChildren = mFolder.getClipChildren();
                mFolderClipToPadding = mFolder.getClipToPadding();
                mContentClipChildren = mContent.getClipChildren();
                mContentClipToPadding = mContent.getClipToPadding();
                mCellLayoutClipChildren = mCellLayout.getClipChildren();
                mCellLayoutClipPadding = mCellLayout.getClipToPadding();

                mFolder.setClipChildren(false);
                mFolder.setClipToPadding(false);
                mContent.setClipChildren(false);
                mContent.setClipToPadding(false);
                mCellLayout.setClipChildren(false);
                mCellLayout.setClipToPadding(false);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mFolder.setTranslationX(0.0f);
                mFolder.setTranslationY(0.0f);
                mFolder.setTranslationZ(0.0f);
                mFolder.mContent.setScaleX(1f);
                mFolder.mContent.setScaleY(1f);
                mFolder.mFooter.setScaleX(1f);
                mFolder.mFooter.setScaleY(1f);
                mFolder.mFooter.setTranslationX(0f);
                mFolder.mFolderName.setAlpha(1f);

                mFolder.setClipChildren(mFolderClipChildren);
                mFolder.setClipToPadding(mFolderClipToPadding);
                mContent.setClipChildren(mContentClipChildren);
                mContent.setClipToPadding(mContentClipToPadding);
                mCellLayout.setClipChildren(mCellLayoutClipChildren);
                mCellLayout.setClipToPadding(mCellLayoutClipPadding);
            }
        });

        // We set the interpolator on all current child animators here, because the preview item
        // animators may use a different interpolator.
        for (Animator animator : a.getChildAnimations()) {
            animator.setInterpolator(mFolderInterpolator);
        }

        int radiusDiff = scaledRadius - mPreviewBackground.getRadius();
        addPreviewItemAnimators(a, initialScale / scaleRelativeToDragLayer,
                // Background can have a scaled radius in drag and drop mode, so we need to add the
                // difference to keep the preview items centered.
                (int) (previewItemOffsetX / scaleRelativeToDragLayer) + radiusDiff, radiusDiff);
        return a;
    }

    /**
     * Returns the list of "preview items" on {@param page}.
     */
    private List<View> getPreviewIconsOnPage(int page) {
        return mPreviewVerifier.setFolderInfo(mFolder.mInfo)
                .previewItemsForPage(page, mFolder.getIconsInReadingOrder());
    }

    /**
     * Animate the items on the current page.
     */
    private void addPreviewItemAnimators(AnimatorSet animatorSet, final float folderScale,
            int previewItemOffsetX, int previewItemOffsetY) {
        ClippedFolderIconLayoutRule rule = mFolderIcon.getLayoutRule();
        boolean isOnFirstPage = mFolder.mContent.getCurrentPage() == 0;
        final List<View> itemsInPreview = getPreviewIconsOnPage(
                isOnFirstPage ? 0 : mFolder.mContent.getCurrentPage());
        final int numItemsInPreview = itemsInPreview.size();
        final int numItemsInFirstPagePreview = isOnFirstPage
                ? numItemsInPreview : MAX_NUM_ITEMS_IN_PREVIEW;

        TimeInterpolator previewItemInterpolator = getPreviewItemInterpolator();

        ShortcutAndWidgetContainer cwc = mContent.getPageAt(0).getShortcutsAndWidgets();
        for (int i = 0; i < numItemsInPreview; ++i) {
            final View v = itemsInPreview.get(i);
            CellLayoutLayoutParams vLp = (CellLayoutLayoutParams) v.getLayoutParams();

            // Calculate the final values in the LayoutParams.
            vLp.isLockedToGrid = true;
            cwc.setupLp(v);

            // Match scale of icons in the preview of the items on the first page.
            float previewScale = rule.scaleForItem(numItemsInFirstPagePreview);
            float previewSize = rule.getIconSize() * previewScale;
            float baseIconSize = getBubbleTextView(v).getIconSize();
            float iconScale = previewSize / baseIconSize;

            final float initialScale = iconScale / folderScale;
            final float finalScale = 1f;
            float scale = mIsOpening ? initialScale : finalScale;
            v.setScaleX(scale);
            v.setScaleY(scale);

            // Match positions of the icons in the folder with their positions in the preview
            rule.computePreviewItemDrawingParams(i, numItemsInFirstPagePreview, mTmpParams);
            // The PreviewLayoutRule assumes that the icon size takes up the entire width so we
            // offset by the actual size.
            int iconOffsetX = (int) ((vLp.width - baseIconSize) * iconScale) / 2;

            final int previewPosX =
                    (int) ((mTmpParams.transX - iconOffsetX + previewItemOffsetX) / folderScale);
            final float paddingTop = v.getPaddingTop() * iconScale;
            final int previewPosY = (int) ((mTmpParams.transY + previewItemOffsetY - paddingTop)
                    / folderScale);

            final float xDistance = previewPosX - vLp.x;
            final float yDistance = previewPosY - vLp.y;

            Animator translationX = getAnimator(v, View.TRANSLATION_X, xDistance, 0f);
            translationX.setInterpolator(previewItemInterpolator);
            play(animatorSet, translationX);

            Animator translationY = getAnimator(v, View.TRANSLATION_Y, yDistance, 0f);
            translationY.setInterpolator(previewItemInterpolator);
            play(animatorSet, translationY);

            Animator scaleAnimator = getAnimator(v, SCALE_PROPERTY, initialScale, finalScale);
            scaleAnimator.setInterpolator(previewItemInterpolator);
            play(animatorSet, scaleAnimator);

            if (mFolder.getItemCount() > MAX_NUM_ITEMS_IN_PREVIEW) {
                // These delays allows the preview items to move as part of the Folder's motion,
                // and its only necessary for large folders because of differing interpolators.
                int delay = mIsOpening ? mDelay : mDelay * 2;
                if (mIsOpening) {
                    translationX.setStartDelay(delay);
                    translationY.setStartDelay(delay);
                    scaleAnimator.setStartDelay(delay);
                }
                translationX.setDuration(translationX.getDuration() - delay);
                translationY.setDuration(translationY.getDuration() - delay);
                scaleAnimator.setDuration(scaleAnimator.getDuration() - delay);
            }

            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    // Necessary to initialize values here because of the start delay.
                    if (mIsOpening) {
                        v.setTranslationX(xDistance);
                        v.setTranslationY(yDistance);
                        v.setScaleX(initialScale);
                        v.setScaleY(initialScale);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    v.setTranslationX(0.0f);
                    v.setTranslationY(0.0f);
                    v.setScaleX(1f);
                    v.setScaleY(1f);
                }
            });
        }
    }

    private void play(AnimatorSet as, Animator a) {
        play(as, a, a.getStartDelay(), mDuration);
    }

    private void play(AnimatorSet as, Animator a, long startDelay, int duration) {
        a.setStartDelay(startDelay);
        a.setDuration(duration);
        as.play(a);
    }

    private boolean isLargeFolder() {
        return mFolder.getItemCount() > MAX_NUM_ITEMS_IN_PREVIEW;
    }

    private TimeInterpolator getPreviewItemInterpolator() {
        if (isLargeFolder()) {
            // With larger folders, we want the preview items to reach their final positions faster
            // (when opening) and later (when closing) so that they appear aligned with the rest of
            // the folder items when they are both visible.
            return mIsOpening
                    ? mLargeFolderPreviewItemOpenInterpolator
                    : mLargeFolderPreviewItemCloseInterpolator;
        }
        return mFolderInterpolator;
    }

    private Animator getAnimator(View view, Property property, float v1, float v2) {
        return mIsOpening
                ? ObjectAnimator.ofFloat(view, property, v1, v2)
                : ObjectAnimator.ofFloat(view, property, v2, v1);
    }

    private ObjectAnimator getAnimator(GradientDrawable drawable, String property, int v1, int v2) {
        return mIsOpening
                ? ObjectAnimator.ofArgb(drawable, property, v1, v2)
                : ObjectAnimator.ofArgb(drawable, property, v2, v1);
    }

    /**
     * Gets the {@link com.android.launcher3.BubbleTextView} from an icon. In some cases the
     * BubbleTextView is the whole icon itself, while in others it is contained within the view and
     * only serves to store the title text.
     */
    private BubbleTextView getBubbleTextView(View v) {
        return v instanceof AppPairIcon
                ? ((AppPairIcon) v).getTitleTextView()
                : (BubbleTextView) v;
    }
}
