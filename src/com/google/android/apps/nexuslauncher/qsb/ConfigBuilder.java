package com.google.android.apps.nexuslauncher.qsb;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;

import com.android.launcher3.AppInfo;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsRecyclerView;
import com.android.launcher3.allapps.AlphabeticalAppsList;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.dynamicui.WallpaperColorInfo;
import com.android.launcher3.util.ComponentKeyMapper;
import com.android.launcher3.util.Themes;
import com.google.android.apps.nexuslauncher.NexusLauncherActivity;
import com.google.android.apps.nexuslauncher.search.AppSearchProvider;
import com.google.android.apps.nexuslauncher.search.nano.SearchProto.a_search;
import com.google.android.apps.nexuslauncher.search.nano.SearchProto.b_search;
import com.google.android.apps.nexuslauncher.search.nano.SearchProto.c_search;
import com.google.android.apps.nexuslauncher.search.nano.SearchProto.d_search;

import java.util.ArrayList;
import java.util.List;

public class ConfigBuilder {
    private final c_search cl;
    private final NexusLauncherActivity mActivity;
    private final Bundle cn;
    private boolean co;
    private final AbstractQsbLayout cp;
    private BubbleTextView cq;
    private final boolean cr;
    private final UserManagerCompat mUserManager;

    public ConfigBuilder(AbstractQsbLayout cp, boolean cr) {
        cn = new Bundle();
        cl = new c_search();
        this.cp = cp;
        mActivity = cp.mActivity;
        this.cr = cr;
        mUserManager = UserManagerCompat.getInstance(mActivity);
    }

    public static Intent getSearchIntent(Rect sourceBounds, View gIcon, View micIcon) {
        Intent intent = new Intent("com.google.nexuslauncher.FAST_TEXT_SEARCH");
        intent.setSourceBounds(sourceBounds);
        if (micIcon.getVisibility() != View.VISIBLE) {
            intent.putExtra("source_mic_alpha", 0.0f);
        }
        return intent.putExtra("source_round_left", true)
                .putExtra("source_round_right", true)
                .putExtra("source_logo_offset", cc(gIcon, sourceBounds))
                .putExtra("source_mic_offset", cc(micIcon, sourceBounds))
                .putExtra("use_fade_animation", true)
                .setPackage("com.google.android.googlequicksearchbox")
                .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private void bW() {
        if (cl.ez != null) {
            return;
        }
        final a_search en = cl.en;
        final a_search ez = new a_search();
        ez.ef = en.ef;
        ez.eg = en.eg + en.ee;
        ez.ee = en.ee;
        ez.eh = en.eh;
        cl.ez = ez;
    }

    private AllAppsRecyclerView bX() {
        return (AllAppsRecyclerView) mActivity.findViewById(R.id.apps_list_view);
    }

    private int bY() {
        return android.support.v4.graphics.ColorUtils.compositeColors(Themes.getAttrColor(mActivity, R.attr.allAppsScrimColor), android.support.v4.graphics.ColorUtils.setAlphaComponent(WallpaperColorInfo.getInstance(mActivity).getMainColor(), 255));
    }

    private b_search bZ(final AppInfo appInfo, final int n) {
        final b_search b = new b_search();
        b.label = appInfo.title.toString();
        b.ej = "icon_bitmap_" + n;
        cn.putParcelable(b.ej, appInfo.iconBitmap);
        Uri dm = AppSearchProvider.dm(appInfo, mUserManager);
        b.el = dm.toString();
        b.ek = new Intent("com.google.android.apps.nexuslauncher.search.APP_LAUNCH", dm.buildUpon().appendQueryParameter("predictionRank", Integer.toString(n)).build()).toUri(0);
        return b;
    }

    private RemoteViews ca() {
        final RemoteViews remoteViews = new RemoteViews(mActivity.getPackageName(), R.layout.apps_search_icon_template);
        final int iconSize = cq.getIconSize();
        final int n2 = (cq.getWidth() - iconSize) / 2;
        final int paddingTop = cq.getPaddingTop();
        final int n3 = cq.getHeight() - iconSize - paddingTop;
        remoteViews.setViewPadding(android.R.id.icon, n2, paddingTop, n2, n3);
        final int min = Math.min((int) (iconSize * 0.12f), Math.min(n2, Math.min(paddingTop, n3)));
        remoteViews.setViewPadding(R.id.click_feedback_wrapper, n2 - min, paddingTop - min, n2 - min, n3 - min);
        remoteViews.setTextViewTextSize(android.R.id.title, 0, mActivity.getDeviceProfile().allAppsIconTextSizePx);
        remoteViews.setViewPadding(android.R.id.title, cq.getPaddingLeft(), cq.getCompoundDrawablePadding() + cq.getIconSize(), cq.getPaddingRight(), 0);
        return remoteViews;
    }

    private RemoteViews cb() {
        final RemoteViews remoteViews = new RemoteViews(mActivity.getPackageName(), R.layout.apps_search_qsb_template);
        final int n = cp.getHeight() - cp.getPaddingTop() - cp.getPaddingBottom() + 20;
        final Bitmap mShadowBitmap = cp.mShadowBitmap;
        final int n2 = (mShadowBitmap.getWidth() - n) / 2;
        final int n3 = (cp.getHeight() - mShadowBitmap.getHeight()) / 2;
        remoteViews.setViewPadding(R.layout.all_apps_discovery_loading_divider, cp.getPaddingLeft() - n2, n3, cp.getPaddingRight() - n2, n3);
        final Bitmap bitmap = Bitmap.createBitmap(mShadowBitmap, 0, 0, mShadowBitmap.getWidth() / 2, mShadowBitmap.getHeight());
        remoteViews.setImageViewBitmap(R.id.qsb_background_1, bitmap);
        remoteViews.setImageViewBitmap(R.id.qsb_background_3, bitmap);
        remoteViews.setImageViewBitmap(R.id.qsb_background_2, Bitmap.createBitmap(mShadowBitmap, (mShadowBitmap.getWidth() - 20) / 2, 0, 20, mShadowBitmap.getHeight()));
        if (cp.mMicIconView.getVisibility() != View.VISIBLE) {
            remoteViews.setViewVisibility(R.id.mic_icon, View.INVISIBLE);
        }
        final View viewById = cp.findViewById(R.id.g_icon);
        int left;
        if (cp.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            left = cp.getWidth() - viewById.getRight();
        } else {
            left = viewById.getLeft();
        }
        remoteViews.setViewPadding(R.id.qsb_icon_container, left, 0, left, 0);
        return remoteViews;
    }

    private static Point cc(final View view, final Rect rect) {
        final int[] array = new int[2];
        view.getLocationInWindow(array);
        final Point point = new Point();
        point.x = array[0] - rect.left + view.getWidth() / 2;
        point.y = array[1] - rect.top + view.getHeight() / 2;
        return point;
    }

    private void cd() {
        cl.ey = "search_box_template";
        cn.putParcelable(cl.ey, cb());
        cl.ew = R.id.g_icon;
        final c_search cl = this.cl;
        int ex;
        if (cp.mMicIconView.getVisibility() != View.VISIBLE) {
            ex = 0;
        } else {
            ex = R.id.mic_icon;
        }
        cl.ex = ex;
        final a_search viewBounds = getViewBounds(mActivity.getDragLayer());
        int eg = cl.en.eg;
        if (!co) {
            eg += cl.en.ee;
        }
        viewBounds.eg += eg;
        viewBounds.ee -= eg;
        cl.et = viewBounds;
        final Bitmap bitmap = Bitmap.createBitmap(viewBounds.eh, viewBounds.ee, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        canvas.translate(0.0f, (float) (-eg));
        final AllAppsRecyclerView bx = bX();
        final int[] array = {0, 0};
        mActivity.getDragLayer().mapCoordInSelfToDescendant(bx, array);
        canvas.translate((float) (-array[0]), (float) (-array[1]));
        bx.draw(canvas);
        canvas.setBitmap(null);
        cl.eu = "preview_bitmap";
        cn.putParcelable(cl.eu, bitmap);
    }

    private void ce() {
        int i;
        View view = null;
        int i2 = 0;
        AllAppsRecyclerView bX = bX();
        GridLayoutManager.SpanSizeLookup spanSizeLookup = ((GridLayoutManager) bX.getLayoutManager())
                .getSpanSizeLookup();
        int i3 = this.mActivity.getDeviceProfile().allAppsNumCols;
        int childCount = bX.getChildCount();
        BubbleTextView[] bubbleTextViewArr = new BubbleTextView[i3];
        int i4 = -1;
        for (i = 0; i < childCount; i++) {
            RecyclerView.ViewHolder childViewHolder = bX.getChildViewHolder(bX.getChildAt(i));
            if (childViewHolder.itemView instanceof BubbleTextView) {
                int spanGroupIndex = spanSizeLookup.getSpanGroupIndex(childViewHolder.getLayoutPosition(), i3);
                if (spanGroupIndex < 0) {
                    continue;
                } else {
                    if (i4 >= 0) {
                        if (spanGroupIndex != i4) {
                            view = childViewHolder.itemView;
                            break;
                        }
                    }
                    i4 = spanGroupIndex;
                    bubbleTextViewArr[((GridLayoutManager.LayoutParams) childViewHolder.itemView.getLayoutParams()).getSpanIndex()] = (BubbleTextView) childViewHolder.itemView;
                }
            }
        }
        if (bubbleTextViewArr[0] == null) {
            Log.e("ConfigBuilder", "No icons rendered in all apps");
            cf();
            return;
        }
        this.cq = bubbleTextViewArr[0];
        this.cl.es = i3;
        this.co = bX.getChildViewHolder(bubbleTextViewArr[0]).getItemViewType() == 4;
        a_search viewBounds = getViewBounds(bubbleTextViewArr[i3 - 1]);
        a_search viewBounds2 = getViewBounds(bubbleTextViewArr[0]);
        if (Utilities.isRtl(this.mActivity.getResources())) {
            a_search aVar = viewBounds;
            viewBounds = viewBounds2;
            viewBounds2 = aVar;
        }
        viewBounds2.eh = (viewBounds.eh + viewBounds.ef) - viewBounds2.ef;
        this.cl.en = viewBounds2;
        if (!this.co) {
            viewBounds2.eg -= viewBounds2.ee;
        } else if (view != null) {
            a_search viewBounds3 = getViewBounds(view);
            viewBounds3.eh = viewBounds2.eh;
            this.cl.ez = viewBounds3;
        }
        bW();
        List predictedApps = bX.getApps().getPredictedApps();
        i = Math.min(predictedApps.size(), i3);
        this.cl.eo = new b_search[i];
        while (i2 < i) {
            this.cl.eo[i2] = bZ((AppInfo) predictedApps.get(i2), i2);
            i2++;
        }
    }

    private void cf() {
        int n2 = 0;
        cl.es = mActivity.getDeviceProfile().allAppsNumCols;
        final int width = mActivity.getHotseat().getWidth();
        final int dimensionPixelSize = mActivity.getResources().getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin);
        final a_search en = new a_search();
        en.ef = dimensionPixelSize;
        en.eh = width - dimensionPixelSize - dimensionPixelSize;
        en.ee = mActivity.getDeviceProfile().allAppsCellHeightPx;
        cl.en = en;
        bW();
        final AlphabeticalAppsList apps = bX().getApps();
        cq = (BubbleTextView) mActivity.getLayoutInflater().inflate(R.layout.all_apps_icon, bX(), false);
        final ViewGroup.LayoutParams layoutParams = cq.getLayoutParams();
        layoutParams.height = en.ee;
        layoutParams.width = en.eh / cl.es;
        if (!apps.getApps().isEmpty()) {
            cq.applyFromApplicationInfo(apps.getApps().get(0));
        }
        cq.measure(View.MeasureSpec.makeMeasureSpec(layoutParams.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(layoutParams.height, View.MeasureSpec.EXACTLY));
        cq.layout(0, 0, layoutParams.width, layoutParams.height);
        final ArrayList<b_search> list = new ArrayList<>(cl.es);
        for (ComponentKeyMapper<AppInfo> cmp : mActivity.getPredictedApps()) {
            final AppInfo app = apps.findApp(cmp);
            int n3;
            if (app != null) {
                list.add(bZ(app, n2));
                n3 = n2 + 1;
                if (n3 >= cl.es) {
                    break;
                }
            } else {
                n3 = n2;
            }
            n2 = n3;
        }

        cl.eo = list.toArray(new b_search[list.size()]);
    }

    private static a_search getViewBounds(final View view) {
        final a_search a = new a_search();
        a.eh = view.getWidth();
        a.ee = view.getHeight();
        final int[] array = new int[2];
        view.getLocationInWindow(array);
        a.ef = array[0];
        a.eg = array[1];
        return a;
    }

    public byte[] build() {
        cl.em = bY();
        cl.eq = Themes.getAttrBoolean(mActivity, R.attr.isMainColorDark);
        if (cr) {
            ce();
        } else {
            cf();
        }
        cl.ep = "icon_view_template";
        cn.putParcelable(cl.ep, ca());
        cl.er = "icon_long_click";
        cn.putParcelable(cl.er, PendingIntent.getBroadcast(mActivity, 2055, new Intent().setComponent(new ComponentName(mActivity, LongClickReceiver.class)), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT));
        LongClickReceiver.bq(mActivity);
        cl.ev = getViewBounds(cp);
        cl.eA = cr;
        if (cr) {
            cd();
        }
        final d_search d = new d_search();
        d.eB = cl;
        return com.google.protobuf.nano.MessageNano.toByteArray(d);
    }

    public Bundle getExtras() {
        return cn;
    }
}
