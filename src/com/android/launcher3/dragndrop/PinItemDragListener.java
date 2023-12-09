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

package com.android.launcher3.dragndrop;


import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PIN_WIDGETS;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.content.pm.LauncherApps.PinItemRequest;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.DragEvent;
import android.view.View;
import android.widget.RemoteViews;

import com.android.launcher3.DragSource;
import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.PendingAddShortcutInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.PendingItemDragHelper;
import com.android.launcher3.widget.WidgetAddFlowHandler;

/**
 * {@link DragSource} for handling drop from a different window. This object is initialized
 * in the source window and is passed on to the Launcher activity as an Intent extra.
 */
@TargetApi(Build.VERSION_CODES.O)
public class PinItemDragListener extends BaseItemDragListener {

    private final PinItemRequest mRequest;
    private final CancellationSignal mCancelSignal;
    private final float mPreviewScale;

    public PinItemDragListener(PinItemRequest request, Rect previewRect,
            int previewBitmapWidth, int previewViewWidth) {
        this(request, previewRect, previewBitmapWidth, previewViewWidth, /* previewScale= */ 1f);
    }

    public PinItemDragListener(PinItemRequest request, Rect previewRect,
            int previewBitmapWidth, int previewViewWidth, float previewScale) {
        super(previewRect, previewBitmapWidth, previewViewWidth);
        mRequest = request;
        mCancelSignal = new CancellationSignal();
        mPreviewScale = previewScale;
    }

    @Override
    protected boolean onDragStart(DragEvent event) {
        if (!mRequest.isValid()) {
            return false;
        }
        return super.onDragStart(event);
    }

    @Override
    protected PendingItemDragHelper createDragHelper() {
        final PendingAddItemInfo item;
        if (mRequest.getRequestType() == PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            item = new PendingAddShortcutInfo(
                    new PinShortcutRequestActivityInfo(mRequest, mLauncher));
        } else {
            // mRequest.getRequestType() == PinItemRequestCompat.REQUEST_TYPE_APPWIDGET
            LauncherAppWidgetProviderInfo providerInfo =
                    LauncherAppWidgetProviderInfo.fromProviderInfo(
                            mLauncher, mRequest.getAppWidgetProviderInfo(mLauncher));
            final PinWidgetFlowHandler flowHandler =
                    new PinWidgetFlowHandler(providerInfo, mRequest);
            item = new PendingAddWidgetInfo(providerInfo, CONTAINER_PIN_WIDGETS) {
                @Override
                public WidgetAddFlowHandler getHandler() {
                    return flowHandler;
                }
            };
        }
        View view = new View(mLauncher);
        view.setTag(item);

        PendingItemDragHelper dragHelper = new PendingItemDragHelper(view);
        if (mRequest.getRequestType() == PinItemRequest.REQUEST_TYPE_APPWIDGET) {
            dragHelper.setRemoteViewsPreview(getPreview(mRequest), mPreviewScale);
        }
        return dragHelper;
    }

    @Override
    protected void postCleanup() {
        super.postCleanup();
        mCancelSignal.cancel();
    }

    public static RemoteViews getPreview(PinItemRequest request) {
        Bundle extras = request.getExtras();
        if (extras != null &&
                extras.get(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW) instanceof RemoteViews) {
            return (RemoteViews) extras.get(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW);
        }
        return null;
    }
}
