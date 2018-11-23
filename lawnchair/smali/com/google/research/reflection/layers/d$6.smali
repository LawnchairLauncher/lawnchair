.class Lcom/google/research/reflection/layers/d$6;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/layers/h;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/research/reflection/layers/d;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic eM:Lcom/google/research/reflection/layers/e;

.field final synthetic eQ:Lcom/google/research/reflection/layers/e;


# direct methods
.method constructor <init>(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
    .locals 0

    .line 330
    iput-object p1, p0, Lcom/google/research/reflection/layers/d$6;->eM:Lcom/google/research/reflection/layers/e;

    iput-object p2, p0, Lcom/google/research/reflection/layers/d$6;->eQ:Lcom/google/research/reflection/layers/e;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public final b(I)Ljava/lang/Boolean;
    .locals 17

    move-object/from16 v0, p0

    move/from16 v1, p1

    .line 333
    iget-object v2, v0, Lcom/google/research/reflection/layers/d$6;->eM:Lcom/google/research/reflection/layers/e;

    iget-object v3, v0, Lcom/google/research/reflection/layers/d$6;->eQ:Lcom/google/research/reflection/layers/e;

    const/4 v4, 0x0

    invoke-virtual {v2, v4}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v5

    invoke-virtual {v2, v4, v1, v4}, Lcom/google/research/reflection/layers/e;->a(ZII)I

    move-result v6

    invoke-virtual {v2, v4, v1, v5}, Lcom/google/research/reflection/layers/e;->a(ZII)I

    move-result v1

    const-wide v7, -0x10000000000001L

    move-wide v8, v7

    move v7, v6

    :goto_0
    if-ge v7, v1, :cond_1

    iget-object v10, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v11, v10, v7

    cmpl-double v10, v11, v8

    if-lez v10, :cond_0

    iget-object v8, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v9, v8, v7

    move-wide v8, v9

    :cond_0
    add-int/lit8 v7, v7, 0x1

    goto :goto_0

    :cond_1
    const-wide/16 v10, 0x0

    move-wide v12, v10

    :goto_1
    if-ge v6, v1, :cond_2

    iget-object v7, v3, Lcom/google/research/reflection/layers/e;->eR:[D

    iget-object v14, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v15, v14, v6

    sub-double v14, v15, v8

    invoke-static {v14, v15}, Ljava/lang/Math;->exp(D)D

    move-result-wide v14

    aput-wide v14, v7, v6

    iget-object v7, v3, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v14, v7, v6

    add-double/2addr v12, v14

    add-int/lit8 v6, v6, 0x1

    goto :goto_1

    :cond_2
    cmpl-double v1, v12, v10

    if-eqz v1, :cond_4

    :goto_2
    if-ge v4, v5, :cond_3

    iget-object v1, v3, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v6, v1, v4

    div-double/2addr v6, v12

    aput-wide v6, v1, v4

    add-int/lit8 v4, v4, 0x1

    goto :goto_2

    :cond_3
    const/4 v1, 0x1

    .line 334
    invoke-static {v1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object v1

    return-object v1

    .line 333
    :cond_4
    new-instance v1, Ljava/lang/RuntimeException;

    const-string v2, "softmax sum = 0"

    invoke-direct {v1, v2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1
.end method
