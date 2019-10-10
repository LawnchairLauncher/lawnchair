package xyz.paphonb.quickstep.compat.ten;

import android.app.ActivityManager;
import android.app.ActivityManager.TaskSnapshot;
import android.graphics.Bitmap;
import android.graphics.HardwareRenderer;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.IRemoteCallback;
import android.os.RemoteException;
import android.view.IAppTransitionAnimationSpecsFuture;
import android.view.IRecentsAnimationController;
import android.view.RemoteAnimationAdapter;
import android.view.SurfaceControl;
import android.view.ViewRootImpl;
import android.view.WindowCallbacks;
import android.view.WindowManagerGlobal;

import androidx.annotation.RequiresApi;

import xyz.paphonb.quickstep.compat.RecentsCompat;

@RequiresApi(Build.VERSION_CODES.Q)
public class RecentsCompatVQ extends RecentsCompat {

    @Override
    public int getTaskId(ActivityManager.RecentTaskInfo info) {
        return info.taskId;
    }

    @Override
    public int getTaskId(ActivityManager.RunningTaskInfo info) {
        return info.taskId;
    }

    @Override
    public int getDisplayId(ActivityManager.RecentTaskInfo info) {
        return info.displayId;
    }

    @Override
    public Bitmap getThumbnail(TaskSnapshot snapshot) {
        return Bitmap.wrapHardwareBuffer(snapshot.getSnapshot(), snapshot.getColorSpace());
    }

    @Override
    public void finishRecentsAnimation(
            IRecentsAnimationController animationController, boolean toHome,
            boolean sendUserLeaveHint) throws RemoteException {
        animationController.finish(toHome, sendUserLeaveHint);
    }

    @Override
    public void registerRtFrameCallback(ViewRootImpl viewRoot, final FrameDrawingCallback callback) {
        viewRoot.registerRtFrameCallback(new HardwareRenderer.FrameDrawingCallback() {
            @Override
            public void onFrameDraw(long frame) {
                callback.onFrameDraw(frame);
            }
        });
    }

    @Override
    public void setSurfaceBufferSize(SurfaceControl.Transaction transaction, SurfaceControl surfaceControl, int w, int h) {
        transaction.setBufferSize(surfaceControl, w, h);
    }

    @Override
    public WindowCallbacks createWindowCallbacks(final WindowCallbacksWrapper callbacks) {
        return new WindowCallbacks() {
            @Override
            public void onWindowSizeIsChanging(Rect newBounds, boolean fullscreen, Rect systemInsets,
                                               Rect stableInsets) {
                callbacks.onWindowSizeIsChanging(newBounds, fullscreen, systemInsets, stableInsets);
            }

            @Override
            public void onWindowDragResizeStart(Rect initialBounds, boolean fullscreen,
                                                Rect systemInsets, Rect stableInsets, int resizeMode) {
                callbacks.onWindowDragResizeStart(initialBounds, fullscreen, systemInsets, stableInsets, resizeMode);
            }

            @Override
            public void onWindowDragResizeEnd() {
                callbacks.onWindowDragResizeEnd();
            }

            @Override
            public boolean onContentDrawn(int offsetX, int offsetY, int sizeX, int sizeY) {
                return callbacks.onContentDrawn(offsetX, offsetY, sizeX, sizeY);
            }

            @Override
            public void onRequestDraw(boolean reportNextDraw) {
                callbacks.onRequestDraw(reportNextDraw);
            }

            @Override
            public void onPostDraw(RecordingCanvas canvas) {
                callbacks.onPostDraw(canvas);
            }
        };
    }

    public void overridePendingAppTransitionMultiThumbFuture(
            IAppTransitionAnimationSpecsFuture specsFuture, IRemoteCallback callback,
            boolean scaleUp, int displayId) throws RemoteException {
        WindowManagerGlobal.getWindowManagerService()
                .overridePendingAppTransitionMultiThumbFuture(specsFuture, callback, scaleUp, displayId);
    }

    public void overridePendingAppTransitionRemote(
            RemoteAnimationAdapter remoteAnimationAdapter, int displayId) throws RemoteException {
        WindowManagerGlobal.getWindowManagerService()
                .overridePendingAppTransitionRemote(remoteAnimationAdapter, displayId);
    }

    public boolean hasNavigationBar(int displayId) throws RemoteException {
        return WindowManagerGlobal.getWindowManagerService().hasNavigationBar(displayId);
    }

    public int getNavBarPosition(int displayId) throws RemoteException {
        return WindowManagerGlobal.getWindowManagerService().getNavBarPosition(displayId);
    }
}
