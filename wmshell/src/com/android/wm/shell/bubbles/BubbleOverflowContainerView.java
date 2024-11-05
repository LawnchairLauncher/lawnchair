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

import static com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_BUBBLES;
import static com.android.wm.shell.bubbles.BubbleDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES;

import android.annotation.NonNull;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.protolog.common.ProtoLog;
import com.android.internal.util.ContrastColorUtil;
import com.android.wm.shell.Flags;
import com.android.wm.shell.R;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Container view for showing aged out bubbles.
 */
public class BubbleOverflowContainerView extends LinearLayout {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "BubbleOverflowActivity" : TAG_BUBBLES;

    private LinearLayout mEmptyState;
    private TextView mEmptyStateTitle;
    private TextView mEmptyStateSubtitle;
    private ImageView mEmptyStateImage;
    private int mHorizontalMargin;
    private int mVerticalMargin;
    private BubbleExpandedViewManager mExpandedViewManager;
    private BubblePositioner mPositioner;
    private BubbleOverflowAdapter mAdapter;
    private RecyclerView mRecyclerView;
    private List<Bubble> mOverflowBubbles = new ArrayList<>();

    private View.OnKeyListener mKeyListener = (view, i, keyEvent) -> {
        if (keyEvent.getAction() == KeyEvent.ACTION_UP
                && keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            mExpandedViewManager.collapseStack();
            return true;
        }
        return false;
    };

    private class OverflowGridLayoutManager extends GridLayoutManager {
        OverflowGridLayoutManager(Context context, int columns) {
            super(context, columns);
        }

        @Override
        public int getColumnCountForAccessibility(RecyclerView.Recycler recycler,
                RecyclerView.State state) {
            int bubbleCount = state.getItemCount();
            int columnCount = super.getColumnCountForAccessibility(recycler, state);
            if (bubbleCount < columnCount) {
                // If there are 4 columns and bubbles <= 3,
                // TalkBack says "AppName 1 of 4 in list 4 items"
                // This is a workaround until TalkBack bug is fixed for GridLayoutManager
                return bubbleCount;
            }
            return columnCount;
        }
    }

    private class OverflowItemDecoration extends RecyclerView.ItemDecoration {
        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            outRect.left = mHorizontalMargin;
            outRect.top = mVerticalMargin;
            outRect.right = mHorizontalMargin;
            outRect.bottom = mVerticalMargin;
        }
    }

    public BubbleOverflowContainerView(Context context) {
        this(context, null);
    }

    public BubbleOverflowContainerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleOverflowContainerView(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public BubbleOverflowContainerView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setFocusableInTouchMode(true);
    }

    /** Initializes the view. Must be called after creation. */
    public void initialize(BubbleExpandedViewManager expandedViewManager,
            BubblePositioner positioner) {
        mExpandedViewManager = expandedViewManager;
        mPositioner = positioner;
    }

    public void show() {
        requestFocus();
        updateOverflow();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mRecyclerView = findViewById(R.id.bubble_overflow_recycler);
        mEmptyState = findViewById(R.id.bubble_overflow_empty_state);
        mEmptyStateTitle = findViewById(R.id.bubble_overflow_empty_title);
        mEmptyStateSubtitle = findViewById(R.id.bubble_overflow_empty_subtitle);
        mEmptyStateImage = findViewById(R.id.bubble_overflow_empty_state_image);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mExpandedViewManager != null) {
            // For the overflow to get key events (e.g. back press) we need to adjust the flags
            mExpandedViewManager.updateWindowFlagsForBackpress(true);
        }
        setOnKeyListener(mKeyListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mExpandedViewManager != null) {
            mExpandedViewManager.updateWindowFlagsForBackpress(false);
        }
        setOnKeyListener(null);
    }

    void updateOverflow() {
        Resources res = getResources();
        int columns = (int) Math.round(getWidth()
                / (res.getDimension(R.dimen.bubble_name_width)));
        columns = columns > 0 ? columns : res.getInteger(R.integer.bubbles_overflow_columns);

        mRecyclerView.setLayoutManager(
                new OverflowGridLayoutManager(getContext(), columns));
        if (mRecyclerView.getItemDecorationCount() == 0) {
            mRecyclerView.addItemDecoration(new OverflowItemDecoration());
        }
        mAdapter = new BubbleOverflowAdapter(getContext(), mOverflowBubbles,
                mExpandedViewManager::promoteBubbleFromOverflow,
                mPositioner);
        mRecyclerView.setAdapter(mAdapter);

        mOverflowBubbles.clear();
        mOverflowBubbles.addAll(mExpandedViewManager.getOverflowBubbles());
        mAdapter.notifyDataSetChanged();

        mExpandedViewManager.setOverflowListener(mDataListener);
        updateEmptyStateVisibility();
        updateTheme();
    }

    void updateEmptyStateVisibility() {
        boolean showEmptyState = mOverflowBubbles.isEmpty()
                && !Flags.enableOptionalBubbleOverflow();
        mEmptyState.setVisibility(showEmptyState ? View.VISIBLE : View.GONE);
        mRecyclerView.setVisibility(mOverflowBubbles.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * Handle theme changes.
     */
    void updateTheme() {
        Resources res = getResources();
        final int mode = res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        final boolean isNightMode = (mode == Configuration.UI_MODE_NIGHT_YES);

        mHorizontalMargin = res.getDimensionPixelSize(
                R.dimen.bubble_overflow_item_padding_horizontal);
        mVerticalMargin = res.getDimensionPixelSize(R.dimen.bubble_overflow_item_padding_vertical);
        if (mRecyclerView != null) {
            mRecyclerView.invalidateItemDecorations();
        }

        mEmptyStateImage.setImageDrawable(isNightMode
                ? res.getDrawable(R.drawable.bubble_ic_empty_overflow_dark)
                : res.getDrawable(R.drawable.bubble_ic_empty_overflow_light));

        findViewById(R.id.bubble_overflow_container)
                .setBackgroundColor(isNightMode
                        ? res.getColor(R.color.bubbles_dark)
                        : res.getColor(R.color.bubbles_light));

        final TypedArray typedArray = getContext().obtainStyledAttributes(new int[] {
                com.android.internal.R.attr.materialColorSurfaceBright,
                com.android.internal.R.attr.materialColorOnSurface});
        int bgColor = typedArray.getColor(0, isNightMode ? Color.BLACK : Color.WHITE);
        int textColor = typedArray.getColor(1, isNightMode ? Color.WHITE : Color.BLACK);
        textColor = ContrastColorUtil.ensureTextContrast(textColor, bgColor, isNightMode);
        typedArray.recycle();
        setBackgroundColor(bgColor);
        mEmptyStateTitle.setTextColor(textColor);
        mEmptyStateSubtitle.setTextColor(textColor);
    }

    public void updateFontSize() {
        final float fontSize = mContext.getResources()
                .getDimensionPixelSize(com.android.internal.R.dimen.text_size_body_2_material);
        mEmptyStateTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
        mEmptyStateSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
    }

    public void updateLocale() {
        mEmptyStateTitle.setText(mContext.getString(R.string.bubble_overflow_empty_title));
        mEmptyStateSubtitle.setText(mContext.getString(R.string.bubble_overflow_empty_subtitle));
    }

    private final BubbleData.Listener mDataListener = new BubbleData.Listener() {

        @Override
        public void applyUpdate(BubbleData.Update update) {

            Bubble toRemove = update.removedOverflowBubble;
            if (toRemove != null) {
                toRemove.cleanupViews();
                final int indexToRemove = mOverflowBubbles.indexOf(toRemove);
                mOverflowBubbles.remove(toRemove);
                mAdapter.notifyItemRemoved(indexToRemove);
            }

            Bubble toAdd = update.addedOverflowBubble;
            if (toAdd != null) {
                final int indexToAdd = mOverflowBubbles.indexOf(toAdd);
                if (indexToAdd > 0) {
                    mOverflowBubbles.remove(toAdd);
                    mOverflowBubbles.add(0, toAdd);
                    mAdapter.notifyItemMoved(indexToAdd, 0);
                } else {
                    mOverflowBubbles.add(0, toAdd);
                    mAdapter.notifyItemInserted(0);
                }
            }

            updateEmptyStateVisibility();

            ProtoLog.d(WM_SHELL_BUBBLES, "Apply overflow update, added=%s removed=%s",
                    (toAdd != null ? toAdd.getKey() : "null"),
                    (toRemove != null ? toRemove.getKey() : "null"));
        }
    };
}

class BubbleOverflowAdapter extends RecyclerView.Adapter<BubbleOverflowAdapter.ViewHolder> {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "BubbleOverflowAdapter" : TAG_BUBBLES;

    private Context mContext;
    private Consumer<Bubble> mPromoteBubbleFromOverflow;
    private BubblePositioner mPositioner;
    private List<Bubble> mBubbles;

    BubbleOverflowAdapter(Context context,
            List<Bubble> list,
            Consumer<Bubble> promoteBubble,
            BubblePositioner positioner) {
        mContext = context;
        mBubbles = list;
        mPromoteBubbleFromOverflow = promoteBubble;
        mPositioner = positioner;
    }

    @Override
    public BubbleOverflowAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        // Set layout for overflow bubble view.
        LinearLayout overflowView = (LinearLayout) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.bubble_overflow_view, parent, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        overflowView.setLayoutParams(params);

        // Ensure name has enough contrast.
        final TypedArray ta = mContext.obtainStyledAttributes(
                new int[]{android.R.attr.colorBackgroundFloating, android.R.attr.textColorPrimary});
        final int bgColor = ta.getColor(0, Color.WHITE);
        int textColor = ta.getColor(1, Color.BLACK);
        textColor = ContrastColorUtil.ensureTextContrast(textColor, bgColor, true);
        ta.recycle();

        TextView viewName = overflowView.findViewById(R.id.bubble_view_name);
        viewName.setTextColor(textColor);

        return new ViewHolder(overflowView, mPositioner);
    }

    @Override
    public void onBindViewHolder(ViewHolder vh, int index) {
        Bubble b = mBubbles.get(index);

        vh.iconView.setRenderedBubble(b);
        vh.iconView.removeDotSuppressionFlag(BadgedImageView.SuppressionFlag.FLYOUT_VISIBLE);
        vh.iconView.setOnClickListener(view -> {
            mBubbles.remove(b);
            notifyDataSetChanged();
            mPromoteBubbleFromOverflow.accept(b);
        });

        String titleStr = b.getTitle();
        if (titleStr == null) {
            titleStr = mContext.getResources().getString(R.string.notification_bubble_title);
        }
        vh.iconView.setContentDescription(mContext.getResources().getString(
                R.string.bubble_content_description_single, titleStr, b.getAppName()));

        vh.iconView.setAccessibilityDelegate(
                new View.AccessibilityDelegate() {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(View host,
                            AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        // Talkback prompts "Double tap to add back to stack"
                        // instead of the default "Double tap to activate"
                        info.addAction(
                                new AccessibilityNodeInfo.AccessibilityAction(
                                        AccessibilityNodeInfo.ACTION_CLICK,
                                        mContext.getResources().getString(
                                                R.string.bubble_accessibility_action_add_back)));
                    }
                });

        CharSequence label = b.getShortcutInfo() != null
                ? b.getShortcutInfo().getLabel()
                : b.getAppName();
        vh.textView.setText(label);
    }

    @Override
    public int getItemCount() {
        return mBubbles.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public BadgedImageView iconView;
        public TextView textView;

        ViewHolder(LinearLayout v, BubblePositioner positioner) {
            super(v);
            iconView = v.findViewById(R.id.bubble_view);
            iconView.initialize(positioner);
            textView = v.findViewById(R.id.bubble_view_name);
        }
    }
}