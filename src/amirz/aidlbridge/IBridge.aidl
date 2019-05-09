package amirz.aidlbridge;

import amirz.aidlbridge.IBridgeCallback;

interface IBridge {
    oneway void bindService(in IBridgeCallback cb, in int flags);
}
