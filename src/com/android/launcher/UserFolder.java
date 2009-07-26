package com.android.launcher;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;

/**
 * Folder which contains applications or shortcuts chosen by the user.
 *
 */
public class UserFolder extends Folder implements DropTarget {
    public UserFolder(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    /**
     * Creates a new UserFolder, inflated from R.layout.user_folder.
     *
     * @param context The application's context.
     *
     * @return A new UserFolder.
     */
    static UserFolder fromXml(Context context) {
        return (UserFolder) LayoutInflater.from(context).inflate(R.layout.user_folder, null);
    }

    public boolean acceptDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            Object dragInfo) {
        final ItemInfo item = (ItemInfo) dragInfo;
        final int itemType = item.itemType;
        return (itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) && item.container != mInfo.id;
    }
    
    public Rect estimateDropLocation(DragSource source, int x, int y, int xOffset, int yOffset, Object dragInfo, Rect recycle) {
        return null;
    }

    public void onDrop(DragSource source, int x, int y, int xOffset, int yOffset, Object dragInfo) {
        final ApplicationInfo item = (ApplicationInfo) dragInfo;
        //noinspection unchecked
        ((ArrayAdapter<ApplicationInfo>) mContent.getAdapter()).add((ApplicationInfo) dragInfo);
        LauncherModel.addOrMoveItemInDatabase(mLauncher, item, mInfo.id, 0, 0, 0);
    }

    public void onDragEnter(DragSource source, int x, int y, int xOffset, int yOffset, Object dragInfo) {
    }

    public void onDragOver(DragSource source, int x, int y, int xOffset, int yOffset, Object dragInfo) {
    }

    public void onDragExit(DragSource source, int x, int y, int xOffset, int yOffset, Object dragInfo) {
    }

    @Override
    public void onDropCompleted(View target, boolean success) {
        if (success) {
            //noinspection unchecked
            ArrayAdapter<ApplicationInfo> adapter =
                    (ArrayAdapter<ApplicationInfo>) mContent.getAdapter();
            adapter.remove(mDragItem);
        }
    }

    void bind(FolderInfo info) {
        super.bind(info);
        setContentAdapter(new ApplicationsAdapter(mContext, ((UserFolderInfo) info).contents));
    }

    // When the folder opens, we need to refresh the GridView's selection by
    // forcing a layout
    @Override
    void onOpen() {
        super.onOpen();
        requestFocus();
    }
}
