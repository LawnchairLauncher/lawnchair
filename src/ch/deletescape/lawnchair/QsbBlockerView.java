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

import ch.deletescape.lawnchair.pixelify.C0277b;
import ch.deletescape.lawnchair.pixelify.C0278a;
import ch.deletescape.lawnchair.pixelify.C0280e;
import ch.deletescape.lawnchair.pixelify.ShadowHostView;

public class QsbBlockerView extends FrameLayout implements Workspace.OnStateChangeListener, C0277b {
    public static final Property bU = new C0292q(Integer.TYPE, "bgAlpha");
    private final Paint mBgPaint = new Paint(1);
    private int mState = 0;
    private View mView;

    public QsbBlockerView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mBgPaint.setColor(-1);
        this.mBgPaint.setAlpha(0);
        //View.inflate(context, R.layout.qsb_wide_experiment, this);
    }


    @Override
    public void setPadding(int i, int i2, int i3, int i4) {
        super.setPadding(0, 0, 0, 0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
//        if (!C0279d.bv(getContext())) {
        Workspace workspace = Launcher.getLauncher(getContext()).getWorkspace();
        workspace.setOnStateChangeListener(this);
        prepareStateChange(workspace.getState(), null);
        C0280e aR = C0278a.aS(getContext()).aR(this);
        if (aR != null) {
            bb(aR);
        }
//        }
    }

    @Override
    protected void onMeasure(int i, int i2) {
        if (this.mView != null && this.mState == 2) {
            DeviceProfile deviceProfile = Launcher.getLauncher(getContext()).getDeviceProfile();
            LayoutParams layoutParams = (LayoutParams) this.mView.getLayoutParams();
            int size = ((MeasureSpec.getSize(i) / deviceProfile.inv.numColumns) - deviceProfile.iconSizePx) / 2;
            layoutParams.rightMargin = size;
            layoutParams.leftMargin = size;
        }
        super.onMeasure(i, i2);
    }

    @Override
    protected void onDetachedFromWindow() {
//        if (!C0279d.bv(getContext())) {
        C0278a.aS(getContext()).aY(this);
//        }
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
            bU.set(this, Integer.valueOf(i));
            return;
        }
        animatorSet.play(ObjectAnimator.ofInt(this, bU, new int[]{i}));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPaint(this.mBgPaint);
    }

    @Override
    public void bb(C0280e c0280e) {
        View view = this.mView;
        int i = this.mState;
        this.mView = ShadowHostView.bG(c0280e, this, this.mView);
        this.mState = 2;
        if (this.mView == null) {
            View inflate;
            this.mState = 1;
            if (view == null || i != 1) {
                inflate = LayoutInflater.from(getContext()).inflate(R.layout.date_widget, this, false);
            } else {
                inflate = view;
            }
            this.mView = inflate;
        }
        if (i != this.mState) {
            if (view != null) {
                view.animate().setDuration(200).alpha(0.0f).withEndAction(new C0293r(this, view));
            }
            addView(this.mView);
            this.mView.setAlpha(0.0f);
            this.mView.animate().setDuration(200).alpha(1.0f);
        } else if (view != this.mView) {
            if (view != null) {
                removeView(view);
            }
            addView(this.mView);
        }
    }

    final class C0293r implements Runnable {
        final /* synthetic */ QsbBlockerView cv;
        final /* synthetic */ View cw;

        C0293r(QsbBlockerView qsbBlockerView, View view) {
            this.cv = qsbBlockerView;
            this.cw = view;
        }

        @Override
        public void run() {
            this.cv.removeView(this.cw);
        }
    }

    static final class C0292q extends Property {
        C0292q(Class cls, String str) {
            super(cls, str);
        }

        @Override
        public /* bridge */ /* synthetic */ void set(Object obj, Object obj2) {
            bX((QsbBlockerView) obj, (Integer) obj2);
        }

        public void bX(QsbBlockerView qsbBlockerView, Integer num) {
            boolean z = false;
            qsbBlockerView.mBgPaint.setAlpha(num.intValue());
            if (num.intValue() == 0) {
                z = true;
            }
            qsbBlockerView.setWillNotDraw(z);
            qsbBlockerView.invalidate();
        }

        @Override
        public /* bridge */ /* synthetic */ Object get(Object obj) {
            return bW((QsbBlockerView) obj);
        }

        public Integer bW(QsbBlockerView qsbBlockerView) {
            return Integer.valueOf(qsbBlockerView.mBgPaint.getAlpha());
        }
    }
}