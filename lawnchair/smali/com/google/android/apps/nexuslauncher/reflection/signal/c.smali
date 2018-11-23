.class public Lcom/google/android/apps/nexuslauncher/reflection/signal/c;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/signal/b;


# instance fields
.field cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 19
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 20
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    return-void
.end method

.method public constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;)V
    .locals 0

    .line 15
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 16
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    return-void
.end method


# virtual methods
.method public final J()Lcom/google/research/reflection/signal/ReflectionPrivatePlace;
    .locals 2

    .line 25
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    iget-object v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    if-nez v0, :cond_0

    const/4 v0, 0x0

    return-object v0

    .line 28
    :cond_0
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/d;

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    iget-object v1, v1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    invoke-direct {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/d;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;)V

    return-object v0
.end method

.method public final K()Lcom/google/research/reflection/signal/c;
    .locals 2

    .line 34
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    iget-object v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    if-nez v0, :cond_0

    const/4 v0, 0x0

    return-object v0

    .line 37
    :cond_0
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/e;

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    iget-object v1, v1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    invoke-direct {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/e;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;)V

    return-object v0
.end method

.method public final L()Lcom/google/research/reflection/signal/a;
    .locals 2

    .line 42
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    iget-object v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->co:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    if-nez v0, :cond_0

    const/4 v0, 0x0

    return-object v0

    .line 45
    :cond_0
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/b;

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    iget-object v1, v1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->co:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    invoke-direct {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/b;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;)V

    return-object v0
.end method

.method public final a(Lcom/google/research/reflection/signal/ReflectionPrivatePlace;)Lcom/google/research/reflection/signal/b;
    .locals 1

    if-nez p1, :cond_0

    .line 61
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    const/4 v0, 0x0

    iput-object v0, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    goto :goto_0

    .line 63
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    check-cast p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/d;

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/d;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    iput-object p1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    :goto_0
    return-object p0
.end method

.method public final a(Lcom/google/research/reflection/signal/a;)Lcom/google/research/reflection/signal/b;
    .locals 1

    if-nez p1, :cond_0

    .line 51
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    const/4 v0, 0x0

    iput-object v0, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->co:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    goto :goto_0

    .line 53
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    check-cast p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/b;

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/b;->dg:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    iput-object p1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->co:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    :goto_0
    return-object p0
.end method

.method public final a(Lcom/google/research/reflection/signal/c;)Lcom/google/research/reflection/signal/b;
    .locals 1

    if-nez p1, :cond_0

    .line 71
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    const/4 v0, 0x0

    iput-object v0, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    goto :goto_0

    .line 73
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    check-cast p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/e;

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/e;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    iput-object p1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    :goto_0
    return-object p0
.end method
