.class public Lcom/google/research/reflection/b/h;
.super Lcom/google/research/reflection/b/f;
.source "SourceFile"


# direct methods
.method public constructor <init>()V
    .locals 0

    .line 11
    invoke-direct/range {p0 .. p0}, Lcom/google/research/reflection/b/f;-><init>()V

    return-void
.end method


# virtual methods
.method public final T()I
    .locals 1

    const/16 v0, 0x18

    return v0
.end method

.method public final U()Lcom/google/research/reflection/b/f;
    .locals 1

    .line 50
    new-instance v0, Lcom/google/research/reflection/b/h;

    invoke-direct {v0}, Lcom/google/research/reflection/b/h;-><init>()V

    return-object v0
.end method

.method protected final a(Lcom/google/research/reflection/a/a;Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/layers/e;
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

    .line 23
    new-instance p1, Lcom/google/research/reflection/layers/e;

    const/4 v0, 0x1

    const/16 v1, 0x18

    invoke-direct {p1, v0, v1}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    .line 24
    invoke-static/range {p2 .. p2}, Lcom/google/research/reflection/a/e;->e(Lcom/google/research/reflection/signal/ReflectionEvent;)Ljava/util/Calendar;

    move-result-object v1

    const/16 v2, 0xb

    invoke-virtual {v1, v2}, Ljava/util/Calendar;->get(I)I

    move-result v1

    .line 25
    iget-object v2, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    const-wide/high16 v3, 0x3ff0000000000000L    # 1.0

    aput-wide v3, v2, v1

    .line 26
    invoke-static/range {p2 .. p2}, Lcom/google/research/reflection/a/e;->e(Lcom/google/research/reflection/signal/ReflectionEvent;)Ljava/util/Calendar;

    move-result-object p2

    const/16 v2, 0xc

    invoke-virtual {p2, v2}, Ljava/util/Calendar;->get(I)I

    move-result p2

    const/16 v2, 0x17

    const/16 v5, 0x1e

    if-ge p2, v5, :cond_1

    add-int/lit8 p2, v1, -0x1

    if-gez p2, :cond_0

    const/16 p2, 0x17

    .line 32
    :cond_0
    iget-object v0, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    aput-wide v3, v0, p2

    goto :goto_0

    :cond_1
    if-le p2, v5, :cond_3

    add-int/2addr v1, v0

    if-le v1, v2, :cond_2

    const/4 v1, 0x0

    .line 38
    :cond_2
    iget-object p2, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    aput-wide v3, p2, v1

    :cond_3
    :goto_0
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
    new-instance v0, Lcom/google/research/reflection/b/h;

    invoke-direct {v0}, Lcom/google/research/reflection/b/h;-><init>()V

    return-object v0
.end method

.method public final getFeatureName()Ljava/lang/String;
    .locals 1

    const-string v0, "hour_of_day"

    return-object v0
.end method
