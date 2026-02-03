/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static android.text.TextUtils.isEmpty;

import static com.android.launcher3.Flags.enableLauncherVisualRefresh;
import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_EXIT_DELAY;
import static com.android.launcher3.LauncherState.EDIT_MODE;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.compat.AccessibilityManagerCompat.sendCustomAccessibilityEvent;
import static com.android.launcher3.config.FeatureFlags.ALWAYS_USE_HARDWARE_OPTIMIZATION_FOR_FOLDER_ANIMATIONS;
import static com.android.launcher3.folder.FolderGridOrganizer.createFolderGridOrganizer;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_LABEL_UPDATED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROP_COMPLETED;
import static com.android.launcher3.model.data.FolderInfo.willAcceptItemType;
import static com.android.launcher3.pageindicators.PaginationArrow.DISABLED_ARROW_OPACITY;
import static com.android.launcher3.pageindicators.PaginationArrow.FULLY_OPAQUE;
import static com.android.launcher3.testing.shared.TestProtocol.FOLDER_OPENED_MESSAGE;
import static com.android.launcher3.util.window.RefreshRateTracker.getSingleFrameMs;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.ColorInt;
import android.annotation.SuppressLint;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.input.InputManager;
import android.os.Looper;
import android.text.InputType;
import android.text.Selection;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.res.ResourcesCompat;

import androidx.core.view.WindowInsetsCompat;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Alarm;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Utilities;
import com.android.launcher3.accessibility.AccessibleDragListenerAdapter;
import com.android.launcher3.accessibility.FolderAccessibilityHelper;
import com.android.launcher3.anim.KeyboardInsetAnimationCallback;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.dragndrop.DragController.DragListener;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.graphics.ShapeDelegate;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.logger.LauncherAtom.FromState;
import com.android.launcher3.logger.LauncherAtom.ToState;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemFactory;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pageindicators.PageIndicatorDots;
import com.android.launcher3.pageindicators.PaginationArrow;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.views.ClipPathView;
import com.android.launcher3.widget.PendingAddShortcutInfo;

import com.androidinternal.graphics.ColorUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.patrykmichalik.opto.core.PreferenceExtensionsKt;
import app.lawnchair.preferences2.PreferenceManager2;
import app.lawnchair.theme.color.ColorOption;
import app.lawnchair.theme.color.tokens.ColorTokens;
import app.lawnchair.theme.drawable.DrawableTokens;
import app.lawnchair.util.EditTextExtensions;
import app.lawnchair.util.LawnchairUtilsKt;

/**
 * Represents a set of icons chosen by the user or generated by the system.
 */
public class Folder extends AbstractFloatingView implements ClipPathView, DragSource,
        View.OnLongClickListener, DropTarget, TextView.OnEditorActionListener,
        View.OnFocusChangeListener, DragListener, ExtendedEditText.OnBackKeyListener,
        LauncherBindableItemsContainer {
    private static final String TAG = "Launcher.Folder";
    private static final boolean DEBUG = false;

    /**
     * Used for separating folder title when logging together.
     */
    private static final CharSequence FOLDER_LABEL_DELIMITER = "~";

    /**
     * We avoid measuring {@link #mContent} with a 0 width or height, as this
     * results in CellLayout being measured as UNSPECIFIED, which it does not support.
     */
    @VisibleForTesting
    static final int MIN_CONTENT_DIMEN = 5;

    public static final int STATE_CLOSED = 0;
    public static final int STATE_ANIMATING = 1;
    public static final int STATE_OPEN = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_CLOSED, STATE_ANIMATING, STATE_OPEN})
    public @interface FolderState {
    }

    /**
     * Time for which the scroll hint is shown before automatically changing page.
     */
    public static final int SCROLL_HINT_DURATION = 500;
    private static final int RESCROLL_EXTRA_DELAY = 150;

    public static final int SCROLL_NONE = -1;
    public static final int SCROLL_LEFT = 0;
    public static final int SCROLL_RIGHT = 1;

    /**
     * Fraction of icon width which behave as scroll region.
     */
    private static final float ICON_OVERSCROLL_WIDTH_FACTOR = 0.45f;

    private static final int FOLDER_NAME_ANIMATION_DURATION = 633;
    private static final int FOLDER_COLOR_ANIMATION_DURATION = 200;

    private static final int REORDER_DELAY = 250;
    static final int ON_EXIT_CLOSE_DELAY = 400;
    private static final Rect sTempRect = new Rect();
    private static final int MIN_FOLDERS_FOR_HARDWARE_OPTIMIZATION = 10;

    /**
     * Checks if {@code o} is an {@link ItemInfo} type that can be placed in folders.
     */
    public static boolean willAccept(Object o) {
        return o instanceof ItemInfo info && willAcceptItemType(info.itemType);
    }

    private Alarm mReorderAlarm = new Alarm(Looper.getMainLooper());
    private Alarm mOnExitAlarm = new Alarm(Looper.getMainLooper());
    private Alarm mOnScrollHintAlarm = new Alarm(Looper.getMainLooper());
    private Alarm mScrollPauseAlarm = new Alarm(Looper.getMainLooper());

    final ArrayList<View> mItemsInReadingOrder = new ArrayList<View>();

    private AnimatorSet mCurrentAnimator;
    private boolean mIsAnimatingClosed = false;

    // Folder can be displayed in Launcher's activity or a separate window (e.g. Taskbar).
    // Anything specific to Launcher should use mLauncherDelegate, otherwise should
    // use mActivityContext.
    protected LauncherDelegate mLauncherDelegate;
    protected final ActivityContext mActivityContext;

    public FolderInfo mInfo;
    private CharSequence mFromTitle;
    private FromState mFromLabelState;

    @Thunk
    FolderIcon mFolderIcon;

    @Thunk
    FolderPagedView mContent;
    FolderNameEditText mFolderName;
    private PageIndicatorDots mPageIndicator;
    private PaginationArrow mLeftArrow;
    private PaginationArrow mRightArrow;

    protected LinearLayout mFooter;
    private int mFooterHeight;

    // Cell ranks used for drag and drop
    @Thunk
    int mTargetRank, mPrevTargetRank, mEmptyCellRank;

    private Path mClipPath;

    @ViewDebug.ExportedProperty(category = "launcher",
            mapping = {
                    @ViewDebug.IntToString(from = STATE_CLOSED, to = "STATE_CLOSED"),
                    @ViewDebug.IntToString(from = STATE_ANIMATING, to = "STATE_ANIMATING"),
                    @ViewDebug.IntToString(from = STATE_OPEN, to = "STATE_OPEN"),
            })
    private int mState = STATE_CLOSED;
    private final List<OnFolderStateChangedListener> mOnFolderStateChangedListeners =
            new ArrayList<>();
    private OnFolderStateChangedListener mPriorityOnFolderStateChangedListener;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mRearrangeOnClose = false;
    boolean mItemsInvalidated = false;
    private View mCurrentDragView;
    private boolean mIsExternalDrag;
    private boolean mIsDragInProgress = false;
    private boolean mDeleteFolderOnDropCompleted = false;

    private boolean mSuppressFolderDeletion = false;
    private boolean mSuppressContentUpdate = false;

    private boolean mItemAddedBackToSelfViaIcon = false;
    private boolean mIsEditingName = false;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mDestroyed;

    // Folder scrolling
    private int mScrollAreaOffset;

    @Thunk
    private int mScrollHintDir = SCROLL_NONE;
    @Thunk
    int mCurrentScrollDir = SCROLL_NONE;

    private StatsLogManager mStatsLogManager;

    @Nullable
    private KeyboardInsetAnimationCallback mKeyboardInsetAnimationCallback;

    private @NonNull GradientDrawable mBackground;

    PreferenceManager2 preferenceManager2;

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs   The attributes set containing the Workspace's customization values.
     */
    public Folder(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAlwaysDrawnWithCacheEnabled(false);

        mActivityContext = ActivityContext.lookupContext(context);
        mLauncherDelegate = LauncherDelegate.from(mActivityContext);

        mStatsLogManager = StatsLogManager.newInstance(context);
        // We need this view to be focusable in touch mode so that when text editing of the folder
        // name is complete, we have something to focus on, thus hiding the cursor and giving
        // reliable behavior when clicking the text field (since it will always gain focus on
        // click).
        setFocusableInTouchMode(true);

        mBackground = (GradientDrawable) Objects.requireNonNull(
                ResourcesCompat.getDrawable(getResources(),
                        R.drawable.round_rect_folder, getContext().getTheme()));
        mBackground.setCallback(this);
        preferenceManager2 = PreferenceManager2.INSTANCE.get(context);
    }

    @Override
    public Drawable getBackground() {
        return mBackground;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        final DeviceProfile dp = mActivityContext.getDeviceProfile();
        final int paddingLeftRight = dp.folderContentPaddingLeftRight;

        mBackground = DrawableTokens.RoundRectFolder.resolve(getContext());
        var alpha = LawnchairUtilsKt.getFolderBackgroundAlpha(getContext());
        mBackground.setAlpha(alpha);

        mContent = findViewById(R.id.folder_content);
        mContent.setPadding(paddingLeftRight, dp.folderContentPaddingTop, paddingLeftRight, 0);
        mContent.setFolder(this);

        mPageIndicator = findViewById(R.id.folder_page_indicator);
        mFooter = findViewById(R.id.folder_footer);
        mFooterHeight = dp.folderFooterHeightPx;
        mFolderName = findViewById(R.id.folder_name);
        if (Flags.enableLauncherVisualRefresh()) {
            mFolderName.setTypeface(Typeface.create("google-sans-flex", Typeface.NORMAL));
        }
        mFolderName.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp.folderLabelTextSizePx);
        mFolderName.setOnBackKeyListener(this);
        mFolderName.setOnEditorActionListener(this);
        mFolderName.setSelectAllOnFocus(true);
        mFolderName.setInputType(mFolderName.getInputType()
                & ~InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        mFolderName.forceDisableSuggestions(true);

        @ColorInt
        int accentColor = Themes.getColorAccent(mFolderName.getContext());
        EditTextExtensions.setCursorColor(mFolderName, accentColor);
        EditTextExtensions.setTextSelectHandleColor(mFolderName, accentColor);

        if (Utilities.ATLEAST_O) {
            mFolderName.setHighlightColor(ColorUtils.setAlphaComponent(accentColor, 82));
        }

        if (Utilities.ATLEAST_R) {
            mKeyboardInsetAnimationCallback = new KeyboardInsetAnimationCallback(this);
            setWindowInsetsAnimationCallback(mKeyboardInsetAnimationCallback);
        }
        
        if (enableLauncherVisualRefresh()) {
            mLeftArrow = findViewById(R.id.left_indicator_arrow);
            mRightArrow = findViewById(R.id.right_indicator_arrow);
            
            if (mRightArrow != null) {
                mRightArrow.setOnClickListener(v -> mContent.snapToPage(
                        mContent.getCurrentPage() + 1));
            }
            if (mLeftArrow != null) {
                mLeftArrow.setOnClickListener(v -> mContent.snapToPage(
                        mContent.getCurrentPage() - 1));
            }
        }
    }

    /**
     * If indicator is visible, set margin between folder title and indicator. Also properly show
     * arrows if pointer is enabled and indicator is visible.
     */
    public void onIndicatorVisibilityChanged() {
        if (mPageIndicator.getVisibility() == View.VISIBLE) {
            ((MarginLayoutParams) mFolderName.getLayoutParams()).setMarginEnd(
                    getResources().getDimensionPixelSize(R.dimen.folder_footer_horiz_padding));
            Context ctx = getContext(); // done here to avoid getting context on bg thread
            Executors.UI_HELPER_EXECUTOR.execute(() -> {
                // Only show arrows if a mouse or touchpad is connected to the device
                int arrowVisibility = isPointerEnabled(ctx) ? View.VISIBLE : View.GONE;
                if (mLeftArrow != null) mLeftArrow.setVisibility(arrowVisibility);
                if (mRightArrow != null) mRightArrow.setVisibility(arrowVisibility);

                // If the arrows are visible, then their touch box will slightly overlap with the
                // footer's padding by 8dp. Update it for proper alignment. PaddingEnd was always
                // equal to paddingRight in both LTR & RTL mode, so isRtl is manually accounted for
                int endPadding = getResources().getDimensionPixelSize(
                        arrowVisibility == View.VISIBLE
                                ? R.dimen.folder_footer_horiz_padding_minus_arrow_overlap
                                : R.dimen.folder_footer_horiz_padding);
                boolean isRtl = Utilities.isRtl(getResources());
                mFooter.setPadding(
                        isRtl ? endPadding : mFooter.getPaddingLeft(),
                        mFooter.getPaddingTop(),
                        isRtl ? mFooter.getPaddingRight() : endPadding,
                        mFooter.getPaddingBottom()
                );
            });
        } else {
            ((MarginLayoutParams) mFolderName.getLayoutParams()).setMarginEnd(0);
            if (mLeftArrow != null) mLeftArrow.setVisibility(View.GONE);
            if (mRightArrow != null) mRightArrow.setVisibility(View.GONE);
        }
    }

    @WorkerThread
    private boolean isPointerEnabled(Context context) {
        InputManager im = context.getSystemService(InputManager.class);
        return Arrays.stream(im.getInputDeviceIds())
                .mapToObj(im::getInputDevice)
                .anyMatch(device -> device.isEnabled()
                        && (device.supportsSource(InputDevice.SOURCE_MOUSE)
                        || device.supportsSource(InputDevice.SOURCE_TOUCHPAD)));
    }

    /**
     * Called when the page is switched. Sets arrow UX to a disabled appearance if the page is at
     * one end or the other.
     */
    public void updateArrowAlphas() {
        if (enableLauncherVisualRefresh()) {
            if (mLeftArrow != null) {
                mLeftArrow.setAlpha(
                        0 == mContent.getCurrentPage() ? DISABLED_ARROW_OPACITY : FULLY_OPAQUE);
            }
            if (mRightArrow != null) {
                mRightArrow.setAlpha(mContent.getPageCount() == mContent.getCurrentPage() + 1
                        ? DISABLED_ARROW_OPACITY : FULLY_OPAQUE);
            }
        }
    }

    public boolean onLongClick(View v) {
        // Return if global dragging is not enabled
        if (!getIsLauncherDraggingEnabled()) return true;
        return startDrag(v, new DragOptions());
    }

    @VisibleForTesting
    boolean getIsLauncherDraggingEnabled() {
        return mLauncherDelegate.isDraggingEnabled();
    }

    public boolean startDrag(View v, DragOptions options) {
        Object tag = v.getTag();
        if (tag instanceof ItemInfo item) {
            mEmptyCellRank = item.rank;
            mCurrentDragView = v;

            addDragListener(options);
            callBeginDragShared(v, options);
        }
        return true;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || (who == mBackground);
    }

    void callBeginDragShared(View v, DragOptions options) {
        mLauncherDelegate.beginDragShared(v, this, options);
    }

    void addDragListener(DragOptions options) {
        mActivityContext.getDragController().addDragListener(this);
        if (!options.isAccessibleDrag) {
            return;
        }
        mActivityContext.getDragController().addDragListener(new AccessibleDragListenerAdapter(
                mContent, FolderAccessibilityHelper::new) {
            @Override
            protected void enableAccessibleDrag(boolean enable,
                    @Nullable DragObject dragObject) {
                super.enableAccessibleDrag(enable, dragObject);
                mFooter.setImportantForAccessibility(enable
                        ? IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        : IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            }
        });
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        if (dragObject.dragSource != this) {
            return;
        }
        if (isInAppDrawer()) {
            close(true);
            // LC-Note: Do not remove item
            return;
        }
        
        mContent.removeItem(mCurrentDragView);
        mItemsInvalidated = true;

        // We do not want to get events for the item being removed, as they will get handled
        // when the drop completes
        executeWithContentUpdateSuppressed(() -> removeFolderContent(true, dragObject.dragInfo));

        mIsDragInProgress = true;
        mItemAddedBackToSelfViaIcon = false;
    }

    public boolean isInAppDrawer() {
        return mInfo.container == ItemInfo.NO_ID;
    }

    @Override
    public void onDragEnd() {
        if (mIsExternalDrag && mIsDragInProgress) {
            completeDragExit();
        }
        mIsDragInProgress = false;
        mActivityContext.getDragController().removeDragListener(this);
    }

    public void startEditingFolderName() {
        showLabelSuggestions();
        mFolderName.setHint("");
        mIsEditingName = true;
    }

    @Override
    public boolean onBackKey() {
        // Convert to a string here to ensure that no other state associated with the text field
        // gets saved.
        String newTitle = mFolderName.getText().toString();
        if (DEBUG) {
            Log.d(TAG, "onBackKey newTitle=" + newTitle);
        }
        mInfo.setTitle(newTitle, mActivityContext.getModelWriter());
        mFolderIcon.onTitleChanged(newTitle);

        if (TextUtils.isEmpty(mInfo.title)) {
            mFolderName.setHint(R.string.folder_hint_text);
            mFolderName.setText("");
        } else {
            mFolderName.setHint(null);
        }

        sendCustomAccessibilityEvent(
                this, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                getContext().getString(R.string.folder_renamed, newTitle));

        // This ensures that focus is gained every time the field is clicked, which selects all
        // the text and brings up the soft keyboard if necessary.
        mFolderName.clearFocus();

        Selection.setSelection(mFolderName.getText(), 0, 0);
        mIsEditingName = false;
        return true;
    }

    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (DEBUG) {
            Log.d(TAG, "onEditorAction actionId=" + actionId + " key="
                    + (event != null ? event.getKeyCode() : "null event"));
        }
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            mFolderName.dispatchBackKey();
            return true;
        }
        return false;
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets windowInsets) {
        this.setTranslationY(0);
        
        // Lawnchair-TODO: Keyboard too close to Folder name edit?

        try {
            if (windowInsets.isVisible(WindowInsets.Type.ime())) {
                Insets keyboardInsets = windowInsets.getInsets(WindowInsets.Type.ime());
                int folderHeightFromBottom = getHeightFromBottom();

                if (keyboardInsets.bottom > folderHeightFromBottom) {
                    // Translate this folder above the keyboard, then add the folder name's padding
                    this.setTranslationY(folderHeightFromBottom - keyboardInsets.bottom
                        - mFolderName.getPaddingBottom());
                }
            }
        } catch (Throwable t) {
            // LC-Catch
            WindowInsetsCompat insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(windowInsets);
            if (insetsCompat.isVisible(WindowInsetsCompat.Type.ime())) {
                androidx.core.graphics.Insets keyboardInsets = insetsCompat.getInsets(WindowInsetsCompat.Type.ime());
                int folderHeightFromBottom = getHeightFromBottom();

                if (keyboardInsets.bottom > folderHeightFromBottom) {
                    // Translate this folder above the keyboard, then add the folder name's padding
                    this.setTranslationY(folderHeightFromBottom - keyboardInsets.bottom
                        - mFolderName.getPaddingBottom());
                }
            }
        }

        return windowInsets;
    }

    public FolderIcon getFolderIcon() {
        return mFolderIcon;
    }

    public void setFolderIcon(FolderIcon icon) {
        mFolderIcon = icon;
    }

    @Override
    protected void onAttachedToWindow() {
        // requestFocus() causes the focus onto the folder itself, which doesn't cause visual
        // effect but the next arrow key can start the keyboard focus inside of the folder, not
        // the folder itself.
        requestFocus();
        super.onAttachedToWindow();
        mFolderName.addOnFocusChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mFolderName.removeOnFocusChangeListener(this);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // When the folder gets focus, we don't want to announce the list of items.
        return true;
    }

    @Override
    public View focusSearch(int direction) {
        // When the folder is focused, further focus search should be within the folder contents.
        return FocusFinder.getInstance().findNextFocus(this, null, direction);
    }

    /**
     * @return the FolderInfo object associated with this folder
     */
    public FolderInfo getInfo() {
        return mInfo;
    }

    void bind(FolderInfo info) {
        mInfo = info;
        mFromTitle = info.title;
        mFromLabelState = info.getFromLabelState();
        updateItemLocationsInDatabaseBatch(true);

        BaseDragLayer.LayoutParams lp = (BaseDragLayer.LayoutParams) getLayoutParams();
        if (lp == null) {
            lp = new BaseDragLayer.LayoutParams(0, 0);
            lp.customPosition = true;
            setLayoutParams(lp);
        }
        reapplyItemInfo();
        // In case any children didn't come across during loading, clean up the folder accordingly
        mFolderIcon.post(() -> {
            if (getItemCount() <= 1) {
                replaceFolderWithFinalItem();
            }
        });
    }

    public void reapplyItemInfo() {
        mItemsInvalidated = true;

        if (!isEmpty(mInfo.title)) {
            mFolderName.setText(mInfo.title);
            mFolderName.setHint(null);
        } else {
            mFolderName.setText("");
            mFolderName.setHint(R.string.folder_hint_text);
        }
    }

    /**
     * Show suggested folder title in FolderEditText if the first suggestion is non-empty, push
     * rest of the suggestions to InputMethodManager.
     */
    void showLabelSuggestions() {
        if (mInfo.suggestedFolderNames == null) {
            return;
        }
        if (mInfo.suggestedFolderNames.hasSuggestions()) {
            // update the primary suggestion if the folder name is empty.
            if (isEmpty(mFolderName.getText())) {
                if (mInfo.suggestedFolderNames.hasPrimary()) {
                    mFolderName.setHint("");
                    mFolderName.setText(mInfo.suggestedFolderNames.getLabels()[0]);
                    mFolderName.selectAll();
                }
            }
            mFolderName.showKeyboard();
            mFolderName.displayCompletions(
                    Stream.of(mInfo.suggestedFolderNames.getLabels())
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            .filter(s -> !s.isEmpty())
                            .filter(s -> !s.equalsIgnoreCase(mFolderName.getText().toString()))
                            .collect(Collectors.toList()));
        }
    }

    /**
     * Creates a new UserFolder, inflated from R.layout.user_folder.
     *
     * @param activityContext The main ActivityContext in which to inflate this Folder. It must also
     *                        be an instance or ContextWrapper around the Launcher activity context.
     * @return A new UserFolder.
     */
    @SuppressLint("InflateParams")
    static <T extends Context & ActivityContext> Folder fromXml(T activityContext) {
        return (Folder) LayoutInflater.from(activityContext).cloneInContext(activityContext)
                .inflate(R.layout.user_folder_icon_normalized, null);
    }

    private void addAnimationStartListeners(AnimatorSet a) {
        mLauncherDelegate.forEachVisibleWorkspacePage(
                visiblePage -> addAnimatorListenerForPage(a, (CellLayout) visiblePage));

        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                setState(STATE_ANIMATING);
                mCurrentAnimator = a;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mCurrentAnimator = null;
            }
        });
    }

    private void addAnimatorListenerForPage(AnimatorSet a, CellLayout currentCellLayout) {
        final boolean useHardware = shouldUseHardwareLayerForAnimation(currentCellLayout);
        final boolean wasHardwareAccelerated = currentCellLayout.isHardwareLayerEnabled();

        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (useHardware) {
                    currentCellLayout.enableHardwareLayer(true);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (useHardware) {
                    currentCellLayout.enableHardwareLayer(wasHardwareAccelerated);
                }
            }
        });
    }

    private boolean shouldUseHardwareLayerForAnimation(CellLayout currentCellLayout) {
        if (ALWAYS_USE_HARDWARE_OPTIMIZATION_FOR_FOLDER_ANIMATIONS.get()) return true;

        int folderCount = 0;
        final ShortcutAndWidgetContainer container = currentCellLayout.getShortcutsAndWidgets();
        for (int i = container.getChildCount() - 1; i >= 0; --i) {
            final View child = container.getChildAt(i);
            if (child instanceof AppWidgetHostView) return false;
            if (child instanceof FolderIcon) ++folderCount;
        }
        return folderCount >= MIN_FOLDERS_FOR_HARDWARE_OPTIMIZATION;
    }

    /**
     * Opens the folder as part of a drag operation
     */
    public void beginExternalDrag() {
        mIsExternalDrag = true;
        mIsDragInProgress = true;

        // Since this folder opened by another controller, it might not get onDrop or
        // onDropComplete. Perform cleanup once drag-n-drop ends.
        mActivityContext.getDragController().addDragListener(this);

        ArrayList<ItemInfo> items = new ArrayList<>(mInfo.getContents());
        mEmptyCellRank = items.size();
        items.add(null);    // Add an empty spot at the end

        animateOpen(items, mEmptyCellRank / mContent.itemsPerPage());
    }

    /**
     * Opens the user folder described by the specified tag. The opening of the folder
     * is animated relative to the specified View. If the View is null, no animation
     * is played.
     */
    public void animateOpen() {
        animateOpen(mInfo.getContents(), 0);
    }

    /**
     * Opens the user folder described by the specified tag. The opening of the folder
     * is animated relative to the specified View. If the View is null, no animation
     * is played.
     */
    private void animateOpen(List<ItemInfo> items, int pageNo) {
        if (!shouldAnimateOpen(items)) {
            return;
        }
        Folder openFolder = getOpen(mActivityContext);
        closeOpenFolder(openFolder);

        mContent.bindItems(items);
        mContent.setCanAnnouncePageDescriptionForFolder(true);
        centerAboutIcon();
        mItemsInvalidated = true;
        updateTextViewFocus();

        mIsOpen = true;

        BaseDragLayer dragLayer = mActivityContext.getDragLayer();
        // Just verify that the folder hasn't already been added to the DragLayer.
        // There was a one-off crash where the folder had a parent already.
        if (getParent() == null) {
            dragLayer.addView(this);
            mActivityContext.getDragController().addDropTarget(this);
        } else {
            if (FeatureFlags.IS_STUDIO_BUILD) {
                Log.e(TAG, "Opening folder (" + this + ") which already has a parent:"
                        + getParent());
            }
        }

        Log.d("b/383526431", "animateOpen: content child count before: "
                + mContent.getTotalChildCount());

        mContent.completePendingPageChanges();
        mContent.setCurrentPage(pageNo);

        Log.d("b/383526431", "animateOpen: content child count after pending page"
                + " changes: " + mContent.getTotalChildCount());

        // This is set to true in close(), but isn't reset to false until onDropCompleted(). This
        // leads to an inconsistent state if you drag out of the folder and drag back in without
        // dropping. One resulting issue is that replaceFolderWithFinalItem() can be called twice.
        mDeleteFolderOnDropCompleted = false;

        cancelRunningAnimations();
        Log.d("b/383526431", "animateOpen: content child count after cancelling"
                + " animation: " + mContent.getTotalChildCount());

        AnimatorSet animatorSet = getFolderAnimationManager()
                .createAnimatorSet(/* isOpening */ true);

        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mFolderIcon.setIconVisible(false);
                mFolderIcon.drawLeaveBehindIfExists();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                setState(STATE_OPEN);
                announceAccessibilityChanges();
                AccessibilityManagerCompat.sendTestProtocolEventToTest(getContext(),
                        FOLDER_OPENED_MESSAGE);

                mContent.setFocusOnFirstChild();
            }
        });

        // Footer animation
        if (mContent.getPageCount() > 1 && !mInfo.hasOption(FolderInfo.FLAG_MULTI_PAGE_ANIMATION)) {
            int footerWidth = mContent.getDesiredWidth()
                    - mFooter.getPaddingLeft() - mFooter.getPaddingRight();

            float textWidth = mFolderName.getPaint().measureText(mFolderName.getText().toString());
            float translation = (footerWidth - textWidth) / 2;
            mFolderName.setTranslationX(mContent.mIsRtl ? -translation : translation);
            mPageIndicator.prepareEntryAnimation();

            // Do not update the flag if we are in drag mode. The flag will be updated, when we
            // actually drop the icon.
            final boolean updateAnimationFlag = !mIsDragInProgress;
            animatorSet.addListener(new AnimatorListenerAdapter() {

                @SuppressLint("InlinedApi")
                @Override
                public void onAnimationEnd(Animator animation) {
                    mFolderName.animate().setDuration(FOLDER_NAME_ANIMATION_DURATION)
                            .translationX(0)
                            .setInterpolator(AnimationUtils.loadInterpolator(
                                    getContext(), android.R.interpolator.fast_out_slow_in));
                    mPageIndicator.playEntryAnimation();

                    if (updateAnimationFlag) {
                        mInfo.setOption(FolderInfo.FLAG_MULTI_PAGE_ANIMATION, true,
                                mActivityContext.getModelWriter());
                    }
                }
            });
        } else {
            mFolderName.setTranslationX(0);
        }

        mPageIndicator.stopAllAnimations();

        // b/282158620 because setCurrentPlayTime() below will start animator, we need to register
        // {@link AnimatorListener} before it so that {@link AnimatorListener#onAnimationStart} can
        // be called to register mCurrentAnimator, which will be used to cancel animator
        addAnimationStartListeners(animatorSet);
        // Because t=0 has the folder match the folder icon, we can skip the
        // first frame and have the same movement one frame earlier.
        Log.d("b/311077782", "Folder.animateOpen");
        animatorSet.setCurrentPlayTime(Math.min(
                getSingleFrameMs(getContext()), animatorSet.getTotalDuration()));
        animatorSet.start();


        // Make sure the folder picks up the last drag move even if the finger doesn't move.
        if (mActivityContext.getDragController().isDragging()) {
            mActivityContext.getDragController().forceTouchMove();
        }
        mContent.verifyVisibleHighResIcons(mContent.getNextPage());
    }

    /**
     * Determines whether we should animate the folder opening.
     */
    boolean shouldAnimateOpen(List<ItemInfo> items) {
        if (items == null || items.size() <= 1) {
            Log.d(TAG, "Couldn't animate folder open because items is: " + items);
            return false;
        }
        return true;
    }

    private FolderAnimationCreator getFolderAnimationManager() {
        boolean shouldUseSpringMotion = Flags.enableLauncherIconShapes()
                && Flags.enableExpressiveFolderExpansion();
        if (shouldUseSpringMotion) {
            ShapeDelegate shapeDelegate =
                    ThemeManager.INSTANCE.get(mActivityContext.asContext()).getFolderShape();
            return new FolderAnimationSpringBuilderManager(
                    this, shapeDelegate, mLauncherDelegate
            );
        } else {
            return new FolderAnimationManager(this);
        }
    }

    /**
     * If there's a folder already open, we want to close it before opening another one.
     */
    @VisibleForTesting
    boolean closeOpenFolder(Folder openFolder) {
        if (openFolder != null && openFolder != this) {
            // Close any open folder before opening a folder.
            openFolder.close(true);
            return true;
        }
        return false;
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_FOLDER) != 0;
    }

    @Override
    protected void handleClose(boolean animate) {
        mIsOpen = false;
        mContent.setCanAnnouncePageDescriptionForFolder(false);

        if (!animate && mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.cancel();
        }

        if (mIsEditingName) {
            mFolderName.dispatchBackKey();
        }

        if (mFolderIcon != null) {
            mFolderIcon.clearLeaveBehindIfExists();
        }

        if (animate) {
            animateClosed();
        } else {
            closeComplete(false);
            post(this::announceAccessibilityChanges);
        }

        // Notify the accessibility manager that this folder "window" has disappeared and no
        // longer occludes the workspace items
        mActivityContext.getDragLayer().sendAccessibilityEvent(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    private void cancelRunningAnimations() {
        if (mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.cancel();
        }
    }

    private void animateClosed() {
        if (mIsAnimatingClosed) {
            return;
        }

        int size = getIconsInReadingOrder().size();
        if (size <= 1) {
            Log.d(TAG, "Couldn't animate folder closed because there's " + size + " icons");
            closeComplete(false);
            post(this::announceAccessibilityChanges);
            return;
        }

        mContent.completePendingPageChanges();
        mContent.snapToPageImmediately(mContent.getDestinationPage());

        cancelRunningAnimations();
        AnimatorSet animatorSet = getFolderAnimationManager()
                .createAnimatorSet(/* isOpening */ false);

        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (Utilities.ATLEAST_R) {
                    setWindowInsetsAnimationCallback(null);
                }
                mIsAnimatingClosed = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mKeyboardInsetAnimationCallback != null) {
                    if (Utilities.ATLEAST_R) {
                        setWindowInsetsAnimationCallback(mKeyboardInsetAnimationCallback);
                    }
                }
                closeComplete(true);
                announceAccessibilityChanges();
                mIsAnimatingClosed = false;
            }
        });
        addAnimationStartListeners(animatorSet);
        animatorSet.start();
    }

    @Override
    protected Pair<View, String> getAccessibilityTarget() {
        return Pair.create(mContent, mIsOpen ? mContent.getAccessibilityDescription()
                : getContext().getString(R.string.folder_closed));
    }

    @Override
    protected View getAccessibilityInitialFocusView() {
        View firstItem = mContent.getFirstItem();
        return firstItem != null ? firstItem : super.getAccessibilityInitialFocusView();
    }

    private void closeComplete(boolean wasAnimated) {
        // TODO: Clear all active animations.
        BaseDragLayer parent = (BaseDragLayer) getParent();
        if (parent != null) {
            parent.removeView(this);
        }
        mActivityContext.getDragController().removeDropTarget(this);
        clearFocus();
        if (mFolderIcon != null) {
            mFolderIcon.setVisibility(View.VISIBLE);
            mFolderIcon.setIconVisible(true);
            mFolderIcon.mFolderName.setTextVisibility(true);
            if (wasAnimated) {
                mFolderIcon.animateBgShadowAndStroke();
                mFolderIcon.onFolderClose(mContent.getCurrentPage());
                if (mFolderIcon.hasDot()) {
                    mFolderIcon.animateDotScale(0f, 1f);
                }
                mFolderIcon.requestFocus();
            }
        }

        if (mRearrangeOnClose) {
            rearrangeChildren();
            mRearrangeOnClose = false;
        }
        if (getItemCount() <= 1) {
            if (!mIsDragInProgress && !mSuppressFolderDeletion) {
                replaceFolderWithFinalItem();
            } else if (mIsDragInProgress) {
                mDeleteFolderOnDropCompleted = true;
            }
        } else if (!mIsDragInProgress) {
            mContent.unbindItems();
        }
        mSuppressFolderDeletion = false;
        clearDragInfo();
        setState(STATE_CLOSED);
        mContent.setCurrentPage(0);
    }

    @Override
    public boolean acceptDrop(DragObject d) {
        return willAcceptItemType(d.dragInfo.itemType);
    }

    public void onDragEnter(DragObject d) {
        mPrevTargetRank = -1;
        mOnExitAlarm.cancelAlarm();
        // Get the area offset such that the folder only closes if half the drag icon width
        // is outside the folder area
        mScrollAreaOffset = d.dragView.getDragRegionWidth() / 2 - d.xOffset;
    }

    OnAlarmListener mReorderAlarmListener = new OnAlarmListener() {
        public void onAlarm(Alarm alarm) {
            mContent.realTimeReorder(mEmptyCellRank, mTargetRank);
            mEmptyCellRank = mTargetRank;
        }
    };

    public boolean isLayoutRtl() {
        return (getLayoutDirection() == LAYOUT_DIRECTION_RTL);
    }

    private int getTargetRank(DragObject d, float[] recycle) {
        recycle = d.getVisualCenter(recycle);
        return mContent.findNearestArea(
                (int) recycle[0] - getPaddingLeft(), (int) recycle[1] - getPaddingTop());
    }

    @Override
    public void onDragOver(DragObject d) {
        if (mScrollPauseAlarm.alarmPending()) {
            return;
        }
        final float[] r = new float[2];
        mTargetRank = getTargetRank(d, r);

        if (mTargetRank != mPrevTargetRank) {
            mReorderAlarm.cancelAlarm();
            mReorderAlarm.setOnAlarmListener(mReorderAlarmListener);
            mReorderAlarm.setAlarm(REORDER_DELAY);
            mPrevTargetRank = mTargetRank;

            if (d.stateAnnouncer != null) {
                d.stateAnnouncer.announce(getContext().getString(R.string.move_to_position,
                        mTargetRank + 1));
            }
        }

        float x = r[0];
        int currentPage = mContent.getNextPage();

        float cellOverlap = mContent.getCurrentCellLayout().getCellWidth()
                * ICON_OVERSCROLL_WIDTH_FACTOR;
        boolean isOutsideLeftEdge = x < cellOverlap;
        boolean isOutsideRightEdge = x > (getWidth() - cellOverlap);

        if (currentPage > 0 && (mContent.mIsRtl ? isOutsideRightEdge : isOutsideLeftEdge)) {
            showScrollHint(SCROLL_LEFT, d);
        } else if (currentPage < (mContent.getPageCount() - 1)
                && (mContent.mIsRtl ? isOutsideLeftEdge : isOutsideRightEdge)) {
            showScrollHint(SCROLL_RIGHT, d);
        } else {
            mOnScrollHintAlarm.cancelAlarm();
            if (mScrollHintDir != SCROLL_NONE) {
                mContent.clearScrollHint();
                mScrollHintDir = SCROLL_NONE;
            }
        }
    }

    private void showScrollHint(int direction, DragObject d) {
        // Show scroll hint on the right
        if (mScrollHintDir != direction) {
            mContent.showScrollHint(direction);
            mScrollHintDir = direction;
        }

        // Set alarm for when the hint is complete
        if (!mOnScrollHintAlarm.alarmPending() || mCurrentScrollDir != direction) {
            mCurrentScrollDir = direction;
            mOnScrollHintAlarm.cancelAlarm();
            mOnScrollHintAlarm.setOnAlarmListener(new OnScrollHintListener(d));
            mOnScrollHintAlarm.setAlarm(SCROLL_HINT_DURATION);

            mReorderAlarm.cancelAlarm();
            mTargetRank = mEmptyCellRank;
        }
    }

    OnAlarmListener mOnExitAlarmListener = new OnAlarmListener() {
        public void onAlarm(Alarm alarm) {
            completeDragExit();
        }
    };

    public void completeDragExit() {
        if (isInAppDrawer()) {
            // LC: ff8c5a827b85f47a0d8ed5e6ac449ab8042705c6
            return;
        }
        if (mIsOpen) {
            close(true);
            mRearrangeOnClose = true;
        } else if (mState == STATE_ANIMATING) {
            mRearrangeOnClose = true;
        } else {
            rearrangeChildren();
            clearDragInfo();
        }
    }

    @VisibleForTesting
    void clearDragInfo() {
        mCurrentDragView = null;
        mIsExternalDrag = false;
    }

    public void onDragExit(DragObject d) {
        // We only close the folder if this is a true drag exit, ie. not because
        // a drop has occurred above the folder.
        if (!d.dragComplete) {
            mOnExitAlarm.setOnAlarmListener(mOnExitAlarmListener);
            mOnExitAlarm.setAlarm(ON_EXIT_CLOSE_DELAY);
        }
        mReorderAlarm.cancelAlarm();

        mOnScrollHintAlarm.cancelAlarm();
        mScrollPauseAlarm.cancelAlarm();
        if (mScrollHintDir != SCROLL_NONE) {
            mContent.clearScrollHint();
            mScrollHintDir = SCROLL_NONE;
        }
    }

    /**
     * When performing an accessibility drop, onDrop is sent immediately after onDragEnter. So we
     * need to complete all transient states based on timers.
     */
    @Override
    public void prepareAccessibilityDrop() {
        if (mReorderAlarm.alarmPending()) {
            mReorderAlarm.cancelAlarm();
            mReorderAlarmListener.onAlarm(mReorderAlarm);
        }
    }

    @Override
    public void onDropCompleted(final View target, final DragObject d,
            final boolean success) {
        if (success) {
            if (getItemCount() <= 1) {
                mDeleteFolderOnDropCompleted = true;
            }
            if (mDeleteFolderOnDropCompleted && !mItemAddedBackToSelfViaIcon
                    && target != this) {
                replaceFolderWithFinalItem();
            }
        } else {
            // The drag failed, we need to return the item to the folder
            ItemInfo info = d.dragInfo;
            View icon = (mCurrentDragView != null && mCurrentDragView.getTag() == info)
                    ? mCurrentDragView : mContent.createNewView(info);
            ArrayList<View> views = getIconsInReadingOrder();
            if (!views.contains(icon)) {
                info.rank = Utilities.boundToRange(info.rank, 0, views.size());
                views.add(info.rank, icon);
                mContent.arrangeChildren(views);
                mItemsInvalidated = true;

                executeWithContentUpdateSuppressed(
                        () -> mFolderIcon.onDrop(d, true /* itemReturnedOnFailedDrop */));
            }
        }

        if (target != this) {
            if (mOnExitAlarm.alarmPending()) {
                mOnExitAlarm.cancelAlarm();
                if (!success) {
                    mSuppressFolderDeletion = true;
                }
                mScrollPauseAlarm.cancelAlarm();
                completeDragExit();
            }
        }

        mDeleteFolderOnDropCompleted = false;
        mIsDragInProgress = false;
        mItemAddedBackToSelfViaIcon = false;
        mCurrentDragView = null;

        // Reordering may have occured, and we need to save the new item locations. We do this once
        // at the end to prevent unnecessary database operations.
        updateItemLocationsInDatabaseBatch(false);
        // Use the item count to check for multi-page as the folder UI may not have
        // been refreshed yet.
        if (getItemCount() <= mContent.itemsPerPage()) {
            // Show the animation, next time something is added to the folder.
            mInfo.setOption(FolderInfo.FLAG_MULTI_PAGE_ANIMATION, false,
                    mActivityContext.getModelWriter());
        }
    }

    private void updateItemLocationsInDatabaseBatch(boolean isBind) {
        FolderGridOrganizer verifier = createFolderGridOrganizer(
                mActivityContext.getDeviceProfile()
        ).setFolderInfo(mInfo);

        ArrayList<ItemInfo> items = new ArrayList<>();
        int total = mInfo.getContents().size();
        for (int i = 0; i < total; i++) {
            ItemInfo itemInfo = mInfo.getContents().get(i);
            if (verifier.updateRankAndPos(itemInfo, i)) {
                items.add(itemInfo);
            }
        }

        if (!items.isEmpty()) {
            mActivityContext.getModelWriter().moveItemsInDatabase(items, mInfo.id, 0);
        }
        if (!isBind && total > 1 /* no need to update if there's one icon */) {
            LauncherComponentProvider.get(getContext()).getFolderNameSuggestionLoader()
                    .getSuggestedFolderName(mInfo.getAppContents(),
                            folderNameInfos -> mInfo.suggestedFolderNames = folderNameInfos);
        }
    }

    public void notifyDrop() {
        if (mIsDragInProgress) {
            mItemAddedBackToSelfViaIcon = true;
        }
    }

    public boolean isDropEnabled() {
        return mState != STATE_ANIMATING;
    }

    private void centerAboutIcon() {
        BaseDragLayer.LayoutParams lp = (BaseDragLayer.LayoutParams) getLayoutParams();
        BaseDragLayer parent = mActivityContext.getDragLayer();
        int width = getFolderWidth();
        int height = getFolderHeight();

        parent.getDescendantRectRelativeToSelf(mFolderIcon, sTempRect);
        int centerX = sTempRect.centerX();
        int centerY = sTempRect.centerY();
        int centeredLeft = centerX - width / 2;
        int centeredTop = centerY - height / 2;

        sTempRect.set(mActivityContext.getFolderBoundingBox());
        int left = Utilities.boundToRange(centeredLeft, sTempRect.left, sTempRect.right - width);
        int top = Utilities.boundToRange(centeredTop, sTempRect.top, sTempRect.bottom - height);
        int[] inOutPosition = new int[]{left, top};
        mActivityContext.updateOpenFolderPosition(inOutPosition, sTempRect, width, height);
        left = inOutPosition[0];
        top = inOutPosition[1];

        int folderPivotX = width / 2 + (centeredLeft - left);
        int folderPivotY = height / 2 + (centeredTop - top);
        setPivotX(folderPivotX);
        setPivotY(folderPivotY);

        lp.width = width;
        lp.height = height;
        lp.x = left;
        lp.y = top;

        mBackground.setBounds(0, 0, width, height);
    }

    protected int getContentAreaHeight() {
        int height = Math.min(getMaxContentAreaHeight(),
                mContent.getDesiredHeight());
        return Math.max(height, MIN_CONTENT_DIMEN);
    }

    @VisibleForTesting
    int getMaxContentAreaHeight() {
        DeviceProfile grid = mActivityContext.getDeviceProfile();
        return grid.getDeviceProperties().getAvailableHeightPx() - grid.getTotalWorkspacePadding().y
                - getFooterHeight();
    }

    @VisibleForTesting
    int getContentAreaWidth() {
        return Math.max(mContent.getDesiredWidth(), MIN_CONTENT_DIMEN);
    }

    @VisibleForTesting
    int getFolderWidth() {
        return getPaddingLeft() + getPaddingRight() + mContent.getDesiredWidth();
    }

    @VisibleForTesting
    int getFolderHeight() {
        return getFolderHeight(getContentAreaHeight());
    }

    @VisibleForTesting
    int getFolderHeight(int contentAreaHeight) {
        return getPaddingTop() + getPaddingBottom() + contentAreaHeight + getFooterHeight();
    }

    @VisibleForTesting
    int getFooterHeight() {
        return mFooterHeight;
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int contentWidth = getContentAreaWidth();
        int contentHeight = getContentAreaHeight();

        int contentAreaWidthSpec = MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY);
        int contentAreaHeightSpec = MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY);

        mContent.setFixedSize(contentWidth, contentHeight);
        mContent.measure(contentAreaWidthSpec, contentAreaHeightSpec);

        mFooter.measure(contentAreaWidthSpec,
                MeasureSpec.makeMeasureSpec(mFooterHeight, MeasureSpec.EXACTLY));

        int folderWidth = getPaddingLeft() + getPaddingRight() + contentWidth;
        int folderHeight = getFolderHeight(contentHeight);
        setMeasuredDimension(folderWidth, folderHeight);
    }

    /**
     * If the Folder Title has less than 100dp of available width, we hide it. The reason we do this
     * calculation in onSizeChange is because this callback is called 1x when the folder is opened.
     * <p>
     * The PageIndicator and the Folder Title share the same horizontal linear layout, but both
     * are dynamically sized. Therefore, we are setting visibility of the folder title AFTER the
     * layout is measured.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int minTitleWidth = getResources().getDimensionPixelSize(R.dimen.folder_title_min_width);
        if (enableLauncherVisualRefresh() && mFolderName.getMeasuredWidth() < minTitleWidth) {
            ((MarginLayoutParams) mFolderName.getLayoutParams()).setMarginEnd(0);
            // The post is necessary for margins to be recalculated. RTL UI is shifted otherwise.
            mFolderName.post(() -> mFolderName.setVisibility(View.GONE));
            mFooter.setGravity(Gravity.END);
        }
    }

    /**
     * Rearranges the children based on their rank.
     */
    public void rearrangeChildren() {
        if (!mContent.areViewsBound()) {
            return;
        }
        mContent.arrangeChildren(getIconsInReadingOrder());
        mItemsInvalidated = true;
    }

    public int getItemCount() {
        return mInfo.getContents().size();
    }

    void replaceFolderWithFinalItem() {
        mDestroyed = mLauncherDelegate.replaceFolderWithFinalItem(this);
    }

    public boolean isDestroyed() {
        return mDestroyed;
    }

    // This method keeps track of the first and last item in the folder for the purposes
    // of keyboard focus
    public void updateTextViewFocus() {
        final View firstChild = mContent.getFirstItem();
        final View lastChild = mContent.getLastItem();
        if (firstChild != null && lastChild != null) {
            mFolderName.setNextFocusDownId(lastChild.getId());
            mFolderName.setNextFocusRightId(lastChild.getId());
            mFolderName.setNextFocusLeftId(lastChild.getId());
            mFolderName.setNextFocusUpId(lastChild.getId());
            // Hitting TAB from the folder name wraps around to the first item on the current
            // folder page, and hitting SHIFT+TAB from that item wraps back to the folder name.
            mFolderName.setNextFocusForwardId(firstChild.getId());
            // When clicking off the folder when editing the name, this Folder gains focus. When
            // pressing an arrow key from that state, give the focus to the first item.
            this.setNextFocusDownId(firstChild.getId());
            this.setNextFocusRightId(firstChild.getId());
            this.setNextFocusLeftId(firstChild.getId());
            this.setNextFocusUpId(firstChild.getId());
            // When pressing shift+tab in the above state, give the focus to the last item.
            setOnKeyListener(new OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    boolean isShiftPlusTab = keyCode == KeyEvent.KEYCODE_TAB &&
                            event.hasModifiers(KeyEvent.META_SHIFT_ON);
                    if (isShiftPlusTab && Folder.this.isFocused()) {
                        return lastChild.requestFocus();
                    }
                    return false;
                }
            });
        } else {
            setOnKeyListener(null);
        }
    }

    @Override
    public void onDrop(DragObject d, DragOptions options) {
        // If the icon was dropped while the page was being scrolled, we need to compute
        // the target location again such that the icon is placed of the final page.
        if (!mContent.rankOnCurrentPage(mEmptyCellRank)) {
            // Reorder again.
            mTargetRank = getTargetRank(d, null);

            // Rearrange items immediately.
            mReorderAlarmListener.onAlarm(mReorderAlarm);

            mOnScrollHintAlarm.cancelAlarm();
            mScrollPauseAlarm.cancelAlarm();
        }
        mContent.completePendingPageChanges();
        Launcher launcher = mLauncherDelegate.getLauncher();
        if (launcher == null) {
            return;
        }

        PendingAddShortcutInfo pasi = d.dragInfo instanceof PendingAddShortcutInfo
                ? (PendingAddShortcutInfo) d.dragInfo : null;
        WorkspaceItemInfo pasiSi =
                pasi != null ? pasi.getActivityInfo(launcher).createWorkspaceItemInfo() : null;
        if (pasi != null && pasiSi == null) {
            // There is no WorkspaceItemInfo, so we have to go through a configuration activity.
            pasi.container = mInfo.id;
            pasi.rank = mEmptyCellRank;

            launcher.addPendingItem(pasi, pasi.container, pasi.screenId, null, pasi.spanX,
                    pasi.spanY);
            d.deferDragViewCleanupPostAnimation = false;
            mRearrangeOnClose = true;
        } else {
            final ItemInfo si;
            if (pasiSi != null) {
                si = pasiSi;
            } else if (d.dragInfo instanceof WorkspaceItemFactory) {
                // Came from all apps -- make a copy.
                si = ((WorkspaceItemFactory) d.dragInfo).makeWorkspaceItem(launcher);
            } else {
                // WorkspaceItemInfo or AppPairInfo
                si = d.dragInfo;
            }

            View currentDragView;
            if (mIsExternalDrag) {
                currentDragView = mContent.createAndAddViewForRank(si, mEmptyCellRank);

                // Actually move the item in the database if it was an external drag. Call this
                // before creating the view, so that the ItemInfo is updated appropriately.
                mActivityContext.getModelWriter().addOrMoveItemInDatabase(
                        si, mInfo.id, 0, si.cellX, si.cellY);
                mIsExternalDrag = false;
            } else {
                currentDragView = mCurrentDragView;
                mContent.addViewForRank(currentDragView, si, mEmptyCellRank);
            }

            if (d.dragView.hasDrawn()) {
                // Temporarily reset the scale such that the animation target gets calculated
                // correctly.
                float scaleX = getScaleX();
                float scaleY = getScaleY();
                setScaleX(1.0f);
                setScaleY(1.0f);
                launcher.getDragLayer().animateViewIntoPosition(d.dragView, currentDragView, null);
                setScaleX(scaleX);
                setScaleY(scaleY);
            } else {
                d.deferDragViewCleanupPostAnimation = false;
                currentDragView.setVisibility(VISIBLE);
            }

            mItemsInvalidated = true;
            rearrangeChildren();

            // Temporarily suppress the listener, as we did all the work already here.
            executeWithContentUpdateSuppressed(() -> addFolderContent(si, mEmptyCellRank, false));

            // We only need to update the locations if it doesn't get handled in
            // #onDropCompleted.
            if (d.dragSource != this) {
                updateItemLocationsInDatabaseBatch(false);
            }
        }

        // Clear the drag info, as it is no longer being dragged.
        mIsDragInProgress = false;

        if (mContent.getPageCount() > 1) {
            // The animation has already been shown while opening the folder.
            mInfo.setOption(FolderInfo.FLAG_MULTI_PAGE_ANIMATION, true,
                    mActivityContext.getModelWriter());
        }

        if (!launcher.isInState(EDIT_MODE)) {
            launcher.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
        }

        if (d.stateAnnouncer != null) {
            d.stateAnnouncer.completeAction(R.string.item_moved);
        }
        mStatsLogManager.logger().withItemInfo(d.dragInfo).withInstanceId(d.logInstanceId)
                .log(LAUNCHER_ITEM_DROP_COMPLETED);
    }

    // This is used so the item doesn't immediately appear in the folder when added. In one case
    // we need to create the illusion that the item isn't added back to the folder yet, to
    // to correspond to the animation of the icon back into the folder. This is
    public void hideItem(ItemInfo info) {
        View v = getViewForInfo(info);
        if (v != null) {
            v.setVisibility(INVISIBLE);
        }
    }

    public void showItem(ItemInfo info) {
        View v = getViewForInfo(info);
        if (v != null) {
            v.setVisibility(VISIBLE);
        }
    }

    /** Add an app or shortcut */
    public void addFolderContent(ItemInfo item) {
        addFolderContent(item, mInfo.getContents().size(), true);
    }

    /** Add an app or shortcut for a specified rank */
    public void addFolderContent(ItemInfo item, int rank, boolean animate) {
        if (!willAcceptItemType(item.itemType)) {
            throw new RuntimeException("tried to add an illegal type into a folder");
        }

        rank = Utilities.boundToRange(rank, 0, mInfo.getContents().size());
        mInfo.getContents().add(rank, item);

        if (!mSuppressContentUpdate) {
            FolderGridOrganizer verifier = createFolderGridOrganizer(
                    mActivityContext.getDeviceProfile()).setFolderInfo(mInfo);
            verifier.updateRankAndPos(item, rank);
            mActivityContext.getModelWriter().addOrMoveItemInDatabase(item, mInfo.id, 0,
                    item.cellX,
                    item.cellY);
            updateItemLocationsInDatabaseBatch(false);

            if (mContent.areViewsBound()) {
                mContent.createAndAddViewForRank(item, rank);
            }
            mItemsInvalidated = true;
            updateTextViewFocus();
        }

        mActivityContext.getModelWriter().notifyItemModified(mInfo);
        mFolderIcon.onItemsChanged(animate);
    }

    /** Remove all matching app or shortcut. Does not change the DB. */
    public void removeFolderContent(boolean animate, ItemInfo... items) {
        List<ItemInfo> itemArray = Arrays.asList(items);
        if (mInfo.getContents().removeAll(itemArray)) {
            mActivityContext.getModelWriter().notifyItemModified(mInfo);
        }

        if (!mSuppressContentUpdate) {
            mItemsInvalidated = true;
            itemArray.forEach(item -> mContent.removeItem(getViewForInfo(item)));
            if (mState == STATE_ANIMATING) {
                mRearrangeOnClose = true;
            } else {
                rearrangeChildren();
            }
            if (getItemCount() <= 1) {
                if (mIsOpen) {
                    close(true);
                } else {
                    replaceFolderWithFinalItem();
                }
            }
            updateTextViewFocus();
        }

        mFolderIcon.onItemsChanged(animate);
    }

    @VisibleForTesting
    View getViewForInfo(final ItemInfo item) {
        return mContent.iterateOverItems((info, view) -> info == item);
    }

    @Override
    public boolean isContainerSupported(int container) {
        return container == mInfo.id;
    }

    /**
     * Utility methods to iterate over items of the view
     */
    @Override
    @Nullable
    public View mapOverItems(@NonNull ItemOperator op) {
        return mContent.iterateOverItems(op);
    }

    /**
     * Returns the sorted list of all the icons in the folder
     */
    public ArrayList<View> getIconsInReadingOrder() {
        if (mItemsInvalidated) {
            mItemsInReadingOrder.clear();
            mContent.iterateOverItems((i, v) -> !mItemsInReadingOrder.add(v));
            mItemsInvalidated = false;
        }
        return mItemsInReadingOrder;
    }

    public List<View> getItemsOnPage(int page) {
        ArrayList<View> allItems = getIconsInReadingOrder();
        int lastPage = mContent.getPageCount() - 1;
        int totalItemsInFolder = allItems.size();
        int itemsPerPage = mContent.itemsPerPage();
        int numItemsOnCurrentPage = page == lastPage
                ? totalItemsInFolder - (itemsPerPage * page)
                : itemsPerPage;

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + numItemsOnCurrentPage, allItems.size());

        List<View> itemsOnCurrentPage = new ArrayList<>(numItemsOnCurrentPage);
        for (int i = startIndex; i < endIndex; ++i) {
            itemsOnCurrentPage.add(allItems.get(i));
        }
        return itemsOnCurrentPage;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v == mFolderName) {
            if (hasFocus) {
                mFromLabelState = mInfo.getFromLabelState();
                mFromTitle = mInfo.title;
                post(this::startEditingFolderName);
            } else {
                StatsLogger statsLogger = mStatsLogManager.logger()
                        .withItemInfo(mInfo)
                        .withFromState(mFromLabelState);

                // If the folder label is suggested, it is logged to improve prediction model.
                // When both old and new labels are logged together delimiter is used.
                StringJoiner labelInfoBuilder = new StringJoiner(FOLDER_LABEL_DELIMITER);
                if (mFromLabelState.equals(FromState.FROM_SUGGESTED)) {
                    labelInfoBuilder.add(mFromTitle);
                }

                ToState toLabelState;
                if (mFromTitle != null && mFromTitle.equals(mInfo.title)) {
                    toLabelState = ToState.UNCHANGED;
                } else {
                    toLabelState = mInfo.getToLabelState();
                    if (toLabelState.toString().startsWith("TO_SUGGESTION")) {
                        labelInfoBuilder.add(mInfo.title);
                    }
                }
                statsLogger.withToState(toLabelState);

                if (labelInfoBuilder.length() > 0) {
                    statsLogger.withEditText(labelInfoBuilder.toString());
                }

                statsLogger.log(LAUNCHER_FOLDER_LABEL_UPDATED);
                mFolderName.dispatchBackKey();
            }
        }
    }

    @Override
    public void getHitRectRelativeToDragLayer(Rect outRect) {
        getHitRect(outRect);
        outRect.left -= mScrollAreaOffset;
        outRect.right += mScrollAreaOffset;
    }

    private class OnScrollHintListener implements OnAlarmListener {

        private final DragObject mDragObject;

        OnScrollHintListener(DragObject object) {
            mDragObject = object;
        }

        /**
         * Scroll hint has been shown long enough. Now scroll to appropriate page.
         */
        @Override
        public void onAlarm(Alarm alarm) {
            if (mCurrentScrollDir == SCROLL_LEFT) {
                mContent.scrollLeft();
                mScrollHintDir = SCROLL_NONE;
            } else if (mCurrentScrollDir == SCROLL_RIGHT) {
                mContent.scrollRight();
                mScrollHintDir = SCROLL_NONE;
            } else {
                // This should not happen
                return;
            }
            mCurrentScrollDir = SCROLL_NONE;

            // Pause drag event until the scrolling is finished
            mScrollPauseAlarm.setOnAlarmListener(new OnScrollFinishedListener(mDragObject));
            int rescrollDelay = getResources().getInteger(
                    R.integer.config_pageSnapAnimationDuration) + RESCROLL_EXTRA_DELAY;
            mScrollPauseAlarm.setAlarm(rescrollDelay);
        }
    }

    private class OnScrollFinishedListener implements OnAlarmListener {

        private final DragObject mDragObject;

        OnScrollFinishedListener(DragObject object) {
            mDragObject = object;
        }

        /**
         * Page scroll is complete.
         */
        @Override
        public void onAlarm(Alarm alarm) {
            // Reorder immediately on page change.
            onDragOver(mDragObject);
        }
    }

    // Compares item position based on rank and position giving priority to the rank.
    public static final Comparator<ItemInfo> ITEM_POS_COMPARATOR = new Comparator<ItemInfo>() {

        @Override
        public int compare(ItemInfo lhs, ItemInfo rhs) {
            if (lhs.rank != rhs.rank) {
                return lhs.rank - rhs.rank;
            } else if (lhs.cellY != rhs.cellY) {
                return lhs.cellY - rhs.cellY;
            } else {
                return lhs.cellX - rhs.cellX;
            }
        }
    };

    /** Executes the task while suppressing the content update for the folder */
    private void executeWithContentUpdateSuppressed(Runnable task) {
        if (mSuppressContentUpdate) {
            task.run();
        } else {
            mSuppressContentUpdate = true;
            task.run();
            mSuppressContentUpdate = false;
            updateTextViewFocus();
        }
    }

    /**
     * Returns a folder which is already open or null
     */
    public static Folder getOpen(ActivityContext activityContext) {
        return getOpenView(activityContext, TYPE_FOLDER);
    }

    /** Navigation bar back key or hardware input back key has been issued. */
    @Override
    public void onBackInvoked() {
        if (mIsEditingName) {
            mFolderName.dispatchBackKey();
        } else {
            super.onBackInvoked();
        }
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            BaseDragLayer dl = (BaseDragLayer) getParent();

            if (mIsEditingName) {
                if (!dl.isEventOverView(mFolderName, ev)) {
                    mFolderName.dispatchBackKey();
                    return true;
                }
                return false;
            } else if (!dl.isEventOverView(this, ev)
                    && mLauncherDelegate.interceptOutsideTouch(ev, dl, this)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canInterceptEventsInSystemGestureRegion() {
        return !mIsEditingName;
    }

    /**
     * Alternative to using {@link #getClipToOutline()} as it only works with derivatives of
     * rounded rect.
     */
    @Override
    public void setClipPath(Path clipPath) {
        mClipPath = clipPath;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mClipPath != null) {
            int count = canvas.save();
            canvas.clipPath(mClipPath);
            mBackground.draw(canvas);
            canvas.restoreToCount(count);
            super.dispatchDraw(canvas);
        } else {
            mBackground.draw(canvas);
            super.dispatchDraw(canvas);
        }
    }

    public FolderPagedView getContent() {
        return mContent;
    }

    @VisibleForTesting
    void setItemAddedBackToSelfViaIcon(boolean value) {
        mItemAddedBackToSelfViaIcon = value;
    }

    @VisibleForTesting
    boolean getItemAddedBackToSelfViaIcon() {
        return mItemAddedBackToSelfViaIcon;
    }

    @VisibleForTesting
    void setIsDragInProgress(boolean value) {
        mIsDragInProgress = value;
    }

    @VisibleForTesting
    boolean getIsDragInProgress() {
        return mIsDragInProgress;
    }

    @VisibleForTesting
    View getCurrentDragView() {
        return mCurrentDragView;
    }

    @VisibleForTesting
    void setCurrentDragView(View view) {
        mCurrentDragView = view;
    }

    @VisibleForTesting
    boolean getItemsInvalidated() {
        return mItemsInvalidated;
    }

    @VisibleForTesting
    void setItemsInvalidated(boolean value) {
        mItemsInvalidated = value;
    }

    @VisibleForTesting
    boolean getIsExternalDrag() {
        return mIsExternalDrag;
    }

    @VisibleForTesting
    void setIsExternalDrag(boolean value) {
        mIsExternalDrag = value;
    }

    public boolean getIsEditingName() {
        return mIsEditingName;
    }

    @VisibleForTesting
    void setIsEditingName(boolean value) {
        mIsEditingName = value;
    }

    @VisibleForTesting
    void setFolderName(FolderNameEditText value) {
        mFolderName = value;
    }

    FolderNameEditText getFolderName() {
        return mFolderName;
    }

    @VisibleForTesting
    boolean getIsOpen() {
        return mIsOpen;
    }

    @VisibleForTesting
    void setIsOpen(boolean value) {
        mIsOpen = value;
    }

    @VisibleForTesting
    boolean getRearrangeOnClose() {
        return mRearrangeOnClose;
    }

    @VisibleForTesting
    void setRearrangeOnClose(boolean value) {
        mRearrangeOnClose = value;
    }

    /** Returns the height of the current folder's bottom edge from the bottom of the screen. */
    private int getHeightFromBottom() {
        BaseDragLayer.LayoutParams layoutParams = (BaseDragLayer.LayoutParams) getLayoutParams();
        int folderBottomPx = layoutParams.y + layoutParams.height;
        int windowBottomPx = mActivityContext.getDeviceProfile().getDeviceProperties().getHeightPx();

        return windowBottomPx - folderBottomPx;
    }

    @VisibleForTesting
    boolean getDeleteFolderOnDropCompleted() {
        return mDeleteFolderOnDropCompleted;
    }

    @VisibleForTesting
    void setDeleteFolderOnDropCompleted(boolean value) {
        mDeleteFolderOnDropCompleted = value;
    }

    /**
     * Save this listener for the special case of when we update the state and concurrently
     * add another listener to {@link #mOnFolderStateChangedListeners} to avoid a
     * ConcurrentModificationException
     */
    public void setPriorityOnFolderStateChangedListener(OnFolderStateChangedListener listener) {
        mPriorityOnFolderStateChangedListener = listener;
    }

    @VisibleForTesting
    int getState() {
        return mState;
    }

    @VisibleForTesting
    void setState(@FolderState int newState) {
        mState = newState;
        if (mPriorityOnFolderStateChangedListener != null) {
            mPriorityOnFolderStateChangedListener.onFolderStateChanged(mState);
        }
        for (OnFolderStateChangedListener listener : mOnFolderStateChangedListeners) {
            if (listener != null) {
                listener.onFolderStateChanged(mState);
            }
        }
    }

    @VisibleForTesting
    Alarm getOnExitAlarm() {
        return mOnExitAlarm;
    }

    @VisibleForTesting
    void setOnExitAlarm(Alarm value) {
        mOnExitAlarm = value;
    }

    @VisibleForTesting
    Alarm getReorderAlarm() {
        return mReorderAlarm;
    }

    @VisibleForTesting
    void setReorderAlarm(Alarm value) {
        mReorderAlarm = value;
    }

    @VisibleForTesting
    Alarm getOnScrollHintAlarm() {
        return mOnScrollHintAlarm;
    }

    @VisibleForTesting
    void setOnScrollHintAlarm(Alarm value) {
        mOnScrollHintAlarm = value;
    }

    @VisibleForTesting
    Alarm getScrollPauseAlarm() {
        return mScrollPauseAlarm;
    }

    @VisibleForTesting
    void setScrollPauseAlarm(Alarm value) {
        mScrollPauseAlarm = value;
    }

    @VisibleForTesting
    int getScrollHintDir() {
        return mScrollHintDir;
    }

    @VisibleForTesting
    void setScrollHintDir(int value) {
        mScrollHintDir = value;
    }

    @VisibleForTesting
    int getScrollAreaOffset() {
        return mScrollAreaOffset;
    }
    /**
     * Adds the provided listener to the running list of Folder listeners
     * {@link #mOnFolderStateChangedListeners}
     */
    public void addOnFolderStateChangedListener(@Nullable OnFolderStateChangedListener listener) {
        if (listener != null) {
            mOnFolderStateChangedListeners.add(listener);
        }
    }

    /** Removes the provided listener from the running list of Folder listeners */
    public void removeOnFolderStateChangedListener(OnFolderStateChangedListener listener) {
        mOnFolderStateChangedListeners.remove(listener);
    }

    /** Listener that can be registered via {@link #addOnFolderStateChangedListener} */
    public interface OnFolderStateChangedListener {
        /** See {@link Folder.FolderState} */
        void onFolderStateChanged(@FolderState int newState);
    }
}
