.class public Lcom/google/android/apps/nexuslauncher/reflection/a;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/android/apps/nexuslauncher/reflection/b;


# instance fields
.field private final T:J


# direct methods
.method public constructor <init>(Landroid/content/Context;)V
    .locals 2

    .line 35
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    const/4 v0, 0x1

    .line 36
    invoke-virtual {p0, p1, v0}, Lcom/google/android/apps/nexuslauncher/reflection/a;->initRecordedTime(Landroid/content/Context;I)J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/a;->T:J

    return-void
.end method


# virtual methods
.method public final g()J
    .locals 2

    .line 40
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/a;->T:J

    return-wide v0
.end method

.method protected getAbsoluteBootTime()J
    .locals 4

    .line 45
    invoke-static {}, Ljava/util/Calendar;->getInstance()Ljava/util/Calendar;

    move-result-object v0

    invoke-virtual {v0}, Ljava/util/Calendar;->getTimeInMillis()J

    move-result-wide v0

    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J

    move-result-wide v2

    sub-long/2addr v0, v2

    return-wide v0
.end method

.method protected initRecordedTime(Landroid/content/Context;I)J
    .locals 8

    .line 50
    new-instance v0, Landroid/content/Intent;

    const-string v1, "com.google.android.apps.nexuslauncher.reflection.ACTION_BOOT_CYCLE"

    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    const/high16 v1, 0x20000000

    .line 51
    invoke-static {p1, p2, v0, v1}, Landroid/app/PendingIntent;->getBroadcast(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;

    move-result-object v1

    .line 53
    new-instance v2, Landroid/util/MutableLong;

    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/a;->getAbsoluteBootTime()J

    move-result-wide v3

    invoke-direct {v2, v3, v4}, Landroid/util/MutableLong;-><init>(J)V

    const/4 v3, 0x1

    if-eqz v1, :cond_0

    .line 57
    :try_start_0
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    .line 58
    new-instance v4, Ljava/util/concurrent/CountDownLatch;

    invoke-direct {v4, v3}, Ljava/util/concurrent/CountDownLatch;-><init>(I)V

    .line 59
    new-instance v5, Lcom/google/android/apps/nexuslauncher/reflection/a$1;

    invoke-direct {v5, p0, v2, v4}, Lcom/google/android/apps/nexuslauncher/reflection/a$1;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/a;Landroid/util/MutableLong;Ljava/util/concurrent/CountDownLatch;)V

    new-instance v6, Landroid/os/Handler;

    .line 66
    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;

    move-result-object v7

    invoke-direct {v6, v7}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V

    .line 59
    invoke-virtual {v1, p2, v5, v6}, Landroid/app/PendingIntent;->send(ILandroid/app/PendingIntent$OnFinished;Landroid/os/Handler;)V

    const-wide/16 v5, 0x1

    .line 69
    sget-object v1, Ljava/util/concurrent/TimeUnit;->SECONDS:Ljava/util/concurrent/TimeUnit;

    invoke-virtual {v4, v5, v6, v1}, Ljava/util/concurrent/CountDownLatch;->await(JLjava/util/concurrent/TimeUnit;)Z

    .line 70
    iget-wide v4, v2, Landroid/util/MutableLong;->value:J
    :try_end_0
    .catch Landroid/app/PendingIntent$CanceledException; {:try_start_0 .. :try_end_0} :catch_0
    .catch Ljava/lang/InterruptedException; {:try_start_0 .. :try_end_0} :catch_0

    return-wide v4

    :catch_0
    :cond_0
    const-string v1, "time"

    .line 79
    iget-wide v4, v2, Landroid/util/MutableLong;->value:J

    invoke-virtual {v0, v1, v4, v5}, Landroid/content/Intent;->putExtra(Ljava/lang/String;J)Landroid/content/Intent;

    const/high16 v1, 0x8000000

    .line 80
    invoke-static {p1, p2, v0, v1}, Landroid/app/PendingIntent;->getBroadcast(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;

    move-result-object p2

    const-string v0, "alarm"

    .line 85
    invoke-virtual {p1, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Landroid/app/AlarmManager;

    const-wide v0, 0x7fffffffffffffffL

    .line 86
    invoke-virtual {p1, v3, v0, v1, p2}, Landroid/app/AlarmManager;->set(IJLandroid/app/PendingIntent;)V

    .line 88
    iget-wide p1, v2, Landroid/util/MutableLong;->value:J

    return-wide p1
.end method
