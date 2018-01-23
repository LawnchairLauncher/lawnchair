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
import java.util.Iterator;
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
    private final PipeDataWriter<Future> eL;
    private LooperExecutor eM;
    private LauncherAppState mApp;

    public AppSearchProvider() {
        this.eL = new PipeDataWriter<Future>() {
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

    public static ComponentKey dl(final Uri uri, final Context context) {
        return new ComponentKey(ComponentName.unflattenFromString(uri.getQueryParameter("component")), UserManagerCompat.getInstance(context).getUserForSerialNumber(Long.parseLong(uri.getQueryParameter("user"))));
    }

    public static Uri dm(final AppInfo appInfo, final UserManagerCompat userManagerCompat) {
        return new Uri.Builder().scheme("content").authority("com.google.android.apps.nexuslauncher.appssearch").appendQueryParameter("component", appInfo.componentName.flattenToShortString()).appendQueryParameter("user", Long.toString(userManagerCompat.getSerialNumberForUser(appInfo.user))).build();
    }

    private Cursor dn(final List list) {
        final MatrixCursor matrixCursor = new MatrixCursor(AppSearchProvider.eK, list.size());
        final UserManagerCompat instance = UserManagerCompat.getInstance(this.getContext());
        final Iterator<AppInfo> iterator = (Iterator<AppInfo>)list.iterator();
        int n = 0;
        while (iterator.hasNext()) {
            final AppInfo appInfo = iterator.next();
            final String string = dm(appInfo, instance).toString();
            final MatrixCursor.RowBuilder row = matrixCursor.newRow();
            final int n2 = n + 1;
            row.add(n).add(appInfo.title.toString()).add(string).add("com.google.android.apps.nexuslauncher.search.APP_LAUNCH").add(string);
            n = n2;
        }
        return matrixCursor;
    }

    public Bundle call(final String s, final String s2, final Bundle bundle) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.d("AppSearchProvider", "Content provider accessed on main thread");
            return null;
        }
        if ("loadIcon".equals(s)) {
            try {
                final Uri parse = Uri.parse(s2);
                final ComponentKey dl = dl(parse, this.getContext());
                final LooperExecutor em = this.eM;
                final g g = new g(this, dl);
                final LooperExecutor looperExecutor = em;
                final Future<Bitmap> submit = looperExecutor.submit((Callable<Bitmap>)g);
                final Bitmap value = submit.get();
                final Bitmap bitmap = value;
                final Bundle bundle2 = new Bundle();
                bundle2.putParcelable("suggest_icon_1", bitmap);
                return bundle2;
            }
            catch (Exception ex) {
                Log.e("AppSearchProvider", "Unable to load icon " + ex);
                return null;
            }
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
        this.eM = new LooperExecutor(LauncherModel.getWorkerLooper());
        this.mApp = LauncherAppState.getInstance(this.getContext());
        return true;
    }

    public ParcelFileDescriptor openFile(final Uri uri, final String s) throws FileNotFoundException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.e("AppSearchProvider", "Content provider accessed on main thread");
            return null;
        }
        try {
            final ComponentKey dl = dl(uri, this.getContext());
            final String s2 = "image/png";
            final LooperExecutor em = this.eM;
            final g g = new g(this, dl);
            final LooperExecutor looperExecutor = em;
            final Future<Object> submit = looperExecutor.submit((Callable<Object>)g);
            return this.openPipeHelper(uri, s2, null, submit, this.eL);
        }
        catch (Exception ex) {
            throw new FileNotFoundException(ex.getMessage());
        }
    }

    public Cursor query(final Uri uri, final String[] array, final String s, final String[] array2, final String s2) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.e("AppSearchProvider", "Content provider accessed on main thread");
            return new MatrixCursor(AppSearchProvider.eK, 0);
        }
        List<?> list;
        try {
            final f f = new f(uri.getLastPathSegment());
            this.mApp.getModel().enqueueModelUpdateTask(f);
            final Object value = f.eN.get(5, TimeUnit.SECONDS);
            list = (List<?>)value;
        }
        catch (InterruptedException | ExecutionException | TimeoutException ex) {
            Log.d("AppSearchProvider", "Error searching apps", ex);
            list = new ArrayList<Object>();
        }
        return this.dn(list);
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
            final ArrayList<AppInfo> data = this.mAllAppsList.data;
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

    class g implements Callable
    {
        final ComponentKey eO;
        final /* synthetic */ AppSearchProvider eP;

        public g(final AppSearchProvider ep, final ComponentKey eo) {
            this.eP = ep;
            this.eO = eo;
        }

        public Bitmap call() {
            final AppItemInfoWithIcon d = new AppItemInfoWithIcon(this.eO);
            this.eP.mApp.getIconCache().getTitleAndIcon(d, false);
            return d.iconBitmap;
        }
    }
}
