/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.content.ClipDescription.MIMETYPE_TEXT_INTENT;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.dragndrop.SimpleDragLayer;
import com.android.launcher3.model.StringCache;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.model.WidgetPredictionsRequester;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.widget.WidgetCell;
import com.android.launcher3.widget.model.WidgetsListBaseEntriesBuilder;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;
import com.android.launcher3.widget.picker.WidgetCategoryFilter;
import com.android.launcher3.widget.picker.WidgetsFullSheet;
import com.android.launcher3.widget.picker.model.WidgetPickerDataProvider;
import com.android.systemui.animation.back.FlingOnBackAnimationCallback;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** An Activity that can host Launcher's widget picker. */
public class WidgetPickerActivity extends BaseActivity implements
        WidgetPredictionsRequester.WidgetPredictionsListener {
    private static final String TAG = "WidgetPickerActivity";
    /**
     * Name of the extra that indicates that a widget being dragged.
     *
     * <p>When set to "true" in the result of startActivityForResult, the client that launched the
     * picker knows that activity was closed due to pending drag.
     */
    private static final String EXTRA_IS_PENDING_WIDGET_DRAG = "is_pending_widget_drag";

    // Intent extras that specify the desired widget width and height. If these are not specified in
    // the intent, then widgets will not be filtered for size.
    private static final String EXTRA_DESIRED_WIDGET_WIDTH = "desired_widget_width";
    private static final String EXTRA_DESIRED_WIDGET_HEIGHT = "desired_widget_height";
    // Unlike the AppWidgetManager.EXTRA_CATEGORY_FILTER, this filter removes certain categories.
    // Filter is ignore if it is not a negative value.
    // Example usage: WIDGET_CATEGORY_HOME_SCREEN.inv() and WIDGET_CATEGORY_NOT_KEYGUARD.inv()
    private static final String EXTRA_CATEGORY_EXCLUSION_FILTER = "category_exclusion_filter";
    /**
     * Widgets currently added by the user in the UI surface.
     * <p>This allows widget picker to exclude existing widgets from suggestions.</p>
     */
    private static final String EXTRA_ADDED_APP_WIDGETS = "added_app_widgets";
    /**
     * Intent extra for the string representing the title displayed within the picker header.
     */
    private static final String EXTRA_PICKER_TITLE = "picker_title";
    /**
     * Intent extra for the string representing the description displayed within the picker header.
     */
    private static final String EXTRA_PICKER_DESCRIPTION = "picker_description";

    /**
     * A unique identifier of the surface hosting the widgets;
     * <p>"widgets" is reserved for home screen surface.</p>
     * <p>"widgets_hub" is reserved for glanceable hub surface.</p>
     */
    private static final String EXTRA_UI_SURFACE = "ui_surface";
    private static final Pattern UI_SURFACE_PATTERN =
            Pattern.compile("^(widgets|widgets_hub)$");

    /**
     * User ids that should be filtered out of the widget lists created by this activity.
     */
    private static final String EXTRA_USER_ID_FILTER = "filtered_user_ids";

    private SimpleDragLayer<WidgetPickerActivity> mDragLayer;
    private WidgetsModel mModel;
    private LauncherAppState mApp;
    private StringCache mStringCache;
    private WidgetPredictionsRequester mWidgetPredictionsRequester;
    private WidgetPickerDataProvider mWidgetPickerDataProvider;

    private int mDesiredWidgetWidth;
    private int mDesiredWidgetHeight;
    private WidgetCategoryFilter mWidgetCategoryInclusionFilter;
    private WidgetCategoryFilter mWidgetCategoryExclusionFilter;
    @Nullable
    private String mUiSurface;
    // Widgets existing on the host surface.
    @NonNull
    private List<AppWidgetProviderInfo> mAddedWidgets = new ArrayList<>();
    @Nullable
    private String mTitle;
    @Nullable
    private String mDescription;

    /** A set of user ids that should be filtered out from the selected widgets. */
    @NonNull
    Set<Integer> mFilteredUserIds = new HashSet<>();

    @Nullable
    private WidgetsFullSheet mWidgetSheet;

    private final Predicate<WidgetItem> mNoShortcutsFilter = widget -> {
        final WidgetAcceptabilityVerdict verdict =
                isWidgetAcceptable(widget, /* applySizeFilter=*/ false);
        verdict.maybeLogVerdict();
        return verdict.isAcceptable;
    };
    private final Predicate<WidgetItem> mHostSizeAndNoShortcutsFilter = widget -> {
        final WidgetAcceptabilityVerdict verdict =
                isWidgetAcceptable(widget, /* applySizeFilter=*/ true);
        verdict.maybeLogVerdict();
        return verdict.isAcceptable;
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);

        mApp = LauncherAppState.getInstance(this);
        InvariantDeviceProfile idp = mApp.getInvariantDeviceProfile();
        mDeviceProfile = idp.getDeviceProfile(this);
        mModel = new WidgetsModel(mApp.getContext());
        mWidgetPickerDataProvider = new WidgetPickerDataProvider(this);

        setContentView(R.layout.widget_picker_activity);
        mDragLayer = findViewById(R.id.drag_layer);
        mDragLayer.recreateControllers();

        WindowInsetsController wc = mDragLayer.getWindowInsetsController();
        wc.hide(navigationBars() + statusBars());

        parseIntentExtras();
        refreshAndBindWidgets();
    }

    @Override
    protected void registerBackDispatcher() {
        if (Utilities.ATLEAST_T) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    new BackAnimationCallback());
        }
    }

    private void parseIntentExtras() {
        mTitle = getIntent().getStringExtra(EXTRA_PICKER_TITLE);
        mDescription = getIntent().getStringExtra(EXTRA_PICKER_DESCRIPTION);

        // A value of 0 for either size means that no filtering will occur in that dimension. If
        // both values are 0, then no size filtering will occur.
        mDesiredWidgetWidth =
                getIntent().getIntExtra(EXTRA_DESIRED_WIDGET_WIDTH, 0);
        mDesiredWidgetHeight =
                getIntent().getIntExtra(EXTRA_DESIRED_WIDGET_HEIGHT, 0);

        // Defaults to '0' to indicate that there isn't a category filter.
        // Negative value indicates it's an exclusion filter (e.g. NOT_KEYGUARD_CATEGORY.inv())
        // Positive value indicates it's inclusion filter (e.g. HOME_SCREEN or KEYGUARD)
        // Note: A filter can either be inclusion or exclusion filter; not both.
        int inclusionFilter = getIntent().getIntExtra(AppWidgetManager.EXTRA_CATEGORY_FILTER, 0);
        if (inclusionFilter < 0) {
            Log.w(TAG, "Invalid EXTRA_CATEGORY_FILTER: " + inclusionFilter);
        }
        mWidgetCategoryInclusionFilter = new WidgetCategoryFilter(max(0, inclusionFilter));
        int exclusionFilter = getIntent().getIntExtra(EXTRA_CATEGORY_EXCLUSION_FILTER, 0);
        if (exclusionFilter > 0) {
            Log.w(TAG, "Invalid EXTRA_CATEGORY_EXCLUSION_FILTER: " + exclusionFilter);
        }
        mWidgetCategoryExclusionFilter = new WidgetCategoryFilter(min(0 , exclusionFilter));

        String uiSurfaceParam = getIntent().getStringExtra(EXTRA_UI_SURFACE);
        if (uiSurfaceParam != null && UI_SURFACE_PATTERN.matcher(uiSurfaceParam).matches()) {
            mUiSurface = uiSurfaceParam;
        }
        if (Utilities.ATLEAST_T) {
            ArrayList<AppWidgetProviderInfo> addedWidgets = getIntent().getParcelableArrayListExtra(
                    EXTRA_ADDED_APP_WIDGETS, AppWidgetProviderInfo.class);
            if (addedWidgets != null) {
                mAddedWidgets = addedWidgets;
            }
        }
        ArrayList<Integer> filteredUsers = getIntent().getIntegerArrayListExtra(
                EXTRA_USER_ID_FILTER);
        mFilteredUserIds.clear();
        if (filteredUsers != null) {
            mFilteredUserIds.addAll(filteredUsers);
        }
    }

    @NonNull
    @Override
    public WidgetPickerDataProvider getWidgetPickerDataProvider() {
        return mWidgetPickerDataProvider;
    }

    @Override
    public SimpleDragLayer<WidgetPickerActivity> getDragLayer() {
        return mDragLayer;
    }

    @Override
    public View.OnClickListener getItemOnClickListener() {
        return v -> {
            final AppWidgetProviderInfo info =
                    (v instanceof WidgetCell) ? ((WidgetCell) v).getWidgetItem().widgetInfo : null;
            if (info == null || info.provider == null) {
                return;
            }

            setResult(RESULT_OK, new Intent()
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, info.provider)
                    .putExtra(Intent.EXTRA_USER, info.getProfile()));

            finish();
        };
    }

    @Override
    public View.OnLongClickListener getAllAppsItemLongClickListener() {
        return view -> {
            if (!(view instanceof WidgetCell widgetCell)) return false;

            if (widgetCell.getWidgetView().getDrawable() == null
                    && widgetCell.getAppWidgetHostViewPreview() == null) {
                // The widget preview hasn't been loaded; so, we abort the drag.
                return false;
            }

            final AppWidgetProviderInfo info = widgetCell.getWidgetItem().widgetInfo;
            if (info == null || info.provider == null) {
                return false;
            }

            View dragView = widgetCell.getDragAndDropView();
            if (dragView == null) {
                return false;
            }

            ClipData clipData = new ClipData(
                    new ClipDescription(
                            /* label= */ "", // not displayed anywhere; so, set to empty.
                            new String[]{MIMETYPE_TEXT_INTENT}
                    ),
                    new ClipData.Item(new Intent()
                            .putExtra(Intent.EXTRA_USER, info.getProfile())
                            .putExtra(Intent.EXTRA_COMPONENT_NAME, info.provider))
            );

            // Set result indicating activity was closed due a widget being dragged.
            setResult(RESULT_OK, new Intent()
                    .putExtra(EXTRA_IS_PENDING_WIDGET_DRAG, true));

            // DRAG_FLAG_GLOBAL permits dragging data beyond app window.
            return dragView.startDragAndDrop(
                    clipData,
                    new View.DragShadowBuilder(dragView),
                    /* myLocalState= */ null,
                    View.DRAG_FLAG_GLOBAL
            );
        };
    }

    /**
     * Updates the model with widgets, applies filters and launches the widgets sheet once
     * widgets are available
     */
    private void refreshAndBindWidgets() {
        MODEL_EXECUTOR.execute(() -> {
            mModel.update(null);

            StringCache stringCache = new StringCache();
            stringCache.loadStrings(this);

            bindStringCache(stringCache);
            bindWidgets(mModel.getWidgetsByPackageItemForPicker());
            // Open sheet once widgets are available, so that it doesn't interrupt the open
            // animation.
            openWidgetsSheet();
            if (mUiSurface != null) {
                mWidgetPredictionsRequester = new WidgetPredictionsRequester(
                        getApplicationContext(), mUiSurface,
                        mModel.getWidgetsByComponentKeyForPicker());
                mWidgetPredictionsRequester.request(mAddedWidgets, this);
            }
        });
    }

    private void bindStringCache(final StringCache stringCache) {
        MAIN_EXECUTOR.execute(() -> mStringCache = stringCache);
    }

    private void bindWidgets(Map<PackageItemInfo, List<WidgetItem>> widgets) {
        WidgetsListBaseEntriesBuilder builder = new WidgetsListBaseEntriesBuilder(
                mApp.getContext());
        final List<WidgetsListBaseEntry> allWidgets = builder.build(widgets, mNoShortcutsFilter);

        // Default list is shown if host has additionally enforced size filtering.
        @Nullable Predicate<WidgetItem> defaultListFilter =
                hasHostSizeFilters() ? mHostSizeAndNoShortcutsFilter : null;

        MAIN_EXECUTOR.execute(() -> {
            mWidgetPickerDataProvider.setHostSpecifiedDefaultWidgetsFilter(defaultListFilter);
            mWidgetPickerDataProvider.setWidgets(allWidgets);
        });
    }

    private void openWidgetsSheet() {
        MAIN_EXECUTOR.execute(() -> {
            mWidgetSheet = WidgetsFullSheet.show(this, true);
            mWidgetSheet.mayUpdateTitleAndDescription(mTitle, mDescription);
            mWidgetSheet.disableNavBarScrim(true);
            mWidgetSheet.addOnCloseListener(this::finish);
        });
    }

    @Override
    public void onPredictionsAvailable(List<ItemInfo> recommendedWidgets) {
        // Bind recommendations once picker has finished open animation.
        MAIN_EXECUTOR.getHandler().postDelayed(
                () -> mWidgetPickerDataProvider.setWidgetRecommendations(recommendedWidgets),
                mDeviceProfile.bottomSheetOpenDuration);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWidgetPickerDataProvider.destroy();
        if (mWidgetPredictionsRequester != null) {
            mWidgetPredictionsRequester.clear();
        }
    }

    @Nullable
    @Override
    public StringCache getStringCache() {
        return mStringCache;
    }

    /**
     * Animation callback for different predictive back animation states for the widget picker.
     */
    private class BackAnimationCallback extends FlingOnBackAnimationCallback {
        @Nullable
        OnBackAnimationCallback mActiveOnBackAnimationCallback;

        @Override
        public void onBackStartedCompat(@NonNull BackEvent backEvent) {
            if (mActiveOnBackAnimationCallback != null) {
                mActiveOnBackAnimationCallback.onBackCancelled();
            }
            if (mWidgetSheet != null) {
                mActiveOnBackAnimationCallback = mWidgetSheet;
                mActiveOnBackAnimationCallback.onBackStarted(backEvent);
            }
        }

        @Override
        public void onBackInvokedCompat() {
            if (mActiveOnBackAnimationCallback == null) {
                return;
            }
            mActiveOnBackAnimationCallback.onBackInvoked();
            mActiveOnBackAnimationCallback = null;
        }

        @Override
        public void onBackProgressedCompat(@NonNull BackEvent backEvent) {
            if (mActiveOnBackAnimationCallback == null) {
                return;
            }
            mActiveOnBackAnimationCallback.onBackProgressed(backEvent);
        }

        @Override
        public void onBackCancelledCompat() {
            if (mActiveOnBackAnimationCallback == null) {
                return;
            }
            mActiveOnBackAnimationCallback.onBackCancelled();
            mActiveOnBackAnimationCallback = null;
        }
    }

    private boolean hasHostSizeFilters() {
        // If optional filters such as size filter are present, we display them as default widgets.
        return mDesiredWidgetWidth != 0 || mDesiredWidgetHeight != 0;
    }

    private WidgetAcceptabilityVerdict isWidgetAcceptable(WidgetItem widget,
            boolean applySizeFilter) {
        final AppWidgetProviderInfo info = widget.widgetInfo;
        if (info == null) {
            return rejectWidget(widget, "shortcut");
        }

        if (mFilteredUserIds.contains(widget.user.getIdentifier())) {
            return rejectWidget(
                    widget,
                    "widget user: %d is being filtered",
                    widget.user.getIdentifier());
        }

        if (!mWidgetCategoryInclusionFilter.matches(info.widgetCategory)
                || !mWidgetCategoryExclusionFilter.matches(info.widgetCategory)) {
            return rejectWidget(
                    widget,
                    "doesn't match category filter [inclusion=%d, exclusion=%d, widget=%d]",
                    mWidgetCategoryInclusionFilter.getCategoryMask(),
                    mWidgetCategoryExclusionFilter.getCategoryMask(),
                    info.widgetCategory);
        }

        if (applySizeFilter) {
            if (mDesiredWidgetWidth == 0 && mDesiredWidgetHeight == 0) {
                // Accept the widget if the desired dimensions are unspecified.
                return acceptWidget(widget);
            }

            final boolean isHorizontallyResizable =
                    (info.resizeMode & AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0;
            if (mDesiredWidgetWidth > 0 && isHorizontallyResizable) {
                if (info.maxResizeWidth > 0
                        && info.maxResizeWidth >= info.minWidth
                        && info.maxResizeWidth < mDesiredWidgetWidth) {
                    return rejectWidget(
                            widget,
                            "maxResizeWidth[%d] < mDesiredWidgetWidth[%d]",
                            info.maxResizeWidth,
                            mDesiredWidgetWidth);
                }

                final int minWidth = min(info.minResizeWidth, info.minWidth);
                if (minWidth > mDesiredWidgetWidth) {
                    return rejectWidget(
                            widget,
                            "min(minWidth[%d], minResizeWidth[%d]) > mDesiredWidgetWidth[%d]",
                            info.minWidth,
                            info.minResizeWidth,
                            mDesiredWidgetWidth);
                }
            }

            final boolean isVerticallyResizable =
                    (info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0;
            if (mDesiredWidgetHeight > 0 && isVerticallyResizable) {
                if (info.maxResizeHeight > 0
                        && info.maxResizeHeight >= info.minHeight
                        && info.maxResizeHeight < mDesiredWidgetHeight) {
                    return rejectWidget(
                            widget,
                            "maxResizeHeight[%d] < mDesiredWidgetHeight[%d]",
                            info.maxResizeHeight,
                            mDesiredWidgetHeight);
                }

                final int minHeight = min(info.minResizeHeight, info.minHeight);
                if (minHeight > mDesiredWidgetHeight) {
                    return rejectWidget(
                            widget,
                            "min(minHeight[%d], minResizeHeight[%d]) > mDesiredWidgetHeight[%d]",
                            info.minHeight,
                            info.minResizeHeight,
                            mDesiredWidgetHeight);
                }
            }

            if (!isHorizontallyResizable || !isVerticallyResizable) {
                return rejectWidget(widget, "not resizeable");
            }
        }

        return acceptWidget(widget);
    }

    private static WidgetAcceptabilityVerdict rejectWidget(
            WidgetItem widget, String rejectionReason, Object... args) {
        return new WidgetAcceptabilityVerdict(
                false,
                widget.widgetInfo != null
                        ? widget.widgetInfo.provider.flattenToShortString()
                        : widget.label,
                String.format(Locale.ENGLISH, rejectionReason, args));
    }

    private static WidgetAcceptabilityVerdict acceptWidget(WidgetItem widget) {
        return new WidgetAcceptabilityVerdict(
                true, widget.widgetInfo.provider.flattenToShortString(), "");
    }

    private record WidgetAcceptabilityVerdict(
            boolean isAcceptable, String widgetLabel, String reason) {
        void maybeLogVerdict() {
            // Only log a verdict if a reason is specified.
            if (Log.isLoggable(TAG, Log.DEBUG) && !reason.isEmpty()) {
                Log.i(TAG, String.format(
                        Locale.ENGLISH,
                        "%s: %s because %s",
                        widgetLabel,
                        isAcceptable ? "accepted" : "rejected",
                        reason));
            }
        }
    }
}
