package com.google.android.apps.nexuslauncher.superg;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import ch.deletescape.lawnchair.LawnchairLauncher;
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController;
import com.android.launcher3.*;
import com.android.launcher3.Workspace.OnStateChangeListener;
import org.jetbrains.annotations.NotNull;

/**
 * A simple view used to show the region blocked by QSB during drag and drop.
 */
public class QsbBlockerView extends FrameLayout implements OnStateChangeListener, LawnchairSmartspaceController.Listener {
    public static final Property<QsbBlockerView, Integer> QSB_BLOCKER_VIEW_ALPHA = new QsbBlockerViewAlpha(Integer.TYPE, "bgAlpha");
    private int mState = 0;
    private View mView;

    private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public QsbBlockerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mBgPaint.setColor(Color.WHITE);
        mBgPaint.setAlpha(0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mView != null && mState == 2) {
            DeviceProfile deviceProfile = Launcher.getLauncher(getContext()).getDeviceProfile();
            LayoutParams layoutParams = (LayoutParams) mView.getLayoutParams();
            int size = ((MeasureSpec.getSize(widthMeasureSpec) / deviceProfile.inv.numColumns) - deviceProfile.iconSizePx) / 2;
            layoutParams.leftMargin = layoutParams.rightMargin = size;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        Workspace w = Launcher.getLauncher(getContext()).getWorkspace();
        w.setOnStateChangeListener(this);
        prepareStateChange(w.getState(), null);

        Launcher launcher = Launcher.getLauncher(getContext());
        if (launcher instanceof LawnchairLauncher) {
            ((LawnchairLauncher) launcher).getSmartspace().addListener(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Launcher launcher = Launcher.getLauncher(getContext());
        if (launcher instanceof LawnchairLauncher) {
            ((LawnchairLauncher) launcher).getSmartspace().addListener(this);
        }
    }

    @Override
    public void prepareStateChange(Workspace.State state, AnimatorSet animatorSet) {
        int i = state == Workspace.State.SPRING_LOADED ? 60 : 0;
        if (animatorSet == null) {
            QSB_BLOCKER_VIEW_ALPHA.set(this, i);
        } else {
            animatorSet.play(ObjectAnimator.ofInt(this, QSB_BLOCKER_VIEW_ALPHA, i));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPaint(mBgPaint);
    }

    @Override
    public void onDataUpdated(@NotNull LawnchairSmartspaceController.DataContainer data) {
        final int oldState = mState;
        final View oldView = mView;

        if (data.getWeather() == null) {
            mState = 1;
            mView = oldView != null && oldState == 1 ?
                    oldView :
                    LayoutInflater.from(getContext()).inflate(R.layout.date_widget, this, false);
        } else {
            mState = 2;
            mView = oldView != null && oldState == 2 ?
                    oldView :
                    LayoutInflater.from(getContext()).inflate(R.layout.weather_widget, this, false);
            applyWeather(mView, data);
        }

        if (oldState != mState) {
            if (oldView != null) {
                oldView.animate().setDuration(200L).alpha(0f).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        removeView(oldView);
                    }
                });
            }
            addView(mView);
            mView.setAlpha(0f);
            mView.animate().setDuration(200L).alpha(1f);
        } else if (oldView != mView) {
            if (oldView != null) {
                removeView(oldView);
            }
            addView(mView);
        }
    }

    private void applyWeather(View view, LawnchairSmartspaceController.DataContainer data) {
        ImageView weatherIcon = view.findViewById(R.id.weather_widget_icon);
        weatherIcon.setImageBitmap(data.getWeather().getIcon());
        TextView weatherTemperature = view.findViewById(R.id.weather_widget_temperature);
        weatherTemperature.setText(data.getWeather().getTitle(
                Utilities.getLawnchairPrefs(getContext()).getUseMetricWeatherUnit()));
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(0, 0, 0, 0);
    }

    static class QsbBlockerViewAlpha extends Property<QsbBlockerView, Integer> {

        public QsbBlockerViewAlpha(Class<Integer> type, String name) {
            super(type, name);
        }

        @Override
        public void set(QsbBlockerView qsbBlockerView, Integer num) {
            qsbBlockerView.mBgPaint.setAlpha(num);
            qsbBlockerView.setWillNotDraw(num == 0);
            qsbBlockerView.invalidate();
        }

        @Override
        public Integer get(QsbBlockerView obj) {
            return obj.mBgPaint.getAlpha();
        }

    }
}