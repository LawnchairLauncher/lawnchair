.class public Lcom/google/research/reflection/b/c;
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

    const/4 v0, 0x7

    return v0
.end method

.method public final U()Lcom/google/research/reflection/b/f;
    .locals 1

    .line 36
    new-instance v0, Lcom/google/research/reflection/b/c;

    invoke-direct {v0}, Lcom/google/research/reflection/b/c;-><init>()V

    return-object v0
.end method

.method protected final a(Lcom/google/research/reflection/a/a;Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/layers/e;
    .locals 3
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

    const/4 v0, 0x7

    const/4 v1, 0x1

    invoke-direct {p1, v1, v0}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    .line 24
    invoke-static/range {p2 .. p2}, Lcom/google/research/reflection/a/e;->e(Lcom/google/research/reflection/signal/ReflectionEvent;)Ljava/util/Calendar;

    move-result-object p2

    invoke-virtual {p2, v0}, Ljava/util/Calendar;->get(I)I

    move-result p2

    sub-int/2addr p2, v1

    .line 25
    iget-object v0, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    const-wide/high16 v1, 0x3ff0000000000000L    # 1.0

    aput-wide v1, v0, p2

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
    new-instance v0, Lcom/google/research/reflection/b/c;

    invoke-direct {v0}, Lcom/google/research/reflection/b/c;-><init>()V

    return-object v0
.end method

.method public final getFeatureName()Ljava/lang/String;
    .locals 1

    const-string v0, "day_of_week"

    return-object v0
.end method
