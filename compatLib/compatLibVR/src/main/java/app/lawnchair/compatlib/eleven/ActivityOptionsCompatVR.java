package app.lawnchair.compatlib.eleven;

import android.app.ActivityOptions;
import android.content.Context;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.lawnchair.compatlib.ten.ActivityOptionsCompatVQ;

public class ActivityOptionsCompatVR extends ActivityOptionsCompatVQ {

    @Override
    public ActivityOptions makeCustomAnimation(
            Context context,
            int enterResId,
            int exitResId,
            @NonNull final Handler callbackHandler,
            @Nullable final Runnable startedListener,
            @Nullable final Runnable finishedListener) {
        return ActivityOptions.makeCustomAnimation(
                context,
                enterResId,
                exitResId,
                callbackHandler,
                new ActivityOptions.OnAnimationStartedListener() {
                    @Override
                    public void onAnimationStarted() {
                        if (startedListener != null) {
                            startedListener.run();
                        }
                    }
                },
                new ActivityOptions.OnAnimationFinishedListener() {
                    @Override
                    public void onAnimationFinished() {
                        if (finishedListener != null) {
                            finishedListener.run();
                        }
                    }
                });
    }
}
