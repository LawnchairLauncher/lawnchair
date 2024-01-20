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

import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;

/**
 * Utility class to handle updates while the popup is visible on the Launcher
 */
public class LauncherPopupLiveUpdateHandler extends PopupLiveUpdateHandler<Launcher> {

    public LauncherPopupLiveUpdateHandler(
            Launcher launcher, PopupContainerWithArrow<Launcher> popupContainerWithArrow) {
        super(launcher, popupContainerWithArrow);
    }

    private View getWidgetsView(ViewGroup container) {
        for (int i = container.getChildCount() - 1; i >= 0; --i) {
            View systemShortcutView = container.getChildAt(i);
            if (systemShortcutView.getTag() instanceof SystemShortcut.Widgets) {
                return systemShortcutView;
            }
        }
        return null;
    }

    @Override
    public void onWidgetsBound() {
        BubbleTextView originalIcon = mPopupContainerWithArrow.getOriginalIcon();
        SystemShortcut widgetInfo = SystemShortcut.WIDGETS.getShortcut(mContext,
                (ItemInfo) originalIcon.getTag(), originalIcon);
        View widgetsView = getWidgetsView(mPopupContainerWithArrow);
        if (widgetsView == null && mPopupContainerWithArrow.getWidgetContainer() != null) {
            widgetsView = getWidgetsView(mPopupContainerWithArrow.getWidgetContainer());
        }

        if (widgetInfo != null && widgetsView == null) {
            // We didn't have any widgets cached but now there are some, so enable the shortcut.
            if (mPopupContainerWithArrow.getSystemShortcutContainer()
                    != mPopupContainerWithArrow) {
                if (mPopupContainerWithArrow.getWidgetContainer() == null) {
                    mPopupContainerWithArrow.setWidgetContainer(
                            mPopupContainerWithArrow.inflateAndAdd(
                                    R.layout.widget_shortcut_container,
                                    mPopupContainerWithArrow));
                }
                mPopupContainerWithArrow.initializeWidgetShortcut(
                        mPopupContainerWithArrow.getWidgetContainer(),
                        widgetInfo);
            } else {
                // If using the expanded system shortcut (as opposed to just the icon), we need
                // to reopen the container to ensure measurements etc. all work out. While this
                // could be quite janky, in practice the user would typically see a small
                // flicker as the animation restarts partway through, and this is a very rare
                // edge case anyway.
                mPopupContainerWithArrow.close(false);
                PopupContainerWithArrow.showForIcon(mPopupContainerWithArrow.getOriginalIcon());
            }
        } else if (widgetInfo == null && widgetsView != null) {
            // No widgets exist, but we previously added the shortcut so remove it.
            if (mPopupContainerWithArrow.getSystemShortcutContainer()
                    != mPopupContainerWithArrow
                    && mPopupContainerWithArrow.getWidgetContainer() != null) {
                mPopupContainerWithArrow.getWidgetContainer().removeView(widgetsView);
            } else {
                mPopupContainerWithArrow.close(false);
                PopupContainerWithArrow.showForIcon(mPopupContainerWithArrow.getOriginalIcon());
            }
        }
    }
}
