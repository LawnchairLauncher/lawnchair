.class public Lcom/google/research/reflection/layers/a;
.super Ljava/lang/Object;
.source "SourceFile"


# instance fields
.field dF:I

.field dG:I

.field dH:I

.field dI:I

.field dJ:Lcom/google/research/reflection/a/a;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lcom/google/research/reflection/a/a<",
            "Lcom/google/research/reflection/layers/e;",
            ">;"
        }
    .end annotation
.end field

.field dK:Lcom/google/research/reflection/a/a;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lcom/google/research/reflection/a/a<",
            "Lcom/google/research/reflection/layers/e;",
            ">;"
        }
    .end annotation
.end field

.field dL:Lcom/google/research/reflection/a/a;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lcom/google/research/reflection/a/a<",
            "Lcom/google/research/reflection/layers/e;",
            ">;"
        }
    .end annotation
.end field

.field dM:Lcom/google/research/reflection/a/a;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lcom/google/research/reflection/a/a<",
            "Lcom/google/research/reflection/layers/e;",
            ">;"
        }
    .end annotation
.end field

.field dN:Lcom/google/research/reflection/a/a;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lcom/google/research/reflection/a/a<",
            "Lcom/google/research/reflection/layers/e;",
            ">;"
        }
    .end annotation
.end field

.field dO:Lcom/google/research/reflection/a/a;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lcom/google/research/reflection/a/a<",
            "Lcom/google/research/reflection/layers/e;",
            ">;"
        }
    .end annotation
.end field

.field dP:[Lcom/google/research/reflection/layers/e;

.field dQ:Lcom/google/research/reflection/layers/e;

.field dR:Lcom/google/research/reflection/layers/e;

.field dS:Lcom/google/research/reflection/layers/e;

.field dT:Lcom/google/research/reflection/layers/e;

.field dU:Lcom/google/research/reflection/layers/e;

.field dV:Lcom/google/research/reflection/layers/e;

.field dW:Lcom/google/research/reflection/layers/e;

.field dX:Lcom/google/research/reflection/layers/e;

.field dY:Lcom/google/research/reflection/layers/e;

.field dZ:Lcom/google/research/reflection/layers/e;

.field ea:Lcom/google/research/reflection/layers/e;

.field eb:Lcom/google/research/reflection/layers/e;

.field ec:Lcom/google/research/reflection/layers/e;

.field ed:Lcom/google/research/reflection/layers/e;

.field ee:Lcom/google/research/reflection/layers/e;

.field ef:Lcom/google/research/reflection/layers/e;


# direct methods
.method static a(ZLcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;I[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;III)V
    .locals 18
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(Z",
            "Lcom/google/research/reflection/layers/e;",
            "Lcom/google/research/reflection/layers/e;",
            "I[",
            "Ljava/util/ArrayList<",
            "Lcom/google/research/reflection/a/d;",
            ">;",
            "Lcom/google/research/reflection/layers/e;",
            "III)V"
        }
    .end annotation

    move-object/from16 v0, p5

    const/4 v1, 0x0

    move/from16 v2, p8

    const/4 v3, 0x0

    :goto_0
    if-ge v3, v2, :cond_4

    move-object/from16 v4, p2

    move/from16 v5, p3

    .line 369
    invoke-virtual {v4, v1, v3, v5}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v10

    if-eqz p0, :cond_2

    const/4 v7, 0x0

    const/4 v8, 0x0

    move-object/from16 v6, p1

    move/from16 v9, p7

    .line 371
    invoke-virtual/range {v6 .. v11}, Lcom/google/research/reflection/layers/e;->a(ZIID)V

    :cond_0
    move/from16 v1, p6

    :cond_1
    const/4 v6, 0x0

    goto :goto_3

    :cond_2
    if-eqz p4, :cond_3

    .line 374
    aget-object v6, p4, v3

    invoke-virtual {v6}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object v6

    :goto_1
    invoke-interface {v6}, Ljava/util/Iterator;->hasNext()Z

    move-result v7

    if-eqz v7, :cond_0

    invoke-interface {v6}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Lcom/google/research/reflection/a/d;

    const/4 v13, 0x0

    .line 375
    iget v8, v7, Lcom/google/research/reflection/a/d;->index:I

    iget v7, v7, Lcom/google/research/reflection/a/d;->value:F

    float-to-double v1, v7

    mul-double v16, v1, v10

    move-object/from16 v12, p1

    move v14, v8

    move/from16 v15, p7

    invoke-virtual/range {v12 .. v17}, Lcom/google/research/reflection/layers/e;->a(ZIID)V

    const/4 v1, 0x0

    move/from16 v2, p8

    goto :goto_1

    :cond_3
    if-eqz v0, :cond_0

    move/from16 v1, p6

    const/4 v2, 0x0

    :goto_2
    if-ge v2, v1, :cond_1

    const/4 v6, 0x0

    .line 379
    invoke-virtual {v0, v6, v3, v2}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v7

    const/4 v13, 0x0

    mul-double v16, v7, v10

    move-object/from16 v12, p1

    move v14, v2

    move/from16 v15, p7

    .line 380
    invoke-virtual/range {v12 .. v17}, Lcom/google/research/reflection/layers/e;->a(ZIID)V

    add-int/lit8 v2, v2, 0x1

    goto :goto_2

    :goto_3
    add-int/lit8 v3, v3, 0x1

    const/4 v1, 0x0

    move/from16 v2, p8

    goto :goto_0

    :cond_4
    return-void
.end method


# virtual methods
.method final a(I[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;I)D
    .locals 7
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(I[",
            "Ljava/util/ArrayList<",
            "Lcom/google/research/reflection/a/d;",
            ">;",
            "Lcom/google/research/reflection/layers/e;",
            "Lcom/google/research/reflection/layers/e;",
            "Lcom/google/research/reflection/layers/e;",
            "I)D"
        }
    .end annotation

    const/4 v0, 0x0

    const-wide/16 v1, 0x0

    if-eqz p2, :cond_0

    .line 393
    aget-object p2, p2, p1

    invoke-virtual {p2}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object p2

    :goto_0
    invoke-interface {p2}, Ljava/util/Iterator;->hasNext()Z

    move-result p3

    if-eqz p3, :cond_1

    invoke-interface {p2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object p3

    check-cast p3, Lcom/google/research/reflection/a/d;

    .line 394
    iget v3, p3, Lcom/google/research/reflection/a/d;->value:F

    float-to-double v3, v3

    iget-object v5, p0, Lcom/google/research/reflection/layers/a;->dQ:Lcom/google/research/reflection/layers/e;

    iget p3, p3, Lcom/google/research/reflection/a/d;->index:I

    invoke-virtual {v5, v0, p3, p6}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v5

    mul-double v3, v3, v5

    add-double/2addr v1, v3

    goto :goto_0

    :cond_0
    const/4 p2, 0x0

    .line 397
    :goto_1
    iget v3, p0, Lcom/google/research/reflection/layers/a;->dH:I

    if-ge p2, v3, :cond_1

    .line 398
    invoke-virtual {p3, v0, p1, p2}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v3

    iget-object v5, p0, Lcom/google/research/reflection/layers/a;->dQ:Lcom/google/research/reflection/layers/e;

    .line 399
    invoke-virtual {v5, v0, p2, p6}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v5

    mul-double v3, v3, v5

    add-double/2addr v1, v3

    add-int/lit8 p2, p2, 0x1

    goto :goto_1

    :cond_1
    const/4 p2, 0x0

    :goto_2
    if-eqz p4, :cond_2

    .line 402
    iget p3, p0, Lcom/google/research/reflection/layers/a;->dI:I

    if-ge p2, p3, :cond_2

    .line 403
    invoke-virtual {p4, v0, p1, p2}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v3

    iget-object p3, p0, Lcom/google/research/reflection/layers/a;->dR:Lcom/google/research/reflection/layers/e;

    .line 404
    invoke-virtual {p3, v0, p2, p6}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v5

    mul-double v3, v3, v5

    add-double/2addr v1, v3

    add-int/lit8 p2, p2, 0x1

    goto :goto_2

    :cond_2
    const/4 p2, 0x0

    :goto_3
    if-eqz p5, :cond_3

    .line 406
    iget p3, p0, Lcom/google/research/reflection/layers/a;->dG:I

    if-ge p2, p3, :cond_3

    .line 407
    invoke-virtual {p5, v0, p1, p2}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide p3

    iget-object v3, p0, Lcom/google/research/reflection/layers/a;->dS:Lcom/google/research/reflection/layers/e;

    .line 408
    invoke-virtual {v3, v0, p2, p6}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v3

    mul-double p3, p3, v3

    add-double/2addr v1, p3

    add-int/lit8 p2, p2, 0x1

    goto :goto_3

    :cond_3
    return-wide v1
.end method

.method final a(ZLcom/google/research/reflection/layers/e;II[Lcom/google/research/reflection/layers/e;[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;)V
    .locals 10
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(Z",
            "Lcom/google/research/reflection/layers/e;",
            "II[",
            "Lcom/google/research/reflection/layers/e;",
            "[",
            "Ljava/util/ArrayList<",
            "Lcom/google/research/reflection/a/d;",
            ">;",
            "Lcom/google/research/reflection/layers/e;",
            ")V"
        }
    .end annotation

    const/4 v0, 0x1

    .line 357
    aget-object v3, p5, v0

    const/4 v4, 0x0

    const/4 v8, 0x0

    move v1, p1

    move-object v2, p2

    move-object/from16 v5, p6

    move-object/from16 v6, p7

    move v7, p3

    move v9, p4

    invoke-static/range {v1 .. v9}, Lcom/google/research/reflection/layers/a;->a(ZLcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;I[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;III)V

    const/4 v0, 0x2

    .line 359
    aget-object v3, p5, v0

    const/4 v8, 0x1

    invoke-static/range {v1 .. v9}, Lcom/google/research/reflection/layers/a;->a(ZLcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;I[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;III)V

    const/4 v0, 0x4

    .line 361
    aget-object v3, p5, v0

    const/4 v8, 0x2

    invoke-static/range {v1 .. v9}, Lcom/google/research/reflection/layers/a;->a(ZLcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;I[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;III)V

    return-void
.end method
