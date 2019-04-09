package com.google.android.apps.nexuslauncher.graphics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.icu.text.DateFormat;
import android.icu.text.DisplayContext;
import android.support.annotation.RequiresApi;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import ch.deletescape.lawnchair.LawnchairPreferences;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;

import java.util.Locale;

public class IcuDateTextView extends DoubleShadowTextView {
    private DateFormat mDateFormat;
    private final BroadcastReceiver mTimeChangeReceiver;
    private boolean mIsVisible = false;

    public IcuDateTextView(final Context context) {
        this(context, null);
    }

    public IcuDateTextView(final Context context, final AttributeSet set) {
        super(context, set, 0);
        mTimeChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                reloadDateFormat(!Intent.ACTION_TIME_TICK.equals(intent.getAction()));
            }
        };
    }

    public void reloadDateFormat(boolean forcedChange) {
        String format;
        if (Utilities.ATLEAST_NOUGAT) {
            mDateFormat = getDateFormat(getContext(), forcedChange, mDateFormat, getId() == R.id.time_above);
            format = mDateFormat.format(System.currentTimeMillis());
        } else {
            format = DateUtils.formatDateTime(getContext(), System.currentTimeMillis(),
                    DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
        }
        setText(format);
        setContentDescription(format);
    }

    @RequiresApi(24)
    public static DateFormat getDateFormat(Context context, boolean forcedChange, DateFormat oldFormat, boolean isTimeAbove) {
        if (oldFormat == null || forcedChange) {
            (oldFormat = DateFormat.getInstanceForSkeleton(context
                    .getString(R.string.icu_abbrev_wday_month_day_no_year), Locale.getDefault()))
                    .setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE);
        }
        LawnchairPreferences prefs = Utilities.getLawnchairPrefs(context);
        boolean showTime = prefs.getSmartspaceTime();
        boolean timeAbove = prefs.getSmartspaceTimeAbove();
        boolean show24h = prefs.getSmartspaceTime24H();
        boolean showDate = prefs.getSmartspaceDate();
        if ((showTime && !timeAbove) || isTimeAbove) {
            String format = context.getString(show24h ? R.string.icu_abbrev_time : R.string.icu_abbrev_time_12h);
            if (showDate && !isTimeAbove)
                format += context.getString(R.string.icu_abbrev_date);
            (oldFormat = DateFormat.getInstanceForSkeleton(format, Locale.getDefault()))
                    .setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE);
        }
        return oldFormat;
    }

    private void registerReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_TIME_TICK);
        intentFilter.addAction(Intent.ACTION_TIME_CHANGED);
        intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getContext().registerReceiver(mTimeChangeReceiver, intentFilter);
    }

    private void unregisterReceiver() {
        getContext().unregisterReceiver(mTimeChangeReceiver);
    }

    public void onVisibilityAggregated(boolean isVisible) {
        if (Utilities.ATLEAST_NOUGAT) {
            super.onVisibilityAggregated(isVisible);
        }
        if (!mIsVisible && isVisible) {
            mIsVisible = true;
            registerReceiver();
            reloadDateFormat(true);
        } else if (mIsVisible && !isVisible) {
            unregisterReceiver();
            mIsVisible = false;
        }
    }
}
