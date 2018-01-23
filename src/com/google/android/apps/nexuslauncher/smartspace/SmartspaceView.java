package com.google.android.apps.nexuslauncher.smartspace;

import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Process;
import android.provider.CalendarContract;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.dynamicui.WallpaperColorInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.Themes;
import com.google.android.apps.nexuslauncher.DynamicIconProvider;
import com.google.android.apps.nexuslauncher.graphics.IcuDateTextView;

import java.util.ArrayList;
import java.util.Collections;

public class SmartspaceView extends FrameLayout implements ISmartspace, ValueAnimator.AnimatorUpdateListener, View.OnClickListener, View.OnLongClickListener, Runnable {
    private TextView mSubtitleWeatherText;
    private final TextPaint dB;
    private View mTitleSeparator;
    private TextView mTitleText;
    private ViewGroup mTitleWeatherContent;
    private ImageView mTitleWeatherIcon;
    private TextView mTitleWeatherText;
    private final ColorStateList dH;
    private final int mSmartspaceBackgroundRes;
    private IcuDateTextView mClockView;
    private ViewGroup mSmartspaceContent;
    private final SmartspaceController dp;
    private SmartspaceDataContainer dq;
    private BubbleTextView dr;
    private boolean ds;
    private boolean mDoubleLine;
    private final OnClickListener mCalendarClickListener;
    private final OnClickListener mWeatherClickListener;
    private ImageView mSubtitleIcon;
    private TextView mSubtitleText;
    private ViewGroup mSubtitleWeatherContent;
    private ImageView mSubtitleWeatherIcon;
    private final Handler mHandler;

    public SmartspaceView(Context context, AttributeSet set) {
        super(context, set);

        mCalendarClickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                cp(10000);
                final Uri content_URI = CalendarContract.CONTENT_URI;
                final Uri.Builder appendPath = content_URI.buildUpon().appendPath("time");
                ContentUris.appendId(appendPath, System.currentTimeMillis());
                final Intent addFlags = new Intent(Intent.ACTION_VIEW)
                        .setData(appendPath.build())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try {
                    final Context context = getContext();
                    Launcher.getLauncher(context).startActivitySafely(v, addFlags, null);
                } catch (ActivityNotFoundException ex) {
                    LauncherAppsCompat.getInstance(getContext()).showAppDetailsForProfile(new ComponentName(DynamicIconProvider.GOOGLE_CALENDAR, ""), Process.myUserHandle());
                }
            }
        };

        mWeatherClickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dq != null && dq.isWeatherAvailable()) {
                    cp(10001);
                    dq.dO.click(v);
                }
            }
        };

        dp = SmartspaceController.get(context);
        mHandler = new Handler();
        dH = ColorStateList.valueOf(Themes.getAttrColor(getContext(), R.attr.workspaceTextColor));
        ds = dp.cY();
        mSmartspaceBackgroundRes = R.drawable.bg_smartspace;
        dB = new TextPaint();
        dB.setTextSize((float) getResources().getDimensionPixelSize(R.dimen.smartspace_title_size));
    }

    private void initListeners(final SmartspaceDataContainer e) {
        final boolean cs = e.cS();
        if (mDoubleLine != cs) {
            mDoubleLine = cs;
            cs();
        }
        setOnClickListener(this);
        setOnLongClickListener(co());
        if (mDoubleLine) {
            loadDoubleLine(e);
        } else {
            loadSingleLine(e);
        }
        mHandler.removeCallbacks(this);
        if (e.cS() && e.dP.cv()) {
            final long cw = e.dP.cw();
            long min = 61000L - System.currentTimeMillis() % 60000L;
            if (cw > 0L) {
                min = Math.min(min, cw);
            }
            mHandler.postDelayed(this, min);
        }
    }

    private void loadDoubleLine(final SmartspaceDataContainer e) {
        ColorStateList dh = null;
        setBackgroundResource(mSmartspaceBackgroundRes);
        final SmartspaceCard dp = e.dP;
        if (!TextUtils.isEmpty(dp.getTitle())) {
            mTitleText.setText(dp.cv() ? cn() : dp.getTitle());
            mTitleText.setEllipsize(dp.cx(true));
        }
        if (!TextUtils.isEmpty(dp.cy()) || dp.getIcon() != null) {
            mSubtitleText.setText(dp.cy());
            mSubtitleText.setEllipsize(dp.cx(false));
            if (dp.getIcon() != null) {
                if (dp.cz() && WallpaperColorInfo.getInstance(getContext()).supportsDarkText()) {
                    dh = dH;
                }
                mSubtitleIcon.setImageTintList(dh);
                mSubtitleIcon.setImageBitmap(dp.getIcon());
            }
        }
        if (e.isWeatherAvailable()) {
            mSubtitleWeatherContent.setVisibility(View.VISIBLE);
            mSubtitleWeatherContent.setOnClickListener(mWeatherClickListener);
            mSubtitleWeatherContent.setOnLongClickListener(co());
            mSubtitleWeatherText.setText(e.dO.getTitle());
            mSubtitleWeatherIcon.setImageBitmap(e.dO.getIcon());
        } else {
            mSubtitleWeatherContent.setVisibility(View.GONE);
        }
    }

    private void loadSingleLine(final SmartspaceDataContainer e) {
        setBackgroundResource(0);
        mClockView.setOnClickListener(mCalendarClickListener);
        mClockView.setOnLongClickListener(co());
        if (e.isWeatherAvailable()) {
            mTitleSeparator.setVisibility(View.VISIBLE);
            mTitleWeatherContent.setVisibility(View.VISIBLE);
            mTitleWeatherContent.setOnClickListener(mWeatherClickListener);
            mTitleWeatherContent.setOnLongClickListener(co());
            mTitleWeatherText.setText(e.dO.getTitle());
            mTitleWeatherIcon.setImageBitmap(e.dO.getIcon());
        } else {
            mTitleWeatherContent.setVisibility(View.GONE);
            mTitleSeparator.setVisibility(View.GONE);
        }

        if (!Utilities.ATLEAST_NOUGAT) {
            mClockView.onVisibilityAggregated(true);
        }
    }

    private void loadViews() {
        mTitleText = findViewById(R.id.title_text);
        mSubtitleText = findViewById(R.id.subtitle_text);
        mSubtitleIcon = findViewById(R.id.subtitle_icon);
        mTitleWeatherIcon = findViewById(R.id.title_weather_icon);
        mSubtitleWeatherIcon = findViewById(R.id.subtitle_weather_icon);
        mSmartspaceContent = findViewById(R.id.smartspace_content);
        mTitleWeatherContent = findViewById(R.id.title_weather_content);
        mSubtitleWeatherContent = findViewById(R.id.subtitle_weather_content);
        mTitleWeatherText = findViewById(R.id.title_weather_text);
        mSubtitleWeatherText = findViewById(R.id.subtitle_weather_text);
        backportClockVisibility(false);
        mClockView = findViewById(R.id.clock);
        backportClockVisibility(true);
        mTitleSeparator = findViewById(R.id.title_sep);

        setGoogleSans(mTitleText, mSubtitleText, mTitleWeatherText, mSubtitleWeatherText, mClockView);
    }

    private void setGoogleSans(TextView... views) {
        Typeface tf = Typeface.createFromAsset(getContext().getAssets(), "fonts/GoogleSans-Regular.ttf");
        for (TextView view : views) {
            if (view != null) {
                view.setTypeface(tf);
            }
        }
    }

    private String cn() {
        final boolean b = true;
        final SmartspaceCard dp = dq.dP;
        return dp.cC(TextUtils.ellipsize(dp.cB(b), dB, getWidth() - getPaddingLeft() - getPaddingRight() - getResources().getDimensionPixelSize(R.dimen.smartspace_horizontal_padding) - dB.measureText(dp.cA(b)), TextUtils.TruncateAt.END).toString());
    }

    private OnLongClickListener co() {
        return ds ? this : null;
    }

    private void cs() {
        final int indexOfChild = indexOfChild(mSmartspaceContent);
        removeView(mSmartspaceContent);
        final LayoutInflater from = LayoutInflater.from(getContext());
        addView(from.inflate(mDoubleLine ?
                R.layout.smartspace_twolines :
                R.layout.smartspace_singleline, this, false), indexOfChild);
        loadViews();
    }

    protected final void cp(int n) {
        //((UserEventDispatcherImpl) Launcher.getLauncher(getContext()).getUserEventDispatcher()).bp(n);
    }

    public void cq() {
        ds = dp.cY();
        if (dq != null) {
            cr(dq);
        } else {
            Log.d("SmartspaceView", "onGsaChanged but no data present");
        }
    }

    public void cr(final SmartspaceDataContainer dq2) {
        dq = dq2;
        boolean visible = mSmartspaceContent.getVisibility() == View.VISIBLE;
        initListeners(dq);
        if (!visible) {
            mSmartspaceContent.setVisibility(View.VISIBLE);
            mSmartspaceContent.setAlpha(0f);
            mSmartspaceContent.animate().setDuration(200L).alpha(1f);
        }
    }

    public void onAnimationUpdate(final ValueAnimator valueAnimator) {
        invalidate();
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        dp.da(this);
    }

    public void onClick(final View view) {
        if (dq != null && dq.cS()) {
            cp(10002);
            dq.dP.click(view);
        }
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        SmartspaceController.get(getContext()).da(null);
    }

    protected void onFinishInflate() {
        super.onFinishInflate();
        loadViews();
        dr = findViewById(R.id.dummyBubbleTextView);
        dr.setTag(new ItemInfo() {
            @Override
            public ComponentName getTargetComponent() {
                return new ComponentName(getContext(), "");
            }
        });
        dr.setContentDescription("");
    }

    protected void onLayout(final boolean b, final int n, final int n2, final int n3, final int n4) {
        super.onLayout(b, n, n2, n3, n4);
        if (dq != null && dq.cS() && dq.dP.cv()) {
            final String cn = cn();
            if (!cn.equals(mTitleText.getText())) {
                mTitleText.setText(cn);
            }
        }
    }

    public boolean onLongClick(final View view) {
        final boolean b = true;
        final Launcher launcher = Launcher.getLauncher(getContext());
        final PopupContainerWithArrow popupContainerWithArrow = (PopupContainerWithArrow) launcher.getLayoutInflater().inflate(R.layout.popup_container, launcher.getDragLayer(), false);
        popupContainerWithArrow.setVisibility(View.INVISIBLE);
        launcher.getDragLayer().addView(popupContainerWithArrow);
        ArrayList<SystemShortcut> list = new ArrayList<>(1);
        list.add(new SmartspacePreferencesShortcut());
        popupContainerWithArrow.populateAndShow(dr, Collections.EMPTY_LIST, Collections.EMPTY_LIST, list);
        return b;
    }

    public void onPause() {
        mHandler.removeCallbacks(this);
        backportClockVisibility(false);
    }

    public void onResume() {
        if (dq != null) {
            initListeners(dq);
        }
        backportClockVisibility(true);
    }

    private void backportClockVisibility(boolean show) {
        if (!Utilities.ATLEAST_NOUGAT && mClockView != null) {
            mClockView.onVisibilityAggregated(show && !mDoubleLine);
        }
    }

    @Override
    public void run() {
        if (dq != null) {
            initListeners(dq);
        }
    }

    @Override
    public void setPadding(final int n, final int n2, final int n3, final int n4) {
        super.setPadding(0, 0, 0, 0);
    }

    final class h implements OnClickListener {
        final SmartspaceView dZ;

        h(final SmartspaceView dz) {
            dZ = dz;
        }

        public void onClick(final View view) {
            dZ.cp(10000);
            final Uri content_URI = CalendarContract.CONTENT_URI;
            final Uri.Builder appendPath = content_URI.buildUpon().appendPath("time");
            ContentUris.appendId(appendPath, System.currentTimeMillis());
            final Intent addFlags = new Intent(Intent.ACTION_VIEW).setData(appendPath.build()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                final Context context = dZ.getContext();
                Launcher.getLauncher(context).startActivitySafely(view, addFlags, null);
            } catch (ActivityNotFoundException ex) {
                LauncherAppsCompat.getInstance(dZ.getContext()).showAppDetailsForProfile(new ComponentName(DynamicIconProvider.GOOGLE_CALENDAR, ""), Process.myUserHandle());
            }
        }
    }
}
