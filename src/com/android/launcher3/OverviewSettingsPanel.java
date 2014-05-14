package com.android.launcher3;

import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.drawable.AnimationDrawable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import com.android.launcher3.list.PinnedHeaderListView;
import com.android.launcher3.list.SettingsPinnedHeaderAdapter;

public class OverviewSettingsPanel {
    public static final String ANDROID_SETTINGS = "org.cyanogenmod.theme.chooser";
    public static final String THEME_SETTINGS =
            "org.cyanogenmod.theme.chooser.ChooserActivity";
    public static final int HOME_SETTINGS_POSITION = 0;
    public static final int DRAWER_SETTINGS_POSITION = 1;

    private Launcher mLauncher;
    private View mOverviewPanel;
    private SettingsPinnedHeaderAdapter mSettingsAdapter;
    private PinnedHeaderListView mListView;

    OverviewSettingsPanel(Launcher launcher, View overviewPanel) {
        mLauncher = launcher;
        mOverviewPanel = overviewPanel;
    }

    // One time initialization of the SettingsPinnedHeaderAdapter
    public void initializeAdapter() {
        // Settings pane Listview
        mListView = (PinnedHeaderListView) mLauncher
                .findViewById(R.id.settings_home_screen_listview);
        mListView.setOverScrollMode(ListView.OVER_SCROLL_NEVER);
        Resources res = mLauncher.getResources();
        String[] headers = new String[] {
                res.getString(R.string.home_screen_settings),
                res.getString(R.string.drawer_settings)};
        String[] values = new String[] {
                res.getString(R.string.home_screen_search_text),
                res.getString(R.string.page_scroll_effect_text),
                res.getString(R.string.larger_icons_text),
                res.getString(R.string.hide_icon_labels)};
        String[] valuesDrawer = new String[] {
                res.getString(R.string.drawer_scroll_effect_text),
                res.getString(R.string.drawer_sorting_text),
                res.getString(R.string.hide_icon_labels)};

        mSettingsAdapter = new SettingsPinnedHeaderAdapter(mLauncher);
        mSettingsAdapter.setHeaders(headers);
        mSettingsAdapter.addPartition(false, true);
        mSettingsAdapter.addPartition(false, true);
        mSettingsAdapter.mPinnedHeaderCount = headers.length;

        mSettingsAdapter.changeCursor(0, createCursor(headers[0], values));
        mSettingsAdapter.changeCursor(1, createCursor(headers[1], valuesDrawer));
        mListView.setAdapter(mSettingsAdapter);
    }

    private Cursor createCursor(String header, String[] values) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"_id", header});
        int count = values.length;
        for (int i = 0; i < count; i++) {
            cursor.addRow(new Object[]{i, values[i]});
        }
        return cursor;
    }

    // One time View setup
    public void initializeViews() {
        mOverviewPanel.setAlpha(0f);
        mOverviewPanel
                .setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        ((SlidingUpPanelLayout) mOverviewPanel)
                .setPanelSlideListener(new SettingsSimplePanelSlideListener());

        //Quick Settings Buttons
        View widgetButton = mLauncher.findViewById(R.id.widget_button);
        widgetButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                mLauncher.showAllApps(true, AppsCustomizePagedView.ContentType.Widgets, true);
            }
        });
        widgetButton.setOnTouchListener(mLauncher.getHapticFeedbackTouchListener());

        View wallpaperButton = mLauncher.findViewById(R.id.wallpaper_button);
        wallpaperButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                mLauncher.startWallpaper();
            }
        });
        wallpaperButton.setOnTouchListener(mLauncher.getHapticFeedbackTouchListener());

        View themesButton = mLauncher.findViewById(R.id.themes_button);
        themesButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                mLauncher.startThemeSettings();
            }
        });
        themesButton.setOnTouchListener(mLauncher.getHapticFeedbackTouchListener());

        View defaultScreenButton = mLauncher.findViewById(R.id.default_screen_button);
        defaultScreenButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                mLauncher.getWorkspace().onClickDefaultScreenButton();
            }
        });

        defaultScreenButton.setOnTouchListener(mLauncher.getHapticFeedbackTouchListener());

        //Handle
        View v = mOverviewPanel.findViewById(R.id.settings_pane_header);
        ((SlidingUpPanelLayout) mOverviewPanel).setEnableDragViewTouchEvents(true);
        ((SlidingUpPanelLayout) mOverviewPanel).setDragView(v);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (((SlidingUpPanelLayout) mOverviewPanel).isExpanded()) {
                    ((SlidingUpPanelLayout) mOverviewPanel).collapsePane();
                } else {
                    ((SlidingUpPanelLayout) mOverviewPanel).expandPane();
                }
            }
        });
    }

    public void update() {
        Resources res = mLauncher.getResources();
        View widgetButton = mOverviewPanel.findViewById(R.id.widget_button);
        View wallpaperButton = mOverviewPanel
                .findViewById(R.id.wallpaper_button);
        View themesButton = mOverviewPanel.findViewById(R.id.themes_button);
        View defaultHomePanel = mOverviewPanel.findViewById(R.id.default_screen_button);

        boolean isAllAppsVisible = mLauncher.isAllAppsVisible();

        PagedView pagedView = !isAllAppsVisible ? mLauncher.getWorkspace()
                : mLauncher.getAppsCustomizeContent();

        defaultHomePanel.setVisibility((pagedView.getPageCount() > 1) ?
                View.VISIBLE : View.GONE);

        if (mLauncher.isAllAppsVisible()) {
            mSettingsAdapter.changeCursor(0, createCursor(res
                    .getString(R.string.home_screen_settings), new String[]{}));
        } else {
            String[] values = new String[] {
                    res.getString(R.string.home_screen_search_text),
                    res.getString(R.string.page_scroll_effect_text),
                    res.getString(R.string.larger_icons_text),
                    res.getString(R.string.hide_icon_labels)};
            mSettingsAdapter.changeCursor(0, createCursor(res
                    .getString(R.string.home_screen_settings), values));
        }

        // Make sure overview panel is drawn above apps customize and collapsed
        mOverviewPanel.bringToFront();
        mOverviewPanel.invalidate();

        ((SlidingUpPanelLayout) mOverviewPanel).setPanelHeight(isAllAppsVisible ?
                res.getDimensionPixelSize(R.dimen.settings_pane_handle)
                : res.getDimensionPixelSize(R.dimen.sliding_panel_padding));

        ((SlidingUpPanelLayout) mOverviewPanel).collapsePane();
    }

    public void notifyDataSetInvalidated() {
        mSettingsAdapter.notifyDataSetInvalidated();
    }


    class SettingsSimplePanelSlideListener extends SlidingUpPanelLayout.SimplePanelSlideListener {
        ImageView mAnimatedArrow;

        public SettingsSimplePanelSlideListener() {
            super();
            mAnimatedArrow = (ImageView) mOverviewPanel.findViewById(R.id.settings_drag_arrow);
        }

        @Override
        public void onPanelCollapsed(View panel) {
            mAnimatedArrow.setBackgroundResource(R.drawable.transition_arrow_reverse);

            AnimationDrawable frameAnimation = (AnimationDrawable) mAnimatedArrow.getBackground();
            frameAnimation.start();
        }

        @Override
        public void onPanelExpanded(View panel) {
            mAnimatedArrow.setBackgroundResource(R.drawable.transition_arrow);

            AnimationDrawable frameAnimation = (AnimationDrawable) mAnimatedArrow.getBackground();
            frameAnimation.start();
        }
    }
}
