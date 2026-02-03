/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.quickstep.recents.di

import android.content.Context
import android.util.Log
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.quickstep.RecentsModel
import com.android.quickstep.recents.data.RecentTasksRepository
import com.android.quickstep.recents.data.TaskVisualsChangedDelegate
import com.android.quickstep.recents.data.TaskVisualsChangedDelegateImpl
import com.android.quickstep.recents.data.TasksRepository
import com.android.quickstep.recents.domain.usecase.GetRemainingAppTimerDurationUseCase
import com.android.quickstep.recents.domain.usecase.GetSysUiStatusNavFlagsUseCase
import com.android.quickstep.recents.domain.usecase.GetTaskUseCase
import com.android.quickstep.recents.domain.usecase.GetThumbnailPositionUseCase
import com.android.quickstep.recents.domain.usecase.IsThumbnailValidUseCase
import com.android.quickstep.recents.domain.usecase.OrganizeDesktopTasksUseCase
import com.android.quickstep.recents.viewmodel.RecentsViewData
import com.android.systemui.shared.recents.utilities.PreviewPositionHelper.PreviewPositionHelperFactory
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

internal typealias RecentsScopeId = String

fun Any.toScopeId(): String = this as? RecentsScopeId ?: this.hashCode().toString()

class RecentsDependencies
private constructor(appContext: Context, dispatcherProvider: DispatcherProvider) {
    private val scopes = mutableMapOf<RecentsScopeId, RecentsDependenciesScope>()

    init {
        startDefaultScope(appContext, dispatcherProvider)
    }

    /**
     * This function initialises the default scope with RecentsView dependencies. Some dependencies
     * are global while others are per-RecentsView. The scope is used to differentiate between
     * RecentsViews.
     */
    private fun startDefaultScope(appContext: Context, dispatcherProvider: DispatcherProvider) {
        Log.d(TAG, "startDefaultScope")
        createScope(DEFAULT_SCOPE_ID).apply {
            set(RecentsViewData::class.java.simpleName, RecentsViewData())
            val recentsCoroutineScope =
                CoroutineScope(
                    SupervisorJob() + dispatcherProvider.unconfined + CoroutineName("RecentsView")
                )
            set(CoroutineScope::class.java.simpleName, recentsCoroutineScope)
            set(DispatcherProvider::class.java.simpleName, dispatcherProvider)
            val recentsModel = RecentsModel.INSTANCE.get(appContext)
            val taskVisualsChangedDelegate =
                TaskVisualsChangedDelegateImpl(
                    recentsModel,
                    recentsModel.thumbnailCache.highResLoadingState,
                )
            set(TaskVisualsChangedDelegate::class.java.simpleName, taskVisualsChangedDelegate)

            // Create RecentsTaskRepository singleton
            val recentTasksRepository: RecentTasksRepository =
                with(recentsModel) {
                    TasksRepository(
                        this,
                        thumbnailCache,
                        iconCache,
                        taskVisualsChangedDelegate,
                        recentsCoroutineScope,
                        dispatcherProvider,
                    )
                }
            set(RecentTasksRepository::class.java.simpleName, recentTasksRepository)
        }
    }

    /**
     * This function initialises a scope associated with the dependencies of a single RecentsView.
     *
     * @param viewContext the Context associated with a RecentsView.
     * @return the scope id associated with the new RecentsDependenciesScope.
     */
    fun createRecentsViewScope(viewContext: Context): String {
        val scopeId = viewContext.toScopeId()
        Log.d(TAG, "createRecentsViewScope $scopeId")
        val scope =
            createScope(scopeId).apply {
                set(RecentsViewData::class.java.simpleName, RecentsViewData())
                val dispatcherProvider: DispatcherProvider =
                    get<DispatcherProvider>(DEFAULT_SCOPE_ID)
                val recentsCoroutineScope =
                    CoroutineScope(
                        SupervisorJob() +
                            dispatcherProvider.unconfined +
                            CoroutineName("RecentsView$scopeId")
                    )
                set(CoroutineScope::class.java.simpleName, recentsCoroutineScope)
            }
        scope.linkTo(getScope(DEFAULT_SCOPE_ID))
        return scopeId
    }

    inline fun <reified T> inject(
        scopeId: RecentsScopeId = "",
        extras: RecentsDependenciesExtras = RecentsDependenciesExtras(),
        noinline factory: ((extras: RecentsDependenciesExtras) -> T)? = null,
    ): T = inject(T::class.java, scopeId = scopeId, extras = extras, factory = factory)

    @Suppress("UNCHECKED_CAST")
    @JvmOverloads
    fun <T> inject(
        modelClass: Class<T>,
        scopeId: RecentsScopeId = DEFAULT_SCOPE_ID,
        extras: RecentsDependenciesExtras = RecentsDependenciesExtras(),
        factory: ((extras: RecentsDependenciesExtras) -> T)? = null,
    ): T {
        val currentScopeId = scopeId.ifEmpty { DEFAULT_SCOPE_ID }
        val scope = scopes[currentScopeId] ?: createScope(currentScopeId)

        log("inject ${modelClass.simpleName} into ${scope.scopeId}", Log.INFO)
        var instance: T?
        synchronized(this) {
            instance = getDependency(scope, modelClass)
            log("found instance? $instance", Log.INFO)
            if (instance == null) {
                instance =
                    factory?.invoke(extras) as T ?: createDependency(modelClass, scopeId, extras)
                scope[modelClass.simpleName] = instance!!
                log(
                    "instance of $modelClass" +
                        " (${instance.hashCode()}) added to scope ${scope.scopeId}"
                )
            }
        }
        return instance!!
    }

    inline fun <reified T> provide(scopeId: RecentsScopeId = "", noinline factory: () -> T): T =
        provide(T::class.java, scopeId = scopeId, factory = factory)

    @JvmOverloads
    fun <T> provide(
        modelClass: Class<T>,
        scopeId: RecentsScopeId = DEFAULT_SCOPE_ID,
        factory: () -> T,
    ) = inject(modelClass, scopeId, factory = { factory.invoke() })

    private fun <T> getDependency(scope: RecentsDependenciesScope, modelClass: Class<T>): T? {
        var instance: T? = scope[modelClass.simpleName] as T?
        if (instance == null) {
            instance =
                scope.scopeIdsLinked.firstNotNullOfOrNull { scopeId ->
                    getScope(scopeId)[modelClass.simpleName]
                } as T?
        }
        if (instance != null) log("Found dependency: $instance", Log.INFO)
        return instance
    }

    fun getScope(scope: Any) = getScope(scope.toScopeId())

    fun getScope(scopeId: RecentsScopeId): RecentsDependenciesScope =
        scopes[scopeId] ?: createScope(scopeId)

    fun removeScope(scope: Any) {
        val scopeId = scope.toScopeId()
        scopes[scopeId]?.close()
        scopes.remove(scopeId)
        log("Scope $scopeId removed")
    }

    // TODO(b/353912757): Create a factory so we can prevent this method of growing indefinitely.
    //  Each class should be responsible for providing a factory function to create a new instance.
    @Suppress("UNCHECKED_CAST")
    private fun <T> createDependency(
        modelClass: Class<T>,
        scopeId: RecentsScopeId,
        extras: RecentsDependenciesExtras,
    ): T {
        log("createDependency ${modelClass.simpleName} with $scopeId and $extras started", Log.WARN)
        log("linked scopes: ${getScope(scopeId).scopeIdsLinked}")
        val instance: Any =
            when (modelClass) {
                IsThumbnailValidUseCase::class.java ->
                    IsThumbnailValidUseCase(rotationStateRepository = inject(scopeId))
                GetRemainingAppTimerDurationUseCase::class.java ->
                    GetRemainingAppTimerDurationUseCase(appTimersRepository = inject(scopeId))
                GetTaskUseCase::class.java ->
                    GetTaskUseCase(
                        tasksRepository = inject(scopeId),
                        getRemainingAppTimerDurationUseCase = inject(scopeId),
                    )
                GetSysUiStatusNavFlagsUseCase::class.java -> GetSysUiStatusNavFlagsUseCase()
                GetThumbnailPositionUseCase::class.java ->
                    GetThumbnailPositionUseCase(
                        deviceProfileRepository = inject(scopeId),
                        rotationStateRepository = inject(scopeId),
                        previewPositionHelperFactory = PreviewPositionHelperFactory(),
                    )
                OrganizeDesktopTasksUseCase::class.java -> OrganizeDesktopTasksUseCase()
                else -> {
                    log("Factory for ${modelClass.simpleName} not defined!", Log.ERROR)
                    error("Factory for ${modelClass.simpleName} not defined!")
                }
            }
        return (instance as T).also {
            log(
                "createDependency ${modelClass.simpleName} with $scopeId and $extras completed",
                Log.WARN,
            )
        }
    }

    private fun createScope(scopeId: RecentsScopeId): RecentsDependenciesScope {
        return RecentsDependenciesScope(scopeId).also { scopes[scopeId] = it }
    }

    private fun log(message: String, @Log.Level level: Int = Log.DEBUG) {
        if (DEBUG) {
            when (level) {
                Log.WARN -> Log.w(TAG, message)
                Log.VERBOSE -> Log.v(TAG, message)
                Log.INFO -> Log.i(TAG, message)
                Log.ERROR -> Log.e(TAG, message)
                else -> Log.d(TAG, message)
            }
        }
    }

    companion object {
        const val DEFAULT_SCOPE_ID = "RecentsDependencies::GlobalScope"
        private const val TAG = "RecentsDependencies"
        private const val DEBUG = false

        @Volatile private var instance: RecentsDependencies? = null

        private fun initialize(
            context: Context,
            dispatcherProvider: DispatcherProvider,
        ): RecentsDependencies {
            Log.d(TAG, "initializing")
            synchronized(this) {
                val newInstance =
                    RecentsDependencies(context.applicationContext, dispatcherProvider)
                instance = newInstance
                return newInstance
            }
        }

        @JvmStatic
        fun maybeInitialize(
            context: Context,
            dispatcherProvider: DispatcherProvider,
        ): RecentsDependencies {
            return instance ?: initialize(context, dispatcherProvider)
        }

        fun getInstance(): RecentsDependencies {
            return instance
                ?: throw UninitializedPropertyAccessException(
                    "Recents dependencies are not initialized. " +
                        "Call `RecentsDependencies.maybeInitialize` before using this container."
                )
        }

        @JvmStatic
        fun destroy(viewContext: Context) {
            synchronized(this) {
                val localInstance = instance ?: return
                val scopeId = viewContext.toScopeId()
                val scope = localInstance.scopes[scopeId]
                if (scope == null) {
                    Log.e(
                        TAG,
                        "Trying to destroy an unknown scope. Scopes: ${localInstance.scopes.size}",
                    )
                    return
                }
                scope.close()
                localInstance.scopes.remove(scopeId)
                if (DEBUG) {
                    Log.d(TAG, "destroyed $scopeId", Exception("Printing stack trace"))
                } else {
                    Log.d(TAG, "destroyed $scopeId")
                }
                if (localInstance.scopes.size == 1) {
                    // Only the default scope left - destroy this too.
                    instance = null
                    Log.d(TAG, "also destroyed default scope")
                }
            }
        }

        fun hasScope(scope: Any) =
            synchronized(this) {
                val localInstance = instance ?: return false
                localInstance.scopes.containsKey(scope.toScopeId())
            }
    }
}

inline fun <reified T> RecentsDependencies.Companion.inject(
    scope: Any = "",
    vararg extras: Pair<String, Any>,
    noinline factory: ((extras: RecentsDependenciesExtras) -> T)? = null,
): Lazy<T> = lazy { get(scope, RecentsDependenciesExtras(extras), factory) }

inline fun <reified T> RecentsDependencies.Companion.get(
    scope: Any = "",
    extras: RecentsDependenciesExtras = RecentsDependenciesExtras(),
    noinline factory: ((extras: RecentsDependenciesExtras) -> T)? = null,
): T {
    return getInstance().inject(scope.toScopeId(), extras, factory)
}

inline fun <reified T> RecentsDependencies.Companion.get(
    scope: Any = "",
    vararg extras: Pair<String, Any>,
    noinline factory: ((extras: RecentsDependenciesExtras) -> T)? = null,
): T = get(scope, RecentsDependenciesExtras(extras), factory)

fun RecentsDependencies.Companion.getScope(scopeId: Any) = getInstance().getScope(scopeId)
