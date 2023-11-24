/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.android.systemui.unfold.progress;
/** Interface exposed by System UI to allow remote process to register for unfold animation events. */
public interface IUnfoldAnimation extends android.os.IInterface
{
  /** Default implementation for IUnfoldAnimation. */
  public static class Default implements IUnfoldAnimation
  {
    /**
     * Sets a listener for the animation.
     *
     * Only one listener is supported. If there are multiple, the earlier one will be overridden.
     */
    @Override public void setListener(IUnfoldTransitionListener listener) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements IUnfoldAnimation
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.android.systemui.unfold.progress.IUnfoldAnimation interface,
     * generating a proxy if needed.
     */
    public static IUnfoldAnimation asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof IUnfoldAnimation))) {
        return ((IUnfoldAnimation)iin);
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
        case TRANSACTION_setListener:
        {
          IUnfoldTransitionListener _arg0;
          _arg0 = IUnfoldTransitionListener.Stub.asInterface(data.readStrongBinder());
          this.setListener(_arg0);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements IUnfoldAnimation
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
      /**
       * Sets a listener for the animation.
       *
       * Only one listener is supported. If there are multiple, the earlier one will be overridden.
       */
      @Override public void setListener(IUnfoldTransitionListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_setListener, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_setListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  public static final String DESCRIPTOR = "com.android.systemui.unfold.progress.IUnfoldAnimation";
  /**
   * Sets a listener for the animation.
   *
   * Only one listener is supported. If there are multiple, the earlier one will be overridden.
   */
  public void setListener(IUnfoldTransitionListener listener) throws android.os.RemoteException;
}
