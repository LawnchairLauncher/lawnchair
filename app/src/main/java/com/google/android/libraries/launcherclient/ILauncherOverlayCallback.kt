package com.google.android.libraries.launcherclient

import android.os.*

interface ILauncherOverlayCallback : IInterface {
    @Throws(RemoteException::class)
    fun overlayScrollChanged(progress: Float)

    @Throws(RemoteException::class)
    fun overlayStatusChanged(status: Int)

    abstract class Stub : Binder(), ILauncherOverlayCallback {
        init {
            attachInterface(this, ILauncherOverlayCallback::class.java.name)
        }

        override fun asBinder(): IBinder {
            return this
        }

        @Throws(RemoteException::class)
        public override fun onTransact(code: Int, data: Parcel, reply: Parcel, flags: Int): Boolean {
            when (code) {
                IBinder.INTERFACE_TRANSACTION -> {
                    reply.writeString(ILauncherOverlay::class.java.name)
                    return true
                }
                OVERLAY_SCROLL_CHANGED_TRANSACTION -> {
                    data.enforceInterface(ILauncherOverlayCallback::class.java.name)
                    overlayScrollChanged(data.readFloat())
                    return true
                }
                OVERLAY_STATUS_CHANGED_TRANSACTION -> {
                    data.enforceInterface(ILauncherOverlayCallback::class.java.name)
                    overlayStatusChanged(data.readInt())
                    return super.onTransact(code, data, reply, flags)
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
        }

        private class Proxy(private val mRemote: IBinder) : ILauncherOverlayCallback {

            override fun asBinder(): IBinder {
                return mRemote
            }

            @Throws(RemoteException::class)
            override fun overlayScrollChanged(progress: Float) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(ILauncherOverlayCallback::class.java.name)
                    data.writeFloat(progress)

                    mRemote.transact(OVERLAY_SCROLL_CHANGED_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }

            @Throws(RemoteException::class)
            override fun overlayStatusChanged(status: Int) {
                val data = Parcel.obtain()
                try {
                    data.writeInterfaceToken(ILauncherOverlayCallback::class.java.name)
                    data.writeInt(status)

                    mRemote.transact(OVERLAY_STATUS_CHANGED_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
                } finally {
                    data.recycle()
                }
            }
        }

        companion object {
            internal val OVERLAY_SCROLL_CHANGED_TRANSACTION = 1
            internal val OVERLAY_STATUS_CHANGED_TRANSACTION = 2

            fun asInterface(obj: IBinder?): ILauncherOverlayCallback? {
                if (obj == null) {
                    return null
                }

                val iin = obj.queryLocalInterface(ILauncherOverlayCallback::class.java.name)
                if (iin != null && iin is ILauncherOverlayCallback) {
                    return iin
                } else {
                    return Proxy(obj)
                }
            }
        }

    }
}
