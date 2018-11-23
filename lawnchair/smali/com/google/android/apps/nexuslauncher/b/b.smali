.class public Lcom/google/android/apps/nexuslauncher/b/b;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Landroid/os/Handler$Callback;


# static fields
.field private static I:Lcom/google/android/apps/nexuslauncher/b/b; = null

.field private static L:Z = true

.field private static M:Z = false


# instance fields
.field public final J:Ljava/util/Map;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Lcom/google/android/apps/nexuslauncher/b/a;",
            ">;"
        }
    .end annotation
.end field

.field public K:Lcom/google/android/apps/nexuslauncher/a;

.field public final b:Landroid/os/Handler;

.field private final mContext:Landroid/content/Context;

.field private final mHandler:Landroid/os/Handler;

.field private final mInstantAppResolver:Lcom/android/launcher3/util/InstantAppResolver;


# direct methods
.method static constructor <clinit>()V
    .locals 0

    return-void
.end method

.method public constructor <init>(Landroid/content/Context;)V
    .locals 1

    .line 52
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 53
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/b/b;->mContext:Landroid/content/Context;

    .line 54
    invoke-static/range {p1 .. p1}, Lcom/android/launcher3/util/InstantAppResolver;->newInstance(Landroid/content/Context;)Lcom/android/launcher3/util/InstantAppResolver;

    move-result-object p1

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/b/b;->mInstantAppResolver:Lcom/android/launcher3/util/InstantAppResolver;

    .line 55
    new-instance p1, Landroid/os/Handler;

    invoke-static {}, Lcom/android/launcher3/LauncherModel;->getWorkerLooper()Landroid/os/Looper;

    move-result-object v0

    invoke-direct {p1, v0, p0}, Landroid/os/Handler;-><init>(Landroid/os/Looper;Landroid/os/Handler$Callback;)V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/b/b;->b:Landroid/os/Handler;

    .line 56
    new-instance p1, Landroid/os/Handler;

    invoke-direct {p1, p0}, Landroid/os/Handler;-><init>(Landroid/os/Handler$Callback;)V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/b/b;->mHandler:Landroid/os/Handler;

    .line 57
    new-instance p1, Ljava/util/HashMap;

    invoke-direct {p1}, Ljava/util/HashMap;-><init>()V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/b/b;->J:Ljava/util/Map;

    return-void
.end method

.method private a(Ljava/lang/String;)Lcom/google/android/apps/nexuslauncher/b/a;
    .locals 7

    .line 140
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/b/b;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;

    move-result-object v0

    const/4 v1, 0x0

    const/4 v2, 0x0

    .line 143
    :try_start_0
    invoke-virtual {v0, p1, v1}, Landroid/content/pm/PackageManager;->getApplicationInfo(Ljava/lang/String;I)Landroid/content/pm/ApplicationInfo;

    move-result-object v3

    .line 144
    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/b/b;->mInstantAppResolver:Lcom/android/launcher3/util/InstantAppResolver;

    invoke-virtual {v4, v3}, Lcom/android/launcher3/util/InstantAppResolver;->isInstantApp(Landroid/content/pm/ApplicationInfo;)Z

    move-result v3
    :try_end_0
    .catch Landroid/content/pm/PackageManager$NameNotFoundException; {:try_start_0 .. :try_end_0} :catch_0

    if-nez v3, :cond_0

    return-object v2

    .line 151
    :cond_0
    new-instance v3, Landroid/content/Intent;

    invoke-direct {v3}, Landroid/content/Intent;-><init>()V

    const-string v4, "android.intent.action.MAIN"

    invoke-virtual {v3, v4}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    move-result-object v3

    const-string v4, "android.intent.category.LAUNCHER"

    invoke-virtual {v3, v4}, Landroid/content/Intent;->addCategory(Ljava/lang/String;)Landroid/content/Intent;

    move-result-object v3

    invoke-virtual {v3, p1}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    move-result-object v3

    const v4, 0x800080

    invoke-virtual {v0, v3, v4}, Landroid/content/pm/PackageManager;->queryIntentActivities(Landroid/content/Intent;I)Ljava/util/List;

    move-result-object v0

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    move-object v3, v2

    :cond_1
    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v4

    if-eqz v4, :cond_2

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Landroid/content/pm/ResolveInfo;

    iget-object v5, v4, Landroid/content/pm/ResolveInfo;->activityInfo:Landroid/content/pm/ActivityInfo;

    iget-object v5, v5, Landroid/content/pm/ActivityInfo;->metaData:Landroid/os/Bundle;

    if-eqz v5, :cond_1

    iget-object v5, v4, Landroid/content/pm/ResolveInfo;->activityInfo:Landroid/content/pm/ActivityInfo;

    iget-object v5, v5, Landroid/content/pm/ActivityInfo;->metaData:Landroid/os/Bundle;

    const-string v6, "default-url"

    invoke-virtual {v5, v6}, Landroid/os/Bundle;->containsKey(Ljava/lang/String;)Z

    move-result v5

    if-eqz v5, :cond_1

    iget-object v3, v4, Landroid/content/pm/ResolveInfo;->activityInfo:Landroid/content/pm/ActivityInfo;

    iget-object v3, v3, Landroid/content/pm/ActivityInfo;->metaData:Landroid/os/Bundle;

    const-string v4, "default-url"

    invoke-virtual {v3, v4}, Landroid/os/Bundle;->getString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    goto :goto_0

    :cond_2
    if-nez v3, :cond_3

    const-string v0, "InstantApps"

    .line 153
    new-instance v1, Ljava/lang/StringBuilder;

    const-string v3, "no default-url available for pkg "

    invoke-direct {v1, v3}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v0, p1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    return-object v2

    .line 157
    :cond_3
    new-instance v0, Landroid/content/Intent;

    const-string v4, "android.intent.action.VIEW"

    invoke-direct {v0, v4}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    const-string v4, "android.intent.category.BROWSABLE"

    .line 158
    invoke-virtual {v0, v4}, Landroid/content/Intent;->addCategory(Ljava/lang/String;)Landroid/content/Intent;

    move-result-object v0

    .line 159
    invoke-static {v3}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v3

    invoke-virtual {v0, v3}, Landroid/content/Intent;->setData(Landroid/net/Uri;)Landroid/content/Intent;

    move-result-object v0

    .line 160
    new-instance v3, Lcom/google/android/apps/nexuslauncher/b/a;

    invoke-direct {v3, v0, p1}, Lcom/google/android/apps/nexuslauncher/b/a;-><init>(Landroid/content/Intent;Ljava/lang/String;)V

    .line 161
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/b/b;->mContext:Landroid/content/Context;

    invoke-static {p1}, Lcom/android/launcher3/LauncherAppState;->getInstance(Landroid/content/Context;)Lcom/android/launcher3/LauncherAppState;

    move-result-object p1

    invoke-virtual {p1}, Lcom/android/launcher3/LauncherAppState;->getIconCache()Lcom/android/launcher3/IconCache;

    move-result-object p1

    .line 162
    invoke-virtual {p1, v3, v1}, Lcom/android/launcher3/IconCache;->getTitleAndIcon(Lcom/android/launcher3/ItemInfoWithIcon;Z)V

    .line 163
    iget-object v0, v3, Lcom/google/android/apps/nexuslauncher/b/a;->iconBitmap:Landroid/graphics/Bitmap;

    if-eqz v0, :cond_5

    iget-object v0, v3, Lcom/google/android/apps/nexuslauncher/b/a;->iconBitmap:Landroid/graphics/Bitmap;

    iget-object v1, v3, Lcom/google/android/apps/nexuslauncher/b/a;->user:Landroid/os/UserHandle;

    invoke-virtual {p1, v0, v1}, Lcom/android/launcher3/IconCache;->isDefaultIcon(Landroid/graphics/Bitmap;Landroid/os/UserHandle;)Z

    move-result p1

    if-eqz p1, :cond_4

    goto :goto_1

    :cond_4
    return-object v3

    :cond_5
    :goto_1
    return-object v2

    :catch_0
    return-object v2
.end method

.method public static b(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/b/b;
    .locals 1

    .line 61
    sget-object v0, Lcom/google/android/apps/nexuslauncher/b/b;->I:Lcom/google/android/apps/nexuslauncher/b/b;

    if-nez v0, :cond_0

    .line 62
    new-instance v0, Lcom/google/android/apps/nexuslauncher/b/b;

    invoke-virtual/range {p0 .. p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p0

    invoke-direct {v0, p0}, Lcom/google/android/apps/nexuslauncher/b/b;-><init>(Landroid/content/Context;)V

    sput-object v0, Lcom/google/android/apps/nexuslauncher/b/b;->I:Lcom/google/android/apps/nexuslauncher/b/b;

    .line 67
    :cond_0
    sget-object p0, Lcom/google/android/apps/nexuslauncher/b/b;->I:Lcom/google/android/apps/nexuslauncher/b/b;

    return-object p0
.end method

.method public static c(Landroid/content/Context;)Ljava/lang/String;
    .locals 19

    .line 119
    invoke-static/range {p0 .. p0}, Lcom/android/launcher3/util/InstantAppResolver;->newInstance(Landroid/content/Context;)Lcom/android/launcher3/util/InstantAppResolver;

    move-result-object v0

    invoke-virtual {v0}, Lcom/android/launcher3/util/InstantAppResolver;->getInstantApps()Ljava/util/List;

    move-result-object v0

    .line 120
    sget-object v1, Lcom/google/android/apps/nexuslauncher/b/c;->N:Lcom/google/android/apps/nexuslauncher/b/c;

    if-nez v1, :cond_0

    new-instance v1, Lcom/google/android/apps/nexuslauncher/b/c;

    invoke-virtual/range {p0 .. p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object v2

    invoke-direct {v1, v2}, Lcom/google/android/apps/nexuslauncher/b/c;-><init>(Landroid/content/Context;)V

    sput-object v1, Lcom/google/android/apps/nexuslauncher/b/c;->N:Lcom/google/android/apps/nexuslauncher/b/c;

    :cond_0
    sget-object v1, Lcom/google/android/apps/nexuslauncher/b/c;->N:Lcom/google/android/apps/nexuslauncher/b/c;

    const-string v2, ""

    .line 124
    sget-boolean v3, Lcom/google/android/apps/nexuslauncher/b/b;->L:Z

    if-eqz v3, :cond_8

    if-eqz v0, :cond_7

    .line 125
    invoke-interface {v0}, Ljava/util/List;->isEmpty()Z

    move-result v2

    if-eqz v2, :cond_1

    goto/16 :goto_4

    :cond_1
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v12

    sget-object v2, Ljava/util/concurrent/TimeUnit;->HOURS:Ljava/util/concurrent/TimeUnit;

    const-wide/16 v3, 0x4

    invoke-virtual {v2, v3, v4}, Ljava/util/concurrent/TimeUnit;->toMillis(J)J

    move-result-wide v2

    sub-long v14, v12, v2

    iget-object v2, v1, Lcom/google/android/apps/nexuslauncher/b/c;->O:Landroid/app/usage/UsageStatsManager;

    invoke-virtual {v2, v14, v15, v12, v13}, Landroid/app/usage/UsageStatsManager;->queryAndAggregateUsageStats(JJ)Ljava/util/Map;

    move-result-object v2

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v7

    const/4 v8, 0x0

    const/16 v16, 0x0

    :goto_0
    invoke-interface {v7}, Ljava/util/Iterator;->hasNext()Z

    move-result v3

    if-eqz v3, :cond_6

    invoke-interface {v7}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v3

    move-object v5, v3

    check-cast v5, Landroid/content/pm/ApplicationInfo;

    iget-object v3, v5, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    invoke-interface {v2, v3}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v3

    move-object v6, v3

    check-cast v6, Landroid/app/usage/UsageStats;

    if-eqz v6, :cond_4

    move-object v3, v1

    move-object v4, v6

    move-object v9, v5

    move-object v10, v6

    move-wide v5, v14

    move-object/from16 v17, v7

    move-object v11, v8

    move-wide v7, v12

    invoke-virtual/range {v3 .. v8}, Lcom/google/android/apps/nexuslauncher/b/c;->a(Landroid/app/usage/UsageStats;JJ)Z

    move-result v3

    if-eqz v3, :cond_5

    if-nez v11, :cond_2

    :goto_1
    const/4 v3, 0x1

    goto :goto_2

    :cond_2
    invoke-virtual {v10}, Landroid/app/usage/UsageStats;->getLastTimeUsed()J

    move-result-wide v3

    invoke-virtual {v11}, Landroid/app/usage/UsageStats;->getLastTimeUsed()J

    move-result-wide v5

    cmp-long v3, v3, v5

    if-lez v3, :cond_3

    goto :goto_1

    :cond_3
    const/4 v3, 0x0

    :goto_2
    if-eqz v3, :cond_5

    iget-object v3, v9, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    move-object/from16 v16, v3

    move-object v8, v10

    goto :goto_3

    :cond_4
    move-object/from16 v17, v7

    move-object v11, v8

    :cond_5
    move-object v8, v11

    :goto_3
    move-object/from16 v7, v17

    goto :goto_0

    :cond_6
    move-object/from16 v2, v16

    goto :goto_5

    :cond_7
    :goto_4
    const/4 v2, 0x0

    .line 128
    :cond_8
    :goto_5
    invoke-static {v2}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v3

    if-eqz v3, :cond_f

    sget-boolean v3, Lcom/google/android/apps/nexuslauncher/b/b;->M:Z

    if-eqz v3, :cond_f

    if-eqz v0, :cond_e

    .line 129
    invoke-interface {v0}, Ljava/util/List;->isEmpty()Z

    move-result v2

    if-eqz v2, :cond_9

    goto :goto_9

    :cond_9
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v9

    sget-object v2, Ljava/util/concurrent/TimeUnit;->DAYS:Ljava/util/concurrent/TimeUnit;

    const-wide/16 v3, 0x2

    invoke-virtual {v2, v3, v4}, Ljava/util/concurrent/TimeUnit;->toMillis(J)J

    move-result-wide v2

    sub-long v11, v9, v2

    iget-object v2, v1, Lcom/google/android/apps/nexuslauncher/b/c;->O:Landroid/app/usage/UsageStatsManager;

    invoke-virtual {v2, v11, v12, v9, v10}, Landroid/app/usage/UsageStatsManager;->queryAndAggregateUsageStats(JJ)Ljava/util/Map;

    move-result-object v2

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    const/4 v13, 0x0

    const/16 v18, 0x0

    :cond_a
    :goto_6
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v3

    if-eqz v3, :cond_d

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v3

    move-object v14, v3

    check-cast v14, Landroid/content/pm/ApplicationInfo;

    iget-object v3, v14, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    invoke-interface {v2, v3}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v3

    move-object v15, v3

    check-cast v15, Landroid/app/usage/UsageStats;

    if-eqz v15, :cond_a

    move-object v3, v1

    move-object v4, v15

    move-wide v5, v11

    move-wide v7, v9

    invoke-virtual/range {v3 .. v8}, Lcom/google/android/apps/nexuslauncher/b/c;->a(Landroid/app/usage/UsageStats;JJ)Z

    move-result v3

    if-eqz v3, :cond_a

    if-nez v13, :cond_b

    :goto_7
    const/4 v3, 0x1

    goto :goto_8

    :cond_b
    invoke-virtual {v15}, Landroid/app/usage/UsageStats;->getTotalTimeInForeground()J

    move-result-wide v3

    invoke-virtual {v13}, Landroid/app/usage/UsageStats;->getTotalTimeInForeground()J

    move-result-wide v5

    cmp-long v3, v3, v5

    if-lez v3, :cond_c

    goto :goto_7

    :cond_c
    const/4 v3, 0x0

    :goto_8
    if-eqz v3, :cond_a

    iget-object v3, v14, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    move-object/from16 v18, v3

    move-object v13, v15

    goto :goto_6

    :cond_d
    move-object/from16 v2, v18

    goto :goto_a

    :cond_e
    :goto_9
    const/4 v2, 0x0

    :cond_f
    :goto_a
    return-object v2
.end method


# virtual methods
.method public handleMessage(Landroid/os/Message;)Z
    .locals 4

    .line 88
    iget v0, p1, Landroid/os/Message;->what:I

    const/4 v1, 0x2

    const/4 v2, 0x1

    if-ne v0, v2, :cond_3

    .line 89
    iget-object v0, p1, Landroid/os/Message;->obj:Ljava/lang/Object;

    if-eqz v0, :cond_0

    .line 90
    iget-object p1, p1, Landroid/os/Message;->obj:Ljava/lang/Object;

    check-cast p1, Ljava/util/List;

    goto :goto_0

    :cond_0
    sget-object p1, Ljava/util/Collections;->EMPTY_LIST:Ljava/util/List;

    .line 91
    :goto_0
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    .line 92
    invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :cond_1
    :goto_1
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_2

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/lang/String;

    .line 93
    invoke-direct {p0, v2}, Lcom/google/android/apps/nexuslauncher/b/b;->a(Ljava/lang/String;)Lcom/google/android/apps/nexuslauncher/b/a;

    move-result-object v3

    if-eqz v3, :cond_1

    .line 95
    invoke-direct {p0, v2}, Lcom/google/android/apps/nexuslauncher/b/b;->a(Ljava/lang/String;)Lcom/google/android/apps/nexuslauncher/b/a;

    move-result-object v2

    invoke-interface {v0, v2}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    goto :goto_1

    .line 98
    :cond_2
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/b/b;->mHandler:Landroid/os/Handler;

    invoke-static {p1, v1, v0}, Landroid/os/Message;->obtain(Landroid/os/Handler;ILjava/lang/Object;)Landroid/os/Message;

    move-result-object p1

    invoke-virtual {p1}, Landroid/os/Message;->sendToTarget()V

    goto :goto_3

    .line 99
    :cond_3
    iget v0, p1, Landroid/os/Message;->what:I

    if-ne v0, v1, :cond_5

    .line 100
    iget-object p1, p1, Landroid/os/Message;->obj:Ljava/lang/Object;

    check-cast p1, Ljava/util/List;

    .line 101
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/b/b;->J:Ljava/util/Map;

    invoke-interface {v0}, Ljava/util/Map;->clear()V

    .line 102
    invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :goto_2
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_4

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/google/android/apps/nexuslauncher/b/a;

    .line 103
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/b/b;->J:Ljava/util/Map;

    invoke-virtual {v0}, Lcom/google/android/apps/nexuslauncher/b/a;->getTargetComponent()Landroid/content/ComponentName;

    move-result-object v2

    invoke-virtual {v2}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;

    move-result-object v2

    invoke-interface {v1, v2, v0}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    goto :goto_2

    .line 106
    :cond_4
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/b/b;->K:Lcom/google/android/apps/nexuslauncher/a;

    if-eqz p1, :cond_5

    .line 107
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/b/b;->K:Lcom/google/android/apps/nexuslauncher/a;

    invoke-interface {p1}, Lcom/google/android/apps/nexuslauncher/a;->onUpdateUI()V

    :cond_5
    :goto_3
    const/4 p1, 0x0

    return p1
.end method
