package com.google.android.apps.nexuslauncher.search;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.launcher3.AllAppsList;
import com.android.launcher3.AppInfo;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.allapps.AppInfoComparator;
import com.android.launcher3.allapps.search.DefaultAppSearchAlgorithm;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.LoaderResults;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LooperExecutor;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AppSearchProvider extends ContentProvider
{
    private static final String[] eK = new String[] { "_id", "suggest_text_1", "suggest_icon_1", "suggest_intent_action", "suggest_intent_data" };
    private final PipeDataWriter<Future> mPipeDataWriter;
    private LooperExecutor mLooper;
    private LauncherAppState mApp;

    public AppSearchProvider() {
        mPipeDataWriter = new PipeDataWriter<Future>() {
            @Override
            public void writeDataToPipe(@NonNull ParcelFileDescriptor output, @NonNull Uri uri, @NonNull String mimeType, @Nullable Bundle opts, @Nullable Future args) {
                ParcelFileDescriptor.AutoCloseOutputStream outStream = null;
                try {
                    outStream = new ParcelFileDescriptor.AutoCloseOutputStream(output);
                    ((Bitmap) args.get()).compress(Bitmap.CompressFormat.PNG, 100, outStream);
                } catch (Throwable e) {
                    Log.w("AppSearchProvider", "fail to write to pipe", e);
                }
                if (outStream != null) {
                    try {
                        outStream.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        };
    }

    public static ComponentKey uriToComponent(final Uri uri, final Context context) {
        return new ComponentKey(ComponentName.unflattenFromString(uri.getQueryParameter("component")),
                UserManagerCompat.getInstance(context).getUserForSerialNumber(Long.parseLong(uri.getQueryParameter("user"))));
    }

    public static Uri buildUri(final AppInfo appInfo, final UserManagerCompat userManagerCompat) {
        return new Uri.Builder()
                .scheme("content")
                .authority(BuildConfig.APPLICATION_ID + ".appssearch")
                .appendQueryParameter("component", appInfo.componentName.flattenToShortString())
                .appendQueryParameter("user", Long.toString(userManagerCompat.getSerialNumberForUser(appInfo.user)))
                .build();
    }

    private Cursor listToCursor(final List<AppInfo> list) {
        final MatrixCursor matrixCursor = new MatrixCursor(AppSearchProvider.eK, list.size());
        final UserManagerCompat instance = UserManagerCompat.getInstance(this.getContext());

        int n = 0;
        for (AppInfo appInfo : list) {
            final String uri = buildUri(appInfo, instance).toString();
            final MatrixCursor.RowBuilder row = matrixCursor.newRow();
            row.add(n++).add(appInfo.title.toString()).add(uri).add("com.google.android.apps.nexuslauncher.search.APP_LAUNCH").add(uri);
        }

        return matrixCursor;
    }

    public Bundle call(final String s, final String s2, final Bundle bundle) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.d("AppSearchProvider", "Content provider accessed on main thread");
            return null;
        }
        if ("loadIcon".equals(s)) try {
            final Uri parse = Uri.parse(s2);
            final ComponentKey dl = uriToComponent(parse, this.getContext());
            final Callable<Bitmap> g = new Callable<Bitmap>() {
                public Bitmap call() {
                    final AppItemInfoWithIcon d = new AppItemInfoWithIcon(dl);
                    mApp.getIconCache().getTitleAndIcon(d, false);
                    return d.iconBitmap;
                }
            };
            final Bundle bundle2 = new Bundle();
            bundle2.putParcelable("suggest_icon_1", mLooper.submit(g).get());
            return bundle2;
        } catch (Exception ex) {
            Log.e("AppSearchProvider", "Unable to load icon " + ex);
            return null;
        }
        return super.call(s, s2, bundle);
    }

    public int delete(final Uri uri, final String s, final String[] array) {
        throw new UnsupportedOperationException();
    }

    public String getType(final Uri uri) {
        return "vnd.android.cursor.dir/vnd.android.search.suggest";
    }

    public Uri insert(final Uri uri, final ContentValues contentValues) {
        throw new UnsupportedOperationException();
    }

    public boolean onCreate() {
        this.mLooper = new LooperExecutor(LauncherModel.getWorkerLooper());
        this.mApp = LauncherAppState.getInstance(this.getContext());
        return true;
    }

    public ParcelFileDescriptor openFile(final Uri uri, final String s) throws FileNotFoundException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.e("AppSearchProvider", "Content provider accessed on main thread");
            return null;
        }
        try {
            final ComponentKey dl = uriToComponent(uri, this.getContext());
            final String s2 = "image/png";
            final Callable<Bitmap> g = new Callable<Bitmap>() {
                public Bitmap call() {
                    final AppItemInfoWithIcon d = new AppItemInfoWithIcon(dl);
                    mApp.getIconCache().getTitleAndIcon(d, false);
                    return d.iconBitmap;
                }
            };
            return openPipeHelper(uri, s2, null, mLooper.submit(g), this.mPipeDataWriter);
        }
        catch (Exception ex) {
            throw new FileNotFoundException(ex.getMessage());
        }
    }

    public Cursor query(@NonNull Uri uri, final String[] array, final String s, final String[] array2, final String s2) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.e("AppSearchProvider", "Content provider accessed on main thread");
            return new MatrixCursor(AppSearchProvider.eK, 0);
        }
        List<AppInfo> list;
        try {
            final f f = new f(uri.getLastPathSegment());
            this.mApp.getModel().enqueueModelUpdateTask(f);
            list = f.eN.get(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException | ExecutionException | TimeoutException ex) {
            Log.d("AppSearchProvider", "Error searching apps", ex);
            list = new ArrayList<>();
        }
        return this.listToCursor(list);
    }

    public int update(final Uri uri, final ContentValues contentValues, final String s, final String[] array) {
        throw new UnsupportedOperationException();
    }


    class f implements Callable<List<AppInfo>>, LauncherModel.ModelUpdateTask
    {
        private final FutureTask<List<AppInfo>> eN;
        private AllAppsList mAllAppsList;
        private LauncherAppState mApp;
        private BgDataModel mBgDataModel;
        private LauncherModel mModel;
        private final String mQuery;

        f(final String s) {
            this.mQuery = s.toLowerCase();
            this.eN = new FutureTask<>(this);
        }

        public List<AppInfo> call() {
            if (!this.mModel.isModelLoaded()) {
                Log.d("AppSearchProvider", "Workspace not loaded, loading now");
                this.mModel.startLoaderForResults(new LoaderResults(this.mApp, this.mBgDataModel, this.mAllAppsList, 0, null));
            }
            if (!this.mModel.isModelLoaded()) {
                Log.d("AppSearchProvider", "Loading workspace failed");
                return Collections.emptyList();
            }
            final ArrayList<AppInfo> list = new ArrayList<>();
            final List<AppInfo> data = DefaultAppSearchAlgorithm.getApps(mApp.getContext(), mAllAppsList.data);
            final DefaultAppSearchAlgorithm.StringMatcher instance = DefaultAppSearchAlgorithm.StringMatcher.getInstance();

            for (final AppInfo appInfo : data) {
                if (DefaultAppSearchAlgorithm.matches(appInfo, this.mQuery, instance)) {
                    list.add(appInfo);
                    if (!appInfo.usingLowResIcon) {
                        continue;
                    }
                    this.mApp.getIconCache().getTitleAndIcon(appInfo, false);
                }
            }

            Collections.sort(list, new AppInfoComparator(this.mApp.getContext()));
            return list;
        }

        public void init(final LauncherAppState mApp, final LauncherModel mModel, final BgDataModel mBgDataModel, final AllAppsList mAllAppsList, final Executor executor) {
            this.mApp = mApp;
            this.mModel = mModel;
            this.mBgDataModel = mBgDataModel;
            this.mAllAppsList = mAllAppsList;
        }

        public void run() {
            this.eN.run();
        }
    }
}
