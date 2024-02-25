/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package android.window;

/**
 * Interface allowing remote processes to play transition animations. The usage flow is as follows:
 *
 * <p>
 *
 * <ol>
 *   <li>The remote tags a lifecycle event with an IRemoteTransition (via a parameter in
 *       ActivityOptions#makeRemoteAnimation) or a transition matches a filter registered via
 *       Transitions#registerRemote.
 *   <li>Shell then associates the transition for the event with the IRemoteTransition
 *   <li>Shell receives onTransitionReady and delegates the animation to the IRemoteTransition via
 *       {@link #startAnimation}.
 *   <li>Once the IRemoteTransition is done animating, it will call the finishCallback.
 *   <li>Shell/Core finish-up the transition.
 * </ul>
 *
 * {@hide}
 */
public interface IRemoteTransition extends android.os.IInterface {
    /** Default implementation for IRemoteTransition. */
    public static class Default implements android.window.IRemoteTransition {
        /**
         * Starts a transition animation. Once complete, the implementation should call
         * `finishCallback`.
         *
         * @param token An identifier for the transition that should be animated.
         */
        @Override
        public void startAnimation(
                android.os.IBinder token,
                android.window.TransitionInfo info,
                android.view.SurfaceControl.Transaction t,
                android.window.IRemoteTransitionFinishedCallback finishCallback)
                throws android.os.RemoteException {}

        /**
         * Attempts to merge a transition animation into the animation that is currently being
         * played by this remote. If merge is not possible/supported, this should be a no-op. If it
         * *is* merged, the implementation should call `finishCallback` immediately.
         *
         * @param transition An identifier for the transition that wants to be merged.
         * @param mergeTarget The transition that is currently being animated by this remote. If it
         *     can be merged, call `finishCallback`; otherwise, do nothing.
         */
        @Override
        public void mergeAnimation(
                android.os.IBinder transition,
                android.window.TransitionInfo info,
                android.view.SurfaceControl.Transaction t,
                android.os.IBinder mergeTarget,
                android.window.IRemoteTransitionFinishedCallback finishCallback)
                throws android.os.RemoteException {}

        @Override
        public android.os.IBinder asBinder() {
            return null;
        }
    }

    /** Local-side IPC implementation stub class. */
    public abstract static class Stub extends android.os.Binder
            implements android.window.IRemoteTransition {
        /** Construct the stub at attach it to the interface. */
        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        /**
         * Cast an IBinder object into an android.window.IRemoteTransition interface, generating a
         * proxy if needed.
         */
        public static android.window.IRemoteTransition asInterface(android.os.IBinder obj) {
            if ((obj == null)) {
                return null;
            }
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin != null) && (iin instanceof android.window.IRemoteTransition))) {
                return ((android.window.IRemoteTransition) iin);
            }
            return new android.window.IRemoteTransition.Stub.Proxy(obj);
        }

        @Override
        public android.os.IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(
                int code, android.os.Parcel data, android.os.Parcel reply, int flags)
                throws android.os.RemoteException {
            java.lang.String descriptor = DESCRIPTOR;
            if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION
                    && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
                data.enforceInterface(descriptor);
            }
            switch (code) {
                case INTERFACE_TRANSACTION:
                    {
                        reply.writeString(descriptor);
                        return true;
                    }
            }
            switch (code) {
                case TRANSACTION_startAnimation:
                    {
                        android.os.IBinder _arg0;
                        _arg0 = data.readStrongBinder();
                        android.window.TransitionInfo _arg1;
                        _arg1 =
                                _Parcel.readTypedObject(
                                        data, android.window.TransitionInfo.CREATOR);
                        android.view.SurfaceControl.Transaction _arg2;
                        _arg2 =
                                _Parcel.readTypedObject(
                                        data, android.view.SurfaceControl.Transaction.CREATOR);
                        android.window.IRemoteTransitionFinishedCallback _arg3;
                        _arg3 =
                                android.window.IRemoteTransitionFinishedCallback.Stub.asInterface(
                                        data.readStrongBinder());
                        this.startAnimation(_arg0, _arg1, _arg2, _arg3);
                        break;
                    }
                case TRANSACTION_mergeAnimation:
                    {
                        android.os.IBinder _arg0;
                        _arg0 = data.readStrongBinder();
                        android.window.TransitionInfo _arg1;
                        _arg1 =
                                _Parcel.readTypedObject(
                                        data, android.window.TransitionInfo.CREATOR);
                        android.view.SurfaceControl.Transaction _arg2;
                        _arg2 =
                                _Parcel.readTypedObject(
                                        data, android.view.SurfaceControl.Transaction.CREATOR);
                        android.os.IBinder _arg3;
                        _arg3 = data.readStrongBinder();
                        android.window.IRemoteTransitionFinishedCallback _arg4;
                        _arg4 =
                                android.window.IRemoteTransitionFinishedCallback.Stub.asInterface(
                                        data.readStrongBinder());
                        this.mergeAnimation(_arg0, _arg1, _arg2, _arg3, _arg4);
                        break;
                    }
                default:
                    {
                        return super.onTransact(code, data, reply, flags);
                    }
            }
            return true;
        }

        private static class Proxy implements android.window.IRemoteTransition {
            private android.os.IBinder mRemote;

            Proxy(android.os.IBinder remote) {
                mRemote = remote;
            }

            @Override
            public android.os.IBinder asBinder() {
                return mRemote;
            }

            public java.lang.String getInterfaceDescriptor() {
                return DESCRIPTOR;
            }

            /**
             * Starts a transition animation. Once complete, the implementation should call
             * `finishCallback`.
             *
             * @param token An identifier for the transition that should be animated.
             */
            @Override
            public void startAnimation(
                    android.os.IBinder token,
                    android.window.TransitionInfo info,
                    android.view.SurfaceControl.Transaction t,
                    android.window.IRemoteTransitionFinishedCallback finishCallback)
                    throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder(token);
                    _Parcel.writeTypedObject(_data, info, 0);
                    _Parcel.writeTypedObject(_data, t, 0);
                    _data.writeStrongInterface(finishCallback);
                    boolean _status =
                            mRemote.transact(
                                    Stub.TRANSACTION_startAnimation,
                                    _data,
                                    null,
                                    android.os.IBinder.FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }

            /**
             * Attempts to merge a transition animation into the animation that is currently being
             * played by this remote. If merge is not possible/supported, this should be a no-op. If
             * it *is* merged, the implementation should call `finishCallback` immediately.
             *
             * @param transition An identifier for the transition that wants to be merged.
             * @param mergeTarget The transition that is currently being animated by this remote. If
             *     it can be merged, call `finishCallback`; otherwise, do nothing.
             */
            @Override
            public void mergeAnimation(
                    android.os.IBinder transition,
                    android.window.TransitionInfo info,
                    android.view.SurfaceControl.Transaction t,
                    android.os.IBinder mergeTarget,
                    android.window.IRemoteTransitionFinishedCallback finishCallback)
                    throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder(transition);
                    _Parcel.writeTypedObject(_data, info, 0);
                    _Parcel.writeTypedObject(_data, t, 0);
                    _data.writeStrongBinder(mergeTarget);
                    _data.writeStrongInterface(finishCallback);
                    boolean _status =
                            mRemote.transact(
                                    Stub.TRANSACTION_mergeAnimation,
                                    _data,
                                    null,
                                    android.os.IBinder.FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }
        }

        static final int TRANSACTION_startAnimation =
                (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
        static final int TRANSACTION_mergeAnimation =
                (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    }

    public static final java.lang.String DESCRIPTOR = "android.window.IRemoteTransition";

    /**
     * Starts a transition animation. Once complete, the implementation should call
     * `finishCallback`.
     *
     * @param token An identifier for the transition that should be animated.
     */
    public void startAnimation(
            android.os.IBinder token,
            android.window.TransitionInfo info,
            android.view.SurfaceControl.Transaction t,
            android.window.IRemoteTransitionFinishedCallback finishCallback)
            throws android.os.RemoteException;

    /**
     * Attempts to merge a transition animation into the animation that is currently being played by
     * this remote. If merge is not possible/supported, this should be a no-op. If it *is* merged,
     * the implementation should call `finishCallback` immediately.
     *
     * @param transition An identifier for the transition that wants to be merged.
     * @param mergeTarget The transition that is currently being animated by this remote. If it can
     *     be merged, call `finishCallback`; otherwise, do nothing.
     */
    public void mergeAnimation(
            android.os.IBinder transition,
            android.window.TransitionInfo info,
            android.view.SurfaceControl.Transaction t,
            android.os.IBinder mergeTarget,
            android.window.IRemoteTransitionFinishedCallback finishCallback)
            throws android.os.RemoteException;

    /** @hide */
    static class _Parcel {
        private static <T> T readTypedObject(
                android.os.Parcel parcel, android.os.Parcelable.Creator<T> c) {
            if (parcel.readInt() != 0) {
                return c.createFromParcel(parcel);
            } else {
                return null;
            }
        }

        private static <T extends android.os.Parcelable> void writeTypedObject(
                android.os.Parcel parcel, T value, int parcelableFlags) {
            if (value != null) {
                parcel.writeInt(1);
                value.writeToParcel(parcel, parcelableFlags);
            } else {
                parcel.writeInt(0);
            }
        }
    }
}
