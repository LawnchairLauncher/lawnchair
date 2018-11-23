.class public Lcom/google/research/reflection/c/d;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/c/c;


# instance fields
.field private final fU:Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/List<",
            "Lcom/google/research/reflection/c/a;",
            ">;"
        }
    .end annotation
.end field

.field private fV:Lcom/google/research/reflection/signal/b;


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 18
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 19
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/google/research/reflection/c/d;->fU:Ljava/util/List;

    return-void
.end method


# virtual methods
.method public final declared-synchronized a(Lcom/google/research/reflection/signal/ReflectionEvent;)V
    .locals 4

    monitor-enter p0

    .line 39
    :try_start_0
    iget-object v0, p0, Lcom/google/research/reflection/c/d;->fV:Lcom/google/research/reflection/signal/b;

    if-nez v0, :cond_1

    .line 40
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->G()[B

    move-result-object v0

    if-eqz v0, :cond_0

    array-length v1, v0

    invoke-interface {p1, v0, v1}, Lcom/google/research/reflection/signal/ReflectionEvent;->a([BI)Lcom/google/research/reflection/signal/ReflectionEvent;

    move-result-object p1

    goto :goto_0

    :cond_0
    const/4 p1, 0x0

    :goto_0
    invoke-interface {p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->E()Lcom/google/research/reflection/signal/b;

    move-result-object p1

    iput-object p1, p0, Lcom/google/research/reflection/c/d;->fV:Lcom/google/research/reflection/signal/b;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    monitor-exit p0

    return-void

    .line 42
    :cond_1
    :try_start_1
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->E()Lcom/google/research/reflection/signal/b;

    move-result-object p1

    .line 43
    iget-object v0, p0, Lcom/google/research/reflection/c/d;->fV:Lcom/google/research/reflection/signal/b;

    invoke-interface {v0}, Lcom/google/research/reflection/signal/b;->J()Lcom/google/research/reflection/signal/ReflectionPrivatePlace;

    move-result-object v0

    if-eqz v0, :cond_2

    .line 44
    invoke-interface {p1}, Lcom/google/research/reflection/signal/b;->J()Lcom/google/research/reflection/signal/ReflectionPrivatePlace;

    move-result-object v0

    if-eqz v0, :cond_3

    iget-object v0, p0, Lcom/google/research/reflection/c/d;->fV:Lcom/google/research/reflection/signal/b;

    .line 45
    invoke-interface {v0}, Lcom/google/research/reflection/signal/b;->J()Lcom/google/research/reflection/signal/ReflectionPrivatePlace;

    move-result-object v0

    invoke-interface {v0}, Lcom/google/research/reflection/signal/ReflectionPrivatePlace;->getTime()J

    move-result-wide v0

    .line 46
    invoke-interface {p1}, Lcom/google/research/reflection/signal/b;->J()Lcom/google/research/reflection/signal/ReflectionPrivatePlace;

    move-result-object v2

    invoke-interface {v2}, Lcom/google/research/reflection/signal/ReflectionPrivatePlace;->getTime()J

    move-result-wide v2

    cmp-long v0, v0, v2

    if-gez v0, :cond_3

    .line 47
    :cond_2
    iget-object v0, p0, Lcom/google/research/reflection/c/d;->fV:Lcom/google/research/reflection/signal/b;

    invoke-interface {p1}, Lcom/google/research/reflection/signal/b;->J()Lcom/google/research/reflection/signal/ReflectionPrivatePlace;

    move-result-object v1

    invoke-interface {v0, v1}, Lcom/google/research/reflection/signal/b;->a(Lcom/google/research/reflection/signal/ReflectionPrivatePlace;)Lcom/google/research/reflection/signal/b;

    .line 49
    :cond_3
    iget-object v0, p0, Lcom/google/research/reflection/c/d;->fV:Lcom/google/research/reflection/signal/b;

    invoke-interface {v0}, Lcom/google/research/reflection/signal/b;->K()Lcom/google/research/reflection/signal/c;

    move-result-object v0

    if-eqz v0, :cond_4

    .line 50
    invoke-interface {p1}, Lcom/google/research/reflection/signal/b;->K()Lcom/google/research/reflection/signal/c;

    move-result-object v0

    if-eqz v0, :cond_5

    iget-object v0, p0, Lcom/google/research/reflection/c/d;->fV:Lcom/google/research/reflection/signal/b;

    .line 51
    invoke-interface {v0}, Lcom/google/research/reflection/signal/b;->K()Lcom/google/research/reflection/signal/c;

    move-result-object v0

    invoke-interface {v0}, Lcom/google/research/reflection/signal/c;->getTime()J

    move-result-wide v0

    invoke-interface {p1}, Lcom/google/research/reflection/signal/b;->K()Lcom/google/research/reflection/signal/c;

    move-result-object v2

    invoke-interface {v2}, Lcom/google/research/reflection/signal/c;->getTime()J

    move-result-wide v2

    cmp-long v0, v0, v2

    if-gez v0, :cond_5

    .line 52
    :cond_4
    iget-object v0, p0, Lcom/google/research/reflection/c/d;->fV:Lcom/google/research/reflection/signal/b;

    invoke-interface {p1}, Lcom/google/research/reflection/signal/b;->K()Lcom/google/research/reflection/signal/c;

    move-result-object v1

    invoke-interface {v0, v1}, Lcom/google/research/reflection/signal/b;->a(Lcom/google/research/reflection/signal/c;)Lcom/google/research/reflection/signal/b;

    .line 54
    :cond_5
    iget-object v0, p0, Lcom/google/research/reflection/c/d;->fV:Lcom/google/research/reflection/signal/b;

    invoke-interface {v0}, Lcom/google/research/reflection/signal/b;->L()Lcom/google/research/reflection/signal/a;

    move-result-object v0

    if-eqz v0, :cond_6

    .line 55
    invoke-interface {p1}, Lcom/google/research/reflection/signal/b;->L()Lcom/google/research/reflection/signal/a;

    move-result-object v0

    if-eqz v0, :cond_7

    iget-object v0, p0, Lcom/google/research/reflection/c/d;->fV:Lcom/google/research/reflection/signal/b;

    .line 56
    invoke-interface {v0}, Lcom/google/research/reflection/signal/b;->L()Lcom/google/research/reflection/signal/a;

    move-result-object v0

    invoke-interface {v0}, Lcom/google/research/reflection/signal/a;->getTime()J

    move-result-wide v0

    invoke-interface {p1}, Lcom/google/research/reflection/signal/b;->L()Lcom/google/research/reflection/signal/a;

    move-result-object v2

    invoke-interface {v2}, Lcom/google/research/reflection/signal/a;->getTime()J

    move-result-wide v2

    cmp-long v0, v0, v2

    if-gez v0, :cond_7

    .line 57
    :cond_6
    iget-object v0, p0, Lcom/google/research/reflection/c/d;->fV:Lcom/google/research/reflection/signal/b;

    invoke-interface {p1}, Lcom/google/research/reflection/signal/b;->L()Lcom/google/research/reflection/signal/a;

    move-result-object p1

    invoke-interface {v0, p1}, Lcom/google/research/reflection/signal/b;->a(Lcom/google/research/reflection/signal/a;)Lcom/google/research/reflection/signal/b;
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 60
    :cond_7
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 38
    monitor-exit p0

    throw p1
.end method

.method public final au()Lcom/google/research/reflection/signal/b;
    .locals 2

    .line 77
    iget-object v0, p0, Lcom/google/research/reflection/c/d;->fU:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_0

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/c/a;

    invoke-interface {v1}, Lcom/google/research/reflection/c/a;->A()V

    goto :goto_0

    .line 78
    :cond_0
    iget-object v0, p0, Lcom/google/research/reflection/c/d;->fV:Lcom/google/research/reflection/signal/b;

    return-object v0
.end method
