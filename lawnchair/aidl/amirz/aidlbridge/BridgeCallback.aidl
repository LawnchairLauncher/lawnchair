package amirz.aidlbridge;

interface BridgeCallback {
    oneway void onBridgeConnected(in IBinder service);

    oneway void onBridgeDisconnected();
}
