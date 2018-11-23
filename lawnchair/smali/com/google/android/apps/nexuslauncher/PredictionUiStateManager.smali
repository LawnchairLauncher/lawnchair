.class public Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;
.implements Landroid/view/ViewTreeObserver$OnGlobalLayoutListener;
.implements Lcom/android/launcher3/IconCache$ItemInfoUpdateReceiver;


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$AppPredictionConsumer;,
        Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;,
        Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;
    }
.end annotation


# static fields
.field private static sInstance:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;


# instance fields
.field private mActiveClient:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

.field private mAppsView:Lcom/android/launcher3/allapps/AllAppsContainerView;

.field private final mContext:Landroid/content/Context;

.field private mCurrentState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

.field private final mIconCache:Lcom/android/launcher3/IconCache;

.field private final mInstantAppsController:Lcom/google/android/apps/nexuslauncher/b/b;

.field private final mMainPrefs:Landroid/content/SharedPreferences;

.field private final mMaxIconsPerRow:I

.field private mPendingState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

.field private final mPrivatePrefs:Landroid/content/SharedPreferences;

.field private final mShortcutPredictionsController:Lcom/google/android/apps/nexuslauncher/a/a;


# direct methods
.method private constructor <init>(Landroid/content/Context;)V
    .locals 1

    .line 96
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 97
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mContext:Landroid/content/Context;

    .line 98
    invoke-static/range {p1 .. p1}, Lcom/android/launcher3/Utilities;->getPrefs(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mMainPrefs:Landroid/content/SharedPreferences;

    .line 99
    invoke-static/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/f;->d(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPrivatePrefs:Landroid/content/SharedPreferences;

    .line 101
    invoke-static/range {p1 .. p1}, Lcom/android/launcher3/LauncherAppState;->getIDP(Landroid/content/Context;)Lcom/android/launcher3/InvariantDeviceProfile;

    move-result-object v0

    iget v0, v0, Lcom/android/launcher3/InvariantDeviceProfile;->numColumns:I

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mMaxIconsPerRow:I

    .line 102
    invoke-static/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/b/b;->b(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/b/b;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mInstantAppsController:Lcom/google/android/apps/nexuslauncher/b/b;

    .line 103
    invoke-static/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/a/a;->a(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/a/a;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mShortcutPredictionsController:Lcom/google/android/apps/nexuslauncher/a/a;

    .line 104
    invoke-static/range {p1 .. p1}, Lcom/android/launcher3/LauncherAppState;->getInstance(Landroid/content/Context;)Lcom/android/launcher3/LauncherAppState;

    move-result-object p1

    invoke-virtual {p1}, Lcom/android/launcher3/LauncherAppState;->getIconCache()Lcom/android/launcher3/IconCache;

    move-result-object p1

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mIconCache:Lcom/android/launcher3/IconCache;

    .line 106
    sget-object p1, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;->HOME:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mActiveClient:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    .line 107
    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->parseLastState()Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    move-result-object p1

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mCurrentState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    .line 109
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mMainPrefs:Landroid/content/SharedPreferences;

    invoke-interface {p1, p0}, Landroid/content/SharedPreferences;->registerOnSharedPreferenceChangeListener(Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;)V

    .line 110
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPrivatePrefs:Landroid/content/SharedPreferences;

    invoke-interface {p1, p0}, Landroid/content/SharedPreferences;->registerOnSharedPreferenceChangeListener(Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;)V

    .line 111
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mInstantAppsController:Lcom/google/android/apps/nexuslauncher/b/b;

    new-instance v0, Lcom/google/android/apps/nexuslauncher/-$$Lambda$DlDqw4RH6m7hcgHZ_OpdKc6yQzE;

    invoke-direct {v0, p0}, Lcom/google/android/apps/nexuslauncher/-$$Lambda$DlDqw4RH6m7hcgHZ_OpdKc6yQzE;-><init>(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;)V

    iput-object v0, p1, Lcom/google/android/apps/nexuslauncher/b/b;->K:Lcom/google/android/apps/nexuslauncher/a;

    .line 112
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mShortcutPredictionsController:Lcom/google/android/apps/nexuslauncher/a/a;

    new-instance v0, Lcom/google/android/apps/nexuslauncher/-$$Lambda$DlDqw4RH6m7hcgHZ_OpdKc6yQzE;

    invoke-direct {v0, p0}, Lcom/google/android/apps/nexuslauncher/-$$Lambda$DlDqw4RH6m7hcgHZ_OpdKc6yQzE;-><init>(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;)V

    iput-object v0, p1, Lcom/google/android/apps/nexuslauncher/a/a;->e:Lcom/google/android/apps/nexuslauncher/a;

    return-void
.end method

.method private applyState(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;)V
    .locals 3

    .line 178
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mCurrentState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    iget-boolean v0, v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->isEnabled:Z

    .line 179
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mCurrentState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    .line 180
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mAppsView:Lcom/android/launcher3/allapps/AllAppsContainerView;

    if-eqz p1, :cond_0

    .line 181
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mAppsView:Lcom/android/launcher3/allapps/AllAppsContainerView;

    invoke-virtual {p1}, Lcom/android/launcher3/allapps/AllAppsContainerView;->getFloatingHeaderView()Lcom/android/launcher3/allapps/FloatingHeaderView;

    move-result-object p1

    check-cast p1, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$AppPredictionConsumer;

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mCurrentState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    iget-boolean v1, v1, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->isEnabled:Z

    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mCurrentState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    iget-object v2, v2, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->apps:Ljava/util/List;

    .line 182
    invoke-interface {p1, v1, v2}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$AppPredictionConsumer;->setPredictedApps(ZLjava/util/List;)V

    .line 184
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mCurrentState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    iget-boolean p1, p1, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->isEnabled:Z

    if-eq v0, p1, :cond_0

    .line 186
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mAppsView:Lcom/android/launcher3/allapps/AllAppsContainerView;

    invoke-virtual {p1}, Lcom/android/launcher3/allapps/AllAppsContainerView;->getContext()Landroid/content/Context;

    move-result-object p1

    invoke-static {p1}, Lcom/android/launcher3/Launcher;->getLauncher(Landroid/content/Context;)Lcom/android/launcher3/Launcher;

    move-result-object p1

    invoke-virtual {p1}, Lcom/android/launcher3/Launcher;->getStateManager()Lcom/android/launcher3/LauncherStateManager;

    move-result-object p1

    const/4 v0, 0x1

    invoke-virtual {p1, v0}, Lcom/android/launcher3/LauncherStateManager;->reapplyState(Z)V

    :cond_0
    return-void
.end method

.method private canApplyPredictions(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;)Z
    .locals 4

    .line 283
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mAppsView:Lcom/android/launcher3/allapps/AllAppsContainerView;

    const/4 v1, 0x1

    if-nez v0, :cond_0

    return v1

    .line 287
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mAppsView:Lcom/android/launcher3/allapps/AllAppsContainerView;

    invoke-virtual {v0}, Lcom/android/launcher3/allapps/AllAppsContainerView;->getContext()Landroid/content/Context;

    move-result-object v0

    invoke-static {v0}, Lcom/android/launcher3/Launcher;->getLauncher(Landroid/content/Context;)Lcom/android/launcher3/Launcher;

    move-result-object v0

    .line 288
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mAppsView:Lcom/android/launcher3/allapps/AllAppsContainerView;

    invoke-virtual {v2}, Lcom/android/launcher3/allapps/AllAppsContainerView;->isShown()Z

    move-result v2

    if-eqz v2, :cond_7

    invoke-virtual {v0}, Lcom/android/launcher3/Launcher;->isForceInvisible()Z

    move-result v2

    if-eqz v2, :cond_1

    goto :goto_1

    .line 292
    :cond_1
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mCurrentState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    iget-boolean v2, v2, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->isEnabled:Z

    iget-boolean v3, p1, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->isEnabled:Z

    if-ne v2, v3, :cond_6

    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mCurrentState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    iget-object v2, v2, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->apps:Ljava/util/List;

    .line 293
    invoke-interface {v2}, Ljava/util/List;->isEmpty()Z

    move-result v2

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->apps:Ljava/util/List;

    invoke-interface {p1}, Ljava/util/List;->isEmpty()Z

    move-result p1

    if-eq v2, p1, :cond_2

    goto :goto_0

    .line 298
    :cond_2
    invoke-virtual {v0}, Lcom/android/launcher3/Launcher;->getDeviceProfile()Lcom/android/launcher3/DeviceProfile;

    move-result-object p1

    invoke-virtual {p1}, Lcom/android/launcher3/DeviceProfile;->isVerticalBarLayout()Z

    move-result p1

    const/4 v2, 0x0

    if-eqz p1, :cond_3

    return v2

    .line 302
    :cond_3
    sget-object p1, Lcom/android/launcher3/LauncherState;->OVERVIEW:Lcom/android/launcher3/LauncherState;

    invoke-virtual {v0, p1}, Lcom/android/launcher3/Launcher;->isInState(Lcom/android/launcher3/LauncherState;)Z

    move-result p1

    if-nez p1, :cond_4

    return v2

    .line 310
    :cond_4
    invoke-virtual {v0}, Lcom/android/launcher3/Launcher;->getAllAppsController()Lcom/android/launcher3/allapps/AllAppsTransitionController;

    move-result-object p1

    invoke-virtual {p1}, Lcom/android/launcher3/allapps/AllAppsTransitionController;->getProgress()F

    move-result p1

    const/high16 v0, 0x3f800000    # 1.0f

    cmpl-float p1, p1, v0

    if-lez p1, :cond_5

    return v1

    :cond_5
    return v2

    :cond_6
    :goto_0
    return v1

    :cond_7
    :goto_1
    return v1
.end method

.method private dispatchOnChange(Z)V
    .locals 1

    if-eqz p1, :cond_0

    .line 192
    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->parseLastState()Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    move-result-object v0

    goto :goto_0

    .line 193
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPendingState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    if-nez v0, :cond_1

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mCurrentState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    goto :goto_0

    :cond_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPendingState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    :goto_0
    if-eqz p1, :cond_2

    .line 194
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mAppsView:Lcom/android/launcher3/allapps/AllAppsContainerView;

    if-eqz p1, :cond_2

    invoke-direct {p0, v0}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->canApplyPredictions(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;)Z

    move-result p1

    if-nez p1, :cond_2

    .line 195
    invoke-direct {p0, v0}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->scheduleApplyPredictedApps(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;)V

    return-void

    .line 197
    :cond_2
    invoke-direct {p0, v0}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->applyState(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;)V

    return-void
.end method

.method public static getInstance(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;
    .locals 1

    .line 73
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertUIThread()V

    .line 74
    sget-object v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->sInstance:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;

    if-nez v0, :cond_0

    .line 75
    new-instance v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;

    invoke-virtual/range {p0 .. p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p0

    invoke-direct {v0, p0}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;-><init>(Landroid/content/Context;)V

    sput-object v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->sInstance:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;

    .line 77
    :cond_0
    sget-object p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->sInstance:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;

    return-object p0
.end method

.method private parseLastState()Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;
    .locals 8

    .line 202
    new-instance v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;-><init>()V

    .line 203
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mMainPrefs:Landroid/content/SharedPreferences;

    const-string v2, "pref_show_predictions"

    const/4 v3, 0x1

    invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v1

    iput-boolean v1, v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->isEnabled:Z

    .line 204
    iget-boolean v1, v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->isEnabled:Z

    if-nez v1, :cond_0

    .line 205
    sget-object v1, Ljava/util/Collections;->EMPTY_LIST:Ljava/util/List;

    iput-object v1, v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->apps:Ljava/util/List;

    .line 206
    sget-object v1, Ljava/util/Collections;->EMPTY_LIST:Ljava/util/List;

    iput-object v1, v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->orderedApps:Ljava/util/List;

    return-object v0

    .line 210
    :cond_0
    new-instance v1, Ljava/util/ArrayList;

    invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V

    iput-object v1, v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->apps:Ljava/util/List;

    .line 211
    new-instance v1, Ljava/util/ArrayList;

    invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V

    iput-object v1, v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->orderedApps:Ljava/util/List;

    .line 213
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPrivatePrefs:Landroid/content/SharedPreferences;

    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mActiveClient:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    .line 214
    invoke-static {v2}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;->access$000(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;)Lcom/google/android/apps/nexuslauncher/reflection/a/d;

    move-result-object v2

    iget-object v2, v2, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->bc:Ljava/lang/String;

    const/4 v3, 0x0

    invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    .line 215
    invoke-static {v1}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v2

    const/4 v4, 0x0

    if-nez v2, :cond_2

    const-string v2, ";"

    .line 216
    invoke-virtual {v1, v2}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;

    move-result-object v1

    .line 217
    array-length v2, v1

    const/4 v5, 0x0

    :goto_0
    if-ge v5, v2, :cond_2

    aget-object v6, v1, v5

    .line 218
    iget-object v7, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mContext:Landroid/content/Context;

    invoke-static {v6, v7}, Lcom/google/android/apps/nexuslauncher/util/a;->a(Ljava/lang/String;Landroid/content/Context;)Lcom/android/launcher3/util/ComponentKey;

    move-result-object v6

    if-eqz v6, :cond_1

    .line 220
    iget-object v7, v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->orderedApps:Ljava/util/List;

    invoke-interface {v7, v6}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    :cond_1
    add-int/lit8 v5, v5, 0x1

    goto :goto_0

    .line 225
    :cond_2
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPrivatePrefs:Landroid/content/SharedPreferences;

    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mActiveClient:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    invoke-static {v2}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;->access$000(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;)Lcom/google/android/apps/nexuslauncher/reflection/a/d;

    move-result-object v2

    iget-object v2, v2, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->aZ:Ljava/lang/String;

    invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    .line 226
    invoke-static {v1}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v2

    if-nez v2, :cond_4

    const-string v2, ";"

    .line 227
    invoke-virtual {v1, v2}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;

    move-result-object v1

    .line 228
    array-length v2, v1

    :goto_1
    if-ge v4, v2, :cond_4

    aget-object v3, v1, v4

    .line 229
    iget-object v5, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mContext:Landroid/content/Context;

    invoke-static {v3, v5}, Lcom/google/android/apps/nexuslauncher/util/a;->a(Ljava/lang/String;Landroid/content/Context;)Lcom/android/launcher3/util/ComponentKey;

    move-result-object v3

    if-eqz v3, :cond_3

    .line 231
    iget-object v5, v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->apps:Ljava/util/List;

    new-instance v6, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;

    iget-object v7, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mContext:Landroid/content/Context;

    invoke-direct {v6, v7, v3}, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;-><init>(Landroid/content/Context;Lcom/android/launcher3/util/ComponentKey;)V

    invoke-interface {v5, v6}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    :cond_3
    add-int/lit8 v4, v4, 0x1

    goto :goto_1

    .line 235
    :cond_4
    invoke-direct {p0, v0}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->updateDependencies(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;)V

    return-object v0
.end method

.method private scheduleApplyPredictedApps(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;)V
    .locals 1

    .line 159
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPendingState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    if-nez v0, :cond_0

    const/4 v0, 0x1

    goto :goto_0

    :cond_0
    const/4 v0, 0x0

    .line 160
    :goto_0
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPendingState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    if-eqz v0, :cond_1

    .line 164
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mAppsView:Lcom/android/launcher3/allapps/AllAppsContainerView;

    invoke-virtual {p1}, Lcom/android/launcher3/allapps/AllAppsContainerView;->getViewTreeObserver()Landroid/view/ViewTreeObserver;

    move-result-object p1

    invoke-virtual {p1, p0}, Landroid/view/ViewTreeObserver;->addOnGlobalLayoutListener(Landroid/view/ViewTreeObserver$OnGlobalLayoutListener;)V

    :cond_1
    return-void
.end method

.method private updateDependencies(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;)V
    .locals 9

    .line 240
    iget-boolean v0, p1, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->isEnabled:Z

    if-eqz v0, :cond_7

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mAppsView:Lcom/android/launcher3/allapps/AllAppsContainerView;

    if-nez v0, :cond_0

    goto/16 :goto_2

    .line 244
    :cond_0
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    .line 245
    new-instance v1, Ljava/util/ArrayList;

    invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V

    .line 246
    iget-object v2, p1, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->apps:Ljava/util/List;

    invoke-interface {v2}, Ljava/util/List;->size()I

    move-result v2

    const/4 v3, 0x0

    const/4 v4, 0x0

    const/4 v5, 0x0

    :goto_0
    if-ge v4, v2, :cond_4

    .line 247
    iget v6, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mMaxIconsPerRow:I

    if-ge v5, v6, :cond_4

    .line 248
    iget-object v6, p1, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->apps:Ljava/util/List;

    invoke-interface {v6, v4}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;

    const-string v7, "@instantapp"

    .line 251
    invoke-virtual {v6}, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->getComponentClass()Ljava/lang/String;

    move-result-object v8

    invoke-virtual {v7, v8}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v7

    if-eqz v7, :cond_1

    .line 252
    invoke-virtual {v6}, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->getPackage()Ljava/lang/String;

    move-result-object v6

    invoke-interface {v0, v6}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    goto :goto_1

    .line 260
    :cond_1
    iget-object v7, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mAppsView:Lcom/android/launcher3/allapps/AllAppsContainerView;

    invoke-virtual {v7}, Lcom/android/launcher3/allapps/AllAppsContainerView;->getAppsStore()Lcom/android/launcher3/allapps/AllAppsStore;

    move-result-object v7

    invoke-virtual {v6, v7}, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->getApp(Lcom/android/launcher3/allapps/AllAppsStore;)Lcom/android/launcher3/ItemInfoWithIcon;

    move-result-object v6

    check-cast v6, Lcom/android/launcher3/AppInfo;

    if-eqz v6, :cond_3

    .line 262
    iget-boolean v7, v6, Lcom/android/launcher3/AppInfo;->usingLowResIcon:Z

    if-eqz v7, :cond_2

    .line 264
    iget-object v7, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mIconCache:Lcom/android/launcher3/IconCache;

    invoke-virtual {v7, p0, v6}, Lcom/android/launcher3/IconCache;->updateIconInBackground(Lcom/android/launcher3/IconCache$ItemInfoUpdateReceiver;Lcom/android/launcher3/ItemInfoWithIcon;)Lcom/android/launcher3/IconCache$IconLoadRequest;

    :cond_2
    :goto_1
    add-int/lit8 v5, v5, 0x1

    :cond_3
    add-int/lit8 v4, v4, 0x1

    goto :goto_0

    .line 270
    :cond_4
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mInstantAppsController:Lcom/google/android/apps/nexuslauncher/b/b;

    invoke-interface {v0}, Ljava/util/List;->isEmpty()Z

    move-result v2

    if-nez v2, :cond_5

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/b/b;->b:Landroid/os/Handler;

    const/4 v2, 0x1

    invoke-static {p1, v2, v0}, Landroid/os/Message;->obtain(Landroid/os/Handler;ILjava/lang/Object;)Landroid/os/Message;

    move-result-object p1

    invoke-virtual {p1}, Landroid/os/Message;->sendToTarget()V

    .line 271
    :cond_5
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mShortcutPredictionsController:Lcom/google/android/apps/nexuslauncher/a/a;

    invoke-interface {v1}, Ljava/util/List;->isEmpty()Z

    move-result v0

    if-nez v0, :cond_6

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/a/a;->b:Landroid/os/Handler;

    invoke-static {p1, v3, v1}, Landroid/os/Message;->obtain(Landroid/os/Handler;ILjava/lang/Object;)Landroid/os/Message;

    move-result-object p1

    invoke-virtual {p1}, Landroid/os/Message;->sendToTarget()V

    :cond_6
    return-void

    :cond_7
    :goto_2
    return-void
.end method


# virtual methods
.method public arePredictionsEnabled()Z
    .locals 1

    .line 279
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mCurrentState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    iget-boolean v0, v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->isEnabled:Z

    return v0
.end method

.method public dispatchOnChange()V
    .locals 1

    const/4 v0, 0x0

    .line 275
    invoke-direct {p0, v0}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->dispatchOnChange(Z)V

    return-void
.end method

.method public getClient()Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;
    .locals 1

    .line 116
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mActiveClient:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    return-object v0
.end method

.method public getCurrentState()Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;
    .locals 1

    .line 314
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mCurrentState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    return-object v0
.end method

.method public onGlobalLayout()V
    .locals 1

    .line 146
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mAppsView:Lcom/android/launcher3/allapps/AllAppsContainerView;

    if-nez v0, :cond_0

    return-void

    .line 149
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPendingState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    if-eqz v0, :cond_1

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPendingState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    invoke-direct {p0, v0}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->canApplyPredictions(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;)Z

    move-result v0

    if-eqz v0, :cond_1

    .line 150
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPendingState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    invoke-direct {p0, v0}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->applyState(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;)V

    const/4 v0, 0x0

    .line 151
    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPendingState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    .line 153
    :cond_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPendingState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    if-nez v0, :cond_2

    .line 154
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mAppsView:Lcom/android/launcher3/allapps/AllAppsContainerView;

    invoke-virtual {v0}, Lcom/android/launcher3/allapps/AllAppsContainerView;->getViewTreeObserver()Landroid/view/ViewTreeObserver;

    move-result-object v0

    invoke-virtual {v0, p0}, Landroid/view/ViewTreeObserver;->removeOnGlobalLayoutListener(Landroid/view/ViewTreeObserver$OnGlobalLayoutListener;)V

    :cond_2
    return-void
.end method

.method public onSharedPreferenceChanged(Landroid/content/SharedPreferences;Ljava/lang/String;)V
    .locals 0

    const-string p1, "pref_show_predictions"

    .line 170
    invoke-virtual {p1, p2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_0

    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mActiveClient:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    .line 171
    invoke-static {p1}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;->access$000(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;)Lcom/google/android/apps/nexuslauncher/reflection/a/d;

    move-result-object p1

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->aZ:Ljava/lang/String;

    invoke-virtual {p1, p2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-nez p1, :cond_0

    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mActiveClient:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    .line 172
    invoke-static {p1}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;->access$000(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;)Lcom/google/android/apps/nexuslauncher/reflection/a/d;

    move-result-object p1

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->bc:Ljava/lang/String;

    invoke-virtual {p1, p2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_1

    :cond_0
    const/4 p1, 0x1

    .line 173
    invoke-direct {p0, p1}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->dispatchOnChange(Z)V

    :cond_1
    return-void
.end method

.method public reapplyItemInfo(Lcom/android/launcher3/ItemInfoWithIcon;)V
    .locals 0

    return-void
.end method

.method public setTargetAppsView(Lcom/android/launcher3/allapps/AllAppsContainerView;)V
    .locals 0

    .line 131
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mAppsView:Lcom/android/launcher3/allapps/AllAppsContainerView;

    .line 132
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPendingState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    if-eqz p1, :cond_0

    .line 133
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPendingState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    invoke-direct {p0, p1}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->applyState(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;)V

    const/4 p1, 0x0

    .line 134
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mPendingState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    goto :goto_0

    .line 136
    :cond_0
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mCurrentState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    invoke-direct {p0, p1}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->applyState(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;)V

    .line 138
    :goto_0
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mCurrentState:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    invoke-direct {p0, p1}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->updateDependencies(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;)V

    return-void
.end method

.method public switchClient(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;)V
    .locals 1

    .line 123
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mActiveClient:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    if-ne p1, v0, :cond_0

    return-void

    .line 126
    :cond_0
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->mActiveClient:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    const/4 p1, 0x1

    .line 127
    invoke-direct {p0, p1}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->dispatchOnChange(Z)V

    return-void
.end method
