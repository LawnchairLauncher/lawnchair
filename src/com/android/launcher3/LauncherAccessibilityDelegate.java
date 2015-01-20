package com.android.launcher3;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

import com.android.launcher3.LauncherModel.ScreenPosProvider;

import java.util.ArrayList;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class LauncherAccessibilityDelegate extends AccessibilityDelegate {

    public static final int REMOVE = R.id.action_remove;
    public static final int INFO = R.id.action_info;
    public static final int UNINSTALL = R.id.action_uninstall;
    public static final int ADD_TO_WORKSPACE = R.id.action_add_to_workspace;

    private final SparseArray<AccessibilityAction> mActions =
            new SparseArray<AccessibilityAction>();
    private final Launcher mLauncher;

    public LauncherAccessibilityDelegate(Launcher launcher) {
        mLauncher = launcher;

        mActions.put(REMOVE, new AccessibilityAction(REMOVE,
                launcher.getText(R.string.delete_target_label)));
        mActions.put(INFO, new AccessibilityAction(INFO,
                launcher.getText(R.string.info_target_label)));
        mActions.put(UNINSTALL, new AccessibilityAction(UNINSTALL,
                launcher.getText(R.string.delete_target_uninstall_label)));
        mActions.put(ADD_TO_WORKSPACE, new AccessibilityAction(ADD_TO_WORKSPACE,
                launcher.getText(R.string.action_add_to_workspace)));
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        if (!(host.getTag() instanceof ItemInfo)) return;
        ItemInfo item = (ItemInfo) host.getTag();

        if ((item instanceof ShortcutInfo)
                || (item instanceof LauncherAppWidgetInfo)
                || (item instanceof FolderInfo)) {
            // Workspace shortcut / widget
            info.addAction(mActions.get(REMOVE));
        } else if ((item instanceof AppInfo) || (item instanceof PendingAddItemInfo)) {
            // App or Widget from customization tray
            if (item instanceof AppInfo) {
                info.addAction(mActions.get(UNINSTALL));
            }
            info.addAction(mActions.get(INFO));
            info.addAction(mActions.get(ADD_TO_WORKSPACE));
        }
    }

    @Override
    public boolean performAccessibilityAction(View host, int action, Bundle args) {
        if ((host.getTag() instanceof ItemInfo)
                && performAction(host, (ItemInfo) host.getTag(), action)) {
            return true;
        }
        return super.performAccessibilityAction(host, action, args);
    }

    public boolean performAction(View host, ItemInfo item, int action) {
        if (action == REMOVE) {
            return DeleteDropTarget.removeWorkspaceOrFolderItem(mLauncher, item, host);
        } else if (action == INFO) {
            InfoDropTarget.startDetailsActivityForInfo(item, mLauncher);
            return true;
        } else if (action == UNINSTALL) {
            DeleteDropTarget.uninstallApp(mLauncher, (AppInfo) item);
            return true;
        } else if (action == ADD_TO_WORKSPACE) {
            final int preferredPage = mLauncher.getWorkspace().getCurrentPage();
            final ScreenPosProvider screenProvider = new ScreenPosProvider() {

                @Override
                public int getScreenIndex(ArrayList<Long> screenIDs) {
                    return preferredPage;
                }
            };
            if (item instanceof AppInfo) {
                final ArrayList<ItemInfo> addShortcuts = new ArrayList<ItemInfo>();
                addShortcuts.add(((AppInfo) item).makeShortcut());
                mLauncher.showWorkspace(true, new Runnable() {

                    @Override
                    public void run() {
                        mLauncher.getModel().addAndBindAddedWorkspaceApps(
                                mLauncher, addShortcuts, screenProvider, 0, true);
                    }
                });
                return true;
            } else if (item instanceof PendingAddItemInfo) {
                mLauncher.getModel().addAndBindPendingItem(
                        mLauncher, (PendingAddItemInfo) item, screenProvider, 0);
                return true;
            }
        }
        return false;
    }
}
