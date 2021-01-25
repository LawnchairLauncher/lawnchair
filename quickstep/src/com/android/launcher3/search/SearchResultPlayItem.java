/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.search;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.app.search.SearchAction;
import android.app.search.SearchTarget;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.icons.BitmapRenderer;
import com.android.launcher3.util.Themes;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

/**
 * A View representing a PlayStore item.
 */
public class SearchResultPlayItem extends LinearLayout implements
        SearchTargetHandler {

    public static final String TARGET_TYPE_PLAY = "play";

    private static final int BITMAP_CROP_MASK_COLOR = 0xff424242;
    final Paint mIconPaint = new Paint();
    final Rect mTempRect = new Rect();
    private final DeviceProfile mDeviceProfile;
    private View mIconView;
    private TextView mTitleView;
    private TextView[] mDetailViews = new TextView[3];
    private Button mPreviewButton;
    private String mPackageName;
    private Intent mIntent;
    private Intent mSecondaryIntent;


    public SearchResultPlayItem(Context context) {
        this(context, null, 0);
    }

    public SearchResultPlayItem(Context context,
            @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchResultPlayItem(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDeviceProfile = Launcher.getLauncher(getContext()).getDeviceProfile();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconView = findViewById(R.id.icon);
        mTitleView = findViewById(R.id.title_view);
        mPreviewButton = findViewById(R.id.try_button);
        mPreviewButton.setOnClickListener(view -> launchIntent(mSecondaryIntent));
        mDetailViews[0] = findViewById(R.id.detail_0);
        mDetailViews[1] = findViewById(R.id.detail_1);
        mDetailViews[2] = findViewById(R.id.detail_2);

        ViewGroup.LayoutParams iconParams = mIconView.getLayoutParams();
        iconParams.height = mDeviceProfile.allAppsIconSizePx;
        iconParams.width = mDeviceProfile.allAppsIconSizePx;
        setOnClickListener(view -> launchIntent(mIntent));
    }

    private void showIfNecessary(TextView textView, @Nullable String string) {
        if (string == null || string.isEmpty()) {
            textView.setVisibility(GONE);
        } else {
            textView.setText(string);
            textView.setVisibility(VISIBLE);
        }
    }

    private void launchIntent(Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
    }

    @Override
    public void applySearchTarget(SearchTarget parentTarget, List<SearchTarget> children) {
        if (parentTarget.getPackageName().equals(mPackageName)) {
            return;
        }
        mPackageName = parentTarget.getPackageName();
        SearchAction action = parentTarget.getSearchAction();
        mTitleView.setText(action.getTitle());
        showIfNecessary(mDetailViews[0], action.getSubtitle().toString());
        mIntent = action.getIntent();

        mIconView.setBackgroundResource(R.drawable.ic_deepshortcut_placeholder);
        loadIcon(action.getIcon().getUri().toString());

        mSecondaryIntent = children.size() == 1 ? children.get(0).getSearchAction().getIntent()
                : null;
        mPreviewButton.setVisibility(mSecondaryIntent == null ? GONE : VISIBLE);
    }

    private void loadIcon(String iconUrl) {
        UI_HELPER_EXECUTOR.execute(() -> {
            try {
                URL url = new URL(iconUrl);
                URLConnection con = url.openConnection();
                con.addRequestProperty("Cache-Control", "max-age: 0");
                con.setUseCaches(true);
                Bitmap bitmap = BitmapFactory.decodeStream(con.getInputStream());
                BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(), getRoundedBitmap(
                        Bitmap.createScaledBitmap(bitmap, mDeviceProfile.allAppsIconSizePx,
                                mDeviceProfile.allAppsIconSizePx, false)));
                mIconView.post(() -> mIconView.setBackground(bitmapDrawable));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private Bitmap getRoundedBitmap(Bitmap bitmap) {
        final int iconSize = bitmap.getWidth();
        final float radius = Themes.getDialogCornerRadius(getContext());

        Bitmap output = BitmapRenderer.createHardwareBitmap(iconSize, iconSize, (canvas) -> {
            mTempRect.set(0, 0, iconSize, iconSize);
            final RectF rectF = new RectF(mTempRect);

            mIconPaint.setAntiAlias(true);
            mIconPaint.reset();
            canvas.drawARGB(0, 0, 0, 0);
            mIconPaint.setColor(BITMAP_CROP_MASK_COLOR);
            canvas.drawRoundRect(rectF, radius, radius, mIconPaint);

            mIconPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(bitmap, mTempRect, mTempRect, mIconPaint);
        });
        return output;
    }
}
