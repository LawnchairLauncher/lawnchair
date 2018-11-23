.class public Lcom/google/research/reflection/b/i;
.super Lcom/google/research/reflection/b/f;
.source "SourceFile"


# static fields
.field public static final dE:J


# direct methods
.method static constructor <clinit>()V
    .locals 3

    .line 13
    sget-object v0, Ljava/util/concurrent/TimeUnit;->HOURS:Ljava/util/concurrent/TimeUnit;

    const-wide/16 v1, 0x1

    invoke-virtual {v0, v1, v2}, Ljava/util/concurrent/TimeUnit;->toMillis(J)J

    move-result-wide v0

    sput-wide v0, Lcom/google/research/reflection/b/i;->dE:J

    return-void
.end method

.method public constructor <init>()V
    .locals 0

    .line 11
    invoke-direct/range {p0 .. p0}, Lcom/google/research/reflection/b/f;-><init>()V

    return-void
.end method


# virtual methods
.method public final T()I
    .locals 1

    const/4 v0, 0x3

    return v0
.end method

.method public final U()Lcom/google/research/reflection/b/f;
    .locals 1

    .line 53
    new-instance v0, Lcom/google/research/reflection/b/i;

    invoke-direct {v0}, Lcom/google/research/reflection/b/i;-><init>()V

    return-object v0
.end method

.method public final a(Lcom/google/research/reflection/a/a;Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/layers/e;
    .locals 12
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lcom/google/research/reflection/a/a<",
            "Lcom/google/research/reflection/signal/ReflectionEvent;",
            ">;",
            "Lcom/google/research/reflection/signal/ReflectionEvent;",
            ")",
            "Lcom/google/research/reflection/layers/e;"
        }
    .end annotation

    .line 34
    new-instance p1, Lcom/google/research/reflection/layers/e;

    const/4 v0, 0x3

    const/4 v1, 0x1

    invoke-direct {p1, v1, v0}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    .line 35
    invoke-interface/range {p2 .. p2}, Lcom/google/research/reflection/signal/ReflectionEvent;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v2

    if-nez v2, :cond_0

    return-object p1

    .line 38
    :cond_0
    invoke-interface/range {p2 .. p2}, Lcom/google/research/reflection/signal/ReflectionEvent;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v2

    invoke-interface {v2}, Lcom/google/research/reflection/signal/b;->L()Lcom/google/research/reflection/signal/a;

    move-result-object v2

    const/4 v3, 0x0

    if-eqz v2, :cond_1

    .line 39
    invoke-interface/range {p2 .. p2}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object p2

    invoke-interface {p2}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v4

    invoke-interface {v2}, Lcom/google/research/reflection/signal/a;->getTime()J

    move-result-wide v6

    sub-long/2addr v4, v6

    invoke-interface {v2}, Lcom/google/research/reflection/signal/a;->getLatitude()D

    move-result-wide v6

    const-wide/16 v8, 0x0

    cmpl-double p2, v6, v8

    if-eqz p2, :cond_1

    invoke-interface {v2}, Lcom/google/research/reflection/signal/a;->getLongitude()D

    move-result-wide v6

    cmpl-double p2, v6, v8

    if-eqz p2, :cond_1

    const-wide/16 v6, 0x0

    cmp-long p2, v6, v4

    if-gtz p2, :cond_1

    sget-wide v6, Lcom/google/research/reflection/b/i;->dE:J

    cmp-long p2, v4, v6

    if-gtz p2, :cond_1

    const/4 p2, 0x1

    goto :goto_0

    :cond_1
    const/4 p2, 0x0

    :goto_0
    if-eqz p2, :cond_3

    .line 40
    invoke-interface {v2}, Lcom/google/research/reflection/signal/a;->getLatitude()D

    move-result-wide v4

    invoke-interface {v2}, Lcom/google/research/reflection/signal/a;->getLongitude()D

    move-result-wide v6

    invoke-static {v4, v5}, Ljava/lang/Math;->toRadians(D)D

    move-result-wide v4

    invoke-static {v6, v7}, Ljava/lang/Math;->toRadians(D)D

    move-result-wide v6

    invoke-static {v4, v5}, Ljava/lang/Math;->cos(D)D

    move-result-wide v8

    new-array p2, v0, [F

    invoke-static {v6, v7}, Ljava/lang/Math;->cos(D)D

    move-result-wide v10

    mul-double v10, v10, v8

    double-to-float v2, v10

    aput v2, p2, v3

    invoke-static {v6, v7}, Ljava/lang/Math;->sin(D)D

    move-result-wide v6

    mul-double v6, v6, v8

    double-to-float v2, v6

    aput v2, p2, v1

    const/4 v1, 0x2

    invoke-static {v4, v5}, Ljava/lang/Math;->sin(D)D

    move-result-wide v4

    double-to-float v2, v4

    aput v2, p2, v1

    .line 41
    iget-object v1, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    array-length v1, v1

    if-ne v1, v0, :cond_2

    :goto_1
    if-ge v3, v0, :cond_3

    iget-object v1, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    aget v2, p2, v3

    float-to-double v4, v2

    aput-wide v4, v1, v3

    add-int/lit8 v3, v3, 0x1

    goto :goto_1

    :cond_2
    new-instance p1, Ljava/lang/RuntimeException;

    invoke-direct {p1}, Ljava/lang/RuntimeException;-><init>()V

    throw p1

    :cond_3
    return-object p1
.end method

.method public synthetic clone()Ljava/lang/Object;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/CloneNotSupportedException;
        }
    .end annotation

    .line 11
    new-instance v0, Lcom/google/research/reflection/b/i;

    invoke-direct {v0}, Lcom/google/research/reflection/b/i;-><init>()V

    return-object v0
.end method

.method public final getFeatureName()Ljava/lang/String;
    .locals 1

    const-string v0, "lat_lng"

    return-object v0
.end method
