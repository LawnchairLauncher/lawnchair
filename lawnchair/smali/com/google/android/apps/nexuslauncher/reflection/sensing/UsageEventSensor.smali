.class public Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/c/a;


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$UsageStatsManagerWrapper;,
        Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;
    }
.end annotation


# static fields
.field public static final cS:J

.field public static final cT:J

.field private static final cU:J

.field private static final cV:Ljava/util/Set;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Set<",
            "Ljava/lang/Integer;",
            ">;"
        }
    .end annotation
.end field

.field private static cW:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;


# instance fields
.field private final cL:Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/List<",
            "Lcom/google/research/reflection/c/c;",
            ">;"
        }
    .end annotation
.end field

.field private final cX:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$UsageStatsManagerWrapper;

.field private final cY:Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/List<",
            "Lcom/google/android/apps/nexuslauncher/reflection/signal/a;",
            ">;"
        }
    .end annotation
.end field

.field private final cZ:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;

.field private da:J

.field private db:Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

.field private dc:Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

.field private final mContext:Landroid/content/Context;

.field mEarliestTrainableTime:J

.field private final mPrivatePrefs:Landroid/content/SharedPreferences;


# direct methods
.method static constructor <clinit>()V
    .locals 3

    .line 43
    sget-object v0, Ljava/util/concurrent/TimeUnit;->MINUTES:Ljava/util/concurrent/TimeUnit;

    const-wide/16 v1, 0x14

    invoke-virtual {v0, v1, v2}, Ljava/util/concurrent/TimeUnit;->toMillis(J)J

    move-result-wide v0

    sput-wide v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cS:J

    .line 44
    sget-object v0, Ljava/util/concurrent/TimeUnit;->MINUTES:Ljava/util/concurrent/TimeUnit;

    const-wide/16 v1, 0xa

    invoke-virtual {v0, v1, v2}, Ljava/util/concurrent/TimeUnit;->toMillis(J)J

    move-result-wide v0

    sput-wide v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cT:J

    .line 45
    sget-object v0, Ljava/util/concurrent/TimeUnit;->SECONDS:Ljava/util/concurrent/TimeUnit;

    const-wide/16 v1, 0x5

    invoke-virtual {v0, v1, v2}, Ljava/util/concurrent/TimeUnit;->toMillis(J)J

    move-result-wide v0

    sput-wide v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cU:J

    .line 46
    new-instance v0, Ljava/util/HashSet;

    invoke-direct {v0}, Ljava/util/HashSet;-><init>()V

    const/4 v1, 0x1

    invoke-static {v1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v1

    invoke-interface {v0, v1}, Ljava/util/Set;->add(Ljava/lang/Object;)Z

    sget v1, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v2, 0x19

    if-lt v1, v2, :cond_0

    const/16 v1, 0x8

    invoke-static {v1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v1

    invoke-interface {v0, v1}, Ljava/util/Set;->add(Ljava/lang/Object;)Z

    :cond_0
    sput-object v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cV:Ljava/util/Set;

    return-void
.end method

.method public constructor <init>(Landroid/app/usage/UsageStatsManager;Landroid/content/Context;)V
    .locals 2

    .line 84
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    const-wide v0, 0x7fffffffffffffffL

    .line 56
    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->mEarliestTrainableTime:J

    .line 57
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->db:Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    .line 58
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->dc:Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    .line 85
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$UsageStatsManagerWrapper;

    invoke-direct {v0, p0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$UsageStatsManagerWrapper;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;Landroid/app/usage/UsageStatsManager;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cX:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$UsageStatsManagerWrapper;

    .line 86
    new-instance p1, Ljava/util/ArrayList;

    invoke-direct {p1}, Ljava/util/ArrayList;-><init>()V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cL:Ljava/util/List;

    .line 87
    new-instance p1, Ljava/util/ArrayList;

    invoke-direct {p1}, Ljava/util/ArrayList;-><init>()V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cY:Ljava/util/List;

    .line 88
    new-instance p1, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;

    invoke-direct {p1, p0, p2}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;Landroid/content/Context;)V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cZ:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;

    .line 89
    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->mContext:Landroid/content/Context;

    .line 90
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->mContext:Landroid/content/Context;

    invoke-static {p1}, Lcom/google/android/apps/nexuslauncher/reflection/f;->d(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object p1

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->mPrivatePrefs:Landroid/content/SharedPreferences;

    return-void
.end method

.method protected constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$UsageStatsManagerWrapper;Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;)V
    .locals 2

    .line 292
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    const-wide v0, 0x7fffffffffffffffL

    .line 56
    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->mEarliestTrainableTime:J

    .line 57
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->db:Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    .line 58
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->dc:Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    .line 293
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cX:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$UsageStatsManagerWrapper;

    .line 294
    new-instance p1, Ljava/util/ArrayList;

    invoke-direct {p1}, Ljava/util/ArrayList;-><init>()V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cL:Ljava/util/List;

    .line 295
    new-instance p1, Ljava/util/ArrayList;

    invoke-direct {p1}, Ljava/util/ArrayList;-><init>()V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cY:Ljava/util/List;

    .line 296
    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cZ:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;

    const/4 p1, 0x0

    .line 297
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->mContext:Landroid/content/Context;

    .line 298
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->mPrivatePrefs:Landroid/content/SharedPreferences;

    return-void
.end method

.method private a(Landroid/app/usage/UsageEvents$Event;)Z
    .locals 1

    .line 206
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cZ:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;

    iget-object v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;->dd:Ljava/util/Set;

    invoke-virtual/range {p1 .. p1}, Landroid/app/usage/UsageEvents$Event;->getPackageName()Ljava/lang/String;

    move-result-object p1

    invoke-interface {v0, p1}, Ljava/util/Set;->contains(Ljava/lang/Object;)Z

    move-result p1

    return p1
.end method

.method private declared-synchronized a(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)Z
    .locals 2

    monitor-enter p0

    .line 104
    :try_start_0
    iget-object v0, p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    iget-object v1, p2, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object v1, v1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_0

    .line 106
    invoke-virtual/range {p2 .. p2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object p2

    invoke-interface {p2}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v0

    invoke-virtual/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object p1

    invoke-interface {p1}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide p1

    sub-long/2addr v0, p1

    .line 105
    invoke-static {v0, v1}, Ljava/lang/Math;->abs(J)J

    move-result-wide p1

    sget-wide v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cU:J
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    cmp-long p1, p1, v0

    if-gtz p1, :cond_0

    const/4 p1, 0x1

    monitor-exit p0

    return p1

    :cond_0
    const/4 p1, 0x0

    .line 104
    monitor-exit p0

    return p1

    :catchall_0
    move-exception p1

    .line 103
    monitor-exit p0

    throw p1
.end method

.method private c(J)V
    .locals 2

    .line 146
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->mPrivatePrefs:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_0

    .line 147
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->mPrivatePrefs:Landroid/content/SharedPreferences;

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v1, "reflection_most_recent_usage_buffer"

    .line 148
    invoke-interface {v0, v1, p1, p2}, Landroid/content/SharedPreferences$Editor;->putLong(Ljava/lang/String;J)Landroid/content/SharedPreferences$Editor;

    move-result-object p1

    .line 151
    invoke-interface {p1}, Landroid/content/SharedPreferences$Editor;->apply()V

    :cond_0
    return-void
.end method

.method public static declared-synchronized e(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;
    .locals 6

    const-class v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    monitor-enter v0

    .line 64
    :try_start_0
    sget-object v1, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cW:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    if-nez v1, :cond_3

    const/4 v1, 0x0

    const-string v2, "appops"

    .line 65
    invoke-virtual {p0, v2}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Landroid/app/AppOpsManager;

    const-string v3, "android:get_usage_stats"

    invoke-static {}, Landroid/os/Process;->myUid()I

    move-result v4

    invoke-virtual/range {p0 .. p0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;

    move-result-object v5

    invoke-virtual {v2, v3, v4, v5}, Landroid/app/AppOpsManager;->checkOpNoThrow(Ljava/lang/String;ILjava/lang/String;)I

    move-result v2

    const/4 v3, 0x3

    const/4 v4, 0x0

    const/4 v5, 0x1

    if-ne v2, v3, :cond_0

    const-string v2, "android.permission.PACKAGE_USAGE_STATS"

    invoke-virtual {p0, v2}, Landroid/content/Context;->checkCallingOrSelfPermission(Ljava/lang/String;)I

    move-result v2

    if-nez v2, :cond_1

    :goto_0
    const/4 v4, 0x1

    goto :goto_1

    :cond_0
    if-nez v2, :cond_1

    goto :goto_0

    :cond_1
    :goto_1
    if-eqz v4, :cond_2

    new-instance v1, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    const-string v2, "usagestats"

    invoke-virtual {p0, v2}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Landroid/app/usage/UsageStatsManager;

    invoke-direct {v1, v2, p0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;-><init>(Landroid/app/usage/UsageStatsManager;Landroid/content/Context;)V

    .line 66
    :cond_2
    sput-object v1, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cW:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    if-eqz v1, :cond_3

    .line 67
    sget-object p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cW:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->mPrivatePrefs:Landroid/content/SharedPreferences;

    const-string v1, "reflection_most_recent_usage"

    const-wide/16 v2, 0x0

    .line 68
    invoke-interface {p0, v1, v2, v3}, Landroid/content/SharedPreferences;->getLong(Ljava/lang/String;J)J

    move-result-wide v4

    cmp-long p0, v4, v2

    if-eqz p0, :cond_3

    .line 71
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v1

    cmp-long p0, v4, v1

    if-gez p0, :cond_3

    .line 72
    sget-object p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cW:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    iput-wide v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->da:J

    .line 81
    :cond_3
    sget-object p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cW:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    monitor-exit v0

    return-object p0

    :catchall_0
    move-exception p0

    .line 63
    monitor-exit v0

    throw p0
.end method

.method private declared-synchronized f(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)Z
    .locals 1

    monitor-enter p0

    .line 99
    :try_start_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->db:Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-direct {p0, p1, v0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->a(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)Z

    move-result v0

    if-nez v0, :cond_1

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->dc:Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    .line 100
    invoke-direct {p0, p1, v0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->a(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)Z

    move-result p1
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    if-eqz p1, :cond_0

    goto :goto_0

    :cond_0
    const/4 p1, 0x0

    .line 99
    monitor-exit p0

    return p1

    :cond_1
    :goto_0
    const/4 p1, 0x1

    .line 100
    monitor-exit p0

    return p1

    :catchall_0
    move-exception p1

    .line 98
    monitor-exit p0

    throw p1
.end method


# virtual methods
.method public final A()V
    .locals 2

    .line 131
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v0

    invoke-virtual {p0, v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->d(J)V

    return-void
.end method

.method public final a(Lcom/google/research/reflection/c/c;)Lcom/google/research/reflection/c/a;
    .locals 1

    .line 121
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cL:Ljava/util/List;

    invoke-interface {v0, p1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    return-object p0
.end method

.method public final declared-synchronized d(J)V
    .locals 5

    monitor-enter p0

    .line 171
    :try_start_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cZ:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;

    iget-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;->mInstantAppResolver:Lcom/android/launcher3/util/InstantAppResolver;

    invoke-virtual {v1}, Lcom/android/launcher3/util/InstantAppResolver;->getInstantApps()Ljava/util/List;

    move-result-object v1

    iget-object v2, v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;->dd:Ljava/util/Set;

    invoke-interface {v2}, Ljava/util/Set;->clear()V

    invoke-interface {v1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v1

    :goto_0
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_0

    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Landroid/content/pm/ApplicationInfo;

    iget-object v3, v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;->dd:Ljava/util/Set;

    iget-object v2, v2, Landroid/content/pm/ApplicationInfo;->packageName:Ljava/lang/String;

    invoke-interface {v3, v2}, Ljava/util/Set;->add(Ljava/lang/Object;)Z

    goto :goto_0

    .line 172
    :cond_0
    invoke-virtual/range {p0 .. p2}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->updateEarliestTrainableTime(J)V

    .line 173
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cY:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->clear()V

    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->da:J

    cmp-long v0, v0, p1

    if-gez v0, :cond_9

    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->da:J

    const-wide/16 v2, 0x1

    add-long/2addr v0, v2

    sget-wide v2, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cS:J

    sub-long v2, p1, v2

    invoke-static {v0, v1, v2, v3}, Ljava/lang/Math;->max(JJ)J

    move-result-wide v0

    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cX:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$UsageStatsManagerWrapper;

    new-instance v3, Ljava/util/ArrayList;

    invoke-direct {v3}, Ljava/util/ArrayList;-><init>()V

    iget-object v2, v2, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$UsageStatsManagerWrapper;->O:Landroid/app/usage/UsageStatsManager;

    invoke-virtual {v2, v0, v1, p1, p2}, Landroid/app/usage/UsageStatsManager;->queryEvents(JJ)Landroid/app/usage/UsageEvents;

    move-result-object p1

    :cond_1
    :goto_1
    invoke-virtual {p1}, Landroid/app/usage/UsageEvents;->hasNextEvent()Z

    move-result p2

    if-eqz p2, :cond_2

    new-instance p2, Landroid/app/usage/UsageEvents$Event;

    invoke-direct {p2}, Landroid/app/usage/UsageEvents$Event;-><init>()V

    invoke-virtual {p1, p2}, Landroid/app/usage/UsageEvents;->getNextEvent(Landroid/app/usage/UsageEvents$Event;)Z

    sget-object v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cV:Ljava/util/Set;

    invoke-virtual {p2}, Landroid/app/usage/UsageEvents$Event;->getEventType()I

    move-result v1

    invoke-static {v1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v1

    invoke-interface {v0, v1}, Ljava/util/Set;->contains(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_1

    invoke-interface {v3, p2}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    goto :goto_1

    :cond_2
    invoke-interface {v3}, Ljava/util/List;->isEmpty()Z

    move-result p1

    const/4 p2, 0x1

    if-nez p1, :cond_3

    invoke-interface {v3}, Ljava/util/List;->size()I

    move-result p1

    sub-int/2addr p1, p2

    invoke-interface {v3, p1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Landroid/app/usage/UsageEvents$Event;

    invoke-virtual {p1}, Landroid/app/usage/UsageEvents$Event;->getTimeStamp()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->da:J

    :cond_3
    invoke-interface {v3}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :cond_4
    :goto_2
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_9

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/app/usage/UsageEvents$Event;

    new-instance v1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-direct {v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>()V

    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;-><init>()V

    invoke-virtual {v0}, Landroid/app/usage/UsageEvents$Event;->getTimeStamp()J

    move-result-wide v3

    invoke-virtual {v2, v3, v4}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->e(J)Lcom/google/research/reflection/signal/d;

    move-result-object v2

    invoke-virtual {v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/d;)Lcom/google/research/reflection/signal/ReflectionEvent;

    const-string v2, "UsageEventSensor"

    invoke-virtual {v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->h(Ljava/lang/String;)Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-virtual {v0}, Landroid/app/usage/UsageEvents$Event;->getEventType()I

    move-result v2

    const/4 v3, 0x0

    if-ne v2, p2, :cond_5

    invoke-direct {p0, v0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->a(Landroid/app/usage/UsageEvents$Event;)Z

    move-result v2

    if-nez v2, :cond_5

    const/4 v2, 0x1

    goto :goto_3

    :cond_5
    const/4 v2, 0x0

    :goto_3
    if-eqz v2, :cond_6

    sget-object v2, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fW:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    invoke-virtual {v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;)Lcom/google/research/reflection/signal/ReflectionEvent;

    new-instance v2, Landroid/content/ComponentName;

    invoke-virtual {v0}, Landroid/app/usage/UsageEvents$Event;->getPackageName()Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v0}, Landroid/app/usage/UsageEvents$Event;->getClassName()Ljava/lang/String;

    move-result-object v0

    invoke-direct {v2, v3, v0}, Landroid/content/ComponentName;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    invoke-static {v2}, Lcom/google/android/apps/nexuslauncher/reflection/f;->a(Landroid/content/ComponentName;)Ljava/lang/String;

    move-result-object v0

    :goto_4
    invoke-virtual {v1, v0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->g(Ljava/lang/String;)Lcom/google/research/reflection/signal/ReflectionEvent;

    goto :goto_5

    :cond_6
    sget v2, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v4, 0x19

    if-lt v2, v4, :cond_7

    invoke-virtual {v0}, Landroid/app/usage/UsageEvents$Event;->getEventType()I

    move-result v2

    const/16 v4, 0x8

    if-ne v2, v4, :cond_7

    const/4 v3, 0x1

    :cond_7
    if-eqz v3, :cond_8

    sget-object v2, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fZ:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    invoke-virtual {v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;)Lcom/google/research/reflection/signal/ReflectionEvent;

    invoke-virtual {v0}, Landroid/app/usage/UsageEvents$Event;->getPackageName()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0}, Landroid/app/usage/UsageEvents$Event;->getShortcutId()Ljava/lang/String;

    move-result-object v0

    invoke-static {v2, v0}, Lcom/google/android/apps/nexuslauncher/reflection/f;->a(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    goto :goto_4

    :cond_8
    invoke-direct {p0, v0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->a(Landroid/app/usage/UsageEvents$Event;)Z

    move-result v2

    if-eqz v2, :cond_4

    sget-object v2, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fY:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    invoke-virtual {v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;)Lcom/google/research/reflection/signal/ReflectionEvent;

    new-instance v2, Landroid/content/ComponentName;

    invoke-virtual {v0}, Landroid/app/usage/UsageEvents$Event;->getPackageName()Ljava/lang/String;

    move-result-object v0

    const-string v3, "@instantapp"

    invoke-direct {v2, v0, v3}, Landroid/content/ComponentName;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    invoke-static {v2}, Lcom/google/android/apps/nexuslauncher/reflection/f;->a(Landroid/content/ComponentName;)Ljava/lang/String;

    move-result-object v0

    goto :goto_4

    :goto_5
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cY:Ljava/util/List;

    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    goto/16 :goto_2

    .line 174
    :cond_9
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cY:Ljava/util/List;

    invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :cond_a
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result p2

    if-eqz p2, :cond_b

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object p2

    check-cast p2, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    .line 175
    invoke-direct {p0, p2}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->f(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)Z

    move-result v0

    if-nez v0, :cond_a

    .line 178
    invoke-virtual {p0, p2}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->trainOnUsageEvent(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)Z

    move-result v0

    if-nez v0, :cond_a

    .line 179
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cL:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_6
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_a

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/c/c;

    .line 180
    invoke-virtual {p2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->I()Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    move-result-object v2

    invoke-interface {v1, v2}, Lcom/google/research/reflection/c/c;->a(Lcom/google/research/reflection/signal/ReflectionEvent;)V

    goto :goto_6

    .line 202
    :cond_b
    iget-wide p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->da:J

    invoke-direct {p0, p1, p2}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->c(J)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 203
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 170
    monitor-exit p0

    throw p1
.end method

.method public final declared-synchronized e(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)V
    .locals 1

    monitor-enter p0

    .line 94
    :try_start_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->db:Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->dc:Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    .line 95
    invoke-virtual/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->I()Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    move-result-object p1

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->db:Lcom/google/android/apps/nexuslauncher/reflection/signal/a;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 96
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 93
    monitor-exit p0

    throw p1
.end method

.method trainOnUsageEvent(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)Z
    .locals 4

    .line 135
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->mContext:Landroid/content/Context;

    if-eqz v0, :cond_1

    invoke-virtual/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v0

    invoke-interface {v0}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v0

    iget-wide v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->mEarliestTrainableTime:J

    cmp-long v0, v0, v2

    if-ltz v0, :cond_1

    .line 136
    invoke-virtual/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->C()Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    move-result-object v0

    sget-object v1, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fZ:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    if-eq v0, v1, :cond_0

    .line 137
    invoke-virtual/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->C()Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    move-result-object v0

    sget-object v1, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fY:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    if-ne v0, v1, :cond_1

    .line 138
    :cond_0
    invoke-virtual/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v0

    invoke-interface {v0}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v0

    invoke-direct {p0, v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->c(J)V

    .line 139
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->mContext:Landroid/content/Context;

    invoke-static {v0}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->getInstance(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;

    move-result-object v0

    invoke-virtual {v0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->onUsageEventTarget(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)V

    const/4 p1, 0x1

    return p1

    :cond_1
    const/4 p1, 0x0

    return p1
.end method

.method updateEarliestTrainableTime(J)V
    .locals 4

    .line 161
    sget-wide v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cS:J

    sub-long v0, p1, v0

    iget-wide v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->da:J

    cmp-long v0, v0, v2

    if-gtz v0, :cond_0

    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->mEarliestTrainableTime:J

    const-wide v2, 0x7fffffffffffffffL

    cmp-long v0, v0, v2

    if-nez v0, :cond_1

    .line 163
    :cond_0
    sget-wide v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->cT:J

    sub-long/2addr p1, v0

    iput-wide p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->mEarliestTrainableTime:J

    :cond_1
    return-void
.end method
