package xyz.paphonb.quickstep.compat;

import android.app.ActivityManager;
import android.app.ActivityManager.TaskSnapshot;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.IRemoteCallback;
import android.os.RemoteException;
import android.view.IAppTransitionAnimationSpecsFuture;
import android.view.IRecentsAnimationController;
import android.view.RemoteAnimationAdapter;
import android.view.SurfaceControl;
import android.view.ViewRootImpl;
import android.view.WindowCallbacks;

public abstract class RecentsCompat {

    public abstract int getTaskId(ActivityManager.RecentTaskInfo info);

    public abstract int getTaskId(ActivityManager.RunningTaskInfo info);

    public abstract int getDisplayId(ActivityManager.RecentTaskInfo info);

    public abstract Bitmap getThumbnail(TaskSnapshot snapshot);

    public abstract void finishRecentsAnimation(
            IRecentsAnimationController animationController, boolean toHome,
            boolean sendUserLeaveHint) throws RemoteException;

    public abstract void registerRtFrameCallback(ViewRootImpl viewRoot, FrameDrawingCallback callback);

    public abstract void setSurfaceBufferSize(SurfaceControl.Transaction transaction, SurfaceControl surfaceControl, int w, int h);

    public abstract WindowCallbacks createWindowCallbacks(WindowCallbacksWrapper callbacks);

    public abstract void overridePendingAppTransitionMultiThumbFuture(
            IAppTransitionAnimationSpecsFuture specsFuture, IRemoteCallback callback,
            boolean scaleUp, int displayId) throws RemoteException;

    public abstract void overridePendingAppTransitionRemote(
            RemoteAnimationAdapter remoteAnimationAdapter, int displayId) throws RemoteException;

    public abstract boolean hasNavigationBar(int displayId) throws RemoteException;

    public abstract int getNavBarPosition(int displayId) throws RemoteException;

    public interface FrameDrawingCallback {

        void onFrameDraw(long frame);
    }

    public interface WindowCallbacksWrapper {

        void onWindowSizeIsChanging(Rect newBounds, boolean fullscreen, Rect systemInsets,
                                    Rect stableInsets);

        void onWindowDragResizeStart(Rect initialBounds, boolean fullscreen,
                                     Rect systemInsets, Rect stableInsets, int resizeMode);

        void onWindowDragResizeEnd();

        boolean onContentDrawn(int offsetX, int offsetY, int sizeX, int sizeY);

        void onRequestDraw(boolean reportNextDraw);

        void onPostDraw(Canvas canvas);
    }
}
