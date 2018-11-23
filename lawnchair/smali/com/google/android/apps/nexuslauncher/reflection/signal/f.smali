.class public Lcom/google/android/apps/nexuslauncher/reflection/signal/f;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/signal/d;


# instance fields
.field public dh:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 15
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 16
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->dh:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    return-void
.end method

.method public constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;)V
    .locals 0

    .line 11
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 12
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->dh:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    return-void
.end method


# virtual methods
.method public final O()J
    .locals 2

    .line 43
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->dh:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    iget-wide v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bs:J

    return-wide v0
.end method

.method public final P()J
    .locals 2

    .line 65
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->dh:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    iget-wide v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bu:J

    return-wide v0
.end method

.method public final e(J)Lcom/google/research/reflection/signal/d;
    .locals 1

    .line 26
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->dh:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    iput-wide p1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->timestamp:J

    return-object p0
.end method

.method public final f(J)Lcom/google/research/reflection/signal/d;
    .locals 1

    .line 37
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->dh:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    iput-wide p1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->br:J

    return-object p0
.end method

.method public final g()J
    .locals 2

    .line 32
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->dh:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    iget-wide v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->br:J

    return-wide v0
.end method

.method public final g(J)Lcom/google/research/reflection/signal/d;
    .locals 1

    .line 48
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->dh:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    iput-wide p1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bs:J

    return-object p0
.end method

.method public final getTimeZone()Ljava/lang/String;
    .locals 1

    .line 54
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->dh:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    iget-object v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bt:Ljava/lang/String;

    return-object v0
.end method

.method public final getTimestamp()J
    .locals 2

    .line 21
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->dh:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    iget-wide v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->timestamp:J

    return-wide v0
.end method

.method public final h(J)Lcom/google/research/reflection/signal/d;
    .locals 1

    .line 70
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->dh:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    iput-wide p1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bu:J

    return-object p0
.end method

.method public final i(Ljava/lang/String;)Lcom/google/research/reflection/signal/d;
    .locals 1

    .line 59
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->dh:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    iput-object p1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bt:Ljava/lang/String;

    return-object p0
.end method
