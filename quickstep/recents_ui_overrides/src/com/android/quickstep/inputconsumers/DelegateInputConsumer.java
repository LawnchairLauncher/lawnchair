package com.android.quickstep.inputconsumers;

import android.view.MotionEvent;

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
    public boolean useSharedSwipeState() {
        return mDelegate.useSharedSwipeState();
    }

    @Override
    public boolean allowInterceptByParent() {
        return mDelegate.allowInterceptByParent() && mState != STATE_ACTIVE;
    }

    @Override
    public void onConsumerAboutToBeSwitched() {
        mDelegate.onConsumerAboutToBeSwitched();
    }

    protected void setActive(MotionEvent ev) {
        mState = STATE_ACTIVE;
        mInputMonitor.pilferPointers();

        // Send cancel event
        MotionEvent event = MotionEvent.obtain(ev);
        event.setAction(MotionEvent.ACTION_CANCEL);
        mDelegate.onMotionEvent(event);
        event.recycle();
    }
}
