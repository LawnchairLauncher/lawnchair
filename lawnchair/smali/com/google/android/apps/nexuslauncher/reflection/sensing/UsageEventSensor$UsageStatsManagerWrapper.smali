.class public Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$UsageStatsManagerWrapper;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x4
    name = "UsageStatsManagerWrapper"
.end annotation


# instance fields
.field O:Landroid/app/usage/UsageStatsManager;

.field final synthetic de:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;


# direct methods
.method public constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;Landroid/app/usage/UsageStatsManager;)V
    .locals 0

    .line 332
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$UsageStatsManagerWrapper;->de:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 333
    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$UsageStatsManagerWrapper;->O:Landroid/app/usage/UsageStatsManager;

    return-void
.end method
