.class public Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/android/launcher3/notification/NotificationListener$StatusBarNotificationsChangedListener;
.implements Lcom/google/android/apps/nexuslauncher/reflection/h$a;
.implements Lcom/google/research/reflection/c/a;


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor$SBNCompareByPostTime;
    }
.end annotation


# static fields
.field protected static final cM:J

.field private static final cN:Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor$SBNCompareByPostTime;


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

.field private final cO:Ljava/util/Set;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Set<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation
.end field

.field private final cP:Ljava/util/Set;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Set<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation
.end field

.field private cQ:J

.field private cR:Lcom/android/launcher3/notification/NotificationListener;

.field private final mContext:Landroid/content/Context;

.field mTimeOfLastReload:J


# direct methods
.method static constructor <clinit>()V
    .locals 3

    .line 47
    sget-object v0, Ljava/util/concurrent/TimeUnit;->MINUTES:Ljava/util/concurrent/TimeUnit;

    const-wide/16 v1, 0x1e

    invoke-virtual {v0, v1, v2}, Ljava/util/concurrent/TimeUnit;->toMillis(J)J

    move-result-wide v0

    sput-wide v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cM:J

    .line 49
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor$SBNCompareByPostTime;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor$SBNCompareByPostTime;-><init>()V

    sput-object v0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cN:Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor$SBNCompareByPostTime;

    return-void
.end method

.method public constructor <init>(Landroid/content/Context;)V
    .locals 2

    .line 58
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 50
    new-instance v0, Ljava/util/HashSet;

    invoke-direct {v0}, Ljava/util/HashSet;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cO:Ljava/util/Set;

    .line 51
    new-instance v0, Ljava/util/HashSet;

    invoke-direct {v0}, Ljava/util/HashSet;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cP:Ljava/util/Set;

    .line 52
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cL:Ljava/util/List;

    const-wide/16 v0, 0x0

    .line 54
    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->mTimeOfLastReload:J

    .line 55
    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cQ:J

    .line 59
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->mContext:Landroid/content/Context;

    return-void
.end method

.method protected constructor <init>(Lcom/android/launcher3/notification/NotificationListener;)V
    .locals 2

    .line 209
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 50
    new-instance v0, Ljava/util/HashSet;

    invoke-direct {v0}, Ljava/util/HashSet;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cO:Ljava/util/Set;

    .line 51
    new-instance v0, Ljava/util/HashSet;

    invoke-direct {v0}, Ljava/util/HashSet;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cP:Ljava/util/Set;

    .line 52
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cL:Ljava/util/List;

    const-wide/16 v0, 0x0

    .line 54
    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->mTimeOfLastReload:J

    .line 55
    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cQ:J

    .line 210
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cR:Lcom/android/launcher3/notification/NotificationListener;

    const/4 p1, 0x0

    .line 211
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->mContext:Landroid/content/Context;

    return-void
.end method

.method private synthetic a(Landroid/service/notification/StatusBarNotification;)V
    .locals 0

    .line 149
    invoke-virtual/range {p0 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->onNotificationRemovedHelper(Landroid/service/notification/StatusBarNotification;)V

    return-void
.end method

.method private a(Landroid/service/notification/StatusBarNotification;ZJ)V
    .locals 4

    .line 170
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cQ:J

    cmp-long v0, p3, v0

    if-gez v0, :cond_0

    return-void

    .line 173
    :cond_0
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>()V

    .line 174
    invoke-virtual/range {p1 .. p1}, Landroid/service/notification/StatusBarNotification;->getPackageName()Ljava/lang/String;

    move-result-object p1

    const/4 v1, 0x1

    const/4 v2, 0x0

    const/4 v3, 0x2

    if-eqz p2, :cond_1

    const-string p2, "%s%s"

    new-array v3, v3, [Ljava/lang/Object;

    aput-object p1, v3, v2

    const-string p1, "/posted"

    aput-object p1, v3, v1

    :goto_0
    invoke-static {p2, v3}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object p1

    goto :goto_1

    :cond_1
    const-string p2, "%s%s"

    new-array v3, v3, [Ljava/lang/Object;

    aput-object p1, v3, v2

    const-string p1, "/removed"

    aput-object p1, v3, v1

    goto :goto_0

    :goto_1
    invoke-virtual {v0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->g(Ljava/lang/String;)Lcom/google/research/reflection/signal/ReflectionEvent;

    move-result-object p1

    sget-object p2, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->gd:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    .line 175
    invoke-interface {p1, p2}, Lcom/google/research/reflection/signal/ReflectionEvent;->a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;)Lcom/google/research/reflection/signal/ReflectionEvent;

    move-result-object p1

    new-instance p2, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;

    invoke-direct {p2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;-><init>()V

    .line 176
    invoke-virtual {p2, p3, p4}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->e(J)Lcom/google/research/reflection/signal/d;

    move-result-object p2

    invoke-interface {p1, p2}, Lcom/google/research/reflection/signal/ReflectionEvent;->a(Lcom/google/research/reflection/signal/d;)Lcom/google/research/reflection/signal/ReflectionEvent;

    move-result-object p1

    check-cast p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    .line 177
    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->mContext:Landroid/content/Context;

    invoke-static {p2}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->e(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    move-result-object p2

    if-eqz p2, :cond_2

    invoke-virtual {p2, p3, p4}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->d(J)V

    .line 178
    :cond_2
    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cL:Ljava/util/List;

    invoke-interface {p2}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p2

    :goto_2
    invoke-interface {p2}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_3

    invoke-interface {p2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/google/research/reflection/c/c;

    .line 179
    invoke-virtual {p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->I()Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    move-result-object v1

    invoke-interface {v0, v1}, Lcom/google/research/reflection/c/c;->a(Lcom/google/research/reflection/signal/ReflectionEvent;)V

    goto :goto_2

    .line 181
    :cond_3
    iput-wide p3, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cQ:J

    return-void
.end method

.method private synthetic b(Landroid/service/notification/StatusBarNotification;)V
    .locals 0

    .line 65
    invoke-virtual/range {p0 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->onNotificationPostedHelper(Landroid/service/notification/StatusBarNotification;)V

    return-void
.end method

.method public static synthetic lambda$DJfaQHaP0-43iDKaKbOBJVymlqM(Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;Landroid/service/notification/StatusBarNotification;)V
    .locals 0

    invoke-direct/range {p0 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->a(Landroid/service/notification/StatusBarNotification;)V

    return-void
.end method

.method public static synthetic lambda$JV4C1Qy8cFKwxu4HvDhifAUXgn4(Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;Landroid/service/notification/StatusBarNotification;)V
    .locals 0

    invoke-direct/range {p0 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->b(Landroid/service/notification/StatusBarNotification;)V

    return-void
.end method


# virtual methods
.method public final A()V
    .locals 0

    return-void
.end method

.method public final a(Lcom/google/research/reflection/c/c;)Lcom/google/research/reflection/c/a;
    .locals 1

    .line 158
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cL:Ljava/util/List;

    invoke-interface {v0, p1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    return-object p0
.end method

.method public final h()V
    .locals 0

    .line 205
    invoke-static {}, Lcom/android/launcher3/notification/NotificationListener;->removeStatusBarNotificationsChangedListener()V

    return-void
.end method

.method public onNotificationPosted(Landroid/service/notification/StatusBarNotification;)V
    .locals 2

    .line 64
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->mContext:Landroid/content/Context;

    invoke-static {v0}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->getInstance(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;

    move-result-object v0

    new-instance v1, Lcom/google/android/apps/nexuslauncher/reflection/sensing/-$$Lambda$NotificationSensor$JV4C1Qy8cFKwxu4HvDhifAUXgn4;

    invoke-direct {v1, p0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/-$$Lambda$NotificationSensor$JV4C1Qy8cFKwxu4HvDhifAUXgn4;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;Landroid/service/notification/StatusBarNotification;)V

    .line 65
    invoke-virtual {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->postNotificationEvent(Ljava/lang/Runnable;)V

    return-void
.end method

.method declared-synchronized onNotificationPostedHelper(Landroid/service/notification/StatusBarNotification;)V
    .locals 4

    monitor-enter p0

    .line 70
    :try_start_0
    invoke-virtual/range {p1 .. p1}, Landroid/service/notification/StatusBarNotification;->getPostTime()J

    move-result-wide v0

    iget-wide v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->mTimeOfLastReload:J

    cmp-long v0, v0, v2

    if-gez v0, :cond_0

    .line 71
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->reloadPostedNotifications()V

    .line 74
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cO:Ljava/util/Set;

    invoke-virtual/range {p1 .. p1}, Landroid/service/notification/StatusBarNotification;->getKey()Ljava/lang/String;

    move-result-object v1

    invoke-interface {v0, v1}, Ljava/util/Set;->contains(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_1

    const/4 v0, 0x1

    .line 75
    invoke-virtual/range {p1 .. p1}, Landroid/service/notification/StatusBarNotification;->getPostTime()J

    move-result-wide v1

    invoke-direct {p0, p1, v0, v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->a(Landroid/service/notification/StatusBarNotification;ZJ)V

    .line 76
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cO:Ljava/util/Set;

    invoke-virtual/range {p1 .. p1}, Landroid/service/notification/StatusBarNotification;->getKey()Ljava/lang/String;

    move-result-object v1

    invoke-interface {v0, v1}, Ljava/util/Set;->add(Ljava/lang/Object;)Z

    .line 87
    :cond_1
    invoke-virtual/range {p1 .. p1}, Landroid/service/notification/StatusBarNotification;->getPostTime()J

    move-result-wide v0

    iget-wide v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->mTimeOfLastReload:J

    sub-long/2addr v0, v2

    sget-wide v2, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cM:J

    cmp-long p1, v0, v2

    if-gtz p1, :cond_2

    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cO:Ljava/util/Set;

    .line 88
    invoke-interface {p1}, Ljava/util/Set;->size()I

    move-result p1

    const/16 v0, 0x64

    if-le p1, v0, :cond_3

    .line 89
    :cond_2
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->reloadPostedNotifications()V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 91
    :cond_3
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 69
    monitor-exit p0

    throw p1
.end method

.method public onNotificationRemoved(Landroid/service/notification/StatusBarNotification;)V
    .locals 2

    .line 148
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->mContext:Landroid/content/Context;

    invoke-static {v0}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->getInstance(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;

    move-result-object v0

    new-instance v1, Lcom/google/android/apps/nexuslauncher/reflection/sensing/-$$Lambda$NotificationSensor$DJfaQHaP0-43iDKaKbOBJVymlqM;

    invoke-direct {v1, p0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/-$$Lambda$NotificationSensor$DJfaQHaP0-43iDKaKbOBJVymlqM;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;Landroid/service/notification/StatusBarNotification;)V

    .line 149
    invoke-virtual {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->postNotificationEvent(Ljava/lang/Runnable;)V

    return-void
.end method

.method declared-synchronized onNotificationRemovedHelper(Landroid/service/notification/StatusBarNotification;)V
    .locals 3

    monitor-enter p0

    const/4 v0, 0x0

    .line 153
    :try_start_0
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v1

    invoke-direct {p0, p1, v0, v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->a(Landroid/service/notification/StatusBarNotification;ZJ)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 154
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 152
    monitor-exit p0

    throw p1
.end method

.method declared-synchronized reloadPostedNotifications()V
    .locals 7

    monitor-enter p0

    .line 103
    :try_start_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cR:Lcom/android/launcher3/notification/NotificationListener;

    if-nez v0, :cond_0

    .line 104
    invoke-static {}, Lcom/android/launcher3/notification/NotificationListener;->getInstanceIfConnected()Lcom/android/launcher3/notification/NotificationListener;

    move-result-object v0

    goto :goto_0

    .line 107
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cR:Lcom/android/launcher3/notification/NotificationListener;

    :goto_0
    if-eqz v0, :cond_3

    .line 110
    invoke-virtual {v0}, Lcom/android/launcher3/notification/NotificationListener;->getActiveNotifications()[Landroid/service/notification/StatusBarNotification;

    move-result-object v0

    .line 111
    sget-object v1, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cN:Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor$SBNCompareByPostTime;

    invoke-static {v0, v1}, Ljava/util/Arrays;->sort([Ljava/lang/Object;Ljava/util/Comparator;)V

    .line 112
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cP:Ljava/util/Set;

    invoke-interface {v1}, Ljava/util/Set;->clear()V

    .line 113
    array-length v1, v0

    const/4 v2, 0x0

    :goto_1
    if-ge v2, v1, :cond_2

    aget-object v3, v0, v2

    .line 115
    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cO:Ljava/util/Set;

    invoke-virtual {v3}, Landroid/service/notification/StatusBarNotification;->getKey()Ljava/lang/String;

    move-result-object v5

    invoke-interface {v4, v5}, Ljava/util/Set;->contains(Ljava/lang/Object;)Z

    move-result v4

    if-nez v4, :cond_1

    .line 116
    invoke-virtual {v3}, Landroid/service/notification/StatusBarNotification;->getPostTime()J

    move-result-wide v4

    const/4 v6, 0x1

    invoke-direct {p0, v3, v6, v4, v5}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->a(Landroid/service/notification/StatusBarNotification;ZJ)V

    .line 118
    :cond_1
    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cP:Ljava/util/Set;

    invoke-virtual {v3}, Landroid/service/notification/StatusBarNotification;->getKey()Ljava/lang/String;

    move-result-object v3

    invoke-interface {v4, v3}, Ljava/util/Set;->add(Ljava/lang/Object;)Z

    add-int/lit8 v2, v2, 0x1

    goto :goto_1

    .line 120
    :cond_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cO:Ljava/util/Set;

    invoke-interface {v0}, Ljava/util/Set;->clear()V

    .line 121
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cO:Ljava/util/Set;

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cP:Ljava/util/Set;

    invoke-interface {v0, v1}, Ljava/util/Set;->addAll(Ljava/util/Collection;)Z

    .line 122
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->cP:Ljava/util/Set;

    invoke-interface {v0}, Ljava/util/Set;->clear()V

    .line 123
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->mTimeOfLastReload:J
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 132
    :cond_3
    monitor-exit p0

    return-void

    :catchall_0
    move-exception v0

    .line 102
    monitor-exit p0

    throw v0
.end method
