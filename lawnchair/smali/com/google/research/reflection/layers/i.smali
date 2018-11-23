.class public Lcom/google/research/reflection/layers/i;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/research/reflection/layers/i$a;
    }
.end annotation


# static fields
.field static fi:Lcom/google/research/reflection/layers/i; = null

.field public static fj:Z = false


# instance fields
.field private ff:I

.field private fg:Ljava/util/concurrent/ExecutorService;

.field private fh:Z

.field private fk:I


# direct methods
.method static constructor <clinit>()V
    .locals 0

    return-void
.end method

.method private constructor <init>()V
    .locals 1

    .line 24
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    const/4 v0, 0x0

    .line 17
    iput-boolean v0, p0, Lcom/google/research/reflection/layers/i;->fh:Z

    .line 25
    invoke-static {}, Ljava/lang/Runtime;->getRuntime()Ljava/lang/Runtime;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/Runtime;->availableProcessors()I

    move-result v0

    .line 26
    div-int/lit8 v0, v0, 0x2

    iput v0, p0, Lcom/google/research/reflection/layers/i;->ff:I

    .line 27
    iget v0, p0, Lcom/google/research/reflection/layers/i;->ff:I

    invoke-static {v0}, Ljava/util/concurrent/Executors;->newFixedThreadPool(I)Ljava/util/concurrent/ExecutorService;

    move-result-object v0

    iput-object v0, p0, Lcom/google/research/reflection/layers/i;->fg:Ljava/util/concurrent/ExecutorService;

    return-void
.end method

.method public static ak()Lcom/google/research/reflection/layers/i;
    .locals 1

    .line 37
    sget-object v0, Lcom/google/research/reflection/layers/i;->fi:Lcom/google/research/reflection/layers/i;

    if-nez v0, :cond_0

    .line 38
    new-instance v0, Lcom/google/research/reflection/layers/i;

    invoke-direct {v0}, Lcom/google/research/reflection/layers/i;-><init>()V

    sput-object v0, Lcom/google/research/reflection/layers/i;->fi:Lcom/google/research/reflection/layers/i;

    .line 40
    :cond_0
    sget-object v0, Lcom/google/research/reflection/layers/i;->fi:Lcom/google/research/reflection/layers/i;

    return-object v0
.end method

.method private declared-synchronized b(ILcom/google/research/reflection/layers/h;)V
    .locals 7

    monitor-enter p0

    const/4 v0, 0x1

    .line 79
    :try_start_0
    iput-boolean v0, p0, Lcom/google/research/reflection/layers/i;->fh:Z

    .line 80
    new-instance v1, Ljava/util/concurrent/ExecutorCompletionService;

    iget-object v2, p0, Lcom/google/research/reflection/layers/i;->fg:Ljava/util/concurrent/ExecutorService;

    invoke-direct {v1, v2}, Ljava/util/concurrent/ExecutorCompletionService;-><init>(Ljava/util/concurrent/Executor;)V

    .line 83
    iget v2, p0, Lcom/google/research/reflection/layers/i;->ff:I

    if-ge p1, v2, :cond_0

    goto :goto_0

    :cond_0
    int-to-float v0, p1

    .line 86
    iget v2, p0, Lcom/google/research/reflection/layers/i;->ff:I

    int-to-float v2, v2

    div-float/2addr v0, v2

    float-to-double v2, v0

    invoke-static {v2, v3}, Ljava/lang/Math;->ceil(D)D

    move-result-wide v2

    double-to-int v0, v2

    .line 88
    :goto_0
    iget v2, p0, Lcom/google/research/reflection/layers/i;->ff:I

    invoke-static {v2, p1}, Ljava/lang/Math;->min(II)I

    move-result v2

    iput v2, p0, Lcom/google/research/reflection/layers/i;->fk:I

    const/4 v2, 0x0

    const/4 v3, 0x0

    .line 89
    :goto_1
    iget v4, p0, Lcom/google/research/reflection/layers/i;->fk:I

    if-ge v3, v4, :cond_1

    .line 91
    new-instance v4, Lcom/google/research/reflection/layers/i$a;

    invoke-direct {v4, v3, v0, p1, p2}, Lcom/google/research/reflection/layers/i$a;-><init>(IIILcom/google/research/reflection/layers/h;)V

    .line 92
    invoke-virtual {v1, v4}, Ljava/util/concurrent/ExecutorCompletionService;->submit(Ljava/util/concurrent/Callable;)Ljava/util/concurrent/Future;

    add-int/lit8 v3, v3, 0x1

    goto :goto_1

    :cond_1
    const/4 p2, 0x0

    .line 94
    :goto_2
    iget v0, p0, Lcom/google/research/reflection/layers/i;->fk:I
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    if-ge p2, v0, :cond_2

    .line 96
    :try_start_1
    invoke-virtual {v1}, Ljava/util/concurrent/ExecutorCompletionService;->take()Ljava/util/concurrent/Future;

    move-result-object v0

    invoke-interface {v0}, Ljava/util/concurrent/Future;->get()Ljava/lang/Object;
    :try_end_1
    .catch Ljava/lang/InterruptedException; {:try_start_1 .. :try_end_1} :catch_1
    .catch Ljava/util/concurrent/ExecutionException; {:try_start_1 .. :try_end_1} :catch_0
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    goto :goto_3

    :catch_0
    move-exception v0

    .line 100
    :try_start_2
    sget-object v3, Ljava/lang/System;->err:Ljava/io/PrintStream;

    iget v4, p0, Lcom/google/research/reflection/layers/i;->fk:I

    const/16 v5, 0x30

    new-instance v6, Ljava/lang/StringBuilder;

    invoke-direct {v6, v5}, Ljava/lang/StringBuilder;-><init>(I)V

    const-string v5, "threadCount: "

    invoke-virtual {v6, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v6, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v4, " for length: "

    invoke-virtual {v6, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v6, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v6}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v3, v4}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V

    .line 101
    invoke-virtual {v0}, Ljava/util/concurrent/ExecutionException;->printStackTrace()V

    goto :goto_3

    :catch_1
    move-exception v0

    .line 97
    invoke-virtual {v0}, Ljava/lang/InterruptedException;->printStackTrace()V

    :goto_3
    add-int/lit8 p2, p2, 0x1

    goto :goto_2

    .line 104
    :cond_2
    iput-boolean v2, p0, Lcom/google/research/reflection/layers/i;->fh:Z
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    .line 105
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 78
    monitor-exit p0

    throw p1
.end method


# virtual methods
.method public final a(ILcom/google/research/reflection/layers/h;)V
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    .line 69
    sget-boolean v0, Lcom/google/research/reflection/layers/i;->fj:Z

    if-eqz v0, :cond_1

    iget-boolean v0, p0, Lcom/google/research/reflection/layers/i;->fh:Z

    if-nez v0, :cond_1

    const/4 v0, 0x1

    if-ne p1, v0, :cond_0

    goto :goto_0

    .line 74
    :cond_0
    invoke-direct/range {p0 .. p2}, Lcom/google/research/reflection/layers/i;->b(ILcom/google/research/reflection/layers/h;)V

    return-void

    :cond_1
    :goto_0
    const/4 v0, 0x0

    :goto_1
    if-ge v0, p1, :cond_2

    .line 71
    invoke-interface {p2, v0}, Lcom/google/research/reflection/layers/h;->b(I)Ljava/lang/Boolean;

    add-int/lit8 v0, v0, 0x1

    goto :goto_1

    :cond_2
    return-void
.end method
