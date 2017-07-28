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

import ch.deletescape.lawnchair.config.FeatureFlags;
import ch.deletescape.lawnchair.weather.WeatherHelper;

public class QsbBlockerView extends FrameLayout implements Workspace.OnStateChangeListener, View.OnLongClickListener {
    public static final Property<QsbBlockerView, Integer> QSB_BLOCKER_VIEW_ALPHA = new QsbBlockerViewAlpha(Integer.TYPE, "bgAlpha");
    private final Paint mBgPaint = new Paint(1);
    private int mState = 0;
    private View mView;

    public QsbBlockerView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mBgPaint.setColor(-1);
        mBgPaint.setAlpha(0);
        if (FeatureFlags.useFullWidthSearchbar(getContext())) {
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
        if (!FeatureFlags.useFullWidthSearchbar(getContext())) {
            Workspace workspace = Launcher.getLauncher(getContext()).getWorkspace();
            workspace.setOnStateChangeListener(this);
            prepareStateChange(workspace.getState(), null);
            setupView();
        }
    }

    @Override
    protected void onMeasure(int i, int i2) {
        if (mView != null && mState == 2) {
            DeviceProfile deviceProfile = Launcher.getLauncher(getContext()).getDeviceProfile();
            LayoutParams layoutParams = (LayoutParams) mView.getLayoutParams();
            int size = ((MeasureSpec.getSize(i) / deviceProfile.inv.numColumns) - deviceProfile.iconSizePx) / 2;
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

    private boolean switchToDate = false;
    private boolean switching = false;

    public void setupView() {
        if (!FeatureFlags.showPixelBar(getContext())) {
            removeAllViews();
            return;
        }
        View view = mView;
        int i = mState;
        mView = null;
        mState = 2;
        mState = 1;
        if (view == null || i != 1 || switching) {
            switching = false;
            if (FeatureFlags.planes(getContext())) {
                mView = LayoutInflater.from(getContext()).inflate(R.layout.plane_widget, this, false);
            } else if (FeatureFlags.showWeather(getContext()) || (switching && !switchToDate)) {
                mView = LayoutInflater.from(getContext()).inflate(R.layout.weather_widget, this, false);
                TextView temperature = mView.findViewById(R.id.weather_widget_temperature);
                ImageView iconView = mView.findViewById(R.id.weather_widget_icon);
                new WeatherHelper(temperature, iconView, getContext());
                mView.findViewById(R.id.weather_widget_time).setOnLongClickListener(this);
                temperature.setOnLongClickListener(this);
                iconView.setOnLongClickListener(this);
            } else {
                mView = LayoutInflater.from(getContext()).inflate(R.layout.date_widget, this, false);
                mView.findViewById(R.id.date_text1).setOnLongClickListener(this);
                mView.findViewById(R.id.date_text2).setOnLongClickListener(this);
            }
            if (FeatureFlags.useFullWidthSearchbar(getContext())) {
                mView.setVisibility(GONE);
            }
        } else {
            mView = view;
        }
        if (i != mState) {
            if (view != null) {
                view.animate().setDuration(200).alpha(0.0f).withEndAction(new QsbBlockerViewViewRemover(this, view));
            }
            addView(mView);
            mView.setAlpha(0.0f);
            mView.animate().setDuration(200).alpha(1.0f);
        } else if (view != mView) {
            if (view != null) {
                removeView(view);
            }
            addView(mView);
        }
    }

    @Override
    public boolean onLongClick(View view) {
        if (!FeatureFlags.showWeather(getContext())) {
            return false;
        }
        switchToDate = !switchToDate;
        switching = true;
        setupView();
        return true;
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