package app.lawnchair.root;

import android.os.Bundle;

interface IRootHelper {
    oneway void goToSleep();

    /**
     * Creates an effect layer owned by root and returns it under the "layer" key. SurfaceFlinger
     * never ignores the blur of layers created by privileged processes, while app layers lose
     * all their blur past 10 blur requests on screen.
     */
    Bundle createGlassBlurLayer();
}
