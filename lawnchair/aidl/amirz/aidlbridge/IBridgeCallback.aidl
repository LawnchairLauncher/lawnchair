package amirz.aidlbridge;

interface IBridgeCallback {
    oneway void onServiceConnected(in ComponentName name, in IBinder service);

    oneway void onServiceDisconnected(in ComponentName name);
}
