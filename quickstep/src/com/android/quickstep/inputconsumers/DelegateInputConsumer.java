package com.android.quickstep.inputconsumers;

import android.view.MotionEvent;

import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.systemui.shared.system.InputMonitorCompat;

public abstract class DelegateInputConsumer implements InputConsumer {

    protected static final int STATE_INACTIVE = 0;
    protected static final int STATE_ACTIVE = 1;
    protected static final int STATE_DELEGATE_ACTIVE = 2;

    protected final InputConsumer mDelegate;
    protected final InputMonitorCompat mInputMonitor;

    protected int mState;

    public DelegateInputConsumer(InputConsumer delegate, InputMonitorCompat inputMonitor) {
        mDelegate = delegate;
        mInputMonitor = inputMonitor;
        mState = STATE_INACTIVE;
    }

    @Override
    public InputConsumer getActiveConsumerInHierarchy() {
        if (mState == STATE_ACTIVE) {
            return this;
        }
        return mDelegate.getActiveConsumerInHierarchy();
    }

    @Override
    public boolean allowInterceptByParent() {
        return mDelegate.allowInterceptByParent() && mState != STATE_ACTIVE;
    }

    @Override
    public void onConsumerAboutToBeSwitched() {
        mDelegate.onConsumerAboutToBeSwitched();
    }

    /**
     * Returns the name of this DelegateInputConsumer.
     */
    protected abstract String getDelegatorName();

    @Override
    public <T extends InputConsumer> T getInputConsumerOfClass(Class<T> c) {
        if (getClass().equals(c)) {
            return c.cast(this);
        }
        return mDelegate.getInputConsumerOfClass(c);
    }

    protected void setActive(MotionEvent ev) {
        ActiveGestureLog.INSTANCE.addLog(new ActiveGestureLog.CompoundString(getDelegatorName())
                .append(" became active"));

        mState = STATE_ACTIVE;
        TestLogging.recordEvent(TestProtocol.SEQUENCE_PILFER, "pilferPointers");
        mInputMonitor.pilferPointers();

        // Send cancel event
        MotionEvent event = MotionEvent.obtain(ev);
        event.setAction(MotionEvent.ACTION_CANCEL);
        mDelegate.onMotionEvent(event);
        event.recycle();
    }
}
