/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.bubbles;

import static android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_BUBBLES;
import static com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.wm.shell.bubbles.BubblePositioner.MAX_HEIGHT;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.IntProperty;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.window.ScreenCapture;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.policy.ScreenDecorationsUtils;
import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.common.AlphaOptimizedButton;
import com.android.wm.shell.common.TriangleShape;
import com.android.wm.shell.taskview.TaskView;

import java.io.PrintWriter;

/**
 * Container for the expanded bubble view, handles rendering the caret and settings icon.
 */
public class BubbleExpandedView extends LinearLayout {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "BubbleExpandedView" : TAG_BUBBLES;

    /** {@link IntProperty} for updating bottom clip */
    public static final IntProperty<BubbleExpandedView> BOTTOM_CLIP_PROPERTY =
            new IntProperty<BubbleExpandedView>("bottomClip") {
                @Override
                public void setValue(BubbleExpandedView expandedView, int value) {
                    expandedView.setBottomClip(value);
                }

                @Override
                public Integer get(BubbleExpandedView expandedView) {
                    return expandedView.mBottomClip;
                }
            };

    /** {@link FloatProperty} for updating taskView or overflow alpha */
    public static final FloatProperty<BubbleExpandedView> CONTENT_ALPHA =
            new FloatProperty<BubbleExpandedView>("contentAlpha") {
                @Override
                public void setValue(BubbleExpandedView expandedView, float value) {
                    expandedView.setContentAlpha(value);
                }

                @Override
                public Float get(BubbleExpandedView expandedView) {
                    return expandedView.getContentAlpha();
                }
            };

    /** {@link FloatProperty} for updating background and pointer alpha */
    public static final FloatProperty<BubbleExpandedView> BACKGROUND_ALPHA =
            new FloatProperty<BubbleExpandedView>("backgroundAlpha") {
                @Override
                public void setValue(BubbleExpandedView expandedView, float value) {
                    expandedView.setBackgroundAlpha(value);
                }

                @Override
                public Float get(BubbleExpandedView expandedView) {
                    return expandedView.getAlpha();
                }
            };

    /** {@link FloatProperty} for updating manage button alpha */
    public static final FloatProperty<BubbleExpandedView> MANAGE_BUTTON_ALPHA =
            new FloatProperty<BubbleExpandedView>("manageButtonAlpha") {
                @Override
                public void setValue(BubbleExpandedView expandedView, float value) {
                    expandedView.mManageButton.setAlpha(value);
                }

                @Override
                public Float get(BubbleExpandedView expandedView) {
                    return expandedView.mManageButton.getAlpha();
                }
            };

    // The triangle pointing to the expanded view
    private View mPointerView;
    @Nullable private int[] mExpandedViewContainerLocation;

    private AlphaOptimizedButton mManageButton;
    private TaskView mTaskView;
    private BubbleOverflowContainerView mOverflowView;

    private int mTaskId = INVALID_TASK_ID;

    private boolean mImeVisible;
    private boolean mNeedsNewHeight;

    /**
     * Whether we want the {@code TaskView}'s content to be visible (alpha = 1f). If
     * {@link #mIsAnimating} is true, this may not reflect the {@code TaskView}'s actual alpha
     * value until the animation ends.
     */
    private boolean mIsContentVisible = false;

    /**
     * Whether we're animating the {@code TaskView}'s alpha value. If so, we will hold off on
     * applying alpha changes from {@link #setContentVisibility} until the animation ends.
     */
    private boolean mIsAnimating = false;

    private int mPointerWidth;
    private int mPointerHeight;
    private float mPointerRadius;
    private float mPointerOverlap;
    private final PointF mPointerPos = new PointF();
    private CornerPathEffect mPointerEffect;
    private ShapeDrawable mCurrentPointer;
    private ShapeDrawable mTopPointer;
    private ShapeDrawable mLeftPointer;
    private ShapeDrawable mRightPointer;
    private float mCornerRadius = 0f;
    private int mBackgroundColorFloating;
    private boolean mUsingMaxHeight;
    private int mLeftClip = 0;
    private int mTopClip = 0;
    private int mRightClip = 0;
    private int mBottomClip = 0;
    @Nullable private Bubble mBubble;
    private PendingIntent mPendingIntent;
    // TODO(b/170891664): Don't use a flag, set the BubbleOverflow object instead
    private boolean mIsOverflow;
    private boolean mIsClipping;

    private BubbleExpandedViewManager mManager;
    private BubbleStackView mStackView;
    private BubblePositioner mPositioner;

    /**
     * Container for the {@code TaskView} that has a solid, round-rect background that shows if the
     * {@code TaskView} hasn't loaded.
     */
    private final FrameLayout mExpandedViewContainer = new FrameLayout(getContext());

    private final TaskView.Listener mTaskViewListener = new TaskView.Listener() {
        private boolean mInitialized = false;
        private boolean mDestroyed = false;

        @Override
        public void onInitialized() {
            if (mDestroyed || mInitialized) {
                ProtoLog.d(WM_SHELL_BUBBLES, "onInitialized: destroyed=%b initialized=%b bubble=%s",
                        mDestroyed, mInitialized, getBubbleKey());
                return;
            }

            // Custom options so there is no activity transition animation
            ActivityOptions options = ActivityOptions.makeCustomAnimation(getContext(),
                    0 /* enterResId */, 0 /* exitResId */);

            // TODO: I notice inconsistencies in lifecycle
            // Post to keep the lifecycle normal
            post(() -> {
                ProtoLog.d(WM_SHELL_BUBBLES, "onInitialized: calling startActivity, bubble=%s",
                        getBubbleKey());
                try {
                    Rect launchBounds = new Rect();
                    mTaskView.getBoundsOnScreen(launchBounds);

                    options.setTaskAlwaysOnTop(true);
                    options.setLaunchedFromBubble(true);
                    options.setPendingIntentBackgroundActivityStartMode(
                            MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    options.setPendingIntentBackgroundActivityLaunchAllowedByPermission(true);

                    Intent fillInIntent = new Intent();
                    // Apply flags to make behaviour match documentLaunchMode=always.
                    fillInIntent.addFlags(FLAG_ACTIVITY_NEW_DOCUMENT);
                    fillInIntent.addFlags(FLAG_ACTIVITY_MULTIPLE_TASK);

                    if (mBubble.isAppBubble()) {
                        Context context =
                                mContext.createContextAsUser(
                                        mBubble.getUser(), Context.CONTEXT_RESTRICTED);
                        PendingIntent pi = PendingIntent.getActivity(
                                context,
                                /* requestCode= */ 0,
                                mBubble.getAppBubbleIntent()
                                        .addFlags(FLAG_ACTIVITY_NEW_DOCUMENT)
                                        .addFlags(FLAG_ACTIVITY_MULTIPLE_TASK),
                                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT,
                                /* options= */ null);
                        mTaskView.startActivity(pi, /* fillInIntent= */ null, options,
                                launchBounds);
                    } else if (!mIsOverflow && mBubble.hasMetadataShortcutId()) {
                        options.setApplyActivityFlagsForBubbles(true);
                        mTaskView.startShortcutActivity(mBubble.getShortcutInfo(),
                                options, launchBounds);
                    } else {
                        if (mBubble != null) {
                            mBubble.setIntentActive();
                        }
                        mTaskView.startActivity(mPendingIntent, fillInIntent, options,
                                launchBounds);
                    }
                } catch (RuntimeException e) {
                    // If there's a runtime exception here then there's something
                    // wrong with the intent, we can't really recover / try to populate
                    // the bubble again so we'll just remove it.
                    Log.w(TAG, "Exception while displaying bubble: " + getBubbleKey()
                            + ", " + e.getMessage() + "; removing bubble");
                    mManager.removeBubble(getBubbleKey(), Bubbles.DISMISS_INVALID_INTENT);
                }
            });
            mInitialized = true;
        }

        @Override
        public void onReleased() {
            mDestroyed = true;
        }

        @Override
        public void onTaskCreated(int taskId, ComponentName name) {
            ProtoLog.d(WM_SHELL_BUBBLES, "onTaskCreated: taskId=%d bubble=%s",
                    taskId, getBubbleKey());
            // The taskId is saved to use for removeTask, preventing appearance in recent tasks.
            mTaskId = taskId;

            if (mBubble != null && mBubble.isAppBubble()) {
                // Let the controller know sooner what the taskId is.
                mManager.setAppBubbleTaskId(mBubble.getKey(), mTaskId);
            }

            // With the task org, the taskAppeared callback will only happen once the task has
            // already drawn
            setContentVisibility(true);
        }

        @Override
        public void onTaskVisibilityChanged(int taskId, boolean visible) {
            ProtoLog.d(WM_SHELL_BUBBLES, "onTaskVisibilityChanged=%b bubble=%s taskId=%d",
                    visible, getBubbleKey(), taskId);
            setContentVisibility(visible);
        }

        @Override
        public void onTaskRemovalStarted(int taskId) {
            ProtoLog.d(WM_SHELL_BUBBLES, "onTaskRemovalStarted: taskId=%d bubble=%s",
                    taskId, getBubbleKey());
            if (mBubble != null) {
                mManager.removeBubble(mBubble.getKey(), Bubbles.DISMISS_TASK_FINISHED);
            }
            if (mTaskView != null) {
                // Release the surface
                mTaskView.release();
                removeView(mTaskView);
                mTaskView = null;
            }
        }

        @Override
        public void onBackPressedOnTaskRoot(int taskId) {
            if (mTaskId == taskId && mStackView.isExpanded()) {
                mStackView.onBackPressed();
            }
        }
    };

    public BubbleExpandedView(Context context) {
        this(context, null);
    }

    public BubbleExpandedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleExpandedView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public BubbleExpandedView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mManageButton = (AlphaOptimizedButton) LayoutInflater.from(getContext()).inflate(
                R.layout.bubble_manage_button, this /* parent */, false /* attach */);
        updateDimensions();
        mPointerView = findViewById(R.id.pointer_view);
        mCurrentPointer = mTopPointer;
        mPointerView.setVisibility(INVISIBLE);

        // Set {@code TaskView}'s alpha value as zero, since there is no view content to be shown.
        setContentVisibility(false);

        mExpandedViewContainer.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                Rect clip = new Rect(mLeftClip, mTopClip, view.getWidth() - mRightClip,
                        view.getHeight() - mBottomClip);
                outline.setRoundRect(clip, mCornerRadius);
            }
        });
        mExpandedViewContainer.setClipToOutline(true);
        mExpandedViewContainer.setLayoutParams(
                new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        addView(mExpandedViewContainer);

        // Expanded stack layout, top to bottom:
        // Expanded view container
        // ==> bubble row
        // ==> expanded view
        //   ==> activity view
        //   ==> manage button
        bringChildToFront(mManageButton);

        applyThemeAttrs();

        setClipToPadding(false);
        setOnTouchListener((view, motionEvent) -> {
            if (mTaskView == null) {
                return false;
            }

            final Rect avBounds = new Rect();
            mTaskView.getBoundsOnScreen(avBounds);

            // Consume and ignore events on the expanded view padding that are within the
            // {@code TaskView}'s vertical bounds. These events are part of a back gesture, and so
            // they should not collapse the stack (which all other touches on areas around the AV
            // would do).
            if (motionEvent.getRawY() >= avBounds.top
                    && motionEvent.getRawY() <= avBounds.bottom
                    && (motionEvent.getRawX() < avBounds.left
                    || motionEvent.getRawX() > avBounds.right)) {
                return true;
            }

            return false;
        });

        // BubbleStackView is forced LTR, but we want to respect the locale for expanded view layout
        // so the Manage button appears on the right.
        setLayoutDirection(LAYOUT_DIRECTION_LOCALE);
    }


    /** Updates the width of the task view if it changed. */
    void updateTaskViewContentWidth() {
        if (mTaskView != null) {
            int width = getContentWidth();
            if (mTaskView.getWidth() != width) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, MATCH_PARENT);
                mTaskView.setLayoutParams(lp);
            }
        }
    }

    private int getContentWidth() {
        boolean isStackOnLeft = mPositioner.isStackOnLeft(mStackView.getStackPosition());
        return mPositioner.getTaskViewContentWidth(isStackOnLeft);
    }

    /**
     * Initialize {@link BubbleController} and {@link BubbleStackView} here, this method must need
     * to be called after view inflate.
     */
    void initialize(BubbleExpandedViewManager expandedViewManager,
            BubbleStackView stackView,
            BubblePositioner positioner,
            boolean isOverflow,
            @Nullable BubbleTaskView bubbleTaskView) {
        mManager = expandedViewManager;
        mStackView = stackView;
        mIsOverflow = isOverflow;
        mPositioner = positioner;

        if (mIsOverflow) {
            mOverflowView = (BubbleOverflowContainerView) LayoutInflater.from(getContext()).inflate(
                    R.layout.bubble_overflow_container, null /* root */);
            mOverflowView.initialize(expandedViewManager, positioner);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
            mExpandedViewContainer.addView(mOverflowView, lp);
            mExpandedViewContainer.setLayoutParams(
                    new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            bringChildToFront(mOverflowView);
            mManageButton.setVisibility(GONE);
        } else {
            mTaskView = bubbleTaskView.getTaskView();
            // reset the insets that might left after TaskView is shown in BubbleBarExpandedView
            mTaskView.setCaptionInsets(null);
            bubbleTaskView.setDelegateListener(mTaskViewListener);

            // set a fixed width so it is not recalculated as part of a rotation. the width will be
            // updated manually after the rotation.
            FrameLayout.LayoutParams lp =
                    new FrameLayout.LayoutParams(getContentWidth(), MATCH_PARENT);
            if (mTaskView.getParent() != null) {
                ((ViewGroup) mTaskView.getParent()).removeView(mTaskView);
            }
            mExpandedViewContainer.addView(mTaskView, lp);
            bringChildToFront(mTaskView);
            if (bubbleTaskView.isCreated()) {
                mTaskViewListener.onTaskCreated(
                        bubbleTaskView.getTaskId(), bubbleTaskView.getComponentName());
            }
        }
    }

    void updateDimensions() {
        Resources res = getResources();
        updateFontSize();

        mPointerWidth = res.getDimensionPixelSize(R.dimen.bubble_pointer_width);
        mPointerHeight = res.getDimensionPixelSize(R.dimen.bubble_pointer_height);
        mPointerRadius = getResources().getDimensionPixelSize(R.dimen.bubble_pointer_radius);
        mPointerEffect = new CornerPathEffect(mPointerRadius);
        mPointerOverlap = getResources().getDimensionPixelSize(R.dimen.bubble_pointer_overlap);
        mTopPointer = new ShapeDrawable(TriangleShape.create(
                mPointerWidth, mPointerHeight, true /* pointUp */));
        mLeftPointer = new ShapeDrawable(TriangleShape.createHorizontal(
                mPointerWidth, mPointerHeight, true /* pointLeft */));
        mRightPointer = new ShapeDrawable(TriangleShape.createHorizontal(
                mPointerWidth, mPointerHeight, false /* pointLeft */));
        updatePointerViewIfExists();
        updateManageButtonIfExists();
    }


    /**
     * Reinflate manage button if {@link #mManageButton} is initialized.
     * Does nothing otherwise.
     */
    private void updateManageButtonIfExists() {
        if (mManageButton == null) {
            return;
        }
        int visibility = mManageButton.getVisibility();
        removeView(mManageButton);
        ContextThemeWrapper ctw = new ContextThemeWrapper(getContext(),
                com.android.internal.R.style.Theme_DeviceDefault_DayNight);
        mManageButton = (AlphaOptimizedButton) LayoutInflater.from(ctw).inflate(
                R.layout.bubble_manage_button, this /* parent */, false /* attach */);
        addView(mManageButton);
        mManageButton.setVisibility(visibility);
        post(() -> {
            int touchAreaHeight =
                    getResources().getDimensionPixelSize(
                            R.dimen.bubble_manage_button_touch_area_height);
            Rect r = new Rect();
            mManageButton.getHitRect(r);
            int extraTouchArea = (touchAreaHeight - r.height()) / 2;
            r.top -= extraTouchArea;
            r.bottom += extraTouchArea;
            setTouchDelegate(new TouchDelegate(r, mManageButton));
        });
    }

    void updateFontSize() {
        final float fontSize = mContext.getResources()
                .getDimensionPixelSize(com.android.internal.R.dimen.text_size_body_2_material);
        if (mManageButton != null) {
            mManageButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
        }
        if (mOverflowView != null) {
            mOverflowView.updateFontSize();
        }
    }

    void updateLocale() {
        if (mManageButton != null) {
            mManageButton.setText(mContext.getString(R.string.manage_bubbles_text));
        }
        if (mOverflowView != null) {
            mOverflowView.updateLocale();
        }
    }

    void applyThemeAttrs() {
        final TypedArray ta = mContext.obtainStyledAttributes(new int[]{
                android.R.attr.dialogCornerRadius,
                com.android.internal.R.attr.materialColorSurfaceBright,
                com.android.internal.R.attr.materialColorSurfaceContainerHigh});
        boolean supportsRoundedCorners = ScreenDecorationsUtils.supportsRoundedCornersOnWindows(
                mContext.getResources());
        mCornerRadius = supportsRoundedCorners ? ta.getDimensionPixelSize(0, 0) : 0;
        mBackgroundColorFloating = ta.getColor(1, Color.WHITE);
        mExpandedViewContainer.setBackgroundColor(mBackgroundColorFloating);
        final int manageMenuBg = ta.getColor(2, Color.WHITE);
        ta.recycle();
        if (mManageButton != null) {
            mManageButton.getBackground().setColorFilter(manageMenuBg, PorterDuff.Mode.SRC_IN);
        }

        if (mTaskView != null) {
            mTaskView.setCornerRadius(mCornerRadius);
        }
        updatePointerViewIfExists();
        updateManageButtonIfExists();
    }

    /**
     * Updates the size and visuals of the pointer if {@link #mPointerView} is initialized.
     * Does nothing otherwise.
     */
    private void updatePointerViewIfExists() {
        if (mPointerView == null) {
            return;
        }
        LayoutParams lp = (LayoutParams) mPointerView.getLayoutParams();
        if (mCurrentPointer == mLeftPointer || mCurrentPointer == mRightPointer) {
            lp.width = mPointerHeight;
            lp.height = mPointerWidth;
        } else {
            lp.width = mPointerWidth;
            lp.height = mPointerHeight;
        }
        mCurrentPointer.setTint(mBackgroundColorFloating);

        Paint arrowPaint = mCurrentPointer.getPaint();
        arrowPaint.setColor(mBackgroundColorFloating);
        arrowPaint.setPathEffect(mPointerEffect);
        mPointerView.setLayoutParams(lp);
        mPointerView.setBackground(mCurrentPointer);
    }

    @VisibleForTesting
    public String getBubbleKey() {
        return mBubble != null ? mBubble.getKey() : mIsOverflow ? BubbleOverflow.KEY : null;
    }

    /**
     * Sets whether the surface displaying app content should sit on top. This is useful for
     * ordering surfaces during animations. When content is drawn on top of the app (e.g. bubble
     * being dragged out, the manage menu) this is set to false, otherwise it should be true.
     */
    public void setSurfaceZOrderedOnTop(boolean onTop) {
        if (mTaskView == null) {
            return;
        }
        mTaskView.setZOrderedOnTop(onTop, true /* allowDynamicChange */);
    }

    void setImeVisible(boolean visible) {
        mImeVisible = visible;
        if (!mImeVisible && mNeedsNewHeight) {
            updateHeight();
        }
    }

    /** Return a GraphicBuffer with the contents of the task view surface. */
    @Nullable
    ScreenCapture.ScreenshotHardwareBuffer snapshotActivitySurface() {
        if (mIsOverflow) {
            // For now, just snapshot the view and return it as a hw buffer so that the animation
            // code for both the tasks and overflow can be the same
            Picture p = new Picture();
            mOverflowView.draw(
                    p.beginRecording(mOverflowView.getWidth(), mOverflowView.getHeight()));
            p.endRecording();
            Bitmap snapshot = Bitmap.createBitmap(p);
            return new ScreenCapture.ScreenshotHardwareBuffer(
                    snapshot.getHardwareBuffer(),
                    snapshot.getColorSpace(),
                    false /* containsSecureLayers */,
                    false /* containsHdrLayers */);
        }
        if (mTaskView == null || mTaskView.getSurfaceControl() == null) {
            return null;
        }
        return ScreenCapture.captureLayers(
                mTaskView.getSurfaceControl(),
                new Rect(0, 0, mTaskView.getWidth(), mTaskView.getHeight()),
                1 /* scale */);
    }

    int[] getTaskViewLocationOnScreen() {
        if (mIsOverflow) {
            // This is only used for animating away the surface when switching bubbles, just use the
            // view location on screen for now to allow us to use the same animation code with tasks
            return mOverflowView.getLocationOnScreen();
        }
        if (mTaskView != null) {
            return mTaskView.getLocationOnScreen();
        } else {
            return new int[]{0, 0};
        }
    }

    // TODO: Could listener be passed when we pass StackView / can we avoid setting this like this
    void setManageClickListener(OnClickListener manageClickListener) {
        mManageButton.setOnClickListener(manageClickListener);
    }

    /**
     * Updates the obscured touchable region for the task surface. This calls onLocationChanged,
     * which results in a call to {@link BubbleStackView#subtractObscuredTouchableRegion}. This is
     * useful if a view has been added or removed from on top of the {@code TaskView}, such as the
     * manage menu.
     */
    void updateObscuredTouchableRegion() {
        if (mTaskView != null) {
            mTaskView.onLocationChanged();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mImeVisible = false;
        mNeedsNewHeight = false;
    }

    /**
     * Whether we are currently animating the {@code TaskView}. If this is set to
     * true, calls to {@link #setContentVisibility} will not be applied until this is set to false
     * again.
     */
    public void setAnimating(boolean animating) {
        mIsAnimating = animating;

        // If we're done animating, apply the correct
        if (!animating) {
            setContentVisibility(mIsContentVisible);
        }
    }

    /** Sets the alpha for the pointer. */
    public void setPointerAlpha(float alpha) {
        mPointerView.setAlpha(alpha);
    }

    /**
     * Get alpha from underlying {@code TaskView} if this view is for a bubble.
     * Or get alpha for the overflow view if this view is for overflow.
     *
     * @return alpha for the content being shown
     */
    public float getContentAlpha() {
        if (mIsOverflow) {
            return mOverflowView.getAlpha();
        }
        if (mTaskView != null) {
            return mTaskView.getAlpha();
        }
        return 1f;
    }

    /**
     * Set alpha of the underlying {@code TaskView} if this view is for a bubble.
     * Or set alpha for the overflow view if this view is for overflow.
     *
     * Changing expanded view's alpha does not affect the {@code TaskView} since it uses a Surface.
     */
    public void setContentAlpha(float alpha) {
        if (mIsOverflow) {
            mOverflowView.setAlpha(alpha);
        } else if (mTaskView != null) {
            mTaskView.setAlpha(alpha);
        }
    }

    /** Sets the alpha of the background. */
    public void setBackgroundAlpha(float alpha) {
        if (Flags.enableNewBubbleAnimations()) {
            setAlpha(alpha);
        } else {
            mPointerView.setAlpha(alpha);
            setAlpha(alpha);
        }
    }

    /**
     * Set translation Y for the expanded view content.
     * Excludes manage button and pointer.
     */
    public void setContentTranslationY(float translationY) {
        mExpandedViewContainer.setTranslationY(translationY);

        // Left or right pointer can become detached when moving the view up
        if (translationY <= 0 && (isShowingLeftPointer() || isShowingRightPointer())) {
            // Y coordinate where the pointer would start to get detached from the expanded view.
            // Takes into account bottom clipping and rounded corners
            float detachPoint =
                    mExpandedViewContainer.getBottom() - mBottomClip - mCornerRadius + translationY;
            float pointerBottom = mPointerPos.y + mPointerHeight;
            // If pointer bottom is past detach point, move it in by that many pixels
            float horizontalShift = 0;
            if (pointerBottom > detachPoint) {
                horizontalShift = pointerBottom - detachPoint;
            }
            if (isShowingLeftPointer()) {
                // Move left pointer right
                movePointerBy(horizontalShift, 0);
            } else {
                // Move right pointer left
                movePointerBy(-horizontalShift, 0);
            }
            // Hide pointer if it is moved by entire width
            mPointerView.setVisibility(
                    horizontalShift > mPointerWidth ? View.INVISIBLE : View.VISIBLE);
        }
    }

    /**
     * Update alpha value for the manage button
     */
    public void setManageButtonAlpha(float alpha) {
        mManageButton.setAlpha(alpha);
    }

    /**
     * Set {@link #setTranslationY(float) translationY} for the manage button
     */
    public void setManageButtonTranslationY(float translationY) {
        mManageButton.setTranslationY(translationY);
    }

    /**
     * Set top clipping for the view
     */
    public void setTopClip(int clip) {
        mTopClip = clip;
        onContainerClipUpdate();
    }

    /**
     * Set bottom clipping for the view
     */
    public void setBottomClip(int clip) {
        mBottomClip = clip;
        onContainerClipUpdate();
    }

    /**
     * Sets the clipping for the view.
     */
    public void setTaskViewClip(Rect rect) {
        mLeftClip = rect.left;
        mTopClip = rect.top;
        mRightClip = rect.right;
        mBottomClip = rect.bottom;
        onContainerClipUpdate();
    }

    /**
     * Returns a rect representing the clipping for the view.
     */
    public Rect getTaskViewClip() {
        return new Rect(mLeftClip, mTopClip, mRightClip, mBottom);
    }

    private void onContainerClipUpdate() {
        if (mTopClip == 0 && mBottomClip == 0 && mRightClip == 0 && mLeftClip == 0) {
            if (mIsClipping) {
                mIsClipping = false;
                if (mTaskView != null) {
                    mTaskView.setClipBounds(null);
                    mTaskView.setEnableSurfaceClipping(false);
                }
                mExpandedViewContainer.invalidateOutline();
            }
        } else {
            if (!mIsClipping) {
                mIsClipping = true;
                if (mTaskView != null) {
                    mTaskView.setEnableSurfaceClipping(true);
                }
            }
            mExpandedViewContainer.invalidateOutline();
            if (mTaskView != null) {
                Rect clipBounds = new Rect(mLeftClip, mTopClip,
                        mTaskView.getWidth() - mRightClip,
                        mTaskView.getHeight() - mBottomClip);
                mTaskView.setClipBounds(clipBounds);
            }
        }
    }

    /**
     * Move pointer from base position
     */
    public void movePointerBy(float x, float y) {
        mPointerView.setTranslationX(mPointerPos.x + x);
        mPointerView.setTranslationY(mPointerPos.y + y);
    }

    /**
     * Set visibility of contents in the expanded state.
     *
     * @param visibility {@code true} if the contents should be visible on the screen.
     *
     * Note that this contents visibility doesn't affect visibility at {@link android.view.View},
     * and setting {@code false} actually means rendering the contents in transparent.
     */
    public void setContentVisibility(boolean visibility) {
        mIsContentVisible = visibility;
        if (mTaskView != null && !mIsAnimating) {
            mTaskView.setAlpha(visibility ? 1f : 0f);
            mPointerView.setAlpha(visibility ? 1f : 0f);
        }
    }

    @Nullable
    TaskView getTaskView() {
        return mTaskView;
    }

    @VisibleForTesting
    public BubbleOverflowContainerView getOverflow() {
        return mOverflowView;
    }


    /**
     * Return content height: taskView or overflow.
     * Takes into account clippings set by {@link #setTopClip(int)} and {@link #setBottomClip(int)}
     *
     * @return if bubble is for overflow, return overflow height, otherwise return taskView height
     */
    public int getContentHeight() {
        if (mIsOverflow) {
            return mOverflowView.getHeight() - mTopClip - mBottomClip;
        }
        if (mTaskView != null) {
            return mTaskView.getHeight() - mTopClip - mBottomClip;
        }
        return 0;
    }

    /**
     * Return bottom position of the content on screen
     *
     * @return if bubble is for overflow, return value for overflow, otherwise taskView
     */
    public int getContentBottomOnScreen() {
        Rect out = new Rect();
        if (mIsOverflow) {
            mOverflowView.getBoundsOnScreen(out);
        }
        if (mTaskView != null) {
            mTaskView.getBoundsOnScreen(out);
        }
        return out.bottom;
    }

    int getTaskId() {
        return mTaskId;
    }

    /**
     * Sets the bubble used to populate this view.
     */
    void update(Bubble bubble) {
        if (mStackView == null) {
            Log.w(TAG, "Stack is null for bubble: " + bubble);
            return;
        }
        boolean isNew = mBubble == null || didBackingContentChange(bubble);
        if (isNew || bubble.getKey().equals(mBubble.getKey())) {
            mBubble = bubble;
            mManageButton.setContentDescription(getResources().getString(
                    R.string.bubbles_settings_button_description, bubble.getAppName()));
            mManageButton.setAccessibilityDelegate(
                    new AccessibilityDelegate() {
                        @Override
                        public void onInitializeAccessibilityNodeInfo(View host,
                                AccessibilityNodeInfo info) {
                            super.onInitializeAccessibilityNodeInfo(host, info);
                            // On focus, have TalkBack say
                            // "Actions available. Use swipe up then right to view."
                            // in addition to the default "double tap to activate".
                            mStackView.setupLocalMenu(info);
                        }
                    });

            if (isNew) {
                mPendingIntent = mBubble.getBubbleIntent();
                if ((mPendingIntent != null || mBubble.hasMetadataShortcutId())
                        && mTaskView != null) {
                    setContentVisibility(false);
                    mTaskView.setVisibility(VISIBLE);
                }
            }
            applyThemeAttrs();
        } else {
            Log.w(TAG, "Trying to update entry with different key, new bubble: "
                    + bubble.getKey() + " old bubble: " + bubble.getKey());
        }
    }

    /**
     * Bubbles are backed by a pending intent or a shortcut, once the activity is
     * started we never change it / restart it on notification updates -- unless the bubbles'
     * backing data switches.
     *
     * This indicates if the new bubble is backed by a different data source than what was
     * previously shown here (e.g. previously a pending intent & now a shortcut).
     *
     * @param newBubble the bubble this view is being updated with.
     * @return true if the backing content has changed.
     */
    private boolean didBackingContentChange(Bubble newBubble) {
        boolean prevWasIntentBased = mBubble != null && mPendingIntent != null;
        boolean newIsIntentBased = newBubble.getBubbleIntent() != null;
        return prevWasIntentBased != newIsIntentBased;
    }

    /**
     * Whether the bubble is using all available height to display or not.
     */
    public boolean isUsingMaxHeight() {
        return mUsingMaxHeight;
    }

    void updateHeight() {
        if (mExpandedViewContainerLocation == null) {
            return;
        }

        if ((mBubble != null && mTaskView != null) || mIsOverflow) {
            float desiredHeight = mPositioner.getExpandedViewHeight(mBubble);
            int maxHeight = mPositioner.getMaxExpandedViewHeight(mIsOverflow);
            float height = desiredHeight == MAX_HEIGHT
                    ? maxHeight
                    : Math.min(desiredHeight, maxHeight);
            mUsingMaxHeight = height == maxHeight;
            FrameLayout.LayoutParams lp = mIsOverflow
                    ? (FrameLayout.LayoutParams) mOverflowView.getLayoutParams()
                    : (FrameLayout.LayoutParams) mTaskView.getLayoutParams();
            mNeedsNewHeight = lp.height != height;
            if (!mImeVisible) {
                // If the ime is visible... don't adjust the height because that will cause
                // a configuration change and the ime will be lost.
                lp.height = (int) height;
                if (mIsOverflow) {
                    mOverflowView.setLayoutParams(lp);
                } else {
                    mTaskView.setLayoutParams(lp);
                }
                mNeedsNewHeight = false;
            }
        }
    }

    /**
     * Update appearance of the expanded view being displayed.
     *
     * @param containerLocationOnScreen The location on-screen of the container the expanded view is
     *                                  added to. This allows us to calculate max height without
     *                                  waiting for layout.
     */
    public void updateView(int[] containerLocationOnScreen) {
        mExpandedViewContainerLocation = containerLocationOnScreen;
        updateHeight();
        if (mTaskView != null
                && mTaskView.getVisibility() == VISIBLE
                && mTaskView.isAttachedToWindow()) {
            // post this to the looper, because if the device orientation just changed, we need to
            // let the current shell transition complete before updating the task view bounds.
            post(() -> {
                if (mTaskView != null) {
                    mTaskView.onLocationChanged();
                }
            });
        }
        if (mIsOverflow) {
            // post this to the looper so that the view has a chance to be laid out before it can
            // calculate row and column sizes correctly.
            post(() -> mOverflowView.show());
        }
    }

    /**
     * Sets the position of the pointer.
     *
     * When bubbles are showing "vertically" they display along the left / right sides of the
     * screen with the expanded view beside them.
     *
     * If they aren't showing vertically they're positioned along the top of the screen with the
     * expanded view below them.
     *
     * @param bubblePosition the x position of the bubble if showing on top, the y position of
     *                       the bubble if showing vertically.
     * @param onLeft whether the stack was on the left side of the screen when expanded.
     * @param animate whether the pointer should animate to this position.
     */
    public void setPointerPosition(float bubblePosition, boolean onLeft, boolean animate) {
        final boolean isRtl = mContext.getResources().getConfiguration().getLayoutDirection()
                == LAYOUT_DIRECTION_RTL;
        // Pointer gets drawn in the padding
        final boolean showVertically = mPositioner.showBubblesVertically();
        final float paddingLeft = (showVertically && onLeft)
                ? mPointerHeight - mPointerOverlap
                : 0;
        final float paddingRight = (showVertically && !onLeft)
                ? mPointerHeight - mPointerOverlap
                : 0;
        final float paddingTop = showVertically
                ? 0
                : mPointerHeight - mPointerOverlap;
        setPadding((int) paddingLeft, (int) paddingTop, (int) paddingRight, 0);

        // Subtract the expandedViewY here because the pointer is placed within the expandedView.
        float pointerPosition = mPositioner.getPointerPosition(bubblePosition);
        final float bubbleCenter = mPositioner.showBubblesVertically()
                ? pointerPosition - mPositioner.getExpandedViewY(mBubble, bubblePosition)
                : pointerPosition;
        // Post because we need the width of the view
        post(() -> {
            mCurrentPointer = showVertically ? onLeft ? mLeftPointer : mRightPointer : mTopPointer;
            updatePointerViewIfExists();
            if (showVertically) {
                mPointerPos.y = bubbleCenter - (mPointerWidth / 2f);
                if (!isRtl) {
                    mPointerPos.x = onLeft
                            ? -mPointerHeight + mPointerOverlap
                            : getWidth() - mPaddingRight - mPointerOverlap;
                } else {
                    mPointerPos.x = onLeft
                            ? -(getWidth() - mPaddingLeft - mPointerOverlap)
                            : mPointerHeight - mPointerOverlap;
                }
            } else {
                mPointerPos.y = mPointerOverlap;
                if (!isRtl) {
                    mPointerPos.x = bubbleCenter - (mPointerWidth / 2f);
                } else {
                    mPointerPos.x = -(getWidth() - mPaddingLeft - bubbleCenter)
                            + (mPointerWidth / 2f);
                }
            }
            if (animate) {
                mPointerView.animate().translationX(mPointerPos.x).translationY(
                        mPointerPos.y).start();
            } else {
                mPointerView.setTranslationY(mPointerPos.y);
                mPointerView.setTranslationX(mPointerPos.x);
                mPointerView.setVisibility(VISIBLE);
            }
        });
    }

    /**
     * Return true if pointer is shown on the left
     */
    public boolean isShowingLeftPointer() {
        return mCurrentPointer == mLeftPointer;
    }

    /**
     * Return true if pointer is shown on the right
     */
    public boolean isShowingRightPointer() {
        return mCurrentPointer == mRightPointer;
    }

    /**
     * Return width of the current pointer
     */
    public int getPointerWidth() {
        return mPointerWidth;
    }

    /**
     * Position of the manage button displayed in the expanded view. Used for placing user
     * education about the manage button.
     */
    public void getManageButtonBoundsOnScreen(Rect rect) {
        mManageButton.getBoundsOnScreen(rect);
    }

    public int getManageButtonMargin() {
        return ((LinearLayout.LayoutParams) mManageButton.getLayoutParams()).getMarginStart();
    }

    /** Hide the task view. */
    public void cleanUpExpandedState() {
        if (mTaskView != null) {
            mTaskView.setVisibility(GONE);
        }
    }

    /**
     * Description of current expanded view state.
     */
    public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
        pw.print(prefix); pw.println("BubbleExpandedView:");
        pw.print(prefix); pw.print("  taskId: "); pw.println(mTaskId);
        pw.print(prefix); pw.print("  stackView: "); pw.println(mStackView);
    }
}
