package ch.deletescape.lawnchair;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.kwabenaberko.openweathermaplib.implementation.OpenWeatherMapHelper;
import com.kwabenaberko.openweathermaplib.models.CurrentWeather;

import ch.deletescape.lawnchair.config.FeatureFlags;
import ch.deletescape.lawnchair.pixelify.OnWeatherInfoListener;
import ch.deletescape.lawnchair.pixelify.ShadowHostView;
import ch.deletescape.lawnchair.pixelify.WeatherInfo;
import ch.deletescape.lawnchair.pixelify.WeatherThing;

public class QsbBlockerView extends FrameLayout implements Workspace.OnStateChangeListener, OnWeatherInfoListener {
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
            WeatherInfo gsa = WeatherThing.getInstance(getContext()).getWeatherInfoAndAddListener(this);
            if (gsa != null) {
                onWeatherInfo(gsa);
            }
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
        if (!FeatureFlags.useFullWidthSearchbar(getContext())) {
            WeatherThing.getInstance(getContext()).removeListener(this);
        }
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

    @Override
    public void onWeatherInfo(WeatherInfo weatherInfo) {
        if (!FeatureFlags.showPixelBar(getContext())) {
            removeAllViews();
            return;
        }
        View view = mView;
        int i = mState;
        mView = ShadowHostView.bG(weatherInfo, this, mView);
        mState = 2;
        if (mView == null) {
            View inflate;
            mState = 1;
            if (view == null || i != 1) {
                if (FeatureFlags.planes(getContext())) {
                    inflate = LayoutInflater.from(getContext()).inflate(R.layout.plane_widget, this, false);
                } else if (FeatureFlags.weatherDebug(getContext())) {
                    inflate = LayoutInflater.from(getContext()).inflate(R.layout.weather_widget, this, false);
                    OpenWeatherMapHelper helper = new OpenWeatherMapHelper();
                    helper.setAppId(BuildConfig.OPENWEATHERMAP_KEY);
                    SharedPreferences prefs = Utilities.getPrefs(getContext());
                    String units = prefs.getString("pref_weatherDebug_units", "metric");
                    final boolean isImperial = units.equals("imperial");
                    helper.setUnits(units);
                    String city = prefs.getString("pref_weatherDebug_city", "Lucerne, CH");
                    final TextView temperature = inflate.findViewById(R.id.weather_widget_temperature);
                    temperature.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse("dynact://velour/weather/ProxyActivity"));
                            intent.setComponent(new ComponentName("com.google.android.googlequicksearchbox",
                                    "com.google.android.apps.gsa.velour.DynamicActivityTrampoline"));
                            getContext().startActivity(intent);
                        }
                    });
                    OpenWeatherMapHelper.CurrentWeatherCallback callback = new OpenWeatherMapHelper.CurrentWeatherCallback() {
                        @Override
                        public void onSuccess(CurrentWeather currentWeather) {
                            temperature.setText(currentWeather.getMain().getTemp() + (isImperial ? "°F" : "°C"));
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            temperature.setText("ERROR°C");
                        }
                    };
                    helper.getCurrentWeatherByCityName(city, callback);
                } else {
                    inflate = LayoutInflater.from(getContext()).inflate(R.layout.date_widget, this, false);
                }
                if (FeatureFlags.useFullWidthSearchbar(getContext())) {
                    inflate.setVisibility(GONE);
                }
            } else {
                inflate = view;
            }
            mView = inflate;
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