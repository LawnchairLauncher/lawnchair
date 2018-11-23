.class public Lcom/google/android/apps/nexuslauncher/reflection/signal/b;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/signal/a;


# instance fields
.field dg:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 10
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 11
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/b;->dg:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    return-void
.end method

.method public constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;)V
    .locals 0

    .line 14
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 15
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/b;->dg:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    return-void
.end method


# virtual methods
.method public final getLatitude()D
    .locals 2

    .line 25
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/b;->dg:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    iget-wide v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;->bB:D

    return-wide v0
.end method

.method public final getLongitude()D
    .locals 2

    .line 30
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/b;->dg:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    iget-wide v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;->bC:D

    return-wide v0
.end method

.method public final getTime()J
    .locals 2

    .line 20
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/b;->dg:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    iget-wide v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;->time:J

    return-wide v0
.end method
