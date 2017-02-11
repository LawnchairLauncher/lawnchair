package com.google.android.libraries.launcherclient;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.view.WindowManager;

public interface ILauncherOverlay extends IInterface {
    void closeOverlay(int options) throws RemoteException;

    void endScroll() throws RemoteException;

    String getVoiceSearchLanguage() throws RemoteException;

    boolean isVoiceDetectionRunning() throws RemoteException;

    void onPause() throws RemoteException;

    void onResume() throws RemoteException;

    void onScroll(float progress) throws RemoteException;

    void openOverlay(int options) throws RemoteException;

    void requestVoiceDetection(boolean start) throws RemoteException;

    void startScroll() throws RemoteException;

    void windowAttached(WindowManager.LayoutParams attrs, ILauncherOverlayCallback callbacks, int options) throws RemoteException;

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
        static final int REQUEST_VOICE_DETECTION_TRANSACTION = 10;
        static final int GET_VOICE_SEARCH_LANGUAGE_TRANSACTION = 11;
        static final int IS_VOICE_DETECTION_RUNNING_TRANSACTION = 12;


        public static ILauncherOverlay asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }

            IInterface iin = obj.queryLocalInterface(ILauncherOverlay.class.getName());
            if (iin != null && iin instanceof ILauncherOverlay) {
                return (ILauncherOverlay) iin;
            }
            else {
                return new Proxy(obj);
            }
        }

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
                case REQUEST_VOICE_DETECTION_TRANSACTION:
                    data.enforceInterface(ILauncherOverlay.class.getName());
                    requestVoiceDetection(data.readInt() != 0);
                    return true;
                case GET_VOICE_SEARCH_LANGUAGE_TRANSACTION:
                    data.enforceInterface(ILauncherOverlay.class.getName());
                    String language = getVoiceSearchLanguage();
                    reply.writeNoException();
                    reply.writeString(language);
                    return true;
                case IS_VOICE_DETECTION_RUNNING_TRANSACTION:
                    data.enforceInterface(ILauncherOverlay.class.getName());
                    boolean running = isVoiceDetectionRunning();
                    reply.writeNoException();
                    reply.writeInt(running ? 1 : 0);
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

            public IBinder asBinder() {
                return mRemote;
            }

            public void closeOverlay(int options) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ILauncherOverlay.class.getName());
                    _data.writeInt(options);

                    mRemote.transact(CLOSE_OVERLAY_TRANSACTION, _data, null, FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }

            public void endScroll() throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ILauncherOverlay.class.getName());

                    mRemote.transact(END_SCROLL_TRANSACTION, _data, null, FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }

            public String getVoiceSearchLanguage() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ILauncherOverlay.class.getName());

                    mRemote.transact(GET_VOICE_SEARCH_LANGUAGE_TRANSACTION, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readString();
                } finally {
                    _data.recycle();
                    _reply.recycle();
                }
            }

            @Override
            public boolean isVoiceDetectionRunning() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ILauncherOverlay.class.getName());

                    mRemote.transact(IS_VOICE_DETECTION_RUNNING_TRANSACTION, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt() != 0;
                } finally {
                    _data.recycle();
                    _reply.recycle();
                }
            }

            @Override
            public void onPause() throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ILauncherOverlay.class.getName());

                    mRemote.transact(ON_PAUSE_TRANSACTION, _data, null, FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }

            @Override
            public void onResume() throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ILauncherOverlay.class.getName());

                    mRemote.transact(ON_RESUME_TRANSACTION, _data, null, FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }

            @Override
            public void onScroll(float progress) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ILauncherOverlay.class.getName());
                    _data.writeFloat(progress);

                    mRemote.transact(ON_SCROLL_TRANSACTION, _data, null, FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }

            @Override
            public void openOverlay(int options) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ILauncherOverlay.class.getName());
                    _data.writeInt(options);

                    mRemote.transact(OPEN_OVERLAY_TRANSACTION, _data, null, FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }

            @Override
            public void requestVoiceDetection(boolean start) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ILauncherOverlay.class.getName());
                    _data.writeInt(start ? 1 : 0);

                    mRemote.transact(REQUEST_VOICE_DETECTION_TRANSACTION, _data, null, FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }

            @Override
            public void startScroll() throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ILauncherOverlay.class.getName());

                    mRemote.transact(START_SCROLL_TRANSACTION, _data, null, FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }

            @Override
            public void windowAttached(WindowManager.LayoutParams attrs, ILauncherOverlayCallback callbacks, int options) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ILauncherOverlay.class.getName());
                    if (attrs != null) {
                        _data.writeInt(1);
                        attrs.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    _data.writeStrongBinder(callbacks.asBinder());
                    _data.writeInt(options);

                    _data.writeInt(1);

                    mRemote.transact(WINDOW_ATTACHED_TRANSACTION, _data, null, FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }

            @Override
            public void windowDetached(boolean isChangingConfigurations) throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(ILauncherOverlay.class.getName());
                    _data.writeInt(isChangingConfigurations ? 1 : 0);

                    mRemote.transact(WINDOW_DETACHED_TRANSACTION, _data, null, FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }
        }


    }
}