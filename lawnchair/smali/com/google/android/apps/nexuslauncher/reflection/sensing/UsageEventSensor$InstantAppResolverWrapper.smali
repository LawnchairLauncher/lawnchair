.class public Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x4
    name = "InstantAppResolverWrapper"
.end annotation


# instance fields
.field dd:Ljava/util/Set;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Set<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation
.end field

.field final synthetic de:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

.field mInstantAppResolver:Lcom/android/launcher3/util/InstantAppResolver;


# direct methods
.method public constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;Landroid/content/Context;)V
    .locals 0

    .line 306
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;->de:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 307
    invoke-static/range {p2 .. p2}, Lcom/android/launcher3/util/InstantAppResolver;->newInstance(Landroid/content/Context;)Lcom/android/launcher3/util/InstantAppResolver;

    move-result-object p1

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;->mInstantAppResolver:Lcom/android/launcher3/util/InstantAppResolver;

    .line 308
    new-instance p1, Ljava/util/HashSet;

    invoke-direct {p1}, Ljava/util/HashSet;-><init>()V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor$InstantAppResolverWrapper;->dd:Ljava/util/Set;

    return-void
.end method
