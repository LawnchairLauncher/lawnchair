package app.lawnchair.gestures;

import android.annotation.SuppressLint;
import android.app.StatusBarManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.quickstep.TouchInteractionService;

public class SwipeDownGesture extends DirectionalGestureListener {

    private final Context mContext;

    public SwipeDownGesture(Context context) {
        super(context);
        mContext = context;
    }

    @SuppressLint("WrongConstant")
    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void onSwipeBottom() {
        if (!TouchInteractionService.isInitialized()) {
            ((StatusBarManager) mContext.getSystemService("statusbar")).expandNotificationsPanel();
        }
    }
}
