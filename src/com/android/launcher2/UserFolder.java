package com.android.launcher2;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;

import com.android.launcher.R;
import java.util.ArrayList;

/**
 * Folder which contains applications or shortcuts chosen by the user.
 *
 */
public class UserFolder extends Folder implements DropTarget {
    private static final String TAG = "Launcher.UserFolder";

    protected CellLayout mContent;
    private final LayoutInflater mInflater;
    private final IconCache mIconCache;

    public UserFolder(Context context, AttributeSet attrs) {
        super(context, attrs);
        mInflater = LayoutInflater.from(context);
        mIconCache = ((LauncherApplication)context.getApplicationContext()).getIconCache();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mContent = (CellLayout) findViewById(R.id.folder_content);
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

    @Override
    void notifyDataSetChanged() {
        // recreate all the children if the data set changes under us. We may want to do this more
        // intelligently (ie just removing the views that should no longer exist)
        mContent.removeAllViewsInLayout();
        bind(mInfo);
    }

    public void onClick(View v) {
        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
            // refactor this code from Folder
            ShortcutInfo item = (ShortcutInfo) tag;
            int[] pos = new int[2];
            v.getLocationOnScreen(pos);
            item.intent.setSourceBounds(new Rect(pos[0], pos[1],
                    pos[0] + v.getWidth(), pos[1] + v.getHeight()));
            mLauncher.startActivitySafely(item.intent, item);
        } else {
            super.onClick(v);
        }
    }

    public boolean onLongClick(View v) {
        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
         // refactor this code from Folder
            ShortcutInfo item = (ShortcutInfo) tag;
            if (!v.isInTouchMode()) {
                return false;
            }

            mDragController.startDrag(v, this, item, DragController.DRAG_ACTION_COPY);
            mLauncher.closeFolder(this);
            mDragItem = item;

            return true;
        } else {
            return super.onLongClick(v);
        }
    }

    public boolean acceptDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        final ItemInfo item = (ItemInfo) dragInfo;
        final int itemType = item.itemType;
        return (itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                    itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT)
                && item.container != mInfo.id;
    }

    public void onDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        ShortcutInfo item;
        if (dragInfo instanceof ApplicationInfo) {
            // Came from all apps -- make a copy
            item = ((ApplicationInfo)dragInfo).makeShortcut();
            item.spanX = 1;
            item.spanY = 1;
        } else {
            item = (ShortcutInfo)dragInfo;
        }
        findAndSetEmptyCells(item);
        ((UserFolderInfo)mInfo).add(item);
        createAndAddShortcut(item);
        LauncherModel.addOrMoveItemInDatabase(mLauncher, item, mInfo.id, 0, item.cellX, item.cellY);
    }

    protected boolean findAndSetEmptyCells(ShortcutInfo item) {
        int[] emptyCell = new int[2];
        if (mContent.findCellForSpan(emptyCell, item.spanX, item.spanY)) {
            item.cellX = emptyCell[0];
            item.cellY = emptyCell[1];
            LauncherModel.addOrMoveItemInDatabase(
                    mLauncher, item, mInfo.id, 0, item.cellX, item.cellY);
            return true;
        } else {
            return false;
        }
    }

    protected void createAndAddShortcut(ShortcutInfo item) {
        final TextView textView =
            (TextView) mInflater.inflate(R.layout.application_boxed, this, false);
        textView.setCompoundDrawablesWithIntrinsicBounds(null,
                new FastBitmapDrawable(item.getIcon(mIconCache)), null, null);
        textView.setText(item.title);
        textView.setTag(item);

        textView.setOnClickListener(this);
        textView.setOnLongClickListener(this);

        CellLayout.LayoutParams lp =
            new CellLayout.LayoutParams(item.cellX, item.cellY, item.spanX, item.spanY);
        boolean insert = false;
        mContent.addViewToCellLayout(textView, insert ? 0 : -1, (int)item.id, lp, true);
    }

    public void onDragEnter(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
    }

    public void onDragOver(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
    }

    public void onDragExit(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
    }

    @Override
    public void onDropCompleted(View target, Object dragInfo, boolean success) {
        if (success) {
            ((UserFolderInfo)mInfo).remove(mDragItem);
        }
    }

    public boolean isDropEnabled() {
        return true;
    }

    void bind(FolderInfo info) {
        super.bind(info);
        ArrayList<ShortcutInfo> children = ((UserFolderInfo)info).contents;
        for (int i = 0; i < children.size(); i++) {
            ShortcutInfo child = (ShortcutInfo) children.get(i);
            if ((child.cellX == -1 && child.cellY == -1) ||
                    mContent.isOccupied(child.cellX, child.cellY)) {
                findAndSetEmptyCells(child);
            }
            createAndAddShortcut((ShortcutInfo) children.get(i));
        }
    }

    @Override
    void onOpen() {
        super.onOpen();
        // When the folder opens, we need to refresh the GridView's selection by
        // forcing a layout
        // TODO: find out if this is still necessary
        mContent.requestLayout();
        requestFocus();
    }

    @Override
    public DropTarget getDropTargetDelegate(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        return null;
    }
}
