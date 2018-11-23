.class public Lcom/google/research/reflection/layers/b;
.super Lcom/google/research/reflection/layers/c;
.source "SourceFile"


# instance fields
.field eg:[Lcom/google/research/reflection/layers/a;

.field eh:I


# direct methods
.method constructor <init>()V
    .locals 0

    .line 16
    invoke-direct/range {p0 .. p0}, Lcom/google/research/reflection/layers/c;-><init>()V

    return-void
.end method


# virtual methods
.method public final Z()Lcom/google/research/reflection/layers/c;
    .locals 1

    const/4 v0, 0x0

    return-object v0
.end method

.method public final a(ZLcom/google/research/reflection/layers/f;[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;
    .locals 8
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(Z",
            "Lcom/google/research/reflection/layers/f;",
            "[",
            "Ljava/util/ArrayList<",
            "Lcom/google/research/reflection/a/d;",
            ">;",
            "Lcom/google/research/reflection/layers/e;",
            ")",
            "Lcom/google/research/reflection/layers/e;"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    const/4 p1, 0x1

    if-eqz p3, :cond_0

    .line 90
    iput-boolean p1, p0, Lcom/google/research/reflection/layers/b;->ev:Z

    .line 91
    iget-object p2, p0, Lcom/google/research/reflection/layers/b;->er:Lcom/google/research/reflection/a/a;

    invoke-virtual {p2, p3}, Lcom/google/research/reflection/a/a;->a(Ljava/lang/Object;)Ljava/lang/Object;

    goto :goto_0

    :cond_0
    const/4 p2, 0x0

    .line 93
    iput-boolean p2, p0, Lcom/google/research/reflection/layers/b;->ev:Z

    .line 94
    iget-object p2, p0, Lcom/google/research/reflection/layers/b;->eq:Lcom/google/research/reflection/a/a;

    invoke-virtual {p2, p4}, Lcom/google/research/reflection/a/a;->a(Ljava/lang/Object;)Ljava/lang/Object;

    .line 97
    :goto_0
    iget-object p2, p0, Lcom/google/research/reflection/layers/b;->ep:Lcom/google/research/reflection/a/a;

    iget-object p2, p2, Lcom/google/research/reflection/a/a;->dl:[Ljava/lang/Object;

    array-length p2, p2

    if-le p2, p1, :cond_1

    .line 98
    iget-object p1, p0, Lcom/google/research/reflection/layers/b;->ep:Lcom/google/research/reflection/a/a;

    invoke-virtual {p1}, Lcom/google/research/reflection/a/a;->getLast()Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lcom/google/research/reflection/layers/e;

    :goto_1
    move-object v4, p1

    goto :goto_2

    :cond_1
    const/4 p1, 0x0

    goto :goto_1

    .line 102
    :goto_2
    iget-object p1, p0, Lcom/google/research/reflection/layers/b;->ep:Lcom/google/research/reflection/a/a;

    invoke-virtual {p1}, Lcom/google/research/reflection/a/a;->Q()Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lcom/google/research/reflection/layers/e;

    if-nez p1, :cond_2

    .line 104
    new-instance p1, Lcom/google/research/reflection/layers/e;

    iget p2, p0, Lcom/google/research/reflection/layers/b;->dF:I

    iget v0, p0, Lcom/google/research/reflection/layers/b;->ew:I

    invoke-direct {p1, p2, v0}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    .line 106
    :cond_2
    iget-object p2, p0, Lcom/google/research/reflection/layers/b;->ep:Lcom/google/research/reflection/a/a;

    invoke-virtual {p2, p1}, Lcom/google/research/reflection/a/a;->a(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lcom/google/research/reflection/layers/e;

    .line 107
    invoke-static {}, Lcom/google/research/reflection/layers/i;->ak()Lcom/google/research/reflection/layers/i;

    move-result-object p2

    iget-object v0, p0, Lcom/google/research/reflection/layers/b;->eg:[Lcom/google/research/reflection/layers/a;

    array-length v6, v0

    new-instance v7, Lcom/google/research/reflection/layers/b$2;

    move-object v0, v7

    move-object v1, p0

    move-object v2, p3

    move-object v3, p4

    move-object v5, p1

    invoke-direct/range {v0 .. v5}, Lcom/google/research/reflection/layers/b$2;-><init>(Lcom/google/research/reflection/layers/b;[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V

    invoke-virtual {p2, v6, v7}, Lcom/google/research/reflection/layers/i;->a(ILcom/google/research/reflection/layers/h;)V

    return-object p1
.end method

.method public final a(Lcom/google/research/reflection/layers/f;ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
    .locals 6
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    .line 39
    iget-object p1, p0, Lcom/google/research/reflection/layers/b;->es:Lcom/google/research/reflection/layers/e;

    const/4 p5, 0x0

    invoke-static {p3, p4, p1, p5}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Z)Lcom/google/research/reflection/layers/e;

    .line 41
    iget-object p1, p0, Lcom/google/research/reflection/layers/b;->eq:Lcom/google/research/reflection/a/a;

    invoke-virtual {p1, p2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object p1

    move-object v4, p1

    check-cast v4, Lcom/google/research/reflection/layers/e;

    .line 42
    iget-object p1, p0, Lcom/google/research/reflection/layers/b;->er:Lcom/google/research/reflection/a/a;

    invoke-virtual {p1, p2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object p1

    move-object v3, p1

    check-cast v3, [Ljava/util/ArrayList;

    .line 43
    iget-object p1, p0, Lcom/google/research/reflection/layers/b;->ep:Lcom/google/research/reflection/a/a;

    add-int/lit8 p3, p2, -0x1

    invoke-virtual {p1, p3}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object p1

    move-object v5, p1

    check-cast v5, Lcom/google/research/reflection/layers/e;

    .line 44
    invoke-static {}, Lcom/google/research/reflection/layers/i;->ak()Lcom/google/research/reflection/layers/i;

    move-result-object p1

    iget-object p3, p0, Lcom/google/research/reflection/layers/b;->eg:[Lcom/google/research/reflection/layers/a;

    array-length p3, p3

    new-instance p4, Lcom/google/research/reflection/layers/b$1;

    move-object v0, p4

    move-object v1, p0

    move v2, p2

    invoke-direct/range {v0 .. v5}, Lcom/google/research/reflection/layers/b$1;-><init>(Lcom/google/research/reflection/layers/b;I[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V

    invoke-virtual {p1, p3, p4}, Lcom/google/research/reflection/layers/i;->a(ILcom/google/research/reflection/layers/h;)V

    .line 59
    iget-object p1, p0, Lcom/google/research/reflection/layers/b;->et:Lcom/google/research/reflection/layers/e;

    iget-object p1, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    const-wide/16 p2, 0x0

    invoke-static {p1, p2, p3}, Ljava/util/Arrays;->fill([DD)V

    const/4 p1, 0x0

    .line 60
    :goto_0
    iget-object p4, p0, Lcom/google/research/reflection/layers/b;->eg:[Lcom/google/research/reflection/layers/a;

    array-length p4, p4

    if-ge p1, p4, :cond_0

    .line 61
    iget-object p4, p0, Lcom/google/research/reflection/layers/b;->et:Lcom/google/research/reflection/layers/e;

    iget-object v0, p0, Lcom/google/research/reflection/layers/b;->eg:[Lcom/google/research/reflection/layers/a;

    aget-object v0, v0, p1

    iget-object v0, v0, Lcom/google/research/reflection/layers/a;->ee:Lcom/google/research/reflection/layers/e;

    invoke-virtual {p4, v0}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;

    add-int/lit8 p1, p1, 0x1

    goto :goto_0

    .line 63
    :cond_0
    iget-object p1, p0, Lcom/google/research/reflection/layers/b;->eu:Lcom/google/research/reflection/layers/e;

    iget-object p1, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {p1, p2, p3}, Ljava/util/Arrays;->fill([DD)V

    .line 64
    :goto_1
    iget-object p1, p0, Lcom/google/research/reflection/layers/b;->eg:[Lcom/google/research/reflection/layers/a;

    array-length p1, p1

    if-ge p5, p1, :cond_1

    .line 65
    iget-object p1, p0, Lcom/google/research/reflection/layers/b;->eu:Lcom/google/research/reflection/layers/e;

    iget-object p2, p0, Lcom/google/research/reflection/layers/b;->eg:[Lcom/google/research/reflection/layers/a;

    aget-object p2, p2, p5

    iget-object p2, p2, Lcom/google/research/reflection/layers/a;->ef:Lcom/google/research/reflection/layers/e;

    invoke-virtual {p1, p2}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;

    add-int/lit8 p5, p5, 0x1

    goto :goto_1

    :cond_1
    return-void
.end method

.method final aa()V
    .locals 7

    .line 71
    invoke-super/range {p0 .. p0}, Lcom/google/research/reflection/layers/c;->aa()V

    .line 72
    iget-object v0, p0, Lcom/google/research/reflection/layers/b;->eg:[Lcom/google/research/reflection/layers/a;

    array-length v1, v0

    const/4 v2, 0x0

    :goto_0
    if-ge v2, v1, :cond_0

    aget-object v3, v0, v2

    const/4 v4, 0x5

    .line 73
    new-array v4, v4, [Lcom/google/research/reflection/layers/e;

    iput-object v4, v3, Lcom/google/research/reflection/layers/a;->dP:[Lcom/google/research/reflection/layers/e;

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->ed:Lcom/google/research/reflection/layers/e;

    iget-object v4, v4, Lcom/google/research/reflection/layers/e;->eR:[D

    const-wide/16 v5, 0x0

    invoke-static {v4, v5, v6}, Ljava/util/Arrays;->fill([DD)V

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->eb:Lcom/google/research/reflection/layers/e;

    iget-object v4, v4, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {v4, v5, v6}, Ljava/util/Arrays;->fill([DD)V

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->ec:Lcom/google/research/reflection/layers/e;

    iget-object v4, v4, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {v4, v5, v6}, Ljava/util/Arrays;->fill([DD)V

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->dZ:Lcom/google/research/reflection/layers/e;

    iget-object v4, v4, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {v4, v5, v6}, Ljava/util/Arrays;->fill([DD)V

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->dY:Lcom/google/research/reflection/layers/e;

    iget-object v4, v4, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {v4, v5, v6}, Ljava/util/Arrays;->fill([DD)V

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->dX:Lcom/google/research/reflection/layers/e;

    iget-object v4, v4, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {v4, v5, v6}, Ljava/util/Arrays;->fill([DD)V

    iget-object v3, v3, Lcom/google/research/reflection/layers/a;->ea:Lcom/google/research/reflection/layers/e;

    iget-object v3, v3, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {v3, v5, v6}, Ljava/util/Arrays;->fill([DD)V

    add-int/lit8 v2, v2, 0x1

    goto :goto_0

    :cond_0
    return-void
.end method

.method public final ab()V
    .locals 5

    .line 79
    invoke-super/range {p0 .. p0}, Lcom/google/research/reflection/layers/c;->ab()V

    .line 80
    iget-object v0, p0, Lcom/google/research/reflection/layers/b;->eg:[Lcom/google/research/reflection/layers/a;

    array-length v1, v0

    const/4 v2, 0x0

    :goto_0
    if-ge v2, v1, :cond_0

    aget-object v3, v0, v2

    .line 81
    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->dK:Lcom/google/research/reflection/a/a;

    invoke-virtual {v4}, Lcom/google/research/reflection/a/a;->clear()V

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->dJ:Lcom/google/research/reflection/a/a;

    invoke-virtual {v4}, Lcom/google/research/reflection/a/a;->clear()V

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->dL:Lcom/google/research/reflection/a/a;

    invoke-virtual {v4}, Lcom/google/research/reflection/a/a;->clear()V

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->dM:Lcom/google/research/reflection/a/a;

    invoke-virtual {v4}, Lcom/google/research/reflection/a/a;->clear()V

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->dN:Lcom/google/research/reflection/a/a;

    invoke-virtual {v4}, Lcom/google/research/reflection/a/a;->clear()V

    iget-object v3, v3, Lcom/google/research/reflection/layers/a;->dO:Lcom/google/research/reflection/a/a;

    invoke-virtual {v3}, Lcom/google/research/reflection/a/a;->clear()V

    add-int/lit8 v2, v2, 0x1

    goto :goto_0

    :cond_0
    return-void
.end method

.method public bridge synthetic clone()Ljava/lang/Object;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/CloneNotSupportedException;
        }
    .end annotation

    const/4 v0, 0x0

    return-object v0
.end method

.method public final getName()Ljava/lang/String;
    .locals 1

    const-string v0, "LSTMLayer"

    return-object v0
.end method

.method public final update()V
    .locals 8
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    .line 126
    iget-object v0, p0, Lcom/google/research/reflection/layers/b;->eg:[Lcom/google/research/reflection/layers/a;

    array-length v1, v0

    const/4 v2, 0x0

    :goto_0
    if-ge v2, v1, :cond_0

    aget-object v3, v0, v2

    .line 127
    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->dQ:Lcom/google/research/reflection/layers/e;

    iget-object v5, v3, Lcom/google/research/reflection/layers/a;->dX:Lcom/google/research/reflection/layers/e;

    sget-wide v6, Lcom/google/research/reflection/layers/c;->eo:D

    neg-double v6, v6

    invoke-virtual {v5, v6, v7}, Lcom/google/research/reflection/layers/e;->a(D)Lcom/google/research/reflection/layers/e;

    move-result-object v5

    invoke-virtual {v4, v5}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->dR:Lcom/google/research/reflection/layers/e;

    iget-object v5, v3, Lcom/google/research/reflection/layers/a;->dY:Lcom/google/research/reflection/layers/e;

    sget-wide v6, Lcom/google/research/reflection/layers/c;->eo:D

    neg-double v6, v6

    invoke-virtual {v5, v6, v7}, Lcom/google/research/reflection/layers/e;->a(D)Lcom/google/research/reflection/layers/e;

    move-result-object v5

    invoke-virtual {v4, v5}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->dS:Lcom/google/research/reflection/layers/e;

    iget-object v5, v3, Lcom/google/research/reflection/layers/a;->dZ:Lcom/google/research/reflection/layers/e;

    sget-wide v6, Lcom/google/research/reflection/layers/c;->eo:D

    neg-double v6, v6

    invoke-virtual {v5, v6, v7}, Lcom/google/research/reflection/layers/e;->a(D)Lcom/google/research/reflection/layers/e;

    move-result-object v5

    invoke-virtual {v4, v5}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->dT:Lcom/google/research/reflection/layers/e;

    iget-object v5, v3, Lcom/google/research/reflection/layers/a;->ea:Lcom/google/research/reflection/layers/e;

    sget-wide v6, Lcom/google/research/reflection/layers/c;->eo:D

    neg-double v6, v6

    invoke-virtual {v5, v6, v7}, Lcom/google/research/reflection/layers/e;->a(D)Lcom/google/research/reflection/layers/e;

    move-result-object v5

    invoke-virtual {v4, v5}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->dU:Lcom/google/research/reflection/layers/e;

    iget-object v5, v3, Lcom/google/research/reflection/layers/a;->eb:Lcom/google/research/reflection/layers/e;

    sget-wide v6, Lcom/google/research/reflection/layers/c;->eo:D

    neg-double v6, v6

    invoke-virtual {v5, v6, v7}, Lcom/google/research/reflection/layers/e;->a(D)Lcom/google/research/reflection/layers/e;

    move-result-object v5

    invoke-virtual {v4, v5}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->dV:Lcom/google/research/reflection/layers/e;

    iget-object v5, v3, Lcom/google/research/reflection/layers/a;->ec:Lcom/google/research/reflection/layers/e;

    sget-wide v6, Lcom/google/research/reflection/layers/c;->eo:D

    neg-double v6, v6

    invoke-virtual {v5, v6, v7}, Lcom/google/research/reflection/layers/e;->a(D)Lcom/google/research/reflection/layers/e;

    move-result-object v5

    invoke-virtual {v4, v5}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;

    iget-object v4, v3, Lcom/google/research/reflection/layers/a;->dW:Lcom/google/research/reflection/layers/e;

    iget-object v3, v3, Lcom/google/research/reflection/layers/a;->ed:Lcom/google/research/reflection/layers/e;

    sget-wide v5, Lcom/google/research/reflection/layers/c;->eo:D

    neg-double v5, v5

    invoke-virtual {v3, v5, v6}, Lcom/google/research/reflection/layers/e;->a(D)Lcom/google/research/reflection/layers/e;

    move-result-object v3

    invoke-virtual {v4, v3}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;

    add-int/lit8 v2, v2, 0x1

    goto :goto_0

    :cond_0
    return-void
.end method
