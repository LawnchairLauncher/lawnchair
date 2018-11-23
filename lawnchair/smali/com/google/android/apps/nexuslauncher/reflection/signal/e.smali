.class public Lcom/google/android/apps/nexuslauncher/reflection/signal/e;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/signal/c;


# instance fields
.field cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 15
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 16
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/e;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    return-void
.end method

.method public constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;)V
    .locals 0

    .line 11
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 12
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/e;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    return-void
.end method


# virtual methods
.method public final N()Ljava/lang/String;
    .locals 1

    .line 26
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/e;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    iget-object v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;->cp:Ljava/lang/String;

    return-object v0
.end method

.method public final getTime()J
    .locals 2

    .line 21
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/e;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    iget-wide v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;->time:J

    return-wide v0
.end method
