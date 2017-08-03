package ch.deletescape.lawnchair.notification;

import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

public class Interpolators {
    public static final Interpolator FAST_OUT_LINEAR_IN = new PathInterpolator(0.4f, 0.0f, 1.0f, 1.0f);
    public static final Interpolator FAST_OUT_SLOW_IN = new PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f);
    public static final Interpolator LINEAR_OUT_SLOW_IN = new PathInterpolator(0.0f, 0.0f, 0.2f, 1.0f);
    public static final Interpolator TOUCH_RESPONSE = new PathInterpolator(0.3f, 0.0f, 0.1f, 1.0f);
}