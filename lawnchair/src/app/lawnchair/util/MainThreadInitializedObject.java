package app.lawnchair.util;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.util.ResourceBasedOverride;
import com.android.launcher3.util.ResourceBasedOverride.Overrides;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.TraceHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Utility class for defining singletons which are initiated on main thread.
 */
public class MainThreadInitializedObject<T> {

    private final ObjectProvider<T> mProvider;
    private T mValue;

    public MainThreadInitializedObject(ObjectProvider<T> provider) {
        mProvider = provider;
    }

    public T get(Context context) {
        if (context instanceof SandboxContext sc) {
            return sc.getObject(this);
        }

        if (mValue == null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                mValue = TraceHelper.allowIpcs("main.thread.object",
                        () -> mProvider.get(context.getApplicationContext()));
                onPostInit(context);
            } else {
                try {
                    return MAIN_EXECUTOR.submit(() -> get(context)).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return mValue;
    }

    protected void onPostInit(Context context) {
    }

    public T getNoCreate() {
        return mValue;
    }

    @VisibleForTesting
    public void initializeForTesting(T value) {
        mValue = value;
    }

    /**
     * Initializes a provider based on resource overrides
     */
    public static <T extends ResourceBasedOverride> MainThreadInitializedObject<T> forOverride(
            Class<T> clazz, int resourceId) {
        return new MainThreadInitializedObject<>(c -> Overrides.getObject(clazz, c, resourceId));
    }

    public interface ObjectProvider<T> {

        T get(Context context);
    }

    /**
     * Abstract Context which allows custom implementations for
     * {@link MainThreadInitializedObject} providers
     */
    public static abstract class SandboxContext extends ContextWrapper {

        private static final String TAG = "SandboxContext";

        protected final Set<MainThreadInitializedObject> mAllowedObjects;
        protected final Map<MainThreadInitializedObject, Object> mObjectMap = new HashMap<>();
        protected final ArrayList<Object> mOrderedObjects = new ArrayList<>();

        private final Object mDestroyLock = new Object();
        private boolean mDestroyed = false;

        public SandboxContext(Context base, MainThreadInitializedObject... allowedObjects) {
            super(base);
            mAllowedObjects = new HashSet<>(Arrays.asList(allowedObjects));
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        public void onDestroy() {
            synchronized (mDestroyLock) {
                // Destroy in reverse order
                for (int i = mOrderedObjects.size() - 1; i >= 0; i--) {
                    Object o = mOrderedObjects.get(i);
                    if (o instanceof SafeCloseable) {
                        ((SafeCloseable) o).close();
                    }
                }
                mDestroyed = true;
            }
        }

        /**
         * Find a cached object from mObjectMap if we have already created one. If not,
         * generate
         * an object using the provider.
         */
        protected <T> T getObject(MainThreadInitializedObject<T> object) {
            synchronized (mDestroyLock) {
                if (mDestroyed) {
                    Log.e(TAG, "Static object access with a destroyed context");
                }
                T t = (T) mObjectMap.get(object);
                if (t != null) {
                    return t;
                }
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    t = createObject(object);
                    // Check if we've explicitly allowed the object or if it's a SafeCloseable,
                    // it will get destroyed in onDestroy()
                    if (!mAllowedObjects.contains(object) && !(t instanceof SafeCloseable)) {
                        throw new IllegalStateException("Leaking unknown objects "
                                + object + "  " + object.mProvider + " " + t);
                    }
                    mObjectMap.put(object, t);
                    mOrderedObjects.add(t);
                    return t;
                }
            }

            try {
                return MAIN_EXECUTOR.submit(() -> getObject(object)).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        @UiThread
        protected <T> T createObject(MainThreadInitializedObject<T> object) {
            return object.mProvider.get(this);
        }
    }
}
