package com.google.android.apps.nexuslauncher;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.DownloadManager;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.FileProvider;

import com.android.launcher3.AppInfo;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.ComponentKeyMapper;
import com.android.launcher3.util.ViewOnDrawExecutor;
import com.google.android.libraries.gsa.launcherclient.LauncherClient;

import java.io.File;
import java.util.List;

public class NexusLauncherActivity extends Launcher {
    private final static int REQUEST_EXTERNAL_STORAGE = 100;
    private static final String BRIDGE_TAG = "bridge";

    private final static String PREF_IS_RELOAD = "pref_reload_workspace";
    private NexusLauncher mLauncher;
    private boolean mIsReload;
    private String mThemeHints;

    public NexusLauncherActivity() {
        mLauncher = new NexusLauncher(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        FeatureFlags.QSB_ON_FIRST_SCREEN = showSmartspace();
        mThemeHints = themeHints();

        super.onCreate(savedInstanceState);

        SharedPreferences prefs = Utilities.getPrefs(this);
        if (mIsReload = prefs.getBoolean(PREF_IS_RELOAD, false)) {
            prefs.edit().remove(PREF_IS_RELOAD).apply();

            // Go back to overview after a reload
            showOverviewMode(false);

            // Fix for long press not working
            // This is overwritten in Launcher.onResume
            setWorkspaceLoading(false);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (FeatureFlags.QSB_ON_FIRST_SCREEN != showSmartspace() || !mThemeHints.equals(themeHints())) {
            Utilities.getPrefs(this).edit().putBoolean(PREF_IS_RELOAD, true).apply();
            recreate();
        } else {
            if (LauncherClient.BRIDGE_USE && Utilities.getPrefs(this)
                    .getBoolean(SettingsActivity.ENABLE_MINUS_ONE_PREF, true)) {
                checkBridge();
            }
        }
    }

    @Override
    public void recreate() {
        if (Utilities.ATLEAST_NOUGAT) {
            super.recreate();
        } else {
            finish();
            startActivity(getIntent());
        }
    }

    @Override
    public void clearPendingExecutor(ViewOnDrawExecutor executor) {
        super.clearPendingExecutor(executor);
        if (mIsReload) {
            mIsReload = false;

            // Call again after the launcher has loaded for proper states
            showOverviewMode(false);

            // Strip empty At A Glance page
            getWorkspace().stripEmptyScreens();
        }
    }

    private boolean showSmartspace() {
        return Utilities.getPrefs(this).getBoolean(SettingsActivity.SMARTSPACE_PREF, true);
    }

    private String themeHints() {
        return Utilities.getPrefs(this).getString(Utilities.THEME_OVERRIDE_KEY, "");
    }

    @Override
    public void overrideTheme(boolean isDark, boolean supportsDarkText, boolean isTransparent) {
        int flags = Utilities.getDevicePrefs(this).getInt(NexusLauncherOverlay.PREF_PERSIST_FLAGS, 0);
        int orientFlag = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 16 : 8;
        boolean useGoogleInOrientation = (orientFlag & flags) != 0;
        supportsDarkText &= Utilities.ATLEAST_NOUGAT;
        if (useGoogleInOrientation && isDark) {
            setTheme(R.style.GoogleSearchLauncherThemeDark);
        } else if (useGoogleInOrientation && supportsDarkText) {
            setTheme(R.style.GoogleSearchLauncherThemeDarkText);
        } else if (useGoogleInOrientation && isTransparent) {
            setTheme(R.style.GoogleSearchLauncherThemeTransparent);
        } else if (useGoogleInOrientation) {
            setTheme(R.style.GoogleSearchLauncherTheme);
        } else {
            super.overrideTheme(isDark, supportsDarkText, isTransparent);
        }
    }

    public List<ComponentKeyMapper<AppInfo>> getPredictedApps() {
        return mLauncher.mCallbacks.getPredictedApps();
    }

    public LauncherClient getGoogleNow() {
        return mLauncher.mClient;
    }

    // Bridge code starts here
    // ToDo: Move to separate class

    public static class InstallFragment extends DialogFragment implements DialogInterface.OnClickListener {
        @Override
        public void onCancel(DialogInterface dialog) {
            Utilities.getPrefs(getActivity())
                    .edit()
                    .putBoolean(SettingsActivity.ENABLE_MINUS_ONE_PREF, false)
                    .apply();
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                ((NexusLauncherActivity) getActivity()).promptBridge();
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                onCancel(getDialog());
            }
        }

        @Override
        public Dialog onCreateDialog(final Bundle bundle) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.bridge_missing_title)
                    .setMessage(R.string.bridge_missing_message)
                    .setNegativeButton(android.R.string.cancel, this)
                    .setPositiveButton(R.string.bridge_install, this)
                    .create();
        }
    }

    private void checkBridge() {
        PackageManager manager = getPackageManager();
        try {
            PackageInfo info = manager.getPackageInfo(LauncherClient.BRIDGE_PACKAGE, PackageManager.GET_SIGNATURES);
            if (info.versionName.equals(getString(R.string.bridge_download_version)) && checkBridgeSignature(info.signatures)) {
                return;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        FragmentManager fm = getFragmentManager();
        if (fm.findFragmentByTag(BRIDGE_TAG) == null) {
            InstallFragment fragment = new InstallFragment();
            fragment.show(fm, BRIDGE_TAG);
        }
    }

    /**
     * Enforce signature check to prevent malicious edits or recompilations from being used as a bridge.
     * @param signatures Extracted signatures from the bridge package.
     * @return True if all signatures match the config, false if at least one does not match or the signatures array is empty.
     */
    private boolean checkBridgeSignature(Signature[] signatures) {
        for (Signature signature : signatures) {
            if (signature.hashCode() != getResources().getInteger(R.integer.bridge_signature_hash)) {
                return false;
            }
        }
        return signatures.length > 0;
    }

    private void promptBridge() {
        if (Utilities.ATLEAST_MARSHMALLOW &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_EXTERNAL_STORAGE);
        } else {
            installBridge();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                installBridge();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void installBridge() {
        // Use application context to ensure it does not expire after the download.
        final Context context = getApplicationContext();

        final String fileName = "/" + getString(R.string.bridge_download_file);

        final String src = getString(R.string.bridge_download_url) + "/" +
                getString(R.string.bridge_download_version) + fileName;

        final String dest = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS) + fileName;

        final File file = new File(dest);
        if (file.exists() && !file.delete()) {
            return;
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(src))
                .setVisibleInDownloadsUi(false)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        final DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        final long downloadId = manager.enqueue(request);

        context.registerReceiver(new BroadcastReceiver() {
            public void onReceive(Context ignored, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                if (id == downloadId) {
                    Intent install;
                    if (Utilities.ATLEAST_NOUGAT) {
                        Uri apkUri = FileProvider.getUriForFile(context,
                                BuildConfig.APPLICATION_ID + ".bridge", file);

                        install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                        install.setData(apkUri);
                        install.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } else {
                        Uri apkUri = Uri.fromFile(file);

                        install = new Intent(Intent.ACTION_VIEW);
                        install.setDataAndType(apkUri, manager.getMimeTypeForDownloadedFile(id));
                        install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }

                    context.startActivity(install);
                    context.unregisterReceiver(this);
                }
            }
        }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }
}
