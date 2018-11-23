.class public Lcom/google/android/apps/nexuslauncher/b/c;
.super Ljava/lang/Object;
.source "SourceFile"


# static fields
.field static N:Lcom/google/android/apps/nexuslauncher/b/c;


# instance fields
.field final O:Landroid/app/usage/UsageStatsManager;


# direct methods
.method constructor <init>(Landroid/content/Context;)V
    .locals 1

    .line 35
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    const-string v0, "usagestats"

    .line 37
    invoke-virtual {p1, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Landroid/app/usage/UsageStatsManager;

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/b/c;->O:Landroid/app/usage/UsageStatsManager;

    return-void
.end method


# virtual methods
.method final a(Landroid/app/usage/UsageStats;JJ)Z
    .locals 2

    .line 90
    invoke-virtual/range {p1 .. p1}, Landroid/app/usage/UsageStats;->getLastTimeUsed()J

    move-result-wide v0

    cmp-long p2, v0, p2

    if-ltz p2, :cond_0

    invoke-virtual/range {p1 .. p1}, Landroid/app/usage/UsageStats;->getLastTimeUsed()J

    move-result-wide p1

    cmp-long p1, p1, p4

    if-gtz p1, :cond_0

    const/4 p1, 0x1

    return p1

    :cond_0
    const/4 p1, 0x0

    return p1
.end method
