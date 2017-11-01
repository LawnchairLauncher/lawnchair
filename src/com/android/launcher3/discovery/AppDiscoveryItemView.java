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

package com.android.launcher3.discovery;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.launcher3.R;

import java.text.DecimalFormat;
import java.text.NumberFormat;

public class AppDiscoveryItemView extends RelativeLayout {

    private static boolean SHOW_REVIEW_COUNT = false;

    private ImageView mImage;
    private TextView mTitle;
    private TextView mRatingText;
    private RatingView mRatingView;
    private TextView mReviewCount;
    private TextView mPrice;
    private OnLongClickListener mOnLongClickListener;

    public AppDiscoveryItemView(Context context) {
        this(context, null);
    }

    public AppDiscoveryItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppDiscoveryItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        this.mImage = (ImageView) findViewById(R.id.image);
        this.mTitle = (TextView) findViewById(R.id.title);
        this.mRatingText = (TextView) findViewById(R.id.rating);
        this.mRatingView = (RatingView) findViewById(R.id.rating_view);
        this.mPrice = (TextView) findViewById(R.id.price);
        this.mReviewCount = (TextView) findViewById(R.id.review_count);
    }

    public void init(OnClickListener clickListener,
                     AccessibilityDelegate accessibilityDelegate,
                     OnLongClickListener onLongClickListener) {
        setOnClickListener(clickListener);
        mImage.setOnClickListener(clickListener);
        setAccessibilityDelegate(accessibilityDelegate);
        mOnLongClickListener = onLongClickListener;
    }

    public void apply(@NonNull AppDiscoveryAppInfo info) {
        setTag(info);
        mImage.setTag(info);
        mImage.setImageBitmap(info.iconBitmap);
        mImage.setOnLongClickListener(info.isDragAndDropSupported() ? mOnLongClickListener : null);
        mTitle.setText(info.title);
        mPrice.setText(info.priceFormatted != null ? info.priceFormatted : "");
        mReviewCount.setVisibility(SHOW_REVIEW_COUNT ? View.VISIBLE : View.GONE);
        if (info.rating >= 0) {
            mRatingText.setText(new DecimalFormat("#.0").format(info.rating));
            mRatingView.setRating(info.rating);
            mRatingView.setVisibility(View.VISIBLE);
            String reviewCountFormatted = NumberFormat.getInstance().format(info.reviewCount);
            mReviewCount.setText("(" + reviewCountFormatted + ")");
        } else {
            // if we don't have a rating
            mRatingView.setVisibility(View.GONE);
            mRatingText.setText("");
            mReviewCount.setText("");
        }
    }
}
