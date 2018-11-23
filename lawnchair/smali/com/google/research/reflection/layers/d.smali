.class public Lcom/google/research/reflection/layers/d;
.super Lcom/google/research/reflection/layers/c;
.source "SourceFile"


# instance fields
.field private eA:Lcom/google/research/reflection/layers/e;

.field private eB:Lcom/google/research/reflection/layers/e;

.field private eC:Lcom/google/research/reflection/layers/e;

.field private eD:Lcom/google/research/reflection/layers/e;

.field public eE:Lcom/google/research/reflection/layers/e;

.field private eF:Lcom/google/research/reflection/layers/e;

.field eG:I

.field private eH:I

.field private eI:F

.field ey:I

.field private ez:Z


# direct methods
.method public constructor <init>()V
    .locals 0

    .line 42
    invoke-direct/range {p0 .. p0}, Lcom/google/research/reflection/layers/c;-><init>()V

    return-void
.end method

.method public constructor <init>(ZIIIIIIIZZF)V
    .locals 11

    move-object v6, p0

    move/from16 v7, p5

    move/from16 v8, p6

    move/from16 v9, p7

    move/from16 v10, p10

    move-object v0, p0

    move v1, p1

    move v2, p2

    move v3, p4

    move/from16 v4, p5

    move/from16 v5, p6

    .line 49
    invoke-direct/range {v0 .. v5}, Lcom/google/research/reflection/layers/c;-><init>(ZIIII)V

    .line 50
    iput v9, v6, Lcom/google/research/reflection/layers/d;->eG:I

    move/from16 v0, p11

    .line 51
    iput v0, v6, Lcom/google/research/reflection/layers/d;->eI:F

    .line 52
    new-instance v0, Lcom/google/research/reflection/layers/e;

    const/4 v1, 0x1

    invoke-direct {v0, v1, v8}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object v0, v6, Lcom/google/research/reflection/layers/d;->eE:Lcom/google/research/reflection/layers/e;

    move v0, p3

    .line 53
    iput v0, v6, Lcom/google/research/reflection/layers/d;->ey:I

    .line 54
    new-instance v0, Lcom/google/research/reflection/layers/e;

    invoke-direct {v0, v7, v8}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object v0, v6, Lcom/google/research/reflection/layers/d;->eC:Lcom/google/research/reflection/layers/e;

    .line 55
    new-instance v0, Lcom/google/research/reflection/layers/e;

    invoke-direct {v0, v8, v8}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object v0, v6, Lcom/google/research/reflection/layers/d;->eD:Lcom/google/research/reflection/layers/e;

    .line 56
    new-instance v0, Lcom/google/research/reflection/layers/e;

    invoke-direct {v0, v1, v8}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object v0, v6, Lcom/google/research/reflection/layers/d;->eF:Lcom/google/research/reflection/layers/e;

    move/from16 v0, p9

    .line 57
    iput-boolean v0, v6, Lcom/google/research/reflection/layers/d;->ez:Z

    if-gez v9, :cond_0

    .line 59
    new-instance v0, Lcom/google/research/reflection/layers/e;

    invoke-direct {v0, v7, v8}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object v0, v6, Lcom/google/research/reflection/layers/d;->eA:Lcom/google/research/reflection/layers/e;

    .line 60
    iget-object v0, v6, Lcom/google/research/reflection/layers/d;->eA:Lcom/google/research/reflection/layers/e;

    invoke-static {v0, v10}, Lcom/google/research/reflection/layers/j;->a(Lcom/google/research/reflection/layers/e;Z)V

    .line 61
    iget-object v0, v6, Lcom/google/research/reflection/layers/d;->eE:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    const-wide/16 v1, 0x0

    invoke-static {v0, v1, v2}, Ljava/util/Arrays;->fill([DD)V

    :cond_0
    move/from16 v0, p8

    .line 63
    iput v0, v6, Lcom/google/research/reflection/layers/d;->eH:I

    .line 64
    new-instance v0, Lcom/google/research/reflection/layers/e;

    invoke-direct {v0, v8, v8}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object v0, v6, Lcom/google/research/reflection/layers/d;->eB:Lcom/google/research/reflection/layers/e;

    .line 65
    iget-object v0, v6, Lcom/google/research/reflection/layers/d;->eB:Lcom/google/research/reflection/layers/e;

    invoke-static {v0, v10}, Lcom/google/research/reflection/layers/j;->a(Lcom/google/research/reflection/layers/e;Z)V

    return-void
.end method

.method static synthetic b(Lcom/google/research/reflection/layers/d;)Lcom/google/research/reflection/layers/e;
    .locals 0

    .line 12
    iget-object p0, p0, Lcom/google/research/reflection/layers/d;->eC:Lcom/google/research/reflection/layers/e;

    return-object p0
.end method

.method static synthetic c(Lcom/google/research/reflection/layers/d;)Lcom/google/research/reflection/layers/e;
    .locals 0

    .line 12
    iget-object p0, p0, Lcom/google/research/reflection/layers/d;->eD:Lcom/google/research/reflection/layers/e;

    return-object p0
.end method

.method static synthetic d(Lcom/google/research/reflection/layers/d;)Lcom/google/research/reflection/layers/e;
    .locals 0

    .line 12
    iget-object p0, p0, Lcom/google/research/reflection/layers/d;->eF:Lcom/google/research/reflection/layers/e;

    return-object p0
.end method

.method static synthetic e(Lcom/google/research/reflection/layers/d;)Z
    .locals 0

    .line 12
    iget-boolean p0, p0, Lcom/google/research/reflection/layers/d;->ez:Z

    return p0
.end method

.method static synthetic f(Lcom/google/research/reflection/layers/d;)Lcom/google/research/reflection/layers/e;
    .locals 0

    .line 12
    iget-object p0, p0, Lcom/google/research/reflection/layers/d;->eA:Lcom/google/research/reflection/layers/e;

    return-object p0
.end method

.method static synthetic g(Lcom/google/research/reflection/layers/d;)Lcom/google/research/reflection/layers/e;
    .locals 0

    .line 12
    iget-object p0, p0, Lcom/google/research/reflection/layers/d;->eB:Lcom/google/research/reflection/layers/e;

    return-object p0
.end method

.method static synthetic h(Lcom/google/research/reflection/layers/d;)Lcom/google/research/reflection/layers/e;
    .locals 0

    .line 12
    iget-object p0, p0, Lcom/google/research/reflection/layers/d;->eE:Lcom/google/research/reflection/layers/e;

    return-object p0
.end method


# virtual methods
.method public synthetic Z()Lcom/google/research/reflection/layers/c;
    .locals 1

    .line 12
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/layers/d;->ae()Lcom/google/research/reflection/layers/d;

    move-result-object v0

    return-object v0
.end method

.method public final a(Lcom/google/research/reflection/layers/f;)Lcom/google/research/reflection/layers/e;
    .locals 1

    .line 77
    iget v0, p0, Lcom/google/research/reflection/layers/d;->eG:I

    if-ltz v0, :cond_0

    .line 78
    iget-object p1, p1, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    iget v0, p0, Lcom/google/research/reflection/layers/d;->eG:I

    invoke-interface {p1, v0}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lcom/google/research/reflection/layers/d;

    iget-object p1, p1, Lcom/google/research/reflection/layers/d;->eA:Lcom/google/research/reflection/layers/e;

    return-object p1

    .line 80
    :cond_0
    iget-object p1, p0, Lcom/google/research/reflection/layers/d;->eA:Lcom/google/research/reflection/layers/e;

    return-object p1
.end method

.method public final a(ZLcom/google/research/reflection/layers/f;[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;
    .locals 15
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

    move-object v7, p0

    move-object/from16 v3, p3

    move-object/from16 v4, p4

    const/4 v8, 0x2

    const/4 v9, 0x1

    const/4 v10, 0x0

    if-eqz v3, :cond_0

    .line 239
    iput-boolean v9, v7, Lcom/google/research/reflection/layers/d;->ev:Z

    .line 240
    iget-object v0, v7, Lcom/google/research/reflection/layers/d;->er:Lcom/google/research/reflection/a/a;

    invoke-virtual {v0, v3}, Lcom/google/research/reflection/a/a;->a(Ljava/lang/Object;)Ljava/lang/Object;

    goto :goto_0

    .line 242
    :cond_0
    iput-boolean v10, v7, Lcom/google/research/reflection/layers/d;->ev:Z

    .line 243
    iget-object v0, v7, Lcom/google/research/reflection/layers/d;->eq:Lcom/google/research/reflection/a/a;

    invoke-virtual {v0, v4}, Lcom/google/research/reflection/a/a;->a(Ljava/lang/Object;)Ljava/lang/Object;

    .line 244
    invoke-virtual {v4, v10}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v0

    iget v1, v7, Lcom/google/research/reflection/layers/c;->dH:I

    if-ne v0, v1, :cond_8

    .line 245
    invoke-virtual {v4, v10}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v0

    iget v1, v7, Lcom/google/research/reflection/layers/c;->dF:I

    if-ne v0, v1, :cond_8

    .line 258
    :goto_0
    iget-object v0, v7, Lcom/google/research/reflection/layers/d;->ep:Lcom/google/research/reflection/a/a;

    iget-object v0, v0, Lcom/google/research/reflection/a/a;->dl:[Ljava/lang/Object;

    array-length v0, v0

    if-le v0, v9, :cond_1

    .line 259
    iget-object v0, v7, Lcom/google/research/reflection/layers/d;->ep:Lcom/google/research/reflection/a/a;

    invoke-virtual {v0}, Lcom/google/research/reflection/a/a;->getLast()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/google/research/reflection/layers/e;

    :goto_1
    move-object v5, v0

    goto :goto_2

    :cond_1
    const/4 v0, 0x0

    goto :goto_1

    .line 263
    :goto_2
    new-instance v11, Lcom/google/research/reflection/layers/e;

    iget v0, v7, Lcom/google/research/reflection/layers/d;->dF:I

    iget v1, v7, Lcom/google/research/reflection/layers/d;->ew:I

    invoke-direct {v11, v0, v1}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    .line 264
    invoke-static {}, Lcom/google/research/reflection/layers/i;->ak()Lcom/google/research/reflection/layers/i;

    move-result-object v12

    iget v0, v7, Lcom/google/research/reflection/layers/d;->dF:I

    iget v1, v7, Lcom/google/research/reflection/layers/d;->ew:I

    mul-int v13, v0, v1

    new-instance v14, Lcom/google/research/reflection/layers/d$4;

    move-object v0, v14

    move-object v1, p0

    move-object/from16 v2, p2

    move-object/from16 v3, p3

    move-object/from16 v4, p4

    move-object v6, v11

    invoke-direct/range {v0 .. v6}, Lcom/google/research/reflection/layers/d$4;-><init>(Lcom/google/research/reflection/layers/d;Lcom/google/research/reflection/layers/f;[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V

    invoke-virtual {v12, v13, v14}, Lcom/google/research/reflection/layers/i;->a(ILcom/google/research/reflection/layers/h;)V

    .line 293
    iget-object v0, v7, Lcom/google/research/reflection/layers/d;->ep:Lcom/google/research/reflection/a/a;

    new-instance v1, Lcom/google/research/reflection/layers/e;

    iget v2, v7, Lcom/google/research/reflection/layers/d;->dF:I

    iget v3, v7, Lcom/google/research/reflection/layers/d;->ew:I

    invoke-direct {v1, v2, v3}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    invoke-virtual {v0, v1}, Lcom/google/research/reflection/a/a;->a(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/google/research/reflection/layers/e;

    .line 294
    iget v1, v7, Lcom/google/research/reflection/layers/d;->ey:I

    if-eq v1, v9, :cond_4

    if-nez v1, :cond_2

    goto :goto_3

    :cond_2
    if-ne v1, v8, :cond_3

    invoke-static {}, Lcom/google/research/reflection/layers/i;->ak()Lcom/google/research/reflection/layers/i;

    move-result-object v1

    invoke-virtual {v11, v10}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v2

    new-instance v3, Lcom/google/research/reflection/layers/d$6;

    invoke-direct {v3, v11, v0}, Lcom/google/research/reflection/layers/d$6;-><init>(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V

    invoke-virtual {v1, v2, v3}, Lcom/google/research/reflection/layers/i;->a(ILcom/google/research/reflection/layers/h;)V

    goto :goto_4

    :cond_3
    new-instance v0, Ljava/lang/RuntimeException;

    const/16 v2, 0x2c

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3, v2}, Ljava/lang/StringBuilder;-><init>(I)V

    const-string v2, "Unsupported activation function: "

    invoke-virtual {v3, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-direct {v0, v1}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v0

    :cond_4
    :goto_3
    invoke-static {}, Lcom/google/research/reflection/layers/i;->ak()Lcom/google/research/reflection/layers/i;

    move-result-object v2

    iget-object v3, v11, Lcom/google/research/reflection/layers/e;->eR:[D

    array-length v3, v3

    new-instance v4, Lcom/google/research/reflection/layers/d$5;

    invoke-direct {v4, v1, v0, v11}, Lcom/google/research/reflection/layers/d$5;-><init>(ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V

    invoke-virtual {v2, v3, v4}, Lcom/google/research/reflection/layers/i;->a(ILcom/google/research/reflection/layers/h;)V

    .line 295
    :goto_4
    iget v1, v7, Lcom/google/research/reflection/layers/d;->eI:F

    const/4 v2, 0x0

    cmpl-float v1, v1, v2

    if-lez v1, :cond_7

    if-eqz p1, :cond_6

    .line 297
    :goto_5
    iget-object v1, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    array-length v1, v1

    if-ge v10, v1, :cond_7

    .line 298
    invoke-static {}, Ljava/lang/Math;->random()D

    move-result-wide v1

    iget v3, v7, Lcom/google/research/reflection/layers/d;->eI:F

    float-to-double v3, v3

    cmpg-double v1, v1, v3

    if-gez v1, :cond_5

    .line 299
    iget-object v1, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    const-wide/16 v2, 0x0

    aput-wide v2, v1, v10

    :cond_5
    add-int/lit8 v10, v10, 0x1

    goto :goto_5

    .line 303
    :cond_6
    :goto_6
    iget-object v1, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    array-length v1, v1

    if-ge v10, v1, :cond_7

    .line 304
    iget-object v1, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v2, v1, v10

    const/high16 v4, 0x3f800000    # 1.0f

    iget v5, v7, Lcom/google/research/reflection/layers/d;->eI:F

    sub-float/2addr v4, v5

    float-to-double v4, v4

    mul-double v2, v2, v4

    aput-wide v2, v1, v10

    add-int/lit8 v10, v10, 0x1

    goto :goto_6

    :cond_7
    return-object v0

    .line 246
    :cond_8
    new-instance v0, Ljava/lang/RuntimeException;

    const/4 v1, 0x4

    new-array v1, v1, [Ljava/lang/Object;

    .line 250
    invoke-virtual {v4, v10}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v2

    invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v2

    aput-object v2, v1, v10

    .line 251
    iget v2, v7, Lcom/google/research/reflection/layers/c;->dH:I

    invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v2

    aput-object v2, v1, v9

    .line 252
    invoke-virtual {v4, v10}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v2

    invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v2

    aput-object v2, v1, v8

    const/4 v2, 0x3

    .line 253
    iget v3, v7, Lcom/google/research/reflection/layers/c;->dF:I

    invoke-static {v3}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v3

    aput-object v3, v1, v2

    const-string v2, "Inconsistent input, input size: %d, expected input size: %d, row size: %d, expected row size: %d"

    .line 247
    invoke-static {v2, v1}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v1

    invoke-direct {v0, v1}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v0
.end method

.method a(ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
    .locals 2
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    .line 170
    invoke-static {}, Lcom/google/research/reflection/layers/i;->ak()Lcom/google/research/reflection/layers/i;

    move-result-object p4

    iget-object v0, p2, Lcom/google/research/reflection/layers/e;->eR:[D

    array-length v0, v0

    new-instance v1, Lcom/google/research/reflection/layers/d$2;

    invoke-direct {v1, p1, p3, p2}, Lcom/google/research/reflection/layers/d$2;-><init>(ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V

    invoke-virtual {p4, v0, v1}, Lcom/google/research/reflection/layers/i;->a(ILcom/google/research/reflection/layers/h;)V

    return-void
.end method

.method public final a(Lcom/google/research/reflection/layers/d;)V
    .locals 1

    .line 106
    invoke-super/range {p0 .. p1}, Lcom/google/research/reflection/layers/c;->a(Lcom/google/research/reflection/layers/c;)V

    .line 107
    iget-boolean v0, p0, Lcom/google/research/reflection/layers/d;->ez:Z

    iput-boolean v0, p1, Lcom/google/research/reflection/layers/d;->ez:Z

    .line 108
    iget v0, p0, Lcom/google/research/reflection/layers/d;->ey:I

    iput v0, p1, Lcom/google/research/reflection/layers/d;->ey:I

    .line 109
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->eA:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v0}, Lcom/google/research/reflection/layers/e;->af()Lcom/google/research/reflection/layers/e;

    move-result-object v0

    iput-object v0, p1, Lcom/google/research/reflection/layers/d;->eA:Lcom/google/research/reflection/layers/e;

    .line 110
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->eC:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v0}, Lcom/google/research/reflection/layers/e;->af()Lcom/google/research/reflection/layers/e;

    move-result-object v0

    iput-object v0, p1, Lcom/google/research/reflection/layers/d;->eC:Lcom/google/research/reflection/layers/e;

    .line 111
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->eB:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v0}, Lcom/google/research/reflection/layers/e;->af()Lcom/google/research/reflection/layers/e;

    move-result-object v0

    iput-object v0, p1, Lcom/google/research/reflection/layers/d;->eB:Lcom/google/research/reflection/layers/e;

    .line 112
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->eD:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v0}, Lcom/google/research/reflection/layers/e;->af()Lcom/google/research/reflection/layers/e;

    move-result-object v0

    iput-object v0, p1, Lcom/google/research/reflection/layers/d;->eD:Lcom/google/research/reflection/layers/e;

    .line 113
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->eE:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v0}, Lcom/google/research/reflection/layers/e;->af()Lcom/google/research/reflection/layers/e;

    move-result-object v0

    iput-object v0, p1, Lcom/google/research/reflection/layers/d;->eE:Lcom/google/research/reflection/layers/e;

    .line 114
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->eF:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v0}, Lcom/google/research/reflection/layers/e;->af()Lcom/google/research/reflection/layers/e;

    move-result-object v0

    iput-object v0, p1, Lcom/google/research/reflection/layers/d;->eF:Lcom/google/research/reflection/layers/e;

    .line 115
    iget v0, p0, Lcom/google/research/reflection/layers/d;->eG:I

    iput v0, p1, Lcom/google/research/reflection/layers/d;->eG:I

    .line 116
    iget v0, p0, Lcom/google/research/reflection/layers/d;->eH:I

    iput v0, p1, Lcom/google/research/reflection/layers/d;->eH:I

    return-void
.end method

.method public final a(Lcom/google/research/reflection/layers/f;ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
    .locals 2
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    .line 124
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->es:Lcom/google/research/reflection/layers/e;

    const/4 v1, 0x0

    invoke-static {p3, p4, v0, v1}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Z)Lcom/google/research/reflection/layers/e;

    .line 125
    iget-object p3, p0, Lcom/google/research/reflection/layers/d;->ep:Lcom/google/research/reflection/a/a;

    invoke-virtual {p3, p2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object p3

    check-cast p3, Lcom/google/research/reflection/layers/e;

    .line 126
    iget p4, p0, Lcom/google/research/reflection/layers/d;->ey:I

    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->es:Lcom/google/research/reflection/layers/e;

    invoke-virtual {p0, p4, v0, p3, p5}, Lcom/google/research/reflection/layers/d;->a(ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V

    .line 128
    iget-object p3, p0, Lcom/google/research/reflection/layers/d;->es:Lcom/google/research/reflection/layers/e;

    invoke-virtual/range {p0 .. p1}, Lcom/google/research/reflection/layers/d;->a(Lcom/google/research/reflection/layers/f;)Lcom/google/research/reflection/layers/e;

    move-result-object p1

    iget-boolean p4, p0, Lcom/google/research/reflection/layers/d;->ez:Z

    const/4 p5, 0x1

    xor-int/2addr p4, p5

    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->et:Lcom/google/research/reflection/layers/e;

    invoke-static {p3, p1, p4, v0, v1}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;ZLcom/google/research/reflection/layers/e;Z)Lcom/google/research/reflection/layers/e;

    .line 129
    iget-boolean p1, p0, Lcom/google/research/reflection/layers/d;->ex:Z

    if-eqz p1, :cond_0

    .line 130
    iget-object p1, p0, Lcom/google/research/reflection/layers/d;->es:Lcom/google/research/reflection/layers/e;

    iget-object p3, p0, Lcom/google/research/reflection/layers/d;->eB:Lcom/google/research/reflection/layers/e;

    iget-object p4, p0, Lcom/google/research/reflection/layers/d;->eu:Lcom/google/research/reflection/layers/e;

    invoke-static {p1, p3, p5, p4, v1}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;ZLcom/google/research/reflection/layers/e;Z)Lcom/google/research/reflection/layers/e;

    .line 133
    :cond_0
    iget-object p1, p0, Lcom/google/research/reflection/layers/d;->eq:Lcom/google/research/reflection/a/a;

    invoke-virtual {p1, p2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lcom/google/research/reflection/layers/e;

    .line 134
    iget-object p3, p0, Lcom/google/research/reflection/layers/d;->er:Lcom/google/research/reflection/a/a;

    invoke-virtual {p3, p2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object p3

    check-cast p3, [Ljava/util/ArrayList;

    .line 135
    iget-object p4, p0, Lcom/google/research/reflection/layers/d;->ep:Lcom/google/research/reflection/a/a;

    sub-int/2addr p2, p5

    invoke-virtual {p4, p2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object p2

    check-cast p2, Lcom/google/research/reflection/layers/e;

    .line 136
    invoke-static {}, Lcom/google/research/reflection/layers/i;->ak()Lcom/google/research/reflection/layers/i;

    move-result-object p4

    iget p5, p0, Lcom/google/research/reflection/layers/d;->dF:I

    iget v0, p0, Lcom/google/research/reflection/layers/d;->ew:I

    mul-int p5, p5, v0

    new-instance v0, Lcom/google/research/reflection/layers/d$1;

    invoke-direct {v0, p0, p3, p1, p2}, Lcom/google/research/reflection/layers/d$1;-><init>(Lcom/google/research/reflection/layers/d;[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V

    invoke-virtual {p4, p5, v0}, Lcom/google/research/reflection/layers/i;->a(ILcom/google/research/reflection/layers/h;)V

    return-void
.end method

.method final aa()V
    .locals 3

    .line 70
    invoke-super/range {p0 .. p0}, Lcom/google/research/reflection/layers/c;->aa()V

    .line 71
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->eC:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    const-wide/16 v1, 0x0

    invoke-static {v0, v1, v2}, Ljava/util/Arrays;->fill([DD)V

    .line 72
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->eD:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {v0, v1, v2}, Ljava/util/Arrays;->fill([DD)V

    .line 73
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->eF:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {v0, v1, v2}, Ljava/util/Arrays;->fill([DD)V

    return-void
.end method

.method public ae()Lcom/google/research/reflection/layers/d;
    .locals 2

    .line 90
    new-instance v0, Lcom/google/research/reflection/layers/d;

    invoke-direct {v0}, Lcom/google/research/reflection/layers/d;-><init>()V

    .line 91
    invoke-super {p0, v0}, Lcom/google/research/reflection/layers/c;->a(Lcom/google/research/reflection/layers/c;)V

    .line 92
    iget-boolean v1, p0, Lcom/google/research/reflection/layers/d;->ez:Z

    iput-boolean v1, v0, Lcom/google/research/reflection/layers/d;->ez:Z

    .line 93
    iget v1, p0, Lcom/google/research/reflection/layers/d;->ey:I

    iput v1, v0, Lcom/google/research/reflection/layers/d;->ey:I

    .line 94
    iget-object v1, p0, Lcom/google/research/reflection/layers/d;->eA:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v1}, Lcom/google/research/reflection/layers/e;->af()Lcom/google/research/reflection/layers/e;

    move-result-object v1

    iput-object v1, v0, Lcom/google/research/reflection/layers/d;->eA:Lcom/google/research/reflection/layers/e;

    .line 95
    iget-object v1, p0, Lcom/google/research/reflection/layers/d;->eC:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v1}, Lcom/google/research/reflection/layers/e;->af()Lcom/google/research/reflection/layers/e;

    move-result-object v1

    iput-object v1, v0, Lcom/google/research/reflection/layers/d;->eC:Lcom/google/research/reflection/layers/e;

    .line 96
    iget-object v1, p0, Lcom/google/research/reflection/layers/d;->eB:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v1}, Lcom/google/research/reflection/layers/e;->af()Lcom/google/research/reflection/layers/e;

    move-result-object v1

    iput-object v1, v0, Lcom/google/research/reflection/layers/d;->eB:Lcom/google/research/reflection/layers/e;

    .line 97
    iget-object v1, p0, Lcom/google/research/reflection/layers/d;->eD:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v1}, Lcom/google/research/reflection/layers/e;->af()Lcom/google/research/reflection/layers/e;

    move-result-object v1

    iput-object v1, v0, Lcom/google/research/reflection/layers/d;->eD:Lcom/google/research/reflection/layers/e;

    .line 98
    iget-object v1, p0, Lcom/google/research/reflection/layers/d;->eE:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v1}, Lcom/google/research/reflection/layers/e;->af()Lcom/google/research/reflection/layers/e;

    move-result-object v1

    iput-object v1, v0, Lcom/google/research/reflection/layers/d;->eE:Lcom/google/research/reflection/layers/e;

    .line 99
    iget-object v1, p0, Lcom/google/research/reflection/layers/d;->eF:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v1}, Lcom/google/research/reflection/layers/e;->af()Lcom/google/research/reflection/layers/e;

    move-result-object v1

    iput-object v1, v0, Lcom/google/research/reflection/layers/d;->eF:Lcom/google/research/reflection/layers/e;

    .line 100
    iget v1, p0, Lcom/google/research/reflection/layers/d;->eG:I

    iput v1, v0, Lcom/google/research/reflection/layers/d;->eG:I

    .line 101
    iget v1, p0, Lcom/google/research/reflection/layers/d;->eH:I

    iput v1, v0, Lcom/google/research/reflection/layers/d;->eH:I

    return-object v0
.end method

.method public b(Ljava/io/DataInputStream;)V
    .locals 3
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 364
    invoke-super/range {p0 .. p1}, Lcom/google/research/reflection/layers/c;->b(Ljava/io/DataInputStream;)V

    .line 365
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readInt()I

    move-result v0

    iput v0, p0, Lcom/google/research/reflection/layers/d;->ey:I

    .line 366
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readBoolean()Z

    move-result v0

    iput-boolean v0, p0, Lcom/google/research/reflection/layers/d;->ez:Z

    .line 367
    new-instance v0, Lcom/google/research/reflection/layers/e;

    invoke-direct {v0}, Lcom/google/research/reflection/layers/e;-><init>()V

    iput-object v0, p0, Lcom/google/research/reflection/layers/d;->eB:Lcom/google/research/reflection/layers/e;

    .line 368
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->eB:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v0, p1}, Lcom/google/research/reflection/layers/e;->b(Ljava/io/DataInputStream;)V

    .line 369
    new-instance v0, Lcom/google/research/reflection/layers/e;

    invoke-direct {v0}, Lcom/google/research/reflection/layers/e;-><init>()V

    iput-object v0, p0, Lcom/google/research/reflection/layers/d;->eE:Lcom/google/research/reflection/layers/e;

    .line 370
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->eE:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v0, p1}, Lcom/google/research/reflection/layers/e;->b(Ljava/io/DataInputStream;)V

    .line 371
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readInt()I

    move-result v0

    iput v0, p0, Lcom/google/research/reflection/layers/d;->eG:I

    .line 372
    iget v0, p0, Lcom/google/research/reflection/layers/d;->eG:I

    if-gez v0, :cond_0

    .line 373
    new-instance v0, Lcom/google/research/reflection/layers/e;

    invoke-direct {v0}, Lcom/google/research/reflection/layers/e;-><init>()V

    iput-object v0, p0, Lcom/google/research/reflection/layers/d;->eA:Lcom/google/research/reflection/layers/e;

    .line 374
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->eA:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v0, p1}, Lcom/google/research/reflection/layers/e;->b(Ljava/io/DataInputStream;)V

    .line 376
    :cond_0
    new-instance v0, Lcom/google/research/reflection/layers/e;

    iget v1, p0, Lcom/google/research/reflection/layers/d;->dH:I

    iget v2, p0, Lcom/google/research/reflection/layers/d;->ew:I

    invoke-direct {v0, v1, v2}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object v0, p0, Lcom/google/research/reflection/layers/d;->eC:Lcom/google/research/reflection/layers/e;

    .line 377
    new-instance v0, Lcom/google/research/reflection/layers/e;

    iget v1, p0, Lcom/google/research/reflection/layers/d;->ew:I

    iget v2, p0, Lcom/google/research/reflection/layers/d;->ew:I

    invoke-direct {v0, v1, v2}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object v0, p0, Lcom/google/research/reflection/layers/d;->eD:Lcom/google/research/reflection/layers/e;

    .line 378
    new-instance v0, Lcom/google/research/reflection/layers/e;

    const/4 v1, 0x1

    iget v2, p0, Lcom/google/research/reflection/layers/d;->ew:I

    invoke-direct {v0, v1, v2}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object v0, p0, Lcom/google/research/reflection/layers/d;->eF:Lcom/google/research/reflection/layers/e;

    .line 379
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readInt()I

    move-result v0

    iput v0, p0, Lcom/google/research/reflection/layers/d;->eH:I

    .line 380
    invoke-virtual/range {p0 .. p1}, Lcom/google/research/reflection/layers/d;->c(Ljava/io/DataInputStream;)V

    return-void
.end method

.method public b(Ljava/io/DataOutputStream;)V
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 344
    invoke-super/range {p0 .. p1}, Lcom/google/research/reflection/layers/c;->b(Ljava/io/DataOutputStream;)V

    .line 345
    iget v0, p0, Lcom/google/research/reflection/layers/d;->ey:I

    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeInt(I)V

    .line 346
    iget-boolean v0, p0, Lcom/google/research/reflection/layers/d;->ez:Z

    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeBoolean(Z)V

    .line 347
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->eB:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v0, p1}, Lcom/google/research/reflection/layers/e;->b(Ljava/io/DataOutputStream;)V

    .line 348
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->eE:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v0, p1}, Lcom/google/research/reflection/layers/e;->b(Ljava/io/DataOutputStream;)V

    .line 349
    iget v0, p0, Lcom/google/research/reflection/layers/d;->eG:I

    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeInt(I)V

    .line 350
    iget v0, p0, Lcom/google/research/reflection/layers/d;->eG:I

    if-gez v0, :cond_0

    .line 351
    iget-object v0, p0, Lcom/google/research/reflection/layers/d;->eA:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v0, p1}, Lcom/google/research/reflection/layers/e;->b(Ljava/io/DataOutputStream;)V

    .line 353
    :cond_0
    iget v0, p0, Lcom/google/research/reflection/layers/d;->eH:I

    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeInt(I)V

    .line 354
    invoke-virtual/range {p0 .. p1}, Lcom/google/research/reflection/layers/d;->c(Ljava/io/DataOutputStream;)V

    return-void
.end method

.method public synthetic clone()Ljava/lang/Object;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/CloneNotSupportedException;
        }
    .end annotation

    .line 12
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/layers/d;->ae()Lcom/google/research/reflection/layers/d;

    move-result-object v0

    return-object v0
.end method

.method public getName()Ljava/lang/String;
    .locals 1

    const-string v0, "LinearLayer"

    return-object v0
.end method

.method public final update()V
    .locals 3
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    .line 190
    invoke-static {}, Lcom/google/research/reflection/layers/i;->ak()Lcom/google/research/reflection/layers/i;

    move-result-object v0

    iget v1, p0, Lcom/google/research/reflection/layers/d;->ew:I

    new-instance v2, Lcom/google/research/reflection/layers/d$3;

    invoke-direct {v2, p0}, Lcom/google/research/reflection/layers/d$3;-><init>(Lcom/google/research/reflection/layers/d;)V

    invoke-virtual {v0, v1, v2}, Lcom/google/research/reflection/layers/i;->a(ILcom/google/research/reflection/layers/h;)V

    return-void
.end method
