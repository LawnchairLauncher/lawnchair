package com.google.android.libraries.launcherclient

import android.os.*
import android.view.WindowManager

interface ILauncherOverlay : IInterface {
    @Throws(RemoteException::class)
    fun closeOverlay(options: Int)

    @Throws(RemoteException::class)
    fun endScroll()

    @Throws(RemoteException::class)
    fun onPause()

    @Throws(RemoteException::class)
    fun onResume()

    @Throws(RemoteException::class)
    fun onScroll(progress: Float)

    @Throws(RemoteException::class)
    fun openOverlay(options: Int)

    @Throws(RemoteException::class)
    fun startScroll()

    @Throws(RemoteException::class)
    fun windowAttached(attrs: WindowManager.LayoutParams, callbacks: ILauncherOverlayCallback, options: Int)

    @Throws(RemoteException::class)
    fun windowAttached2(bundle: Bundle, iLauncherOverlayCallback: ILauncherOverlayCallback)

    @Throws(RemoteException::class)
    fun setActivityState(activityState: Int)

    @Throws(RemoteException::class)
    fun windowDetached(isChangingConfigurations: Boolean)

    abstract class Stub : Binder(), ILauncherOverlay {

        @Throws(RemoteException::class)
        public override fun onTransact(code: Int, data: Parcel, reply: Parcel, flags: Int): Boolean {
            when (code) {
                IBinder.INTERFACE_TRANSACTION -> {
                    reply.writeString(ILauncherOverlay::class.java.name)
                    return true
                }
                START_SCROLL_TRANSACTION -> {
                    data.enforceInterface(ILauncherOverlay::class.java.name)
                    startScroll()
                    return true
                }
                ON_SCROLL_TRANSACTION -> {
                    data.enforceInterface(ILauncherOverlay::class.java.name)
                    val _arg0 = data.readFloat()
                    onScroll(_arg0)
                    return true
                }
                END_SCROLL_TRANSACTION -> {
                    data.enforceInterface(ILauncherOverlay::class.java.name)
                    endScroll()
                    return true
                }
                WINDOW_ATTACHED_TRANSACTION -> {
                    data.enforceInterface(ILauncherOverlay::class.java.name)
                    var layoutParams: WindowManager.LayoutParams? = null
                    if (data.readInt() != 0) {
                        layoutParams = WindowManager.LayoutParams.CREATOR.createFromParcel(data)
                    }

                    windowAttached(
                            layoutParams!!,
                            ILauncherOverlayCallback.Stub.asInterface(data.readStrongBinder())!!,
                            data.readInt())

                    return true
                }
                WINDOW_DETACHED_TRANSACTION -> {
                    data.enforceInterface(ILauncherOverlay::class.java.name)
                    windowDetached(data.readInt() != 0)
                    return true
                }
                CLOSE_OVERLAY_TRANSACTION -> {
                    data.enforceInterface(ILauncherOverlay::class.java.name)
                    closeOverlay(data.readInt())
                    return true
                }
                ON_PAUSE_TRANSACTION -> {
                    data.enforceInterface(ILauncherOverlay::class.java.name)
                    onPause()
                    return true
                }
                ON_RESUME_TRANSACTION -> {
                    data.enforceInterface(ILauncherOverlay::class.java.name)
                    onResume()
                    return true
                }
                OPEN_OVERLAY_TRANSACTION -> {
                    data.enforceInterface(ILauncherOverlay::class.java.name)
                    openOverlay(data.readInt())
                    return true
                }
                14 -> {
                    data.enforceInterface(ILauncherOverlay::class.java.name)
                    var bundle: Bundle? = null
                    if (data.readInt() != 0) {
                        bundle = Bundle.CREATOR.createFromParcel(data)
                    }
                    windowAttached2(bundle!!, ILauncherOverlayCallback.Stub.asInterface(data.readStrongBinder())!!)
                    return true
                }
                16 -> {
                    data.enforceInterface(ILauncherOverlay::class.java.name)
                    setActivityState(data.readInt())
                    return true
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
        }

        private class Proxy(private val mRemote: IBinder) : ILauncherOverlay {

            override fun asBinder(): IBinder {
                return mRemote
            }

            @Throws(RemoteException::class)
            override fun closeOverlay(options: Int) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(ILauncherOverlay::class.java.name)
                    data.writeInt(options)

                    mRemote.transact(CLOSE_OVERLAY_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun endScroll() {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(ILauncherOverlay::class.java.name)

                    mRemote.transact(END_SCROLL_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun onPause() {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(ILauncherOverlay::class.java.name)

                    mRemote.transact(ON_PAUSE_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun onResume() {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(ILauncherOverlay::class.java.name)

                    mRemote.transact(ON_RESUME_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun onScroll(progress: Float) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(ILauncherOverlay::class.java.name)
                    data.writeFloat(progress)

                    mRemote.transact(ON_SCROLL_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun openOverlay(options: Int) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(ILauncherOverlay::class.java.name)
                    data.writeInt(options)

                    mRemote.transact(OPEN_OVERLAY_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun startScroll() {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(ILauncherOverlay::class.java.name)

                    mRemote.transact(START_SCROLL_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun windowAttached(attrs: WindowManager.LayoutParams, callbacks: ILauncherOverlayCallback, options: Int) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(ILauncherOverlay::class.java.name)
                    if (attrs != null) {
                        data.writeInt(1)
                        attrs.writeToParcel(data, 0)
                    } else {
                        data.writeInt(0)
                    }
                    data.writeStrongBinder(callbacks.asBinder())
                    data.writeInt(options)

                    data.writeInt(1)

                    mRemote.transact(WINDOW_ATTACHED_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun windowAttached2(bundle: Bundle, iLauncherOverlayCallback: ILauncherOverlayCallback) {
                var iBinder: IBinder? = null
                val obtain = Parcel.obtain()
                try {
                    obtain.writeInterfaceToken("com.google.android.libraries.launcherclient.ILauncherOverlay")
                    if (bundle == null) {
                        obtain.writeInt(0)
                    } else {
                        obtain.writeInt(1)
                        bundle.writeToParcel(obtain, 0)
                    }
                    if (iLauncherOverlayCallback != null) {
                        iBinder = iLauncherOverlayCallback.asBinder()
                    }
                    obtain.writeStrongBinder(iBinder)
                    this.mRemote.transact(14, obtain, null, IBinder.FLAG_ONEWAY)
                } finally {
                    obtain.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun setActivityState(activityState: Int) {
                val obtain = Parcel.obtain()
                try {
                    obtain.writeInterfaceToken("com.google.android.libraries.launcherclient.ILauncherOverlay")
                    obtain.writeInt(activityState)
                    this.mRemote.transact(16, obtain, null, IBinder.FLAG_ONEWAY)
                } finally {
                    obtain.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun windowDetached(isChangingConfigurations: Boolean) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(ILauncherOverlay::class.java.name)
                    data.writeInt(if (isChangingConfigurations) 1 else 0)

                    mRemote.transact(WINDOW_DETACHED_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }
        }

        companion object {
            internal val START_SCROLL_TRANSACTION = 1
            internal val ON_SCROLL_TRANSACTION = 2
            internal val END_SCROLL_TRANSACTION = 3
            internal val WINDOW_ATTACHED_TRANSACTION = 4
            internal val WINDOW_DETACHED_TRANSACTION = 5
            internal val CLOSE_OVERLAY_TRANSACTION = 6
            internal val ON_PAUSE_TRANSACTION = 7
            internal val ON_RESUME_TRANSACTION = 8
            internal val OPEN_OVERLAY_TRANSACTION = 9


            fun asInterface(obj: IBinder?): ILauncherOverlay? {
                if (obj == null) {
                    return null
                }

                val iin = obj.queryLocalInterface(ILauncherOverlay::class.java.name)
                if (iin != null && iin is ILauncherOverlay) {
                    return iin
                } else {
                    return Proxy(obj)
                }
            }
        }


    }
}