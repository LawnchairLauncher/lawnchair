package com.google.android.libraries.launcherclient;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.WindowManager;

public interface ILauncherOverlay extends IInterface {
    void closeOverlay(int options) throws RemoteException;

    void endScroll() throws RemoteException;

    void onPause() throws RemoteException;

    void onResume() throws RemoteException;

    void onScroll(float progress) throws RemoteException;

    void openOverlay(int options) throws RemoteException;

    void startScroll() throws RemoteException;

    void windowAttached(WindowManager.LayoutParams attrs, ILauncherOverlayCallback callbacks, int options) throws RemoteException;

    void windowAttached2(Bundle bundle, ILauncherOverlayCallback iLauncherOverlayCallback) throws RemoteException;

    void setActivityState(int activityState) throws RemoteException;

    void windowDetached(boolean isChangingConfigurations) throws RemoteException;

    abstract class Stub extends Binder implements ILauncherOverlay {
        static final int START_SCROLL_TRANSACTION = 1;
        static final int ON_SCROLL_TRANSACTION = 2;
        static final int END_SCROLL_TRANSACTION = 3;
        static final int WINDOW_ATTACHED_TRANSACTION = 4;
        static final int WINDOW_DETACHED_TRANSACTION = 5;
        static final int CLOSE_OVERLAY_TRANSACTION = 6;
        static final int ON_PAUSE_TRANSACTION = 7;
        static final int ON_RESUME_TRANSACTION = 8;
        static final int OPEN_OVERLAY_TRANSACTION = 9;


        public static ILauncherOverlay asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }

            IInterface iin = obj.queryLocalInterface(ILauncherOverlay.class.getName());
            if (iin != null && iin instanceof ILauncherOverlay) {
                return (ILauncherOverlay) iin;
            } else {
                return new Proxy(obj);
            }
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION:
                    reply.writeString(ILauncherOverlay.class.getName());
                    return true;
                case START_SCROLL_TRANSACTION:
                    data.enforceInterface(ILauncherOverlay.class.getName());
                    startScroll();
                    return true;
                case ON_SCROLL_TRANSACTION:
                    data.enforceInterface(ILauncherOverlay.class.getName());
                    float _arg0 = data.readFloat();
                    onScroll(_arg0);
                    return true;
                case END_SCROLL_TRANSACTION:
                    data.enforceInterface(ILauncherOverlay.class.getName());
                    endScroll();
                    return true;
                case WINDOW_ATTACHED_TRANSACTION:
                    data.enforceInterface(ILauncherOverlay.class.getName());
                    WindowManager.LayoutParams layoutParams = null;
                    if (data.readInt() != 0) {
                        layoutParams = WindowManager.LayoutParams.CREATOR.createFromParcel(data);
                    }

                    windowAttached(
                            layoutParams,
                            ILauncherOverlayCallback.Stub.asInterface(data.readStrongBinder()),
                            data.readInt()
                    );

                    return true;
                case WINDOW_DETACHED_TRANSACTION:
                    data.enforceInterface(ILauncherOverlay.class.getName());
                    windowDetached(data.readInt() != 0);
                    return true;
                case CLOSE_OVERLAY_TRANSACTION:
                    data.enforceInterface(ILauncherOverlay.class.getName());
                    closeOverlay(data.readInt());
                    return true;
                case ON_PAUSE_TRANSACTION:
                    data.enforceInterface(ILauncherOverlay.class.getName());
                    onPause();
                    return true;
                case ON_RESUME_TRANSACTION:
                    data.enforceInterface(ILauncherOverlay.class.getName());
                    onResume();
                    return true;
                case OPEN_OVERLAY_TRANSACTION:
                    data.enforceInterface(ILauncherOverlay.class.getName());
                    openOverlay(data.readInt());
                    return true;
                case 14:
                    data.enforceInterface(ILauncherOverlay.class.getName());
                    Bundle bundle = null;
                    if (data.readInt() != 0) {
                        bundle = Bundle.CREATOR.createFromParcel(data);
                    }
                    windowAttached2(bundle, com.google.android.libraries.launcherclient.ILauncherOverlayCallback.Stub.asInterface(data.readStrongBinder()));
                    return true;
                case 16:
                    data.enforceInterface(ILauncherOverlay.class.getName());
                    setActivityState(data.readInt());
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements ILauncherOverlay {
            private IBinder mRemote;

            public Proxy(IBinder remote) {
                mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return mRemote;
            }

            @Override
            public void closeOverlay(int options) throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(ILauncherOverlay.class.getName());
                    data.writeInt(options);

                    mRemote.transact(CLOSE_OVERLAY_TRANSACTION, data, null, FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }

            @Override
            public void endScroll() throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(ILauncherOverlay.class.getName());

                    mRemote.transact(END_SCROLL_TRANSACTION, data, null, FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }

            @Override
            public void onPause() throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(ILauncherOverlay.class.getName());

                    mRemote.transact(ON_PAUSE_TRANSACTION, data, null, FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }

            @Override
            public void onResume() throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(ILauncherOverlay.class.getName());

                    mRemote.transact(ON_RESUME_TRANSACTION, data, null, FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }

            @Override
            public void onScroll(float progress) throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(ILauncherOverlay.class.getName());
                    data.writeFloat(progress);

                    mRemote.transact(ON_SCROLL_TRANSACTION, data, null, FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }

            @Override
            public void openOverlay(int options) throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(ILauncherOverlay.class.getName());
                    data.writeInt(options);

                    mRemote.transact(OPEN_OVERLAY_TRANSACTION, data, null, FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }

            @Override
            public void startScroll() throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(ILauncherOverlay.class.getName());

                    mRemote.transact(START_SCROLL_TRANSACTION, data, null, FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }

            @Override
            public void windowAttached(WindowManager.LayoutParams attrs, ILauncherOverlayCallback callbacks, int options) throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(ILauncherOverlay.class.getName());
                    if (attrs != null) {
                        data.writeInt(1);
                        attrs.writeToParcel(data, 0);
                    } else {
                        data.writeInt(0);
                    }
                    data.writeStrongBinder(callbacks.asBinder());
                    data.writeInt(options);

                    data.writeInt(1);

                    mRemote.transact(WINDOW_ATTACHED_TRANSACTION, data, null, FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }

            @Override
            public void windowAttached2(Bundle bundle, ILauncherOverlayCallback iLauncherOverlayCallback) throws RemoteException {
                IBinder iBinder = null;
                Parcel obtain = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.google.android.libraries.launcherclient.ILauncherOverlay");
                    if (bundle == null) {
                        obtain.writeInt(0);
                    } else {
                        obtain.writeInt(1);
                        bundle.writeToParcel(obtain, 0);
                    }
                    if (iLauncherOverlayCallback != null) {
                        iBinder = iLauncherOverlayCallback.asBinder();
                    }
                    obtain.writeStrongBinder(iBinder);
                    this.mRemote.transact(14, obtain, null, FLAG_ONEWAY);
                } finally {
                    obtain.recycle();
                }
            }

            @Override
            public void setActivityState(int activityState) throws RemoteException {
                Parcel obtain = Parcel.obtain();
                try {
                    obtain.writeInterfaceToken("com.google.android.libraries.launcherclient.ILauncherOverlay");
                    obtain.writeInt(activityState);
                    this.mRemote.transact(16, obtain, null, FLAG_ONEWAY);
                } finally {
                    obtain.recycle();
                }
            }

            @Override
            public void windowDetached(boolean isChangingConfigurations) throws RemoteException {
                Parcel data = Parcel.obtain();
                try {
                    data.writeInterfaceToken(ILauncherOverlay.class.getName());
                    data.writeInt(isChangingConfigurations ? 1 : 0);

                    mRemote.transact(WINDOW_DETACHED_TRANSACTION, data, null, FLAG_ONEWAY);
                } finally {
                    data.recycle();
                }
            }
        }


    }
}