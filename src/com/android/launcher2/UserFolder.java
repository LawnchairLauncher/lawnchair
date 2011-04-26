package com.android.launcher2;

import java.util.ArrayList;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.animation.Animator.AnimatorListener;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.android.launcher.R;

/**
 * Folder which contains applications or shortcuts chosen by the user.
 *
 */
public class UserFolder extends Folder implements DropTarget {
    private static final String TAG = "Launcher.UserFolder";

    static final int STATE_NONE = -1;
    static final int STATE_SMALL = 0;
    static final int STATE_ANIMATING = 1;
    static final int STATE_OPEN = 2;

    private int mExpandDuration;
    protected CellLayout mContent;
    private final LayoutInflater mInflater;
    private final IconCache mIconCache;
    private int mState = STATE_NONE;

    public UserFolder(Context context, AttributeSet attrs) {
        super(context, attrs);
        mInflater = LayoutInflater.from(context);
        mIconCache = ((LauncherApplication)context.getApplicationContext()).getIconCache();
        mExpandDuration = getResources().getInteger(R.integer.config_folderAnimDuration);
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

    /**
     * This method is intended to make the UserFolder to be visually identical in size and position
     * to its associated FolderIcon. This allows for a seamless transition into the expanded state.
     */
    private void positionAndSizeAsIcon() {
        if (!(getParent() instanceof CellLayoutChildren)) return;

        CellLayoutChildren clc = (CellLayoutChildren) getParent();
        CellLayout cellLayout = (CellLayout) clc.getParent();

        FolderIcon fi = (FolderIcon) cellLayout.getChildAt(mInfo.cellX, mInfo.cellY);
        CellLayout.LayoutParams iconLp = (CellLayout.LayoutParams) fi.getLayoutParams();
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) getLayoutParams();

        lp.width = iconLp.width;
        lp.height = iconLp.height;
        lp.x = iconLp.x;
        lp.y = iconLp.y;

        mContent.setAlpha(0f);
        mState = STATE_SMALL;
    }

    public void animateOpen() {
        if (mState != STATE_SMALL) {
            positionAndSizeAsIcon();
        }
        if (!(getParent() instanceof CellLayoutChildren)) return;

        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) getLayoutParams();

        CellLayoutChildren clc = (CellLayoutChildren) getParent();
        CellLayout cellLayout = (CellLayout) clc.getParent();
        Rect r = cellLayout.getContentRect(null);

        PropertyValuesHolder width = PropertyValuesHolder.ofInt("width", r.width());
        PropertyValuesHolder height = PropertyValuesHolder.ofInt("height", r.height());
        PropertyValuesHolder x = PropertyValuesHolder.ofInt("x", 0);
        PropertyValuesHolder y = PropertyValuesHolder.ofInt("y", 0);

        ObjectAnimator oa = ObjectAnimator.ofPropertyValuesHolder(lp, width, height, x, y);
        oa.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                requestLayout();
            }
        });

        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 1.0f);
        ObjectAnimator oaContentAlpha = ObjectAnimator.ofPropertyValuesHolder(mContent, alpha);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(oa, oaContentAlpha);
        set.setDuration(mExpandDuration);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mState = STATE_ANIMATING;
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                mState = STATE_SMALL;
            }
        });
        set.start();
    }

    public void animateClosed() {
        if (!(getParent() instanceof CellLayoutChildren)) return;

        CellLayoutChildren clc = (CellLayoutChildren) getParent();
        final CellLayout cellLayout = (CellLayout) clc.getParent();

        FolderIcon fi = (FolderIcon) cellLayout.getChildAt(mInfo.cellX, mInfo.cellY);
        CellLayout.LayoutParams iconLp = (CellLayout.LayoutParams) fi.getLayoutParams();
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) getLayoutParams();

        PropertyValuesHolder width = PropertyValuesHolder.ofInt("width", iconLp.width);
        PropertyValuesHolder height = PropertyValuesHolder.ofInt("height", iconLp.height);
        PropertyValuesHolder x = PropertyValuesHolder.ofInt("x",iconLp.x);
        PropertyValuesHolder y = PropertyValuesHolder.ofInt("y", iconLp.y);

        ObjectAnimator oa = ObjectAnimator.ofPropertyValuesHolder(lp, width, height, x, y);
        oa.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                requestLayout();
            }
        });

        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 0f);
        ObjectAnimator oaContentAlpha = ObjectAnimator.ofPropertyValuesHolder(mContent, alpha);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(oa, oaContentAlpha);
        set.setDuration(mExpandDuration);

        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                cellLayout.removeViewWithoutMarkingCells(UserFolder.this);
                mState = STATE_OPEN;
            }
            @Override
            public void onAnimationStart(Animator animation) {
                mState = STATE_ANIMATING;
            }
        });
        set.start();
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

            mLauncher.getWorkspace().onDragStartedWithItem(v);
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
