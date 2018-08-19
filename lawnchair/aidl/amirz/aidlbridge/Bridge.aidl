package amirz.aidlbridge;

import amirz.aidlbridge.BridgeCallback;

interface Bridge {
    oneway void setCallback(in int index, in BridgeCallback cb);
}
