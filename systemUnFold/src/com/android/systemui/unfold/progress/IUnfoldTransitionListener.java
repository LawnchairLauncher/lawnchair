/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.android.systemui.unfold.progress;
/** Implemented by remote processes to receive unfold animation events from System UI. */
public interface IUnfoldTransitionListener extends android.os.IInterface
{
  /** Default implementation for IUnfoldTransitionListener. */
  public static class Default implements IUnfoldTransitionListener
  {
    /** Sent when unfold animation started. */
    @Override public void onTransitionStarted() throws android.os.RemoteException
    {
    }
    /** Sent when unfold animation progress changes. */
    @Override public void onTransitionProgress(float progress) throws android.os.RemoteException
    {
    }
    /** Sent when unfold animation finished. */
    @Override public void onTransitionFinished() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements IUnfoldTransitionListener
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.android.systemui.unfold.progress.IUnfoldTransitionListener interface,
     * generating a proxy if needed.
     */
    public static IUnfoldTransitionListener asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof IUnfoldTransitionListener))) {
        return ((IUnfoldTransitionListener)iin);
      }
      return new Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_onTransitionStarted:
        {
          this.onTransitionStarted();
          break;
        }
        case TRANSACTION_onTransitionProgress:
        {
          float _arg0;
          _arg0 = data.readFloat();
          this.onTransitionProgress(_arg0);
          break;
        }
        case TRANSACTION_onTransitionFinished:
        {
          this.onTransitionFinished();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements IUnfoldTransitionListener
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      /** Sent when unfold animation started. */
      @Override public void onTransitionStarted() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onTransitionStarted, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Sent when unfold animation progress changes. */
      @Override public void onTransitionProgress(float progress) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeFloat(progress);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onTransitionProgress, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
      /** Sent when unfold animation finished. */
      @Override public void onTransitionFinished() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onTransitionFinished, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onTransitionStarted = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_onTransitionProgress = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_onTransitionFinished = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
  }
  public static final String DESCRIPTOR = "com.android.systemui.unfold.progress.IUnfoldTransitionListener";
  /** Sent when unfold animation started. */
  public void onTransitionStarted() throws android.os.RemoteException;
  /** Sent when unfold animation progress changes. */
  public void onTransitionProgress(float progress) throws android.os.RemoteException;
  /** Sent when unfold animation finished. */
  public void onTransitionFinished() throws android.os.RemoteException;
}
