.class public Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Landroid/os/Handler$Callback;


# static fields
.field static final MSG_DESTROY:I = 0x1

.field static final MSG_INIT:I = 0x0

.field static final MSG_LAUNCH:I = 0x2

.field public static final PREF_KEY_ENABLE:Ljava/lang/String; = "pref_show_predictions"

.field private static final af:Ljava/lang/Object;

.field private static ag:Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;


# instance fields
.field private final ah:Lcom/android/launcher3/InvariantDeviceProfile;

.field private final mContext:Landroid/content/Context;

.field final mMessageHandler:Landroid/os/Handler;

.field final mPlaceThread:Landroid/os/HandlerThread;

.field mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

.field final mWorkerThread:Landroid/os/HandlerThread;


# direct methods
.method static constructor <clinit>()V
    .locals 1

    .line 54
    new-instance v0, Ljava/lang/Object;

    invoke-direct {v0}, Ljava/lang/Object;-><init>()V

    sput-object v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->af:Ljava/lang/Object;

    return-void
.end method

.method private constructor <init>(Landroid/content/Context;)V
    .locals 2

    .line 76
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    const/4 v0, 0x0

    .line 63
    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    .line 77
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mContext:Landroid/content/Context;

    .line 78
    new-instance v0, Landroid/os/HandlerThread;

    const-string v1, "reflection-thread"

    invoke-direct {v0, v1}, Landroid/os/HandlerThread;-><init>(Ljava/lang/String;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mWorkerThread:Landroid/os/HandlerThread;

    .line 79
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mWorkerThread:Landroid/os/HandlerThread;

    invoke-virtual {v0}, Landroid/os/HandlerThread;->start()V

    .line 80
    new-instance v0, Landroid/os/Handler;

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mWorkerThread:Landroid/os/HandlerThread;

    invoke-virtual {v1}, Landroid/os/HandlerThread;->getLooper()Landroid/os/Looper;

    move-result-object v1

    invoke-direct {v0, v1, p0}, Landroid/os/Handler;-><init>(Landroid/os/Looper;Landroid/os/Handler$Callback;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mMessageHandler:Landroid/os/Handler;

    .line 81
    new-instance v0, Landroid/os/HandlerThread;

    const-string v1, "reflection-place-thread"

    invoke-direct {v0, v1}, Landroid/os/HandlerThread;-><init>(Ljava/lang/String;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mPlaceThread:Landroid/os/HandlerThread;

    .line 82
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mPlaceThread:Landroid/os/HandlerThread;

    invoke-virtual {v0}, Landroid/os/HandlerThread;->start()V

    .line 83
    invoke-static/range {p1 .. p1}, Lcom/android/launcher3/LauncherAppState;->getIDP(Landroid/content/Context;)Lcom/android/launcher3/InvariantDeviceProfile;

    move-result-object p1

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->ah:Lcom/android/launcher3/InvariantDeviceProfile;

    return-void
.end method

.method public static getInstance(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;
    .locals 4

    .line 66
    sget-object v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->af:Ljava/lang/Object;

    monitor-enter v0

    .line 67
    :try_start_0
    sget-object v1, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->ag:Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;

    if-nez v1, :cond_0

    .line 68
    new-instance v1, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;

    invoke-virtual/range {p0 .. p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object v2

    invoke-direct {v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;-><init>(Landroid/content/Context;)V

    .line 69
    sput-object v1, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->ag:Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;

    invoke-static/range {p0 .. p0}, Lcom/android/launcher3/Utilities;->getPrefs(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object p0

    const-string v2, "pref_show_predictions"

    const/4 v3, 0x1

    .line 70
    invoke-interface {p0, v2, v3}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result p0

    .line 69
    invoke-virtual {v1, p0}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->setEnabled(Z)V

    .line 72
    :cond_0
    monitor-exit v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 73
    sget-object p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->ag:Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;

    return-object p0

    :catchall_0
    move-exception p0

    .line 72
    :try_start_1
    monitor-exit v0
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    throw p0
.end method


# virtual methods
.method public getPlaceLooperForGoogleApi()Landroid/os/Looper;
    .locals 1

    .line 189
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mPlaceThread:Landroid/os/HandlerThread;

    invoke-virtual {v0}, Landroid/os/HandlerThread;->getLooper()Landroid/os/Looper;

    move-result-object v0

    return-object v0
.end method

.method public handleMessage(Landroid/os/Message;)Z
    .locals 26

    move-object/from16 v0, p0

    move-object/from16 v1, p1

    .line 126
    iget v2, v1, Landroid/os/Message;->what:I

    const/4 v3, 0x0

    const/4 v4, 0x2

    const/4 v5, 0x0

    const/4 v6, 0x1

    packed-switch v2, :pswitch_data_0

    const/4 v1, 0x0

    return v1

    .line 169
    :pswitch_0
    iget-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    if-eqz v1, :cond_0

    .line 173
    iget-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    iget-object v1, v1, Lcom/google/android/apps/nexuslauncher/reflection/h;->aE:Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;

    invoke-virtual {v1}, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->update()V

    .line 176
    iget-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->ah:Lcom/android/launcher3/InvariantDeviceProfile;

    iget v1, v1, Lcom/android/launcher3/InvariantDeviceProfile;->numColumns:I

    .line 177
    iget-object v2, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    const-string v3, "GEL"

    invoke-virtual {v2, v3, v1}, Lcom/google/android/apps/nexuslauncher/reflection/h;->a(Ljava/lang/String;I)V

    .line 179
    iget-object v2, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    const-string v3, "OVERVIEW_GEL"

    invoke-virtual {v2, v3, v1}, Lcom/google/android/apps/nexuslauncher/reflection/h;->a(Ljava/lang/String;I)V

    :cond_0
    return v6

    .line 163
    :pswitch_1
    iget-object v2, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    if-eqz v2, :cond_1

    .line 164
    iget-object v2, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    iget-object v1, v1, Landroid/os/Message;->obj:Ljava/lang/Object;

    check-cast v1, Lcom/android/launcher3/util/PackageUserKey;

    iget-object v3, v2, Lcom/google/android/apps/nexuslauncher/reflection/h;->mContext:Landroid/content/Context;

    invoke-static {v3}, Lcom/android/launcher3/compat/LauncherAppsCompat;->getInstance(Landroid/content/Context;)Lcom/android/launcher3/compat/LauncherAppsCompat;

    move-result-object v3

    iget-object v4, v1, Lcom/android/launcher3/util/PackageUserKey;->mPackageName:Ljava/lang/String;

    iget-object v7, v1, Lcom/android/launcher3/util/PackageUserKey;->mUser:Landroid/os/UserHandle;

    invoke-virtual {v3, v4, v7}, Lcom/android/launcher3/compat/LauncherAppsCompat;->getActivityList(Ljava/lang/String;Landroid/os/UserHandle;)Ljava/util/List;

    move-result-object v3

    invoke-interface {v3}, Ljava/util/List;->isEmpty()Z

    move-result v4

    if-nez v4, :cond_1

    iget-object v4, v2, Lcom/google/android/apps/nexuslauncher/reflection/h;->ay:Lcom/google/android/apps/nexuslauncher/reflection/b/c;

    invoke-interface {v3, v5}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Landroid/content/pm/LauncherActivityInfo;

    invoke-virtual {v3}, Landroid/content/pm/LauncherActivityInfo;->getComponentName()Landroid/content/ComponentName;

    move-result-object v9

    iget-object v2, v2, Lcom/google/android/apps/nexuslauncher/reflection/h;->mContext:Landroid/content/Context;

    invoke-static {v2}, Lcom/android/launcher3/compat/UserManagerCompat;->getInstance(Landroid/content/Context;)Lcom/android/launcher3/compat/UserManagerCompat;

    move-result-object v2

    iget-object v1, v1, Lcom/android/launcher3/util/PackageUserKey;->mUser:Landroid/os/UserHandle;

    invoke-virtual {v2, v1}, Lcom/android/launcher3/compat/UserManagerCompat;->getSerialNumberForUser(Landroid/os/UserHandle;)J

    move-result-wide v10

    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v12

    iget-object v1, v4, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bj:Ljava/util/LinkedList;

    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;

    move-object v7, v2

    move-object v8, v4

    invoke-direct/range {v7 .. v13}, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/b/c;Landroid/content/ComponentName;JJ)V

    invoke-virtual {v1, v2}, Ljava/util/LinkedList;->add(Ljava/lang/Object;)Z

    invoke-virtual {v4}, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->o()V

    :cond_1
    return v6

    .line 152
    :pswitch_2
    iget-object v2, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    if-eqz v2, :cond_2

    .line 153
    iget-object v2, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->ah:Lcom/android/launcher3/InvariantDeviceProfile;

    iget v2, v2, Lcom/android/launcher3/InvariantDeviceProfile;->numColumns:I

    .line 154
    iget-object v1, v1, Landroid/os/Message;->obj:Ljava/lang/Object;

    check-cast v1, Ljava/lang/String;

    .line 158
    iget-object v3, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    invoke-virtual {v3, v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/h;->a(Ljava/lang/String;I)V

    :cond_2
    return v6

    .line 140
    :pswitch_3
    iget-object v2, v1, Landroid/os/Message;->obj:Ljava/lang/Object;

    check-cast v2, Landroid/util/Pair;

    .line 141
    iget-object v3, v2, Landroid/util/Pair;->first:Ljava/lang/Object;

    check-cast v3, Lcom/android/launcher3/util/ComponentKey;

    .line 142
    iget-object v2, v2, Landroid/util/Pair;->second:Ljava/lang/Object;

    check-cast v2, Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;

    .line 143
    iget-object v7, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    if-eqz v7, :cond_7

    .line 144
    iget-object v7, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mContext:Landroid/content/Context;

    invoke-static {v7}, Lcom/android/launcher3/compat/UserManagerCompat;->getInstance(Landroid/content/Context;)Lcom/android/launcher3/compat/UserManagerCompat;

    move-result-object v7

    iget-object v8, v3, Lcom/android/launcher3/util/ComponentKey;->user:Landroid/os/UserHandle;

    .line 145
    invoke-virtual {v7, v8}, Lcom/android/launcher3/compat/UserManagerCompat;->getSerialNumberForUser(Landroid/os/UserHandle;)J

    move-result-wide v7

    .line 146
    iget v1, v1, Landroid/os/Message;->what:I

    if-ne v1, v4, :cond_3

    iget-object v1, v3, Lcom/android/launcher3/util/ComponentKey;->componentName:Landroid/content/ComponentName;

    iget-object v3, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mContext:Landroid/content/Context;

    invoke-static {v1, v7, v8, v3}, Lcom/google/android/apps/nexuslauncher/reflection/a/e;->a(Landroid/content/ComponentName;JLandroid/content/Context;)Ljava/lang/String;

    move-result-object v1

    sget-object v3, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fW:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    :goto_0
    move-object v8, v1

    move-object v7, v3

    goto :goto_1

    :cond_3
    const/4 v9, 0x6

    if-ne v1, v9, :cond_4

    iget-object v1, v3, Lcom/android/launcher3/util/ComponentKey;->componentName:Landroid/content/ComponentName;

    iget-object v3, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mContext:Landroid/content/Context;

    const-string v9, "%s%s"

    new-array v4, v4, [Ljava/lang/Object;

    const-string v10, "_"

    aput-object v10, v4, v5

    invoke-static {v1, v7, v8, v3}, Lcom/google/android/apps/nexuslauncher/reflection/a/e;->a(Landroid/content/ComponentName;JLandroid/content/Context;)Ljava/lang/String;

    move-result-object v1

    aput-object v1, v4, v6

    invoke-static {v9, v4}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v1

    sget-object v3, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fZ:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    goto :goto_0

    :cond_4
    const/4 v4, 0x7

    if-ne v1, v4, :cond_7

    iget-object v1, v3, Lcom/android/launcher3/util/ComponentKey;->componentName:Landroid/content/ComponentName;

    iget-object v3, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mContext:Landroid/content/Context;

    invoke-static {v1, v7, v8, v3}, Lcom/google/android/apps/nexuslauncher/reflection/a/e;->a(Landroid/content/ComponentName;JLandroid/content/Context;)Ljava/lang/String;

    move-result-object v1

    sget-object v3, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fY:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    goto :goto_0

    :goto_1
    iget-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    if-nez v8, :cond_5

    const-string v1, "Reflection.SvcHandler"

    const-string v2, "Empty event string"

    invoke-static {v1, v2}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    goto :goto_2

    :cond_5
    const-string v3, ""

    iget-object v4, v2, Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;->srcTarget:[Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;

    array-length v4, v4

    if-le v4, v6, :cond_6

    iget-object v3, v2, Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;->srcTarget:[Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;

    aget-object v3, v3, v6

    iget v3, v3, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;->containerType:I

    invoke-static {v3}, Ljava/lang/Integer;->toString(I)Ljava/lang/String;

    move-result-object v3

    :cond_6
    move-object v9, v3

    iget-object v3, v1, Lcom/google/android/apps/nexuslauncher/reflection/h;->aD:Lcom/google/android/apps/nexuslauncher/reflection/b;

    invoke-interface {v3}, Lcom/google/android/apps/nexuslauncher/reflection/b;->g()J

    move-result-wide v10

    iget-object v3, v1, Lcom/google/android/apps/nexuslauncher/reflection/h;->av:Lcom/google/research/reflection/c/d;

    invoke-virtual {v3}, Lcom/google/research/reflection/c/d;->au()Lcom/google/research/reflection/signal/b;

    move-result-object v12

    invoke-static/range {v7 .. v12}, Lcom/google/android/apps/nexuslauncher/reflection/a/e;->a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;Ljava/lang/String;Ljava/lang/String;JLcom/google/research/reflection/signal/b;)Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    move-result-object v3

    invoke-virtual {v1, v3, v6}, Lcom/google/android/apps/nexuslauncher/reflection/h;->a(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;Z)V

    invoke-virtual {v1, v3, v2}, Lcom/google/android/apps/nexuslauncher/reflection/h;->a(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;)V

    :cond_7
    :goto_2
    return v6

    .line 132
    :pswitch_4
    iget-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    if-eqz v1, :cond_8

    iget-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    invoke-virtual {v1, v6}, Lcom/google/android/apps/nexuslauncher/reflection/h;->a(Z)V

    iput-object v3, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    :cond_8
    return v6

    .line 128
    :pswitch_5
    iget-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    if-nez v1, :cond_12

    iget-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mContext:Landroid/content/Context;

    new-instance v2, Ljava/util/ArrayList;

    invoke-direct {v2}, Ljava/util/ArrayList;-><init>()V

    new-instance v15, Lcom/google/android/apps/nexuslauncher/reflection/a;

    invoke-direct {v15, v1}, Lcom/google/android/apps/nexuslauncher/reflection/a;-><init>(Landroid/content/Context;)V

    new-instance v14, Lcom/google/android/apps/nexuslauncher/reflection/a/b;

    invoke-direct {v14, v1}, Lcom/google/android/apps/nexuslauncher/reflection/a/b;-><init>(Landroid/content/Context;)V

    invoke-static {v1}, Lcom/android/launcher3/Utilities;->getPrefs(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object v7

    new-instance v8, Lcom/google/android/apps/nexuslauncher/reflection/d/a;

    const-string v9, "reflection.events"

    invoke-direct {v8, v1, v9}, Lcom/google/android/apps/nexuslauncher/reflection/d/a;-><init>(Landroid/content/Context;Ljava/lang/String;)V

    new-instance v13, Lcom/google/android/apps/nexuslauncher/reflection/d/c;

    invoke-direct {v13, v8, v14}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/d/a;Lcom/google/android/apps/nexuslauncher/reflection/a/b;)V

    new-instance v8, Ljava/io/File;

    invoke-virtual {v1}, Landroid/content/Context;->getCacheDir()Ljava/io/File;

    move-result-object v9

    const-string v10, "client_actions"

    invoke-direct {v8, v9, v10}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    const-string v9, "pre_debug"

    invoke-interface {v7, v9, v5}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v7

    if-eqz v7, :cond_9

    new-instance v3, Lcom/google/android/apps/nexuslauncher/reflection/d/d;

    const-wide/32 v9, 0xa00000

    invoke-direct {v3, v8, v9, v10}, Lcom/google/android/apps/nexuslauncher/reflection/d/d;-><init>(Ljava/io/File;J)V

    goto :goto_3

    :cond_9
    invoke-virtual {v8}, Ljava/io/File;->exists()Z

    move-result v7

    if-eqz v7, :cond_a

    invoke-virtual {v8}, Ljava/io/File;->delete()Z

    :cond_a
    :goto_3
    invoke-static {v1}, Lcom/google/android/apps/nexuslauncher/reflection/f;->d(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object v12

    new-instance v11, Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    invoke-direct {v11, v1}, Lcom/google/android/apps/nexuslauncher/reflection/b/b;-><init>(Landroid/content/Context;)V

    new-instance v10, Lcom/google/android/apps/nexuslauncher/reflection/b/d;

    invoke-direct {v10, v14}, Lcom/google/android/apps/nexuslauncher/reflection/b/d;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/a/b;)V

    new-instance v9, Lcom/google/android/apps/nexuslauncher/reflection/b/c;

    invoke-direct {v9, v1}, Lcom/google/android/apps/nexuslauncher/reflection/b/c;-><init>(Landroid/content/Context;)V

    new-instance v8, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;

    invoke-virtual {v1}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v7

    invoke-direct {v8, v7, v12, v3, v1}, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;-><init>(Landroid/content/ContentResolver;Landroid/content/SharedPreferences;Lcom/google/android/apps/nexuslauncher/reflection/d/d;Landroid/content/Context;)V

    invoke-virtual {v8}, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->update()V

    new-instance v7, Lcom/google/android/apps/nexuslauncher/reflection/g;

    const-string v16, "foreground_evt_buf.properties"

    const/16 v17, 0x0

    move-object/from16 v20, v7

    move-object/from16 v19, v8

    move-object v8, v1

    move-object/from16 v18, v9

    move-object v9, v13

    move-object/from16 v21, v10

    move-object v10, v13

    move-object/from16 v22, v11

    move-object v11, v12

    move-object/from16 v23, v12

    move-object/from16 v12, v16

    move-object/from16 v16, v13

    move-object/from16 v13, v17

    move-object/from16 v24, v14

    move-object/from16 v14, v22

    invoke-direct/range {v7 .. v14}, Lcom/google/android/apps/nexuslauncher/reflection/g;-><init>(Landroid/content/Context;Lcom/google/android/apps/nexuslauncher/reflection/d/c;Lcom/google/research/reflection/a/c;Landroid/content/SharedPreferences;Ljava/lang/String;Ljava/lang/Runnable;Lcom/google/android/apps/nexuslauncher/reflection/b/b;)V

    new-instance v14, Lcom/google/research/reflection/c/d;

    invoke-direct {v14}, Lcom/google/research/reflection/c/d;-><init>()V

    sget-object v7, Lcom/google/android/apps/nexuslauncher/reflection/i;->aI:Lcom/google/android/apps/nexuslauncher/reflection/i;

    const/4 v8, 0x3

    new-array v8, v8, [Lcom/google/research/reflection/c/c;

    move-object/from16 v13, v20

    iget-object v9, v13, Lcom/google/android/apps/nexuslauncher/reflection/g;->al:Lcom/google/research/reflection/c/b;

    aput-object v9, v8, v5

    iget-object v9, v13, Lcom/google/android/apps/nexuslauncher/reflection/g;->am:Lcom/google/android/apps/nexuslauncher/reflection/d/b;

    aput-object v9, v8, v6

    aput-object v14, v8, v4

    invoke-static {v8}, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;

    move-result-object v4

    new-instance v8, Ljava/util/ArrayList;

    invoke-direct {v8, v4}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V

    sget-boolean v4, Lcom/google/android/apps/nexuslauncher/reflection/i;->aH:Z

    if-eqz v4, :cond_b

    new-instance v4, Lcom/google/android/apps/nexuslauncher/reflection/i$1;

    invoke-direct {v4, v7}, Lcom/google/android/apps/nexuslauncher/reflection/i$1;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/i;)V

    invoke-interface {v8, v4}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    :cond_b
    new-instance v4, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;

    invoke-direct {v4, v1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;-><init>(Landroid/content/Context;)V

    invoke-static {v4, v8}, Lcom/google/android/apps/nexuslauncher/reflection/i;->a(Lcom/google/research/reflection/c/a;Ljava/util/List;)V

    invoke-interface {v2, v4}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    sget-boolean v4, Lcom/google/android/apps/nexuslauncher/reflection/i;->aH:Z

    if-eqz v4, :cond_c

    sget-object v4, Lcom/google/android/apps/nexuslauncher/reflection/i;->TAG:Ljava/lang/String;

    const-string v7, "Registered HeadsetPlugReceiver"

    invoke-static {v4, v7}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    :cond_c
    invoke-static {v1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->e(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    move-result-object v4

    if-eqz v4, :cond_d

    invoke-static {v4, v8}, Lcom/google/android/apps/nexuslauncher/reflection/i;->a(Lcom/google/research/reflection/c/a;Ljava/util/List;)V

    sget-boolean v4, Lcom/google/android/apps/nexuslauncher/reflection/i;->aH:Z

    if-eqz v4, :cond_d

    sget-object v4, Lcom/google/android/apps/nexuslauncher/reflection/i;->TAG:Ljava/lang/String;

    const-string v7, "UsageEventSensor added."

    invoke-static {v4, v7}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    :cond_d
    new-instance v4, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;

    invoke-direct {v4, v1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;-><init>(Landroid/content/Context;)V

    invoke-static {v4, v8}, Lcom/google/android/apps/nexuslauncher/reflection/i;->a(Lcom/google/research/reflection/c/a;Ljava/util/List;)V

    invoke-interface {v2, v4}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    invoke-static {v4}, Lcom/android/launcher3/notification/NotificationListener;->setStatusBarNotificationsChangedListener(Lcom/android/launcher3/notification/NotificationListener$StatusBarNotificationsChangedListener;)V

    sget-boolean v4, Lcom/google/android/apps/nexuslauncher/reflection/i;->aH:Z

    if-eqz v4, :cond_e

    sget-object v4, Lcom/google/android/apps/nexuslauncher/reflection/i;->TAG:Ljava/lang/String;

    const-string v7, "NotificationSensor added."

    invoke-static {v4, v7}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    :cond_e
    sget-boolean v4, Lcom/google/android/apps/nexuslauncher/reflection/i;->aH:Z

    if-eqz v4, :cond_f

    sget-object v4, Lcom/google/android/apps/nexuslauncher/reflection/i;->TAG:Ljava/lang/String;

    const-string v7, "Sensors made and connected."

    invoke-static {v4, v7}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    :cond_f
    new-instance v4, Ljava/io/File;

    invoke-virtual {v1}, Landroid/content/Context;->getFilesDir()Ljava/io/File;

    move-result-object v7

    const-string v8, "reflection.engine"

    invoke-direct {v4, v7, v8}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    new-instance v11, Ljava/io/File;

    invoke-virtual {v1}, Landroid/content/Context;->getFilesDir()Ljava/io/File;

    move-result-object v7

    const-string v8, "reflection.engine.background"

    invoke-direct {v11, v7, v8}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    new-instance v12, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;

    invoke-virtual {v1}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v7

    move-object/from16 v10, v23

    invoke-direct {v12, v7, v10, v3, v1}, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;-><init>(Landroid/content/ContentResolver;Landroid/content/SharedPreferences;Lcom/google/android/apps/nexuslauncher/reflection/d/d;Landroid/content/Context;)V

    new-instance v9, Lcom/google/android/apps/nexuslauncher/reflection/j;

    move-object v7, v9

    move-object v8, v1

    move-object v5, v9

    move-object/from16 v9, v16

    move-object/from16 v25, v10

    move-object/from16 v16, v12

    move-object v12, v13

    move-object v6, v13

    move-object/from16 v13, v16

    move-object/from16 v16, v14

    move-object/from16 v14, v22

    invoke-direct/range {v7 .. v14}, Lcom/google/android/apps/nexuslauncher/reflection/j;-><init>(Landroid/content/Context;Lcom/google/android/apps/nexuslauncher/reflection/d/c;Landroid/content/SharedPreferences;Ljava/io/File;Lcom/google/android/apps/nexuslauncher/reflection/g;Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;Lcom/google/android/apps/nexuslauncher/reflection/b/b;)V

    new-instance v7, Lcom/google/android/apps/nexuslauncher/reflection/c;

    invoke-direct {v7}, Lcom/google/android/apps/nexuslauncher/reflection/c;-><init>()V

    invoke-virtual {v7, v4, v6, v5}, Lcom/google/android/apps/nexuslauncher/reflection/c;->a(Ljava/io/File;Lcom/google/android/apps/nexuslauncher/reflection/g;Lcom/google/android/apps/nexuslauncher/reflection/j;)V

    invoke-virtual {v6, v4}, Lcom/google/android/apps/nexuslauncher/reflection/g;->a(Ljava/io/File;)V

    new-instance v4, Lcom/google/android/apps/nexuslauncher/reflection/e;

    move-object/from16 v7, v25

    invoke-direct {v4, v7}, Lcom/google/android/apps/nexuslauncher/reflection/e;-><init>(Landroid/content/SharedPreferences;)V

    new-instance v8, Ljava/util/ArrayList;

    invoke-direct {v8}, Ljava/util/ArrayList;-><init>()V

    sget-object v9, Lcom/google/android/apps/nexuslauncher/reflection/f;->ALL_FILES:Ljava/util/List;

    invoke-interface {v9}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v9

    :goto_4
    invoke-interface {v9}, Ljava/util/Iterator;->hasNext()Z

    move-result v10

    if-eqz v10, :cond_11

    invoke-interface {v9}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v10

    check-cast v10, Ljava/lang/String;

    const-string v11, "/"

    invoke-virtual {v10, v11}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v11

    if-eqz v11, :cond_10

    const/4 v11, 0x1

    invoke-virtual {v10, v11}, Ljava/lang/String;->substring(I)Ljava/lang/String;

    move-result-object v10

    const/4 v11, 0x0

    invoke-virtual {v1, v10, v11}, Landroid/content/Context;->getDir(Ljava/lang/String;I)Ljava/io/File;

    move-result-object v10

    invoke-virtual {v10}, Ljava/io/File;->getAbsolutePath()Ljava/lang/String;

    move-result-object v10

    :cond_10
    invoke-virtual {v8, v10}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    goto :goto_4

    :cond_11
    new-instance v14, Lcom/google/android/apps/nexuslauncher/reflection/d/e;

    new-instance v9, Ljava/io/File;

    invoke-virtual {v1}, Landroid/content/Context;->getApplicationInfo()Landroid/content/pm/ApplicationInfo;

    move-result-object v10

    iget-object v10, v10, Landroid/content/pm/ApplicationInfo;->dataDir:Ljava/lang/String;

    invoke-direct {v9, v10}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    invoke-direct {v14, v7, v9, v8}, Lcom/google/android/apps/nexuslauncher/reflection/d/e;-><init>(Landroid/content/SharedPreferences;Ljava/io/File;Ljava/util/List;)V

    new-instance v13, Lcom/google/android/apps/nexuslauncher/reflection/h;

    move-object v7, v13

    move-object v8, v1

    move-object v9, v6

    move-object v10, v5

    move-object/from16 v11, v16

    move-object/from16 v12, v22

    move-object v5, v13

    move-object/from16 v13, v21

    move-object v6, v14

    move-object/from16 v14, v18

    move-object/from16 v18, v15

    move-object v15, v4

    move-object/from16 v16, v6

    move-object/from16 v17, v3

    invoke-direct/range {v7 .. v19}, Lcom/google/android/apps/nexuslauncher/reflection/h;-><init>(Landroid/content/Context;Lcom/google/android/apps/nexuslauncher/reflection/g;Lcom/google/android/apps/nexuslauncher/reflection/j;Lcom/google/research/reflection/c/d;Lcom/google/android/apps/nexuslauncher/reflection/b/b;Lcom/google/android/apps/nexuslauncher/reflection/b/d;Lcom/google/android/apps/nexuslauncher/reflection/b/c;Lcom/google/android/apps/nexuslauncher/reflection/e;Lcom/google/android/apps/nexuslauncher/reflection/d/e;Lcom/google/android/apps/nexuslauncher/reflection/d/d;Lcom/google/android/apps/nexuslauncher/reflection/b;Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;)V

    new-instance v3, Lcom/google/android/apps/nexuslauncher/reflection/d;

    move-object/from16 v6, v22

    move-object/from16 v4, v24

    invoke-direct {v3, v1, v5, v6, v4}, Lcom/google/android/apps/nexuslauncher/reflection/d;-><init>(Landroid/content/Context;Lcom/google/android/apps/nexuslauncher/reflection/h;Lcom/google/android/apps/nexuslauncher/reflection/b/b;Lcom/google/android/apps/nexuslauncher/reflection/a/b;)V

    invoke-virtual {v2, v3}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    iget-object v1, v5, Lcom/google/android/apps/nexuslauncher/reflection/h;->au:Ljava/util/ArrayList;

    invoke-virtual {v1, v2}, Ljava/util/ArrayList;->addAll(Ljava/util/Collection;)Z

    invoke-virtual {v3}, Lcom/google/android/apps/nexuslauncher/reflection/d;->initialize()V

    iput-object v5, v0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    const-string v1, "GEL"

    invoke-virtual {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->updatePredictionsNow(Ljava/lang/String;)V

    const-string v1, "OVERVIEW_GEL"

    invoke-virtual {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->updatePredictionsNow(Ljava/lang/String;)V

    :cond_12
    const/4 v1, 0x1

    return v1

    nop

    :pswitch_data_0
    .packed-switch 0x0
        :pswitch_5
        :pswitch_4
        :pswitch_3
        :pswitch_2
        :pswitch_2
        :pswitch_1
        :pswitch_3
        :pswitch_3
        :pswitch_0
    .end packed-switch
.end method

.method public onAppLaunch(Lcom/android/launcher3/util/ComponentKey;Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;)V
    .locals 3

    .line 226
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mMessageHandler:Landroid/os/Handler;

    invoke-static/range {p1 .. p2}, Landroid/util/Pair;->create(Ljava/lang/Object;Ljava/lang/Object;)Landroid/util/Pair;

    move-result-object p1

    const/4 p2, 0x2

    invoke-static {v0, p2, p1}, Landroid/os/Message;->obtain(Landroid/os/Handler;ILjava/lang/Object;)Landroid/os/Message;

    move-result-object p1

    invoke-virtual {p1}, Landroid/os/Message;->sendToTarget()V

    const-string p1, "OVERVIEW_GEL"

    .line 231
    sget-wide v0, Lcom/google/android/apps/nexuslauncher/reflection/f;->ak:J

    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mMessageHandler:Landroid/os/Handler;

    const/4 v2, 0x4

    invoke-static {p2, v2, p1}, Landroid/os/Message;->obtain(Landroid/os/Handler;ILjava/lang/Object;)Landroid/os/Message;

    move-result-object p1

    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mMessageHandler:Landroid/os/Handler;

    invoke-virtual {p2, p1, v0, v1}, Landroid/os/Handler;->sendMessageDelayed(Landroid/os/Message;J)Z

    return-void
.end method

.method public onInstantAppLaunch(Lcom/android/launcher3/util/ComponentKey;Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;)V
    .locals 1

    .line 258
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mMessageHandler:Landroid/os/Handler;

    .line 259
    invoke-static/range {p1 .. p2}, Landroid/util/Pair;->create(Ljava/lang/Object;Ljava/lang/Object;)Landroid/util/Pair;

    move-result-object p1

    const/4 p2, 0x7

    .line 258
    invoke-static {v0, p2, p1}, Landroid/os/Message;->obtain(Landroid/os/Handler;ILjava/lang/Object;)Landroid/os/Message;

    move-result-object p1

    .line 259
    invoke-virtual {p1}, Landroid/os/Message;->sendToTarget()V

    return-void
.end method

.method public onNewInstall(Lcom/android/launcher3/util/PackageUserKey;)V
    .locals 2

    .line 237
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mMessageHandler:Landroid/os/Handler;

    const/4 v1, 0x5

    invoke-static {v0, v1, p1}, Landroid/os/Message;->obtain(Landroid/os/Handler;ILjava/lang/Object;)Landroid/os/Message;

    move-result-object p1

    invoke-virtual {p1}, Landroid/os/Message;->sendToTarget()V

    return-void
.end method

.method public onProviderChanged()V
    .locals 4

    .line 297
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mMessageHandler:Landroid/os/Handler;

    const/16 v1, 0x8

    invoke-virtual {v0, v1}, Landroid/os/Handler;->removeMessages(I)V

    .line 298
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mMessageHandler:Landroid/os/Handler;

    sget-wide v2, Lcom/google/android/apps/nexuslauncher/reflection/f;->aj:J

    invoke-virtual {v0, v1, v2, v3}, Landroid/os/Handler;->sendEmptyMessageDelayed(IJ)Z

    return-void
.end method

.method public onShortcutLaunch(Lcom/android/launcher3/shortcuts/ShortcutKey;Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;)V
    .locals 0

    return-void
.end method

.method public onUsageEventTarget(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)V
    .locals 2

    .line 274
    invoke-virtual/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->C()Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    sget-object v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fZ:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    .line 276
    invoke-virtual/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->C()Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    move-result-object v0

    sget-object v1, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fY:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    if-ne v0, v1, :cond_0

    .line 284
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    const/4 v1, 0x0

    invoke-virtual {v0, p1, v1}, Lcom/google/android/apps/nexuslauncher/reflection/h;->a(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;)V

    :cond_0
    return-void
.end method

.method public postNotificationEvent(Ljava/lang/Runnable;)V
    .locals 1

    .line 264
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mMessageHandler:Landroid/os/Handler;

    invoke-virtual {v0, p1}, Landroid/os/Handler;->post(Ljava/lang/Runnable;)Z

    return-void
.end method

.method public setEnabled(Z)V
    .locals 2

    const/4 v0, 0x0

    const/4 v1, 0x1

    if-eqz p1, :cond_0

    .line 213
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mMessageHandler:Landroid/os/Handler;

    invoke-virtual {p1, v1}, Landroid/os/Handler;->removeMessages(I)V

    .line 214
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mMessageHandler:Landroid/os/Handler;

    invoke-virtual {p1, v0}, Landroid/os/Handler;->sendEmptyMessage(I)Z

    return-void

    .line 216
    :cond_0
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mMessageHandler:Landroid/os/Handler;

    invoke-virtual {p1, v0}, Landroid/os/Handler;->removeMessages(I)V

    .line 217
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mMessageHandler:Landroid/os/Handler;

    invoke-virtual {p1, v1}, Landroid/os/Handler;->sendEmptyMessage(I)Z

    return-void
.end method

.method public updatePredictionsNow(Ljava/lang/String;)V
    .locals 2

    .line 194
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mMessageHandler:Landroid/os/Handler;

    const/4 v1, 0x3

    invoke-virtual {v0, v1, p1}, Landroid/os/Handler;->removeMessages(ILjava/lang/Object;)V

    .line 195
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->mMessageHandler:Landroid/os/Handler;

    invoke-static {v0, v1, p1}, Landroid/os/Message;->obtain(Landroid/os/Handler;ILjava/lang/Object;)Landroid/os/Message;

    move-result-object p1

    invoke-virtual {p1}, Landroid/os/Message;->sendToTarget()V

    return-void
.end method
