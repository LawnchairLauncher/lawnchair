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
package com.android.launcher3.widget;

import static com.android.launcher3.anim.Interpolators.scrollInterpolatorForVelocity;
import static com.android.launcher3.logging.LoggerUtils.newContainerTarget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.animation.Interpolator;
import android.widget.Toast;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.graphics.GradientView;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.Themes;

/**
 * Base class for various widgets popup
 */
abstract class BaseWidgetSheet extends AbstractFloatingView
        implements OnClickListener, OnLongClickListener, DragSource, SwipeDetector.Listener {


    protected static Property<BaseWidgetSheet, Float> TRANSLATION_SHIFT =
            new Property<BaseWidgetSheet, Float>(Float.class, "translationShift") {

                @Override
                public Float get(BaseWidgetSheet view) {
                    return view.mTranslationShift;
                }

                @Override
                public void set(BaseWidgetSheet view, Float value) {
                    view.setTranslationShift(value);
                }
            };
    protected static final float TRANSLATION_SHIFT_CLOSED = 1f;
    protected static final float TRANSLATION_SHIFT_OPENED = 0f;

    /* Touch handling related member variables. */
    private Toast mWidgetInstructionToast;

    protected final Launcher mLauncher;
    protected final SwipeDetector mSwipeDetector;
    protected final ObjectAnimator mOpenCloseAnimator;

    protected View mContent;
    protected GradientView mGradientView;
    protected Interpolator mScrollInterpolator;

    // range [0, 1], 0=> completely open, 1=> completely closed
    protected float mTranslationShift = TRANSLATION_SHIFT_CLOSED;

    protected boolean mNoIntercept;

    public BaseWidgetSheet(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);

        mScrollInterpolator = Interpolators.SCROLL_CUBIC;
        mSwipeDetector = new SwipeDetector(context, this, SwipeDetector.VERTICAL);

        mOpenCloseAnimator = LauncherAnimUtils.ofPropertyValuesHolder(this);
        mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mSwipeDetector.finishedScrolling();
            }
        });
    }

    @Override
    public final void onClick(View v) {
        // Let the user know that they have to long press to add a widget
        if (mWidgetInstructionToast != null) {
            mWidgetInstructionToast.cancel();
        }

        CharSequence msg = Utilities.wrapForTts(
                getContext().getText(R.string.long_press_widget_to_add),
                getContext().getString(R.string.long_accessible_way_to_add));
        mWidgetInstructionToast = Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT);
        mWidgetInstructionToast.show();
    }

    @Override
    public final boolean onLongClick(View v) {
        if (!mLauncher.isDraggingEnabled()) return false;

        if (v instanceof WidgetCell) {
            return beginDraggingWidget((WidgetCell) v);
        }
        return true;
    }

    protected void setTranslationShift(float translationShift) {
        mTranslationShift = translationShift;
        mGradientView.setAlpha(1 - mTranslationShift);
        mContent.setTranslationY(mTranslationShift * mContent.getHeight());
    }

    private boolean beginDraggingWidget(WidgetCell v) {
        // Get the widget preview as the drag representation
        WidgetImageView image = v.getWidgetView();

        // If the ImageView doesn't have a drawable yet, the widget preview hasn't been loaded and
        // we abort the drag.
        if (image.getBitmap() == null) {
            return false;
        }

        int[] loc = new int[2];
        mLauncher.getDragLayer().getLocationInDragLayer(image, loc);

        new PendingItemDragHelper(v).startDrag(
                image.getBitmapBounds(), image.getBitmap().getWidth(), image.getWidth(),
                new Point(loc[0], loc[1]), this, new DragOptions());
        close(true);
        return true;
    }

    //
    // Drag related handling methods that implement {@link DragSource} interface.
    //

    @Override
    public void onDropCompleted(View target, DragObject d, boolean success) { }


    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_UP && !mNoIntercept) {
            // If we got ACTION_UP without ever returning true on intercept,
            // the user never started dragging the bottom sheet.
            if (!mLauncher.getDragLayer().isEventOverView(mContent, ev)) {
                close(true);
                return false;
            }
        }

        if (mNoIntercept) {
            return false;
        }

        int directionsToDetectScroll = mSwipeDetector.isIdleState() ?
                SwipeDetector.DIRECTION_NEGATIVE : 0;
        mSwipeDetector.setDetectableScrollConditions(
                directionsToDetectScroll, false);
        mSwipeDetector.onTouchEvent(ev);
        return mSwipeDetector.isDraggingOrSettling();
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return mSwipeDetector.onTouchEvent(ev);
    }

    /* SwipeDetector.Listener */

    @Override
    public void onDragStart(boolean start) { }

    @Override
    public boolean onDrag(float displacement, float velocity) {
        float range = mContent.getHeight();
        displacement = Utilities.boundToRange(displacement, 0, range);
        setTranslationShift(displacement / range);
        return true;
    }

    @Override
    public void onDragEnd(float velocity, boolean fling) {
        if ((fling && velocity > 0) || mTranslationShift > 0.5f) {
            mScrollInterpolator = scrollInterpolatorForVelocity(velocity);
            mOpenCloseAnimator.setDuration(SwipeDetector.calculateDuration(
                    velocity, TRANSLATION_SHIFT_CLOSED - mTranslationShift));
            close(true);
        } else {
            mOpenCloseAnimator.setValues(PropertyValuesHolder.ofFloat(
                    TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED));
            mOpenCloseAnimator.setDuration(
                    SwipeDetector.calculateDuration(velocity, mTranslationShift))
                    .setInterpolator(Interpolators.DEACCEL);
            mOpenCloseAnimator.start();
        }
    }

    protected void handleClose(boolean animate, long defaultDuration) {
        if (!mIsOpen || mOpenCloseAnimator.isRunning()) {
            return;
        }
        if (animate) {
            mOpenCloseAnimator.setValues(
                    PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_CLOSED));
            mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onCloseComplete();
                }
            });
            if (mSwipeDetector.isIdleState()) {
                mOpenCloseAnimator
                        .setDuration(defaultDuration)
                        .setInterpolator(Interpolators.ACCEL);
            } else {
                mOpenCloseAnimator.setInterpolator(mScrollInterpolator);
            }
            mOpenCloseAnimator.start();
        } else {
            setTranslationShift(TRANSLATION_SHIFT_CLOSED);
            onCloseComplete();
        }
    }

    protected void onCloseComplete() {
        mIsOpen = false;
        mLauncher.getDragLayer().removeView(this);
        mLauncher.getSystemUiController().updateUiState(
                SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET, 0);
    }

    protected void setupNavBarColor() {
        boolean isSheetDark = Themes.getAttrBoolean(mLauncher, R.attr.isMainColorDark);
        mLauncher.getSystemUiController().updateUiState(
                SystemUiController.UI_STATE_WIDGET_BOTTOM_SHEET,
                isSheetDark ? SystemUiController.FLAG_DARK_NAV : SystemUiController.FLAG_LIGHT_NAV);
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        targetParent.containerType = ContainerType.WIDGETS;
        targetParent.cardinality = getElementsRowCount();
    }

    @Override
    public final void logActionCommand(int command) {
        Target target = newContainerTarget(ContainerType.WIDGETS);
        target.cardinality = getElementsRowCount();
        mLauncher.getUserEventDispatcher().logActionCommand(command, target);
    }

    protected abstract int getElementsRowCount();

}
