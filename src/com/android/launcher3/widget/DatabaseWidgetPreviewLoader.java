/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.BitmapRenderer;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.icons.ShadowGenerator;
import com.android.launcher3.icons.cache.HandlerRunnable;
import com.android.launcher3.model.WidgetItem;
import com.android.launcher3.pm.ShortcutConfigActivityInfo;
import com.android.launcher3.util.Executors;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.util.WidgetSizes;

import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Utility class to load widget previews */
public class DatabaseWidgetPreviewLoader {

    private static final String TAG = "WidgetPreviewLoader";

    private final Context mContext;
    private final float mPreviewBoxCornerRadius;

    public DatabaseWidgetPreviewLoader(Context context) {
        mContext = context;
        float previewCornerRadius = RoundedCornerEnforcement.computeEnforcedRadius(context);
        mPreviewBoxCornerRadius = previewCornerRadius > 0
                ? previewCornerRadius
                : mContext.getResources().getDimension(R.dimen.widget_preview_corner_radius);
    }

    /**
     * Generates the widget preview on {@link AsyncTask#THREAD_POOL_EXECUTOR}. Must be
     * called on UI thread.
     *
     * @return a request id which can be used to cancel the request.
     */
    @NonNull
    public HandlerRunnable loadPreview(
            @NonNull WidgetItem item,
            @NonNull Size previewSize,
            @NonNull Consumer<Bitmap> callback) {
        Handler handler = Executors.UI_HELPER_EXECUTOR.getHandler();
        HandlerRunnable<Bitmap> request = new HandlerRunnable<>(handler,
                () -> generatePreview(item, previewSize.getWidth(), previewSize.getHeight()),
                MAIN_EXECUTOR,
                callback);
        Utilities.postAsyncCallback(handler, request);
        return request;
    }

    /**
     * Returns a generated preview for a widget and if the preview should be saved in persistent
     * storage.
     */
    private Bitmap generatePreview(WidgetItem item, int previewWidth, int previewHeight) {
        if (item.widgetInfo != null) {
            return generateWidgetPreview(item.widgetInfo, previewWidth, null);
        } else {
            return generateShortcutPreview(item.activityInfo, previewWidth, previewHeight);
        }
    }

    /**
     * Generates the widget preview from either the {@link WidgetManagerHelper} or cache
     * and add badge at the bottom right corner.
     *
     * @param info                        information about the widget
     * @param maxPreviewWidth             width of the preview on either workspace or tray
     * @param preScaledWidthOut           return the width of the returned bitmap
     */
    public Bitmap generateWidgetPreview(LauncherAppWidgetProviderInfo info,
            int maxPreviewWidth, int[] preScaledWidthOut) {
        // Load the preview image if possible
        if (maxPreviewWidth < 0) maxPreviewWidth = Integer.MAX_VALUE;

        Drawable drawable = null;
        if (info.previewImage != 0) {
            try {
                drawable = info.loadPreviewImage(mContext, 0);
            } catch (OutOfMemoryError e) {
                Log.w(TAG, "Error loading widget preview for: " + info.provider, e);
                // During OutOfMemoryError, the previous heap stack is not affected. Catching
                // an OOM error here should be safe & not affect other parts of launcher.
                drawable = null;
            }
            if (drawable != null) {
                drawable = mutateOnMainThread(drawable);
            } else {
                Log.w(TAG, "Can't load widget preview drawable 0x"
                        + Integer.toHexString(info.previewImage)
                        + " for provider: "
                        + info.provider);
            }
        }

        final boolean widgetPreviewExists = (drawable != null);
        final int spanX = info.spanX;
        final int spanY = info.spanY;

        int previewWidth;
        int previewHeight;

        DeviceProfile dp = ActivityContext.lookupContext(mContext).getDeviceProfile();

        if (widgetPreviewExists && drawable.getIntrinsicWidth() > 0
                && drawable.getIntrinsicHeight() > 0) {
            previewWidth = drawable.getIntrinsicWidth();
            previewHeight = drawable.getIntrinsicHeight();
        } else {
            Size widgetSize = WidgetSizes.getWidgetSizePx(dp, spanX, spanY);
            previewWidth = widgetSize.getWidth();
            previewHeight = widgetSize.getHeight();
        }

        if (preScaledWidthOut != null) {
            preScaledWidthOut[0] = previewWidth;
        }
        // Scale to fit width only - let the widget preview be clipped in the
        // vertical dimension
        final float scale = previewWidth > maxPreviewWidth
                ? (maxPreviewWidth / (float) (previewWidth)) : 1f;
        if (scale != 1f) {
            previewWidth = Math.max((int) (scale * previewWidth), 1);
            previewHeight = Math.max((int) (scale * previewHeight), 1);
        }

        final int previewWidthF = previewWidth;
        final int previewHeightF = previewHeight;
        final Drawable drawableF = drawable;

        return BitmapRenderer.createHardwareBitmap(previewWidth, previewHeight, c -> {
            // Draw the scaled preview into the final bitmap
            if (widgetPreviewExists) {
                drawableF.setBounds(0, 0, previewWidthF, previewHeightF);
                drawableF.draw(c);
            } else {
                RectF boxRect;

                // Draw horizontal and vertical lines to represent individual columns.
                final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

                if (Utilities.ATLEAST_S) {
                    boxRect = new RectF(/* left= */ 0, /* top= */ 0, /* right= */
                            previewWidthF, /* bottom= */ previewHeightF);

                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.WHITE);
                    float roundedCorner = mContext.getResources().getDimension(
                            android.R.dimen.system_app_widget_background_radius);
                    c.drawRoundRect(boxRect, roundedCorner, roundedCorner, p);
                } else {
                    boxRect = drawBoxWithShadow(c, previewWidthF, previewHeightF);
                }

                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(mContext.getResources()
                        .getDimension(R.dimen.widget_preview_cell_divider_width));
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

                float t = boxRect.left;
                float tileSize = boxRect.width() / spanX;
                for (int i = 1; i < spanX; i++) {
                    t += tileSize;
                    c.drawLine(t, 0, t, previewHeightF, p);
                }

                t = boxRect.top;
                tileSize = boxRect.height() / spanY;
                for (int i = 1; i < spanY; i++) {
                    t += tileSize;
                    c.drawLine(0, t, previewWidthF, t, p);
                }

                // Draw icon in the center.
                try {
                    Drawable icon = LauncherAppState.getInstance(mContext).getIconCache()
                            .getFullResIcon(info.provider.getPackageName(), info.icon);
                    if (icon != null) {
                        int appIconSize = dp.iconSizePx;
                        int iconSize = (int) Math.min(appIconSize * scale,
                                Math.min(boxRect.width(), boxRect.height()));

                        icon = mutateOnMainThread(icon);
                        int hoffset = (previewWidthF - iconSize) / 2;
                        int yoffset = (previewHeightF - iconSize) / 2;
                        icon.setBounds(hoffset, yoffset, hoffset + iconSize, yoffset + iconSize);
                        icon.draw(c);
                    }
                } catch (Resources.NotFoundException e) {
                }
            }
        });
    }

    private RectF drawBoxWithShadow(Canvas c, int width, int height) {
        Resources res = mContext.getResources();

        ShadowGenerator.Builder builder = new ShadowGenerator.Builder(Color.WHITE);
        builder.shadowBlur = res.getDimension(R.dimen.widget_preview_shadow_blur);
        builder.radius = mPreviewBoxCornerRadius;
        builder.keyShadowDistance = res.getDimension(R.dimen.widget_preview_key_shadow_distance);

        builder.bounds.set(builder.shadowBlur, builder.shadowBlur,
                width - builder.shadowBlur,
                height - builder.shadowBlur - builder.keyShadowDistance);
        builder.drawShadow(c);
        return builder.bounds;
    }

    private Bitmap generateShortcutPreview(
            ShortcutConfigActivityInfo info, int maxWidth, int maxHeight) {
        int iconSize = ActivityContext.lookupContext(mContext).getDeviceProfile().allAppsIconSizePx;
        int padding = mContext.getResources()
                .getDimensionPixelSize(R.dimen.widget_preview_shortcut_padding);

        int size = iconSize + 2 * padding;
        if (maxHeight < size || maxWidth < size) {
            throw new RuntimeException("Max size is too small for preview");
        }
        return BitmapRenderer.createHardwareBitmap(size, size, c -> {
            LauncherIcons li = LauncherIcons.obtain(mContext);
            Drawable icon = li.createBadgedIconBitmap(
                    mutateOnMainThread(info.getFullResIcon(
                            LauncherAppState.getInstance(mContext).getIconCache())))
                    .newIcon(mContext);
            li.recycle();

            icon.setBounds(padding, padding, padding + iconSize, padding + iconSize);
            icon.draw(c);
        });
    }

    private Drawable mutateOnMainThread(final Drawable drawable) {
        try {
            return MAIN_EXECUTOR.submit(drawable::mutate).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
