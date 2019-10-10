package xyz.paphonb.quickstep.compat.pie;

import android.app.ActivityManager;
import android.app.ActivityManager.TaskSnapshot;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.IRemoteCallback;
import android.os.RemoteException;
import android.view.IAppTransitionAnimationSpecsFuture;
import android.view.DisplayListCanvas;
import android.view.IRecentsAnimationController;
import android.view.RemoteAnimationAdapter;
import android.view.SurfaceControl;
import android.view.ThreadedRenderer;
import android.view.ViewRootImpl;
import android.view.WindowCallbacks;
import android.view.WindowManagerGlobal;

import xyz.paphonb.quickstep.compat.RecentsCompat;

public class RecentsCompatVP extends RecentsCompat {

    @Override
    public int getTaskId(ActivityManager.RecentTaskInfo info) {
        return info.persistentId;
    }

    @Override
    public int getTaskId(ActivityManager.RunningTaskInfo info) {
        return info.id;
    }

    @Override
    public int getDisplayId(ActivityManager.RecentTaskInfo info) {
        return 0;
    }

    @Override
    public Bitmap getThumbnail(TaskSnapshot snapshot) {
        return Bitmap.createHardwareBitmap(snapshot.getSnapshot());
    }

    @Override
    public void finishRecentsAnimation(
            IRecentsAnimationController animationController, boolean toHome,
            boolean sendUserLeaveHint) throws RemoteException {
        animationController.finish(toHome);
    }

    @Override
    public void registerRtFrameCallback(ViewRootImpl viewRoot, final FrameDrawingCallback callback) {
        viewRoot.registerRtFrameCallback(new ThreadedRenderer.FrameDrawingCallback() {
            @Override
            public void onFrameDraw(long frame) {
                callback.onFrameDraw(frame);
            }
        });
    }

    @Override
    public void setSurfaceBufferSize(SurfaceControl.Transaction transaction, SurfaceControl surfaceControl, int w, int h) {
        transaction.setSize(surfaceControl, w, h);
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
            public void onPostDraw(DisplayListCanvas canvas) {
                callbacks.onPostDraw(canvas);
            }
        };
    }

    public void overridePendingAppTransitionMultiThumbFuture(
            IAppTransitionAnimationSpecsFuture specsFuture, IRemoteCallback callback,
            boolean scaleUp, int displayId) throws RemoteException {
        WindowManagerGlobal.getWindowManagerService()
                .overridePendingAppTransitionMultiThumbFuture(specsFuture, callback, scaleUp);
    }

    public void overridePendingAppTransitionRemote(
            RemoteAnimationAdapter remoteAnimationAdapter, int displayId) throws RemoteException {
        WindowManagerGlobal.getWindowManagerService()
                .overridePendingAppTransitionRemote(remoteAnimationAdapter);
    }

    public boolean hasNavigationBar(int displayId) throws RemoteException {
        return WindowManagerGlobal.getWindowManagerService().hasNavigationBar();
    }

    public int getNavBarPosition(int displayId) throws RemoteException {
        return WindowManagerGlobal.getWindowManagerService().getNavBarPosition();
    }
}
