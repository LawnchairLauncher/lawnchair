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

package ch.deletescape.lawnchair.dragndrop;

import android.graphics.Rect;
import android.os.CancellationSignal;
import android.view.View;
import com.android.launcher3.DragSource;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.dragndrop.BaseItemDragListener;
import com.android.launcher3.uioverrides.UiFactory;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.PendingItemDragHelper;

/**
 * {@link DragSource} for handling drop from a different window. This object is initialized
 * in the source window and is passed on to the Launcher activity as an Intent extra.
 */
public class CustomWidgetDragListener extends BaseItemDragListener {

    private final LauncherAppWidgetProviderInfo mProvider;
    private final CancellationSignal mCancelSignal;

    public CustomWidgetDragListener(LauncherAppWidgetProviderInfo provider, Rect previewRect,
            int previewBitmapWidth, int previewViewWidth) {
        super(previewRect, previewBitmapWidth, previewViewWidth);
        mProvider = provider;
        mCancelSignal = new CancellationSignal();
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
        item = new PendingAddWidgetInfo(mProvider);
        View view = new View(mLauncher);
        view.setTag(item);

        return new PendingItemDragHelper(view);
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
}
