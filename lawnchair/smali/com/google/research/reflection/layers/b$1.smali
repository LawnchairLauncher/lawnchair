.class Lcom/google/research/reflection/layers/b$1;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/layers/h;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/google/research/reflection/layers/b;->a(Lcom/google/research/reflection/layers/f;ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic ei:I

.field final synthetic ej:[Ljava/util/ArrayList;

.field final synthetic ek:Lcom/google/research/reflection/layers/e;

.field final synthetic el:Lcom/google/research/reflection/layers/e;

.field final synthetic em:Lcom/google/research/reflection/layers/b;


# direct methods
.method constructor <init>(Lcom/google/research/reflection/layers/b;I[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
    .locals 0

    .line 44
    iput-object p1, p0, Lcom/google/research/reflection/layers/b$1;->em:Lcom/google/research/reflection/layers/b;

    iput p2, p0, Lcom/google/research/reflection/layers/b$1;->ei:I

    iput-object p3, p0, Lcom/google/research/reflection/layers/b$1;->ej:[Ljava/util/ArrayList;

    iput-object p4, p0, Lcom/google/research/reflection/layers/b$1;->ek:Lcom/google/research/reflection/layers/e;

    iput-object p5, p0, Lcom/google/research/reflection/layers/b$1;->el:Lcom/google/research/reflection/layers/e;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public final b(I)Ljava/lang/Boolean;
    .locals 32
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    move-object/from16 v0, p0

    .line 47
    new-instance v8, Lcom/google/research/reflection/layers/e;

    iget-object v2, v0, Lcom/google/research/reflection/layers/b$1;->em:Lcom/google/research/reflection/layers/b;

    iget v2, v2, Lcom/google/research/reflection/layers/b;->dF:I

    iget-object v3, v0, Lcom/google/research/reflection/layers/b$1;->em:Lcom/google/research/reflection/layers/b;

    iget v3, v3, Lcom/google/research/reflection/layers/b;->eh:I

    invoke-direct {v8, v2, v3}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    .line 48
    iget-object v2, v0, Lcom/google/research/reflection/layers/b$1;->em:Lcom/google/research/reflection/layers/b;

    iget v2, v2, Lcom/google/research/reflection/layers/b;->eh:I

    mul-int v9, p1, v2

    const/4 v10, 0x0

    const/4 v11, 0x0

    .line 49
    :goto_0
    iget-object v2, v0, Lcom/google/research/reflection/layers/b$1;->em:Lcom/google/research/reflection/layers/b;

    iget v2, v2, Lcom/google/research/reflection/layers/b;->dF:I

    if-ge v11, v2, :cond_1

    const/4 v12, 0x0

    .line 50
    :goto_1
    iget-object v2, v0, Lcom/google/research/reflection/layers/b$1;->em:Lcom/google/research/reflection/layers/b;

    iget v2, v2, Lcom/google/research/reflection/layers/b;->eh:I

    if-ge v12, v2, :cond_0

    const/4 v3, 0x0

    .line 51
    iget-object v2, v0, Lcom/google/research/reflection/layers/b$1;->em:Lcom/google/research/reflection/layers/b;

    iget-object v2, v2, Lcom/google/research/reflection/layers/b;->es:Lcom/google/research/reflection/layers/e;

    add-int v4, v9, v12

    invoke-virtual {v2, v10, v11, v4}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v6

    move-object v2, v8

    move v4, v11

    move v5, v12

    invoke-virtual/range {v2 .. v7}, Lcom/google/research/reflection/layers/e;->b(ZIID)V

    add-int/lit8 v12, v12, 0x1

    goto :goto_1

    :cond_0
    add-int/lit8 v11, v11, 0x1

    goto :goto_0

    .line 54
    :cond_1
    iget-object v2, v0, Lcom/google/research/reflection/layers/b$1;->em:Lcom/google/research/reflection/layers/b;

    iget-object v2, v2, Lcom/google/research/reflection/layers/b;->eg:[Lcom/google/research/reflection/layers/a;

    aget-object v1, v2, p1

    iget v2, v0, Lcom/google/research/reflection/layers/b$1;->ei:I

    iget-object v3, v0, Lcom/google/research/reflection/layers/b$1;->ej:[Ljava/util/ArrayList;

    iget-object v4, v0, Lcom/google/research/reflection/layers/b$1;->ek:Lcom/google/research/reflection/layers/e;

    iget-object v5, v0, Lcom/google/research/reflection/layers/b$1;->el:Lcom/google/research/reflection/layers/e;

    iget-object v6, v1, Lcom/google/research/reflection/layers/a;->dJ:Lcom/google/research/reflection/a/a;

    invoke-virtual {v6, v2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Lcom/google/research/reflection/layers/e;

    new-instance v7, Lcom/google/research/reflection/layers/e;

    iget v9, v1, Lcom/google/research/reflection/layers/a;->dF:I

    const/4 v15, 0x1

    invoke-direct {v7, v9, v15}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    const/4 v9, 0x0

    :goto_2
    iget v11, v1, Lcom/google/research/reflection/layers/a;->dF:I

    const-wide/high16 v16, 0x3ff0000000000000L    # 1.0

    if-ge v9, v11, :cond_3

    const/4 v11, 0x0

    const-wide/16 v12, 0x0

    :goto_3
    iget v14, v1, Lcom/google/research/reflection/layers/a;->dG:I

    if-ge v11, v14, :cond_2

    invoke-virtual {v6, v10, v9, v11}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v18

    invoke-virtual {v8, v10, v9, v11}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v20

    mul-double v18, v18, v20

    add-double v12, v12, v18

    add-int/lit8 v11, v11, 0x1

    goto :goto_3

    :cond_2
    iget-object v11, v1, Lcom/google/research/reflection/layers/a;->dM:Lcom/google/research/reflection/a/a;

    invoke-virtual {v11, v2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v11

    check-cast v11, Lcom/google/research/reflection/layers/e;

    invoke-virtual {v11, v10, v9, v10}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v18

    sub-double v16, v16, v18

    mul-double v16, v16, v18

    mul-double v16, v16, v12

    const/4 v12, 0x0

    const/4 v14, 0x0

    move-object v11, v7

    move v13, v9

    move-wide/from16 v15, v16

    invoke-virtual/range {v11 .. v16}, Lcom/google/research/reflection/layers/e;->b(ZIID)V

    add-int/lit8 v9, v9, 0x1

    const/4 v15, 0x1

    goto :goto_2

    :cond_3
    new-instance v9, Lcom/google/research/reflection/layers/e;

    iget v11, v1, Lcom/google/research/reflection/layers/a;->dF:I

    iget v14, v1, Lcom/google/research/reflection/layers/a;->dG:I

    invoke-direct {v9, v11, v14}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iget-object v11, v1, Lcom/google/research/reflection/layers/a;->dN:Lcom/google/research/reflection/a/a;

    add-int/lit8 v14, v2, 0x1

    invoke-virtual {v11, v14}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v11

    check-cast v11, Lcom/google/research/reflection/layers/e;

    iget-object v14, v1, Lcom/google/research/reflection/layers/a;->dK:Lcom/google/research/reflection/a/a;

    invoke-virtual {v14, v2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v14

    move-object/from16 v19, v14

    check-cast v19, Lcom/google/research/reflection/layers/e;

    const/4 v14, 0x0

    :goto_4
    iget v15, v1, Lcom/google/research/reflection/layers/a;->dF:I

    if-ge v14, v15, :cond_7

    iget-object v13, v1, Lcom/google/research/reflection/layers/a;->dM:Lcom/google/research/reflection/a/a;

    invoke-virtual {v13, v2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v13

    check-cast v13, Lcom/google/research/reflection/layers/e;

    invoke-virtual {v13, v10, v14, v10}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v20

    if-eqz v11, :cond_4

    invoke-virtual {v11, v10, v14, v10}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v23

    move-wide/from16 v29, v23

    goto :goto_5

    :cond_4
    const-wide/16 v29, 0x0

    :goto_5
    const/4 v13, 0x0

    :goto_6
    iget v15, v1, Lcom/google/research/reflection/layers/a;->dG:I

    if-ge v13, v15, :cond_6

    invoke-virtual {v6, v10, v14, v13}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v23

    mul-double v23, v23, v23

    sub-double v23, v16, v23

    mul-double v23, v23, v20

    invoke-virtual {v8, v10, v14, v13}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v25

    mul-double v23, v23, v25

    iget-object v15, v1, Lcom/google/research/reflection/layers/a;->dP:[Lcom/google/research/reflection/layers/e;

    aget-object v15, v15, v10

    if-eqz v15, :cond_5

    iget-object v15, v1, Lcom/google/research/reflection/layers/a;->dP:[Lcom/google/research/reflection/layers/e;

    aget-object v15, v15, v10

    invoke-virtual {v15, v10, v14, v13}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v25

    mul-double v25, v25, v29

    add-double v23, v23, v25

    iget-object v15, v1, Lcom/google/research/reflection/layers/a;->dS:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v15, v10, v13, v10}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v25

    iget-object v15, v1, Lcom/google/research/reflection/layers/a;->dP:[Lcom/google/research/reflection/layers/e;

    const/4 v12, 0x1

    aget-object v15, v15, v12

    invoke-virtual {v15, v10, v14, v10}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v27

    mul-double v25, v25, v27

    add-double v23, v23, v25

    iget-object v15, v1, Lcom/google/research/reflection/layers/a;->dS:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v15, v10, v13, v12}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v25

    iget-object v15, v1, Lcom/google/research/reflection/layers/a;->dP:[Lcom/google/research/reflection/layers/e;

    const/4 v12, 0x2

    aget-object v15, v15, v12

    invoke-virtual {v15, v10, v14, v10}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v27

    mul-double v25, v25, v27

    add-double v23, v23, v25

    goto :goto_7

    :cond_5
    const/4 v12, 0x2

    :goto_7
    iget-object v15, v1, Lcom/google/research/reflection/layers/a;->dS:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v15, v10, v13, v12}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v25

    invoke-virtual {v7, v10, v14, v10}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v27

    mul-double v25, v25, v27

    add-double v27, v23, v25

    const/16 v24, 0x0

    move-object/from16 v23, v9

    move/from16 v25, v14

    move/from16 v26, v13

    invoke-virtual/range {v23 .. v28}, Lcom/google/research/reflection/layers/e;->b(ZIID)V

    add-int/lit8 v13, v13, 0x1

    goto :goto_6

    :cond_6
    add-int/lit8 v14, v14, 0x1

    goto/16 :goto_4

    :cond_7
    iget-object v6, v1, Lcom/google/research/reflection/layers/a;->dO:Lcom/google/research/reflection/a/a;

    invoke-virtual {v6, v2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Lcom/google/research/reflection/layers/e;

    iget-object v8, v1, Lcom/google/research/reflection/layers/a;->dL:Lcom/google/research/reflection/a/a;

    invoke-virtual {v8, v2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v8

    check-cast v8, Lcom/google/research/reflection/layers/e;

    new-instance v11, Lcom/google/research/reflection/layers/e;

    iget v12, v1, Lcom/google/research/reflection/layers/a;->dF:I

    iget v13, v1, Lcom/google/research/reflection/layers/a;->dG:I

    invoke-direct {v11, v12, v13}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    const/4 v12, 0x0

    :goto_8
    iget v13, v1, Lcom/google/research/reflection/layers/a;->dF:I

    if-ge v12, v13, :cond_9

    invoke-virtual {v6, v10, v12, v10}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v13

    const/4 v15, 0x0

    :goto_9
    iget v0, v1, Lcom/google/research/reflection/layers/a;->dG:I

    if-ge v15, v0, :cond_8

    invoke-virtual {v8, v10, v12, v15}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v20

    mul-double v20, v20, v20

    sub-double v20, v16, v20

    mul-double v20, v20, v13

    invoke-virtual {v9, v10, v12, v15}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v22

    mul-double v24, v20, v22

    const/16 v21, 0x0

    move-object/from16 v20, v11

    move/from16 v22, v12

    move/from16 v23, v15

    invoke-virtual/range {v20 .. v25}, Lcom/google/research/reflection/layers/e;->b(ZIID)V

    add-int/lit8 v15, v15, 0x1

    goto :goto_9

    :cond_8
    add-int/lit8 v12, v12, 0x1

    move-object/from16 v0, p0

    goto :goto_8

    :cond_9
    new-instance v0, Lcom/google/research/reflection/layers/e;

    iget v6, v1, Lcom/google/research/reflection/layers/a;->dF:I

    const/4 v12, 0x1

    invoke-direct {v0, v6, v12}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iget-object v6, v1, Lcom/google/research/reflection/layers/a;->dN:Lcom/google/research/reflection/a/a;

    invoke-virtual {v6, v2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Lcom/google/research/reflection/layers/e;

    iget-object v12, v1, Lcom/google/research/reflection/layers/a;->dK:Lcom/google/research/reflection/a/a;

    add-int/lit8 v13, v2, -0x1

    invoke-virtual {v12, v13}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v12

    move-object v15, v12

    check-cast v15, Lcom/google/research/reflection/layers/e;

    const/4 v12, 0x0

    :goto_a
    iget v13, v1, Lcom/google/research/reflection/layers/a;->dF:I

    if-ge v12, v13, :cond_b

    invoke-virtual {v6, v10, v12, v10}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v13

    sub-double v20, v16, v13

    mul-double v20, v20, v13

    const/4 v13, 0x0

    const-wide/16 v22, 0x0

    :goto_b
    if-eqz v15, :cond_a

    iget v14, v1, Lcom/google/research/reflection/layers/a;->dG:I

    if-ge v13, v14, :cond_a

    invoke-virtual {v15, v10, v12, v13}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v24

    invoke-virtual {v9, v10, v12, v13}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v26

    mul-double v24, v24, v26

    add-double v22, v22, v24

    add-int/lit8 v13, v13, 0x1

    goto :goto_b

    :cond_a
    const/4 v13, 0x0

    const/4 v14, 0x0

    mul-double v24, v22, v20

    move-object/from16 v20, v0

    move/from16 v21, v13

    move/from16 v22, v12

    move/from16 v23, v14

    invoke-virtual/range {v20 .. v25}, Lcom/google/research/reflection/layers/e;->b(ZIID)V

    add-int/lit8 v12, v12, 0x1

    goto :goto_a

    :cond_b
    new-instance v6, Lcom/google/research/reflection/layers/e;

    iget v12, v1, Lcom/google/research/reflection/layers/a;->dF:I

    const/4 v13, 0x1

    invoke-direct {v6, v12, v13}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iget-object v12, v1, Lcom/google/research/reflection/layers/a;->dO:Lcom/google/research/reflection/a/a;

    invoke-virtual {v12, v2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/google/research/reflection/layers/e;

    const/4 v12, 0x0

    :goto_c
    iget v13, v1, Lcom/google/research/reflection/layers/a;->dF:I

    if-ge v12, v13, :cond_d

    invoke-virtual {v2, v10, v12, v10}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v13

    sub-double v20, v16, v13

    mul-double v20, v20, v13

    const/4 v13, 0x0

    const-wide/16 v22, 0x0

    :goto_d
    iget v14, v1, Lcom/google/research/reflection/layers/a;->dG:I

    if-ge v13, v14, :cond_c

    invoke-virtual {v8, v10, v12, v13}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v24

    invoke-virtual {v9, v10, v12, v13}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v26

    mul-double v24, v24, v26

    add-double v22, v22, v24

    add-int/lit8 v13, v13, 0x1

    goto :goto_d

    :cond_c
    const/4 v13, 0x0

    const/4 v14, 0x0

    mul-double v24, v22, v20

    move-object/from16 v20, v6

    move/from16 v21, v13

    move/from16 v22, v12

    move/from16 v23, v14

    invoke-virtual/range {v20 .. v25}, Lcom/google/research/reflection/layers/e;->b(ZIID)V

    add-int/lit8 v12, v12, 0x1

    goto :goto_c

    :cond_d
    const/4 v2, 0x5

    new-array v2, v2, [Lcom/google/research/reflection/layers/e;

    aput-object v9, v2, v10

    const/4 v8, 0x1

    aput-object v6, v2, v8

    const/4 v8, 0x2

    aput-object v0, v2, v8

    const/4 v9, 0x3

    aput-object v11, v2, v9

    const/16 v29, 0x4

    aput-object v7, v2, v29

    iput-object v2, v1, Lcom/google/research/reflection/layers/a;->dP:[Lcom/google/research/reflection/layers/e;

    invoke-static {v6, v0}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;

    move-result-object v0

    invoke-static {v0, v7}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;

    move-result-object v0

    invoke-static {v0, v11}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;

    move-result-object v0

    iget-object v2, v1, Lcom/google/research/reflection/layers/a;->dQ:Lcom/google/research/reflection/layers/e;

    iget-object v6, v1, Lcom/google/research/reflection/layers/a;->dV:Lcom/google/research/reflection/layers/e;

    invoke-static {v2, v6}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;

    move-result-object v2

    iget-object v6, v1, Lcom/google/research/reflection/layers/a;->ee:Lcom/google/research/reflection/layers/e;

    const/4 v7, 0x1

    invoke-static {v0, v2, v7, v6, v10}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;ZLcom/google/research/reflection/layers/e;Z)Lcom/google/research/reflection/layers/e;

    iget-object v2, v1, Lcom/google/research/reflection/layers/a;->dR:Lcom/google/research/reflection/layers/e;

    iget-object v6, v1, Lcom/google/research/reflection/layers/a;->dU:Lcom/google/research/reflection/layers/e;

    invoke-static {v2, v6}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;

    move-result-object v2

    iget-object v6, v1, Lcom/google/research/reflection/layers/a;->ef:Lcom/google/research/reflection/layers/e;

    invoke-static {v0, v2, v7, v6, v10}, Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;ZLcom/google/research/reflection/layers/e;Z)Lcom/google/research/reflection/layers/e;

    const/4 v12, 0x0

    iget-object v13, v1, Lcom/google/research/reflection/layers/a;->dX:Lcom/google/research/reflection/layers/e;

    iget v14, v1, Lcom/google/research/reflection/layers/a;->dH:I

    iget v0, v1, Lcom/google/research/reflection/layers/a;->dF:I

    iget-object v2, v1, Lcom/google/research/reflection/layers/a;->dP:[Lcom/google/research/reflection/layers/e;

    move-object v11, v1

    const/4 v6, 0x1

    const/4 v7, 0x2

    move-object v8, v15

    move v15, v0

    move-object/from16 v16, v2

    move-object/from16 v17, v3

    move-object/from16 v18, v4

    invoke-virtual/range {v11 .. v18}, Lcom/google/research/reflection/layers/a;->a(ZLcom/google/research/reflection/layers/e;II[Lcom/google/research/reflection/layers/e;[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;)V

    iget-object v13, v1, Lcom/google/research/reflection/layers/a;->dY:Lcom/google/research/reflection/layers/e;

    iget v14, v1, Lcom/google/research/reflection/layers/a;->dI:I

    iget v15, v1, Lcom/google/research/reflection/layers/a;->dF:I

    iget-object v0, v1, Lcom/google/research/reflection/layers/a;->dP:[Lcom/google/research/reflection/layers/e;

    const/16 v17, 0x0

    move-object/from16 v16, v0

    move-object/from16 v18, v5

    invoke-virtual/range {v11 .. v18}, Lcom/google/research/reflection/layers/a;->a(ZLcom/google/research/reflection/layers/e;II[Lcom/google/research/reflection/layers/e;[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;)V

    const/16 v20, 0x0

    iget-object v0, v1, Lcom/google/research/reflection/layers/a;->dZ:Lcom/google/research/reflection/layers/e;

    iget-object v2, v1, Lcom/google/research/reflection/layers/a;->dP:[Lcom/google/research/reflection/layers/e;

    aget-object v22, v2, v6

    const/16 v23, 0x0

    const/16 v24, 0x0

    iget v2, v1, Lcom/google/research/reflection/layers/a;->dG:I

    const/16 v27, 0x0

    iget v11, v1, Lcom/google/research/reflection/layers/a;->dF:I

    move-object/from16 v21, v0

    move-object/from16 v25, v8

    move/from16 v26, v2

    move/from16 v28, v11

    invoke-static/range {v20 .. v28}, Lcom/google/research/reflection/layers/a;->a(ZLcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;I[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;III)V

    iget-object v0, v1, Lcom/google/research/reflection/layers/a;->dZ:Lcom/google/research/reflection/layers/e;

    iget-object v2, v1, Lcom/google/research/reflection/layers/a;->dP:[Lcom/google/research/reflection/layers/e;

    aget-object v22, v2, v7

    iget v2, v1, Lcom/google/research/reflection/layers/a;->dG:I

    const/16 v27, 0x1

    iget v7, v1, Lcom/google/research/reflection/layers/a;->dF:I

    move-object/from16 v21, v0

    move/from16 v26, v2

    move/from16 v28, v7

    invoke-static/range {v20 .. v28}, Lcom/google/research/reflection/layers/a;->a(ZLcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;I[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;III)V

    iget-object v0, v1, Lcom/google/research/reflection/layers/a;->dZ:Lcom/google/research/reflection/layers/e;

    iget-object v2, v1, Lcom/google/research/reflection/layers/a;->dP:[Lcom/google/research/reflection/layers/e;

    aget-object v25, v2, v29

    const/16 v26, 0x0

    const/16 v27, 0x0

    iget v2, v1, Lcom/google/research/reflection/layers/a;->dG:I

    const/16 v30, 0x2

    iget v7, v1, Lcom/google/research/reflection/layers/a;->dF:I

    move-object/from16 v24, v0

    move-object/from16 v28, v19

    move/from16 v29, v2

    move/from16 v31, v7

    invoke-static/range {v23 .. v31}, Lcom/google/research/reflection/layers/a;->a(ZLcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;I[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;III)V

    const/4 v12, 0x1

    iget-object v13, v1, Lcom/google/research/reflection/layers/a;->ea:Lcom/google/research/reflection/layers/e;

    const/4 v14, 0x1

    iget v15, v1, Lcom/google/research/reflection/layers/a;->dF:I

    iget-object v0, v1, Lcom/google/research/reflection/layers/a;->dP:[Lcom/google/research/reflection/layers/e;

    const/16 v18, 0x0

    move-object v11, v1

    move-object/from16 v16, v0

    invoke-virtual/range {v11 .. v18}, Lcom/google/research/reflection/layers/a;->a(ZLcom/google/research/reflection/layers/e;II[Lcom/google/research/reflection/layers/e;[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;)V

    :goto_e
    iget v0, v1, Lcom/google/research/reflection/layers/a;->dG:I

    if-ge v10, v0, :cond_e

    const/4 v11, 0x0

    iget-object v12, v1, Lcom/google/research/reflection/layers/a;->ec:Lcom/google/research/reflection/layers/e;

    iget-object v0, v1, Lcom/google/research/reflection/layers/a;->dP:[Lcom/google/research/reflection/layers/e;

    aget-object v13, v0, v9

    iget v0, v1, Lcom/google/research/reflection/layers/a;->dH:I

    iget v2, v1, Lcom/google/research/reflection/layers/a;->dF:I

    move v14, v10

    move-object v15, v3

    move-object/from16 v16, v4

    move/from16 v17, v0

    move/from16 v18, v10

    move/from16 v19, v2

    invoke-static/range {v11 .. v19}, Lcom/google/research/reflection/layers/a;->a(ZLcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;I[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;III)V

    iget-object v12, v1, Lcom/google/research/reflection/layers/a;->eb:Lcom/google/research/reflection/layers/e;

    iget-object v0, v1, Lcom/google/research/reflection/layers/a;->dP:[Lcom/google/research/reflection/layers/e;

    aget-object v13, v0, v9

    const/4 v15, 0x0

    iget v0, v1, Lcom/google/research/reflection/layers/a;->dI:I

    iget v2, v1, Lcom/google/research/reflection/layers/a;->dF:I

    move-object/from16 v16, v5

    move/from16 v17, v0

    move/from16 v19, v2

    invoke-static/range {v11 .. v19}, Lcom/google/research/reflection/layers/a;->a(ZLcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;I[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;III)V

    const/16 v19, 0x1

    iget-object v0, v1, Lcom/google/research/reflection/layers/a;->ed:Lcom/google/research/reflection/layers/e;

    iget-object v2, v1, Lcom/google/research/reflection/layers/a;->dP:[Lcom/google/research/reflection/layers/e;

    aget-object v21, v2, v9

    const/16 v23, 0x0

    const/16 v24, 0x0

    const/16 v25, 0x1

    iget v2, v1, Lcom/google/research/reflection/layers/a;->dF:I

    move-object/from16 v20, v0

    move/from16 v22, v10

    move/from16 v26, v10

    move/from16 v27, v2

    invoke-static/range {v19 .. v27}, Lcom/google/research/reflection/layers/a;->a(ZLcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;I[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;III)V

    add-int/lit8 v10, v10, 0x1

    goto :goto_e

    .line 56
    :cond_e
    invoke-static {v6}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object v0

    return-object v0
.end method
