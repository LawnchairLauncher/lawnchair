/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.ButtonDropTarget.TOOLTIP_DEFAULT;
import static com.android.launcher3.anim.AlphaUpdateListener.updateVisibility;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragController.DragListener;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.testing.TestProtocol;

import java.util.Arrays;

/*
 * The top bar containing various drop targets: Delete/App Info/Uninstall.
 */
public class DropTargetBar extends FrameLayout
        implements DragListener, Insettable {

    protected static final int DEFAULT_DRAG_FADE_DURATION = 175;
    protected static final TimeInterpolator DEFAULT_INTERPOLATOR = Interpolators.ACCEL;

    private final Runnable mFadeAnimationEndRunnable =
            () -> updateVisibility(DropTargetBar.this);

    @ViewDebug.ExportedProperty(category = "launcher")
    protected boolean mDeferOnDragEnd;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected boolean mVisible = false;

    private ButtonDropTarget[] mDropTargets;
    private ViewPropertyAnimator mCurrentAnimation;

    private boolean mIsVertical = true;

    public DropTargetBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DropTargetBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDropTargets = new ButtonDropTarget[getChildCount()];
        for (int i = 0; i < mDropTargets.length; i++) {
            mDropTargets[i] = (ButtonDropTarget) getChildAt(i);
            mDropTargets[i].setDropTargetBar(this);
        }
    }

    @Override
    public void setInsets(Rect insets) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = Launcher.getLauncher(getContext()).getDeviceProfile();
        mIsVertical = grid.isVerticalBarLayout();

        lp.leftMargin = insets.left;
        lp.topMargin = insets.top;
        lp.bottomMargin = insets.bottom;
        lp.rightMargin = insets.right;
        int tooltipLocation = TOOLTIP_DEFAULT;

        int horizontalMargin;
        if (grid.isTablet) {
            // XXX: If the icon size changes across orientations, we will have to take
            //      that into account here too.
            horizontalMargin = ((grid.widthPx - 2 * grid.edgeMarginPx
                    - (grid.inv.numColumns * grid.cellWidthPx))
                    / (2 * (grid.inv.numColumns + 1)))
                    + grid.edgeMarginPx;
        } else {
            horizontalMargin = getContext().getResources()
                    .getDimensionPixelSize(R.dimen.drop_target_bar_margin_horizontal);
        }
        lp.topMargin += grid.dropTargetBarTopMarginPx;
        lp.bottomMargin += grid.dropTargetBarBottomMarginPx;
        lp.width = grid.availableWidthPx - 2 * horizontalMargin;
        if (mIsVertical) {
            lp.leftMargin = (grid.widthPx - lp.width) / 2;
            lp.rightMargin = (grid.widthPx - lp.width) / 2;
        }
        lp.height = grid.dropTargetBarSizePx;
        lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;

        setLayoutParams(lp);
        for (ButtonDropTarget button : mDropTargets) {
            button.setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.dropTargetTextSizePx);
            button.setToolTipLocation(tooltipLocation);
        }
    }

    public void setup(DragController dragController) {
        dragController.addDragListener(this);
        for (int i = 0; i < mDropTargets.length; i++) {
            dragController.addDragListener(mDropTargets[i]);
            dragController.addDropTarget(mDropTargets[i]);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        int visibleCount = getVisibleButtonsCount();
        if (visibleCount > 0) {
            int availableWidth = width / visibleCount;
            boolean textVisible = true;
            for (ButtonDropTarget buttons : mDropTargets) {
                if (buttons.getVisibility() != GONE) {
                    textVisible = textVisible && !buttons.isTextTruncated(availableWidth);
                }
            }

            int widthSpec = MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST);
            int heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            for (ButtonDropTarget button : mDropTargets) {
                if (button.getVisibility() != GONE) {
                    button.setTextVisible(textVisible);
                    button.measure(widthSpec, heightSpec);
                }
            }
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int visibleCount = getVisibleButtonsCount();
        if (visibleCount == 0) {
            return;
        }

        Launcher launcher = Launcher.getLauncher(getContext());
        Workspace workspace = launcher.getWorkspace();
        DeviceProfile dp = launcher.getDeviceProfile();
        int buttonHorizontalPadding = dp.dropTargetHorizontalPaddingPx;
        int buttonVerticalPadding = dp.dropTargetVerticalPaddingPx;
        int barCenter = (right - left) / 2;

        ButtonDropTarget[] visibleButtons = Arrays.stream(mDropTargets)
                .filter(b -> b.getVisibility() != GONE)
                .toArray(ButtonDropTarget[]::new);
        Arrays.stream(visibleButtons).forEach(
                b -> b.setPadding(buttonHorizontalPadding, buttonVerticalPadding,
                        buttonHorizontalPadding, buttonVerticalPadding));

        if (visibleCount == 1) {
            ButtonDropTarget button = visibleButtons[0];
            button.layout(barCenter - (button.getMeasuredWidth() / 2), 0,
                    barCenter + (button.getMeasuredWidth() / 2), button.getMeasuredHeight());
        } else if (visibleCount == 2) {
            int buttonGap = dp.dropTargetGapPx;

            if (dp.isTwoPanels) {
                ButtonDropTarget leftButton = visibleButtons[0];
                leftButton.layout(barCenter - leftButton.getMeasuredWidth() - (buttonGap / 2), 0,
                        barCenter - (buttonGap / 2), leftButton.getMeasuredHeight());

                ButtonDropTarget rightButton = visibleButtons[1];
                rightButton.layout(barCenter + (buttonGap / 2), 0,
                        barCenter + rightButton.getMeasuredWidth() + (buttonGap / 2),
                        rightButton.getMeasuredHeight());
            } else if (dp.isTablet) {
                int numberOfMargins = visibleCount - 1;
                int buttonWidths = Arrays.stream(mDropTargets)
                        .filter(b -> b.getVisibility() != GONE)
                        .mapToInt(ButtonDropTarget::getMeasuredWidth)
                        .sum();
                int totalWidth = buttonWidths + (numberOfMargins * buttonGap);
                int buttonsStartMargin = barCenter - (totalWidth / 2);

                int start = buttonsStartMargin;
                for (ButtonDropTarget button : visibleButtons) {
                    int margin = (start != buttonsStartMargin) ? buttonGap : 0;
                    button.layout(start + margin, 0, start + margin + button.getMeasuredWidth(),
                            button.getMeasuredHeight());
                    start += button.getMeasuredWidth() + margin;
                }
            } else if (mIsVertical) {
                // Center buttons over workspace, not screen.
                int verticalCenter = (workspace.getRight() - workspace.getLeft()) / 2;
                ButtonDropTarget leftButton = visibleButtons[0];
                leftButton.layout(verticalCenter - leftButton.getMeasuredWidth() - (buttonGap / 2),
                        0, verticalCenter - (buttonGap / 2), leftButton.getMeasuredHeight());

                ButtonDropTarget rightButton = visibleButtons[1];
                rightButton.layout(verticalCenter + (buttonGap / 2), 0,
                        verticalCenter + rightButton.getMeasuredWidth() + (buttonGap / 2),
                        rightButton.getMeasuredHeight());
            } else if (dp.isPhone) {
                // Buttons aligned to outer edges of scaled workspace.
                float shrunkTop = dp.getWorkspaceSpringLoadShrunkTop();
                float shrunkBottom = dp.getWorkspaceSpringLoadShrunkBottom();
                float scale =
                        (shrunkBottom - shrunkTop) / launcher.getWorkspace().getNormalChildHeight();
                int workspaceWidth = (int) (launcher.getWorkspace().getNormalChildWidth() * scale);
                int start = barCenter - (workspaceWidth / 2);
                int end = barCenter + (workspaceWidth / 2);

                ButtonDropTarget leftButton = visibleButtons[0];
                ButtonDropTarget rightButton = visibleButtons[1];

                // If the text within the buttons is too long, the buttons can overlap
                int overlap = start + leftButton.getMeasuredWidth() + rightButton.getMeasuredWidth()
                        - end;
                if (overlap > 0) {
                    start -= overlap / 2;
                    end += overlap / 2;
                }

                leftButton.layout(start, 0, start + leftButton.getMeasuredWidth(),
                        leftButton.getMeasuredHeight());
                rightButton.layout(end - rightButton.getMeasuredWidth(), 0, end,
                        rightButton.getMeasuredHeight());
            }
        }
    }

    private int getVisibleButtonsCount() {
        int visibleCount = 0;
        for (ButtonDropTarget buttons : mDropTargets) {
            if (buttons.getVisibility() != GONE) {
                visibleCount++;
            }
        }
        return visibleCount;
    }

    public void animateToVisibility(boolean isVisible) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.NO_DROP_TARGET, "8");
        }
        if (mVisible != isVisible) {
            mVisible = isVisible;

            // Cancel any existing animation
            if (mCurrentAnimation != null) {
                mCurrentAnimation.cancel();
                mCurrentAnimation = null;
            }

            float finalAlpha = mVisible ? 1 : 0;
            if (Float.compare(getAlpha(), finalAlpha) != 0) {
                setVisibility(View.VISIBLE);
                mCurrentAnimation = animate().alpha(finalAlpha)
                        .setInterpolator(DEFAULT_INTERPOLATOR)
                        .setDuration(DEFAULT_DRAG_FADE_DURATION)
                        .withEndAction(mFadeAnimationEndRunnable);
            }

        }
    }

    /*
     * DragController.DragListener implementation
     */
    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.NO_DROP_TARGET, "7");
        }
        animateToVisibility(true);
    }

    /**
     * This is called to defer hiding the delete drop target until the drop animation has completed,
     * instead of hiding immediately when the drag has ended.
     */
    protected void deferOnDragEnd() {
        mDeferOnDragEnd = true;
    }

    @Override
    public void onDragEnd() {
        if (!mDeferOnDragEnd) {
            animateToVisibility(false);
        } else {
            mDeferOnDragEnd = false;
        }
    }

    public ButtonDropTarget[] getDropTargets() {
        return mDropTargets;
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (TestProtocol.sDebugTracing) {
            if (visibility == VISIBLE) {
                Log.d(TestProtocol.NO_DROP_TARGET, "9");
            } else {
                Log.d(TestProtocol.NO_DROP_TARGET, "Hiding drop target", new Exception());
            }
        }
    }
}
