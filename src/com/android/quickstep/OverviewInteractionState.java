package com.android.quickstep;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Looper;
import android.support.annotation.Keep;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.Workspace;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class OverviewInteractionState {
    @SuppressLint("StaticFieldLeak")
    private static OverviewInteractionState INSTANCE = null;
    @SuppressWarnings("FieldCanBeLocal")
    private Context mContext;
    private Workspace.State mState;
    private boolean mIsInFocus;

    private OverviewInteractionState(Context context) {
        mContext = context;
    }

    public void onLauncherStateOrFocusChanged(Launcher launcher) {
        Workspace workspace = launcher.getWorkspace();
        mState = workspace != null ? workspace.getState() : null;
        mIsInFocus = launcher.hasWindowFocus() && AbstractFloatingView.getTopOpenView(launcher) == null;
        updateBackButtonVisible();
    }

    private void updateBackButtonVisible() {
        setBackButtonVisible(mState != Workspace.State.NORMAL || !mIsInFocus);
    }

    @Keep
    private void setBackButtonVisible(@SuppressWarnings("unused") boolean visible) {

    }

    public static OverviewInteractionState getInstance(final Context context) {
        if (INSTANCE == null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                INSTANCE = new OverviewInteractionState(context.getApplicationContext());
            } else {
                try {
                    return new MainThreadExecutor().submit(new Callable<OverviewInteractionState>() {
                        @Override
                        public OverviewInteractionState call() {
                            return OverviewInteractionState.getInstance(context);
                        }
                    }).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return INSTANCE;
    }
}
