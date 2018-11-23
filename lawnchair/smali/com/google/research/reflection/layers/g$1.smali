.class Lcom/google/research/reflection/layers/g$1;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/layers/h;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/google/research/reflection/layers/g;->a(ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic eL:I

.field final synthetic eM:Lcom/google/research/reflection/layers/e;

.field final synthetic eN:Lcom/google/research/reflection/layers/e;

.field final synthetic fd:Lcom/google/research/reflection/layers/e;

.field final synthetic fe:Lcom/google/research/reflection/layers/g;


# direct methods
.method constructor <init>(Lcom/google/research/reflection/layers/g;ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
    .locals 0

    .line 38
    iput-object p1, p0, Lcom/google/research/reflection/layers/g$1;->fe:Lcom/google/research/reflection/layers/g;

    iput p2, p0, Lcom/google/research/reflection/layers/g$1;->eL:I

    iput-object p3, p0, Lcom/google/research/reflection/layers/g$1;->eN:Lcom/google/research/reflection/layers/e;

    iput-object p4, p0, Lcom/google/research/reflection/layers/g$1;->eM:Lcom/google/research/reflection/layers/e;

    iput-object p5, p0, Lcom/google/research/reflection/layers/g$1;->fd:Lcom/google/research/reflection/layers/e;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public final b(I)Ljava/lang/Boolean;
    .locals 9

    .line 41
    iget v0, p0, Lcom/google/research/reflection/layers/g$1;->eL:I

    const/4 v1, 0x1

    if-nez v0, :cond_1

    .line 42
    iget-object v0, p0, Lcom/google/research/reflection/layers/g$1;->fe:Lcom/google/research/reflection/layers/g;

    invoke-static {v0}, Lcom/google/research/reflection/layers/g;->a(Lcom/google/research/reflection/layers/g;)I

    move-result v0

    if-nez v0, :cond_0

    .line 43
    iget-object v0, p0, Lcom/google/research/reflection/layers/g$1;->eN:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    iget-object v2, p0, Lcom/google/research/reflection/layers/g$1;->eM:Lcom/google/research/reflection/layers/e;

    iget-object v2, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v3, v2, p1

    const-wide/high16 v5, 0x3ff0000000000000L    # 1.0

    iget-object v2, p0, Lcom/google/research/reflection/layers/g$1;->eM:Lcom/google/research/reflection/layers/e;

    iget-object v2, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v7, v2, p1

    sub-double/2addr v5, v7

    mul-double v3, v3, v5

    iget-object v2, p0, Lcom/google/research/reflection/layers/g$1;->eM:Lcom/google/research/reflection/layers/e;

    iget-object v2, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v5, v2, p1

    iget-object v2, p0, Lcom/google/research/reflection/layers/g$1;->eN:Lcom/google/research/reflection/layers/e;

    iget-object v2, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v7, v2, p1

    sub-double/2addr v5, v7

    mul-double v3, v3, v5

    aput-wide v3, v0, p1

    goto :goto_0

    .line 46
    :cond_0
    iget-object v0, p0, Lcom/google/research/reflection/layers/g$1;->fe:Lcom/google/research/reflection/layers/g;

    invoke-static {v0}, Lcom/google/research/reflection/layers/g;->a(Lcom/google/research/reflection/layers/g;)I

    move-result v0

    if-ne v0, v1, :cond_2

    .line 47
    iget-object v0, p0, Lcom/google/research/reflection/layers/g$1;->eN:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    iget-object v2, p0, Lcom/google/research/reflection/layers/g$1;->eM:Lcom/google/research/reflection/layers/e;

    iget-object v2, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v3, v2, p1

    iget-object v2, p0, Lcom/google/research/reflection/layers/g$1;->eN:Lcom/google/research/reflection/layers/e;

    iget-object v2, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v5, v2, p1

    sub-double/2addr v3, v5

    aput-wide v3, v0, p1

    goto :goto_0

    .line 49
    :cond_1
    iget v0, p0, Lcom/google/research/reflection/layers/g$1;->eL:I

    const/4 v2, 0x2

    if-ne v0, v2, :cond_4

    .line 50
    iget-object v0, p0, Lcom/google/research/reflection/layers/g$1;->eN:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    iget-object v2, p0, Lcom/google/research/reflection/layers/g$1;->eM:Lcom/google/research/reflection/layers/e;

    iget-object v2, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v3, v2, p1

    iget-object v2, p0, Lcom/google/research/reflection/layers/g$1;->eN:Lcom/google/research/reflection/layers/e;

    iget-object v2, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v5, v2, p1

    sub-double/2addr v3, v5

    aput-wide v3, v0, p1

    .line 54
    :cond_2
    :goto_0
    iget-object v0, p0, Lcom/google/research/reflection/layers/g$1;->fd:Lcom/google/research/reflection/layers/e;

    if-eqz v0, :cond_3

    .line 55
    iget-object v0, p0, Lcom/google/research/reflection/layers/g$1;->eN:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v2, v0, p1

    iget-object v4, p0, Lcom/google/research/reflection/layers/g$1;->fd:Lcom/google/research/reflection/layers/e;

    iget-object v4, v4, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v5, v4, p1

    mul-double v2, v2, v5

    aput-wide v2, v0, p1

    .line 57
    :cond_3
    invoke-static {v1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object p1

    return-object p1

    .line 52
    :cond_4
    new-instance p1, Ljava/lang/RuntimeException;

    const-string v0, "unsupported activation function for the output layer"

    invoke-direct {p1, v0}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw p1
.end method
