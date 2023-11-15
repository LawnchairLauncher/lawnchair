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

package com.android.launcher3.allapps;

import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_ICON;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_HEADER;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.views.ActivityContext;

/**
 * Decorator which changes the background color for Private Space Icon Rows in AllAppsContainer.
 */
public class PrivateAppsSectionDecorator extends RecyclerView.ItemDecoration {

    private final Path mTmpPath = new Path();
    private final RectF mTmpRect = new RectF();
    private final Context mContext;
    private final AlphabeticalAppsList<?> mAppsList;
    private final PrivateProfileManager mPrivateProfileManager;
    private final UserCache mUserCache;
    private final Paint mPaint;

    public PrivateAppsSectionDecorator(ActivityAllAppsContainerView<?> appsContainerView,
            AlphabeticalAppsList<?> appsList,
            PrivateProfileManager privateProfileManager) {
        mAppsList = appsList;
        mPrivateProfileManager = privateProfileManager;
        mContext = appsContainerView.mActivityContext;
        mUserCache = UserCache.getInstance(appsContainerView.mActivityContext);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(ContextCompat.getColor(mContext,
                R.color.material_color_surface_container_high));
    }

    /** Decorates Private Space Header and Icon Rows to give the shape of a container. */
    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        mTmpPath.reset();
        mTmpRect.setEmpty();
        int numCol = ActivityContext.lookupContext(mContext).getDeviceProfile()
                .numShownAllAppsColumns;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(view);
            BaseAllAppsAdapter.AdapterItem adapterItem = mAppsList.getAdapterItems().get(position);
            // Rectangle that covers the bottom half of the PS Header View when Space is unlocked.
            if (adapterItem.viewType == VIEW_TYPE_PRIVATE_SPACE_HEADER
                    && mPrivateProfileManager
                    .getCurrentState() == PrivateProfileManager.STATE_ENABLED) {
                // We flatten the bottom corners of the rectangle, so that it merges with
                // the private space app row decorator.
                mTmpRect.set(
                        view.getLeft(),
                        view.getTop() + (float) (view.getBottom() - view.getTop()) / 2,
                        view.getRight(),
                        view.getBottom());
                mTmpPath.addRect(mTmpRect, Path.Direction.CW);
                c.drawPath(mTmpPath, mPaint);
            } else if (adapterItem.viewType == VIEW_TYPE_ICON
                    && mUserCache.getUserInfo(adapterItem.itemInfo.user).isPrivate()
                    // No decoration for any private space app icon other than those at first row.
                    && adapterItem.rowAppIndex == 0) {
                c.drawPath(getPrivateAppRowPath(parent, view, position, numCol), mPaint);
            }
        }
    }

    /** Returns the path to be decorated for Private Space App Row */
    private Path getPrivateAppRowPath(RecyclerView parent, View iconView, int adapterPosition,
            int numCol) {
        // We always decorate the entire app row here.
        // As the iconView just represents the first icon of the row, we get the right margin of
        // our decorator using the parent view.
        mTmpRect.set(iconView.getLeft(),
                iconView.getTop(),
                parent.getRight() - parent.getPaddingRight(),
                iconView.getBottom());
        // Decorates last app row with rounded bottom corners.
        if (adapterPosition + numCol >= mAppsList.getAdapterItems().size()) {
            int corner = mContext.getResources().getDimensionPixelSize(
                    R.dimen.ps_container_corner_radius);
            float[] mCornersBot = new float[]{0, 0, 0, 0, corner, corner, corner, corner};
            mTmpPath.addRoundRect(mTmpRect, mCornersBot, Path.Direction.CW);
        } else {
            // Decorate other rows as a plain rectangle
            mTmpPath.addRect(mTmpRect, Path.Direction.CW);
        }
        return mTmpPath;
    }
}
