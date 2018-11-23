.class public Lcom/google/research/reflection/b/k;
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

    sput-wide v0, Lcom/google/research/reflection/b/k;->dE:J

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

    const/16 v0, 0x7d

    return v0
.end method

.method public final U()Lcom/google/research/reflection/b/f;
    .locals 1

    .line 55
    new-instance v0, Lcom/google/research/reflection/b/k;

    invoke-direct {v0}, Lcom/google/research/reflection/b/k;-><init>()V

    return-object v0
.end method

.method public final a(Lcom/google/research/reflection/a/a;Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/layers/e;
    .locals 6
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

    const/4 v0, 0x1

    const/16 v1, 0x7d

    invoke-direct {p1, v0, v1}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    .line 35
    invoke-interface/range {p2 .. p2}, Lcom/google/research/reflection/signal/ReflectionEvent;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    if-eqz v1, :cond_3

    invoke-interface/range {p2 .. p2}, Lcom/google/research/reflection/signal/ReflectionEvent;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/b;->K()Lcom/google/research/reflection/signal/c;

    move-result-object v1

    if-nez v1, :cond_0

    goto :goto_1

    .line 38
    :cond_0
    invoke-interface/range {p2 .. p2}, Lcom/google/research/reflection/signal/ReflectionEvent;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/b;->K()Lcom/google/research/reflection/signal/c;

    move-result-object v1

    if-eqz v1, :cond_1

    .line 39
    invoke-interface/range {p2 .. p2}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object p2

    invoke-interface {p2}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v2

    invoke-interface {v1}, Lcom/google/research/reflection/signal/c;->getTime()J

    move-result-wide v4

    sub-long/2addr v2, v4

    invoke-interface {v1}, Lcom/google/research/reflection/signal/c;->N()Ljava/lang/String;

    move-result-object p2

    invoke-virtual {p2}, Ljava/lang/String;->isEmpty()Z

    move-result p2

    if-nez p2, :cond_1

    const-wide/16 v4, 0x0

    cmp-long p2, v4, v2

    if-gtz p2, :cond_1

    sget-wide v4, Lcom/google/research/reflection/b/k;->dE:J

    cmp-long p2, v2, v4

    if-gtz p2, :cond_1

    goto :goto_0

    :cond_1
    const/4 v0, 0x0

    :goto_0
    if-eqz v0, :cond_2

    .line 40
    sget-object p2, Lcom/google/research/reflection/predictor/e;->fE:Ljava/util/Map;

    invoke-interface {v1}, Lcom/google/research/reflection/signal/c;->N()Ljava/lang/String;

    move-result-object v0

    invoke-interface {p2, v0}, Ljava/util/Map;->containsKey(Ljava/lang/Object;)Z

    move-result p2

    if-eqz p2, :cond_2

    .line 41
    sget-object p2, Lcom/google/research/reflection/predictor/e;->fE:Ljava/util/Map;

    invoke-interface {v1}, Lcom/google/research/reflection/signal/c;->N()Ljava/lang/String;

    move-result-object v0

    invoke-interface {p2, v0}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p2

    check-cast p2, Ljava/lang/Integer;

    invoke-virtual {p2}, Ljava/lang/Integer;->intValue()I

    move-result p2

    .line 42
    iget-object v0, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    const-wide/high16 v1, 0x3ff0000000000000L    # 1.0

    aput-wide v1, v0, p2

    :cond_2
    return-object p1

    :cond_3
    :goto_1
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
    new-instance v0, Lcom/google/research/reflection/b/k;

    invoke-direct {v0}, Lcom/google/research/reflection/b/k;-><init>()V

    return-object v0
.end method

.method public final getFeatureName()Ljava/lang/String;
    .locals 1

    const-string v0, "public_place"

    return-object v0
.end method
