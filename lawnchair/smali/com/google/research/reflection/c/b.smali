.class public Lcom/google/research/reflection/c/b;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/c/c;


# instance fields
.field private fG:Lcom/google/research/reflection/predictor/b;


# direct methods
.method public constructor <init>(Lcom/google/research/reflection/predictor/b;)V
    .locals 0

    .line 14
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 15
    invoke-virtual/range {p0 .. p1}, Lcom/google/research/reflection/c/b;->c(Lcom/google/research/reflection/predictor/b;)V

    return-void
.end method


# virtual methods
.method public final declared-synchronized a(Lcom/google/research/reflection/signal/ReflectionEvent;)V
    .locals 0

    monitor-enter p0

    .line 20
    :try_start_0
    invoke-virtual/range {p0 .. p1}, Lcom/google/research/reflection/c/b;->c(Lcom/google/research/reflection/signal/ReflectionEvent;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 21
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 19
    monitor-exit p0

    throw p1
.end method

.method public final declared-synchronized c(Lcom/google/research/reflection/predictor/b;)V
    .locals 0

    monitor-enter p0

    .line 37
    :try_start_0
    iput-object p1, p0, Lcom/google/research/reflection/c/b;->fG:Lcom/google/research/reflection/predictor/b;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 38
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 36
    monitor-exit p0

    throw p1
.end method

.method public final declared-synchronized c(Lcom/google/research/reflection/signal/ReflectionEvent;)V
    .locals 1

    monitor-enter p0

    .line 29
    :try_start_0
    iget-object v0, p0, Lcom/google/research/reflection/c/b;->fG:Lcom/google/research/reflection/predictor/b;

    invoke-virtual {v0, p1}, Lcom/google/research/reflection/predictor/b;->c(Lcom/google/research/reflection/signal/ReflectionEvent;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 30
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 28
    monitor-exit p0

    throw p1
.end method
