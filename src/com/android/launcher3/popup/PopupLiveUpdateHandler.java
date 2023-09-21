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
package com.android.launcher3.popup;

import static android.view.View.GONE;

import android.content.Context;
import android.view.View;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.notification.NotificationContainer;
import com.android.launcher3.notification.NotificationKeyData;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.views.ActivityContext;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Utility class to handle updates while the popup is visible (like widgets and
 * notification changes)
 *
 * @param <T> The activity on which the popup shows
 */
public abstract class PopupLiveUpdateHandler<T extends Context & ActivityContext> implements
        PopupDataProvider.PopupDataChangeListener, View.OnAttachStateChangeListener {

    protected final T mContext;
    protected final PopupContainerWithArrow<T> mPopupContainerWithArrow;

    public PopupLiveUpdateHandler(
            T context, PopupContainerWithArrow<T> popupContainerWithArrow) {
        mContext = context;
        mPopupContainerWithArrow = popupContainerWithArrow;
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        PopupDataProvider popupDataProvider = mContext.getPopupDataProvider();

        if (popupDataProvider != null) {
            popupDataProvider.setChangeListener(this);
        }
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        PopupDataProvider popupDataProvider = mContext.getPopupDataProvider();

        if (popupDataProvider != null) {
            popupDataProvider.setChangeListener(null);
        }
    }

    /**
     * Updates the notification header if the original icon's dot updated.
     */
    @Override
    public void onNotificationDotsUpdated(Predicate<PackageUserKey> updatedDots) {
        ItemInfo itemInfo = (ItemInfo) mPopupContainerWithArrow.getOriginalIcon().getTag();
        PackageUserKey packageUser = PackageUserKey.fromItemInfo(itemInfo);
        if (updatedDots.test(packageUser)) {
            mPopupContainerWithArrow.updateNotificationHeader();
        }
    }


    @Override
    public void trimNotifications(Map<PackageUserKey, DotInfo> updatedDots) {
        NotificationContainer notificationContainer =
                mPopupContainerWithArrow.getNotificationContainer();
        if (notificationContainer == null) {
            return;
        }
        ItemInfo originalInfo = (ItemInfo) mPopupContainerWithArrow.getOriginalIcon().getTag();
        DotInfo dotInfo = updatedDots.get(PackageUserKey.fromItemInfo(originalInfo));
        if (dotInfo == null || dotInfo.getNotificationKeys().size() == 0) {
            // No more notifications, remove the notification views and expand all shortcuts.
            notificationContainer.setVisibility(GONE);
            mPopupContainerWithArrow.updateHiddenShortcuts();
            mPopupContainerWithArrow.assignMarginsAndBackgrounds(mPopupContainerWithArrow);
            mPopupContainerWithArrow.updateArrowColor();
        } else {
            notificationContainer.trimNotifications(
                    NotificationKeyData.extractKeysOnly(dotInfo.getNotificationKeys()));
        }
    }

    @Override
    public void onSystemShortcutsUpdated() {
        mPopupContainerWithArrow.close(true);
        showPopupContainerForIcon(mPopupContainerWithArrow.getOriginalIcon());
    }

    protected abstract void showPopupContainerForIcon(BubbleTextView originalIcon);
}
