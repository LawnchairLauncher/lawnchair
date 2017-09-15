package ch.deletescape.lawnchair;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import ch.deletescape.lawnchair.weather.WeatherHelper;

public class QsbBlockerView extends FrameLayout implements Workspace.OnStateChangeListener, View.OnLongClickListener, WeatherHelper.OnWeatherLoadListener {
    public static final Property<QsbBlockerView, Integer> QSB_BLOCKER_VIEW_ALPHA = new QsbBlockerViewAlpha(Integer.TYPE, "bgAlpha");
    private final Paint mBgPaint = new Paint(1);
    private View mView;
    private WeatherHelper weatherHelper;
    private boolean switchToDate = false;
    private boolean switching = false;
    private boolean weatherShowing = false;

    public QsbBlockerView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mBgPaint.setColor(-1);
        mBgPaint.setAlpha(0);
        if (Utilities.getPrefs(getContext()).getUseFullWidthSearchBar()) {
            View.inflate(context, R.layout.qsb_wide_experiment, this);
        }
    }


    @Override
    public void setPadding(int i, int i2, int i3, int i4) {
        super.setPadding(0, 0, 0, 0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!Utilities.getPrefs(getContext()).getUseFullWidthSearchBar()) {
            Workspace workspace = Launcher.getLauncher(getContext()).getWorkspace();
            workspace.setOnStateChangeListener(this);
            prepareStateChange(workspace.getState(), null);
            setupView(true);
        }
    }

    @Override
    protected void onMeasure(int i, int i2) {
        if (mView != null && weatherShowing) {
            DeviceProfile deviceProfile = Launcher.getLauncher(getContext()).getDeviceProfile();
            LayoutParams layoutParams = (LayoutParams) mView.getLayoutParams();
            int size = ((MeasureSpec.getSize(i) / deviceProfile.inv.numColumnsOriginal) - deviceProfile.iconSizePxOriginal) / 2;
            layoutParams.rightMargin = size;
            layoutParams.leftMargin = size;
        }
        super.onMeasure(i, i2);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    public void prepareStateChange(Workspace.State state, AnimatorSet animatorSet) {
        int i;
        if (state == Workspace.State.SPRING_LOADED) {
            i = 60;
        } else {
            i = 0;
        }
        if (animatorSet == null) {
            QSB_BLOCKER_VIEW_ALPHA.set(this, i);
            return;
        }
        animatorSet.play(ObjectAnimator.ofInt(this, QSB_BLOCKER_VIEW_ALPHA, i));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPaint(mBgPaint);
    }

    public void setupView(boolean startListener) {
        if (!Utilities.getPrefs(getContext()).getShowPixelBar()) {
            removeAllViews();
            return;
        }
        View view = mView;
        mView = null;
        if (view == null || switching) {
            if (Utilities.getPrefs(getContext()).getEnablePlanes()) {
                mView = LayoutInflater.from(getContext()).inflate(R.layout.plane_widget, this, false);
            } else if ((Utilities.getPrefs(getContext()).getShowWeather() && !switchToDate) || (switching && !switchToDate)) {
                weatherShowing = true;
                mView = LayoutInflater.from(getContext()).inflate(R.layout.weather_widget, this, false);
                TextView temperature = mView.findViewById(R.id.weather_widget_temperature);
                ImageView iconView = mView.findViewById(R.id.weather_widget_icon);
                weatherHelper = startListener || weatherHelper == null ? new WeatherHelper(temperature, iconView, getContext()) : weatherHelper;
                weatherHelper.setListener(this);
                mView.findViewById(R.id.weather_widget_time).setOnLongClickListener(this);
                temperature.setOnLongClickListener(this);
                iconView.setOnLongClickListener(this);
            } else {
                weatherShowing = false;
                mView = LayoutInflater.from(getContext()).inflate(R.layout.date_widget, this, false);
                mView.findViewById(R.id.date_text1).setOnLongClickListener(this);
                mView.findViewById(R.id.date_text2).setOnLongClickListener(this);
            }
            if (Utilities.getPrefs(getContext()).getUseFullWidthSearchBar()) {
                mView.setVisibility(GONE);
            }
        } else {
            mView = view;
        }
        if (switching) {
            if (view != null) {
                view.animate().setDuration(200).alpha(0.0f).withEndAction(new QsbBlockerViewViewRemover(this, view));
            }
            addView(mView);
            mView.setAlpha(0.0f);
            mView.animate().setDuration(200).alpha(1.0f);
            switching = false;
        } else if (view != mView) {
            if (view != null) {
                removeView(view);
            }
            addView(mView);
        }
    }

    @Override
    public boolean onLongClick(View view) {
        switchToDate = weatherShowing;
        switching = true;
        if (weatherHelper != null) {
            weatherHelper.stop();
        }
        setupView(true);
        return true;
    }

    @Override
    public void onLoad(boolean success) {
        if (weatherShowing && !success) {
            switchToDate = true;
            switching = true;
            setupView(false);
        } else if (!weatherShowing && success) {
            switchToDate = false;
            switching = true;
            setupView(false);
        }
    }

    private final class QsbBlockerViewViewRemover implements Runnable {
        final QsbBlockerView mQsbBlockerView;
        final View mView;

        QsbBlockerViewViewRemover(QsbBlockerView qsbBlockerView, View view) {
            mQsbBlockerView = qsbBlockerView;
            mView = view;
        }

        @Override
        public void run() {
            mQsbBlockerView.removeView(mView);
        }
    }

    private static final class QsbBlockerViewAlpha extends Property<QsbBlockerView, Integer> {

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