package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Represents a row in the apps list view.
 */
class AppsRow {
    int sectionId;
    String sectionDescription;
    List<AppInfo> apps;

    public AppsRow(int sId, String sc, List<AppInfo> ai) {
        sectionId = sId;
        sectionDescription = sc;
        apps = ai;
    }

    public AppsRow(int sId, List<AppInfo> ai) {
        sectionId = sId;
        apps = ai;
    }
}

/**
 * An interface to an algorithm that generates app rows.
 */
interface AppRowAlgorithm {
    public List<AppsRow> computeAppRows(List<AppInfo> sortedApps, int appsPerRow);
    public int getIconViewLayoutId();
    public int getRowViewLayoutId();
    public void bindRowViewIconToInfo(BubbleTextView icon, AppInfo info);
}

/**
 * Computes the rows in the apps list view.
 */
class SectionedAppsAlgorithm implements AppRowAlgorithm {

    @Override
    public List<AppsRow> computeAppRows(List<AppInfo> sortedApps, int appsPerRow) {
        List<AppsRow> rows = new ArrayList<>();
        LinkedHashMap<String, List<AppInfo>> sections = computeSectionedApps(sortedApps);
        int sectionId = 0;
        for (Map.Entry<String, List<AppInfo>> sectionEntry : sections.entrySet()) {
            String section = sectionEntry.getKey();
            List<AppInfo> apps = sectionEntry.getValue();
            int numRows = (int) Math.ceil((float) apps.size() / appsPerRow);
            for (int i = 0; i < numRows; i++) {
                List<AppInfo> appsInRow = new ArrayList<>();
                int offset = i * appsPerRow;
                for (int j = 0; j < appsPerRow; j++) {
                    if (offset + j < apps.size()) {
                        appsInRow.add(apps.get(offset + j));
                    }
                }
                if (i == 0) {
                    rows.add(new AppsRow(sectionId, section, appsInRow));
                } else {
                    rows.add(new AppsRow(sectionId, appsInRow));
                }
            }
            sectionId++;
        }
        return rows;
    }

    @Override
    public int getIconViewLayoutId() {
        return R.layout.apps_grid_row_icon_view;
    }

    @Override
    public int getRowViewLayoutId() {
        return R.layout.apps_grid_row_view;
    }

    private LinkedHashMap<String, List<AppInfo>> computeSectionedApps(List<AppInfo> sortedApps) {
        LinkedHashMap<String, List<AppInfo>> sections = new LinkedHashMap<>();
        for (AppInfo info : sortedApps) {
            String section = getSection(info);
            List<AppInfo> sectionApps = sections.get(section);
            if (sectionApps == null) {
                sectionApps = new ArrayList<>();
                sections.put(section, sectionApps);
            }
            sectionApps.add(info);
        }
        return sections;
    }

    @Override
    public void bindRowViewIconToInfo(BubbleTextView icon, AppInfo info) {
        icon.applyFromApplicationInfo(info);
    }

    private String getSection(AppInfo app) {
        return app.title.toString().substring(0, 1).toLowerCase();
    }
}

/**
 * Computes the rows in the apps grid view.
 */
class ListedAppsAlgorithm implements AppRowAlgorithm {

    @Override
    public List<AppsRow> computeAppRows(List<AppInfo> sortedApps, int appsPerRow) {
        List<AppsRow> rows = new ArrayList<>();
        int sectionId = -1;
        String prevSection = "";
        for (AppInfo info : sortedApps) {
            List<AppInfo> appsInRow = new ArrayList<>();
            appsInRow.add(info);
            String section = getSection(info);
            if (!prevSection.equals(section)) {
                prevSection = section;
                sectionId++;
                rows.add(new AppsRow(sectionId, section, appsInRow));
            } else {
                rows.add(new AppsRow(sectionId, appsInRow));
            }
        }
        return rows;
    }

    @Override
    public int getIconViewLayoutId() {
        return R.layout.apps_list_row_icon_view;
    }

    @Override
    public int getRowViewLayoutId() {
        return R.layout.apps_list_row_view;
    }

    @Override
    public void bindRowViewIconToInfo(BubbleTextView icon, AppInfo info) {
        icon.applyFromApplicationInfo(info);
    }

    private String getSection(AppInfo app) {
        return app.title.toString().substring(0, 1).toLowerCase();
    }
}

/**
 * The adapter of all the apps
 */
class AppsListAdapter extends BaseAdapter implements SectionIndexer {

    private LayoutInflater mLayoutInflater;
    private List<AppsRow> mAppRows = new ArrayList<>();
    private View.OnTouchListener mTouchListener;
    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;
    private AppRowAlgorithm mRowAlgorithm;
    private int mAppsPerRow;

    public AppsListAdapter(Context context, View.OnTouchListener touchListener,
            View.OnClickListener iconClickListener, View.OnLongClickListener iconLongClickListener) {
        mLayoutInflater = LayoutInflater.from(context);
        mTouchListener = touchListener;
        mIconClickListener = iconClickListener;
        mIconLongClickListener = iconLongClickListener;
    }

    void setApps(List<AppsRow> apps, int appsPerRow, AppRowAlgorithm algo) {
        mAppsPerRow = appsPerRow;
        mRowAlgorithm = algo;
        mAppRows.clear();
        mAppRows.addAll(apps);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mAppRows.size();
    }

    @Override
    public Object getItem(int position) {
        return mAppRows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        AppsRow info = mAppRows.get(position);
        ViewGroup row = (ViewGroup) convertView;
        if (row == null) {
            // Inflate the row and all the icon children necessary
            row = (ViewGroup) mLayoutInflater.inflate(mRowAlgorithm.getRowViewLayoutId(),
                    parent, false);
            for (int i = 0; i < mAppsPerRow; i++) {
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        mRowAlgorithm.getIconViewLayoutId(), row, false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                lp.gravity = Gravity.CENTER_VERTICAL;
                icon.setLayoutParams(lp);
                icon.setOnTouchListener(mTouchListener);
                icon.setOnClickListener(mIconClickListener);
                icon.setOnLongClickListener(mIconLongClickListener);
                icon.setFocusable(true);
                row.addView(icon);
            }
        }
        // Bind the section header
        TextView tv = (TextView) row.findViewById(R.id.section);
        if (info.sectionDescription != null) {
            tv.setText(info.sectionDescription);
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.INVISIBLE);
        }
        // Bind the icons
        for (int i = 0; i < mAppsPerRow; i++) {
            BubbleTextView icon = (BubbleTextView) row.getChildAt(i + 1);
            if (i < info.apps.size()) {
                mRowAlgorithm.bindRowViewIconToInfo(icon, info.apps.get(i));
                icon.setVisibility(View.VISIBLE);
            } else {
                icon.setVisibility(View.INVISIBLE);
            }
        }
        return row;
    }

    @Override
    public Object[] getSections() {
        ArrayList<Object> sections = new ArrayList<>();
        int prevSectionId = -1;
        for (AppsRow row : mAppRows) {
            if (row.sectionId != prevSectionId) {
                sections.add(row.sectionDescription.toUpperCase());
                prevSectionId = row.sectionId;
            }
        }
        return sections.toArray();
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        for (int i = 0; i < mAppRows.size(); i++) {
            AppsRow row = mAppRows.get(i);
            if (row.sectionId == sectionIndex) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public int getSectionForPosition(int position) {
        return mAppRows.get(position).sectionId;
    }
}

/**
 * The alphabetically sorted list of applications.
 */
class AlphabeticalAppList {

    /**
     * Callbacks for when this list is modified.
     */
    public interface Callbacks {
        public void onAppsUpdated();
    }

    private List<AppInfo> mApps;
    private Callbacks mCb;

    public AlphabeticalAppList(Callbacks cb) {
        mCb = cb;
    }

    /**
     * Returns the list of applications.
     */
    public List<AppInfo> getApps() {
        return mApps;
    }

    /**
     * Sets the current set of apps.
     */
    public void setApps(List<AppInfo> apps) {
        Collections.sort(apps, LauncherModel.getAppNameComparator());
        mApps = apps;
        mCb.onAppsUpdated();
    }

    /**
     * Adds new apps to the list.
     */
    public void addApps(List<AppInfo> apps) {
        // We add it in place, in alphabetical order
        Comparator<AppInfo> appNameComparator = LauncherModel.getAppNameComparator();
        for (AppInfo info : apps) {
            // This call will return the exact index of where the item is if >= 0, or the index
            // where it should be inserted if < 0.
            int index = Collections.binarySearch(mApps, info, appNameComparator);
            if (index < 0) {
                mApps.add(-(index + 1), info);
            }
        }
        mCb.onAppsUpdated();
    }

    /**
     * Updates existing apps in the list
     */
    public void updateApps(List<AppInfo> apps) {
        Comparator<AppInfo> appNameComparator = LauncherModel.getAppNameComparator();
        for (AppInfo info : apps) {
            int index = mApps.indexOf(info);
            if (index != -1) {
                mApps.set(index, info);
            } else {
                index = Collections.binarySearch(mApps, info, appNameComparator);
                if (index < 0) {
                    mApps.add(-(index + 1), info);
                }
            }
        }
        mCb.onAppsUpdated();
    }

    /**
     * Removes some apps from the list.
     */
    public void removeApps(List<AppInfo> apps) {
        for (AppInfo info : apps) {
            int removeIndex = findAppByComponent(mApps, info);
            if (removeIndex != -1) {
                mApps.remove(removeIndex);
            }
        }
        mCb.onAppsUpdated();
    }

    /**
     * Finds the index of an app given a target AppInfo.
     */
    private int findAppByComponent(List<AppInfo> apps, AppInfo targetInfo) {
        ComponentName targetComponent = targetInfo.intent.getComponent();
        int length = apps.size();
        for (int i = 0; i < length; ++i) {
            AppInfo info = apps.get(i);
            if (info.user.equals(info.user)
                    && info.intent.getComponent().equals(targetComponent)) {
                return i;
            }
        }
        return -1;
    }

}

/**
 * The all apps list view container.
 */
public class AppsContainerView extends FrameLayout implements DragSource, View.OnTouchListener,
        View.OnLongClickListener, Insettable, AlphabeticalAppList.Callbacks {

    static final int GRID_LAYOUT = 0;
    static final int LIST_LAYOUT = 1;
    static final int USE_LAYOUT = LIST_LAYOUT;

    private Launcher mLauncher;
    private AppRowAlgorithm mAppRowsAlgorithm;
    private AppsListAdapter mAdapter;
    private AlphabeticalAppList mApps;
    private ListView mList;
    private int mAppsRowSize;
    private Point mLastTouchDownPos = new Point();
    private Rect mPadding = new Rect();

    public AppsContainerView(Context context) {
        this(context, null);
    }

    public AppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AppsContainerView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();

        mLauncher = (Launcher) context;
        if (USE_LAYOUT == GRID_LAYOUT) {
            mAppRowsAlgorithm = new SectionedAppsAlgorithm();
            mAppsRowSize = grid.allAppsRowsSize;
        } else if (USE_LAYOUT == LIST_LAYOUT) {
            mAppRowsAlgorithm = new ListedAppsAlgorithm();
            mAppsRowSize = 1;
        }
        mAdapter = new AppsListAdapter(context, this, mLauncher, this);
        mApps = new AlphabeticalAppList(this);
    }

    /**
     * Sets the current set of apps.
     */
    public void setApps(List<AppInfo> apps) {
        mApps.setApps(apps);
    }

    /**
     * Adds new apps to the list.
     */
    public void addApps(List<AppInfo> apps) {
        mApps.addApps(apps);
    }

    /**
     * Updates existing apps in the list
     */
    public void updateApps(List<AppInfo> apps) {
        mApps.updateApps(apps);
    }

    /**
     * Removes some apps from the list.
     */
    public void removeApps(List<AppInfo> apps) {
        mApps.removeApps(apps);
    }

    /**
     * Scrolls this list view to the top.
     */
    public void scrollToTop() {
        mList.scrollTo(0, 0);
    }

    /**
     * Returns the content view used for the launcher transitions.
     */
    public View getContentView() {
        return findViewById(R.id.apps_list);
    }

    /**
     * Returns the reveal view used for the launcher transitions.
     */
    public View getRevealView() {
        return findViewById(R.id.all_apps_transition_overlay);
    }

    @Override
    public void onAppsUpdated() {
        List<AppsRow> rows = mAppRowsAlgorithm.computeAppRows(mApps.getApps(), mAppsRowSize);
        mAdapter.setApps(rows, mAppsRowSize, mAppRowsAlgorithm);
    }

    @Override
    protected void onFinishInflate() {
        mList = (ListView) findViewById(R.id.apps_list);
        mList.setFastScrollEnabled(true);
        mList.setFastScrollAlwaysVisible(true);
        mList.setItemsCanFocus(true);
        mList.setAdapter(mAdapter);
        mPadding.set(getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom());
    }

    @Override
    public void setInsets(Rect insets) {
        setPadding(mPadding.left + insets.left, mPadding.top + insets.top,
                mPadding.right + insets.right, mPadding.bottom + insets.bottom);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE) {
            mLastTouchDownPos.set((int) event.getX(), (int) event.getY());
        }
        return false;
    }

    @Override
    public boolean onLongClick(View v) {
        // Return early if this is not initiated from a touch
        if (!v.isInTouchMode()) return false;
        // When we have exited all apps or are in transition, disregard long clicks
        if (!mLauncher.isAppsViewVisible() ||
                mLauncher.getWorkspace().isSwitchingState()) return false;
        // Return if global dragging is not enabled
        if (!mLauncher.isDraggingEnabled()) return false;

        // Start the drag
        mLauncher.getWorkspace().beginDragShared(v, mLastTouchDownPos, this, false);

        // We delay entering spring-loaded mode slightly to make sure the UI
        // thready is free of any work.
        postDelayed(new Runnable() {
            @Override
            public void run() {
                // We don't enter spring-loaded mode if the drag has been cancelled
                if (mLauncher.getDragController().isDragging()) {
                    // Go into spring loaded mode (must happen before we startDrag())
                    mLauncher.enterSpringLoadedDragMode();
                }
            }
        }, 150);

        return false;
    }

    @Override
    public boolean supportsFlingToDelete() {
        return true;
    }

    @Override
    public boolean supportsAppInfoDropTarget() {
        return true;
    }

    @Override
    public boolean supportsDeleteDropTarget() {
        return true;
    }

    @Override
    public float getIntrinsicIconScaleFactor() {
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        return (float) grid.allAppsIconSizePx / grid.iconSizePx;
    }

    @Override
    public void onFlingToDeleteCompleted() {
        // We just dismiss the drag when we fling, so cleanup here
        mLauncher.exitSpringLoadedDragModeDelayed(true,
                Launcher.EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
        mLauncher.unlockScreenOrientation(false);
    }

    @Override
    public void onDropCompleted(View target, DropTarget.DragObject d, boolean isFlingToDelete, boolean success) {
        if (isFlingToDelete || !success || (target != mLauncher.getWorkspace() &&
                !(target instanceof DeleteDropTarget) && !(target instanceof Folder))) {
            // Exit spring loaded mode if we have not successfully dropped or have not handled the
            // drop in Workspace
            mLauncher.exitSpringLoadedDragModeDelayed(true,
                    Launcher.EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT, null);
        }
        mLauncher.unlockScreenOrientation(false);

        // Display an error message if the drag failed due to there not being enough space on the
        // target layout we were dropping on.
        if (!success) {
            boolean showOutOfSpaceMessage = false;
            if (target instanceof Workspace) {
                int currentScreen = mLauncher.getCurrentWorkspaceScreen();
                Workspace workspace = (Workspace) target;
                CellLayout layout = (CellLayout) workspace.getChildAt(currentScreen);
                ItemInfo itemInfo = (ItemInfo) d.dragInfo;
                if (layout != null) {
                    layout.calculateSpans(itemInfo);
                    showOutOfSpaceMessage =
                            !layout.findCellForSpan(null, itemInfo.spanX, itemInfo.spanY);
                }
            }
            if (showOutOfSpaceMessage) {
                mLauncher.showOutOfSpaceMessage(false);
            }

            d.deferDragViewCleanupPostAnimation = false;
        }
    }
}
