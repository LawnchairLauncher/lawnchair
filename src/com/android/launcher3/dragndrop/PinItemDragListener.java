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
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.uioverrides.UiFactory;
import com.android.launcher3.userevent.nano.LauncherLogProto;
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

    public PinItemDragListener(PinItemRequest request, Rect previewRect,
            int previewBitmapWidth, int previewViewWidth) {
        super(previewRect, previewBitmapWidth, previewViewWidth);
        mRequest = request;
        mCancelSignal = new CancellationSignal();
    }

    @Override
    protected boolean onDragStart(DragEvent event) {
        if (!mRequest.isValid()) {
            return false;
        }
        return super.onDragStart(event);
    }

    @Override
    public boolean init(Launcher launcher, boolean alreadyOnHome) {
        super.init(launcher, alreadyOnHome);
        if (!alreadyOnHome) {
            UiFactory.useFadeOutAnimationForLauncherStart(launcher, mCancelSignal);
        }
        return false;
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
            item = new PendingAddWidgetInfo(providerInfo) {
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
            dragHelper.setPreview(getPreview(mRequest));
        }
        return dragHelper;
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, LauncherLogProto.Target target,
            LauncherLogProto.Target targetParent) {
        targetParent.containerType = LauncherLogProto.ContainerType.PINITEM;
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
