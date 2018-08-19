package com.google.android.libraries.gsa.launcherclient;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import amirz.aidlbridge.Bridge;
import amirz.aidlbridge.BridgeCallback;

public class LauncherClientBridge extends BridgeCallback.Stub implements ServiceConnection {
    private final BaseClientService mClientService;

    public LauncherClientBridge(BaseClientService launcherClientService) {
        mClientService = launcherClientService;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Bridge bridge = Bridge.Stub.asInterface(service);
        try {
            bridge.setCallback(mClientService instanceof LauncherClientService ? 1 : 0, this);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBridgeConnected(IBinder service) {
        mClientService.onServiceConnected(null, service);
    }

    @Override
    public void onBridgeDisconnected() {
        mClientService.onServiceDisconnected(null);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        onBridgeDisconnected();
    }
}
