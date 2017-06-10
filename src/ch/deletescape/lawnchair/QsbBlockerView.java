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

import ch.deletescape.lawnchair.config.FeatureFlags;
import ch.deletescape.lawnchair.pixelify.WeatherThing;
import ch.deletescape.lawnchair.pixelify.GoogleSearchApp;
import ch.deletescape.lawnchair.pixelify.OnGsaListener;
import ch.deletescape.lawnchair.pixelify.ShadowHostView;

public class QsbBlockerView extends FrameLayout implements Workspace.OnStateChangeListener, OnGsaListener {
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
            GoogleSearchApp gsa = WeatherThing.getInstance(getContext()).getGoogleSearchAppAndAddListener(this);
            if (gsa != null) {
                onGsa(gsa);
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
            WeatherThing.getInstance(getContext()).aY(this);
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
    public void onGsa(GoogleSearchApp gsa) {
        View view = mView;
        int i = mState;
        mView = ShadowHostView.bG(gsa, this, mView);
        mState = 2;
        if (mView == null) {
            View inflate;
            mState = 1;
            if (view == null || i != 1) {
                inflate = LayoutInflater.from(getContext()).inflate(R.layout.date_widget, this, false);
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