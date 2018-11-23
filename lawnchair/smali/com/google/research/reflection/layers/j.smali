.class public Lcom/google/research/reflection/layers/j;
.super Ljava/lang/Object;
.source "SourceFile"


# direct methods
.method public static a(Lcom/google/research/reflection/layers/e;Z)V
    .locals 14

    const/4 v0, 0x0

    .line 114
    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v1

    const/4 v8, 0x0

    :goto_0
    if-ge v8, v1, :cond_2

    .line 116
    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v9

    const-wide v10, 0x3fb999999999999aL    # 0.1

    if-eqz p1, :cond_0

    const/4 v12, 0x0

    :goto_1
    if-ge v12, v9, :cond_1

    const/4 v3, 0x0

    invoke-static {}, Ljava/lang/Math;->random()D

    move-result-wide v4

    mul-double v6, v4, v10

    move-object v2, p0

    move v4, v8

    move v5, v12

    invoke-virtual/range {v2 .. v7}, Lcom/google/research/reflection/layers/e;->b(ZIID)V

    add-int/lit8 v12, v12, 0x1

    goto :goto_1

    :cond_0
    new-instance v12, Ljava/util/Random;

    invoke-direct {v12}, Ljava/util/Random;-><init>()V

    const/4 v13, 0x0

    :goto_2
    if-ge v13, v9, :cond_1

    const/4 v3, 0x0

    invoke-virtual {v12}, Ljava/util/Random;->nextGaussian()D

    move-result-wide v4

    mul-double v6, v4, v10

    move-object v2, p0

    move v4, v8

    move v5, v13

    invoke-virtual/range {v2 .. v7}, Lcom/google/research/reflection/layers/e;->b(ZIID)V

    add-int/lit8 v13, v13, 0x1

    goto :goto_2

    :cond_1
    add-int/lit8 v8, v8, 0x1

    goto :goto_0

    :cond_2
    return-void
.end method

.method static b(D)D
    .locals 2

    neg-double p0, p0

    .line 55
    invoke-static {p0, p1}, Ljava/lang/Math;->exp(D)D

    move-result-wide p0

    .line 56
    invoke-static {p0, p1}, Ljava/lang/Double;->isInfinite(D)Z

    move-result v0

    if-eqz v0, :cond_0

    const-wide/16 p0, 0x0

    return-wide p0

    :cond_0
    const-wide/high16 v0, 0x3ff0000000000000L    # 1.0

    add-double/2addr p0, v0

    div-double/2addr v0, p0

    return-wide v0
.end method
