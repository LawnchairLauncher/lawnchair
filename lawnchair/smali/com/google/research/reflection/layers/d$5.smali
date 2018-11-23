.class Lcom/google/research/reflection/layers/d$5;
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
.field final synthetic eL:I

.field final synthetic eM:Lcom/google/research/reflection/layers/e;

.field final synthetic eQ:Lcom/google/research/reflection/layers/e;


# direct methods
.method constructor <init>(ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
    .locals 0

    .line 316
    iput p1, p0, Lcom/google/research/reflection/layers/d$5;->eL:I

    iput-object p2, p0, Lcom/google/research/reflection/layers/d$5;->eQ:Lcom/google/research/reflection/layers/e;

    iput-object p3, p0, Lcom/google/research/reflection/layers/d$5;->eM:Lcom/google/research/reflection/layers/e;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public final b(I)Ljava/lang/Boolean;
    .locals 7

    .line 319
    iget v0, p0, Lcom/google/research/reflection/layers/d$5;->eL:I

    const/4 v1, 0x1

    if-ne v0, v1, :cond_1

    .line 320
    iget-object v0, p0, Lcom/google/research/reflection/layers/d$5;->eQ:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    iget-object v2, p0, Lcom/google/research/reflection/layers/d$5;->eM:Lcom/google/research/reflection/layers/e;

    iget-object v2, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v3, v2, p1

    const-wide/16 v5, 0x0

    cmpl-double v2, v3, v5

    if-lez v2, :cond_0

    goto :goto_0

    :cond_0
    move-wide v3, v5

    .line 321
    :goto_0
    aput-wide v3, v0, p1

    goto :goto_1

    .line 323
    :cond_1
    iget-object v0, p0, Lcom/google/research/reflection/layers/d$5;->eQ:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    iget-object v2, p0, Lcom/google/research/reflection/layers/d$5;->eM:Lcom/google/research/reflection/layers/e;

    iget-object v2, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v3, v2, p1

    .line 324
    invoke-static {v3, v4}, Lcom/google/research/reflection/layers/j;->b(D)D

    move-result-wide v2

    aput-wide v2, v0, p1

    .line 326
    :goto_1
    invoke-static {v1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object p1

    return-object p1
.end method
