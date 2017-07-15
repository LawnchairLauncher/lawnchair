package ch.deletescape.lawnchair;

/**
 * This class is a listener for {@link LauncherProvider} changes. It gets notified in the
 * sendNotify method. This listener is needed because by default the Launcher suppresses
 * standard data change callbacks.
 */
public interface LauncherProviderChangeListener {

    void onLauncherProviderChange();

    void onExtractedColorsChanged();
}
