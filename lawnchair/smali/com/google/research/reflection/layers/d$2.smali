.class Lcom/google/research/reflection/layers/d$2;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/layers/h;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/google/research/reflection/layers/d;->a(ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic eL:I

.field final synthetic eM:Lcom/google/research/reflection/layers/e;

.field final synthetic eN:Lcom/google/research/reflection/layers/e;


# direct methods
.method constructor <init>(ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
    .locals 0

    .line 170
    iput p1, p0, Lcom/google/research/reflection/layers/d$2;->eL:I

    iput-object p2, p0, Lcom/google/research/reflection/layers/d$2;->eM:Lcom/google/research/reflection/layers/e;

    iput-object p3, p0, Lcom/google/research/reflection/layers/d$2;->eN:Lcom/google/research/reflection/layers/e;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public final b(I)Ljava/lang/Boolean;
    .locals 9

    .line 173
    iget v0, p0, Lcom/google/research/reflection/layers/d$2;->eL:I

    const/4 v1, 0x1

    if-ne v0, v1, :cond_0

    .line 174
    iget-object v0, p0, Lcom/google/research/reflection/layers/d$2;->eM:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v2, v0, p1

    const-wide/16 v4, 0x0

    cmpl-double v0, v2, v4

    if-nez v0, :cond_1

    .line 175
    iget-object v0, p0, Lcom/google/research/reflection/layers/d$2;->eN:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    aput-wide v4, v0, p1

    goto :goto_0

    .line 177
    :cond_0
    iget v0, p0, Lcom/google/research/reflection/layers/d$2;->eL:I

    if-nez v0, :cond_2

    .line 178
    iget-object v0, p0, Lcom/google/research/reflection/layers/d$2;->eN:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    iget-object v2, p0, Lcom/google/research/reflection/layers/d$2;->eM:Lcom/google/research/reflection/layers/e;

    iget-object v2, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v3, v2, p1

    const-wide/high16 v5, 0x3ff0000000000000L    # 1.0

    iget-object v2, p0, Lcom/google/research/reflection/layers/d$2;->eM:Lcom/google/research/reflection/layers/e;

    iget-object v2, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v7, v2, p1

    sub-double/2addr v5, v7

    mul-double v3, v3, v5

    iget-object v2, p0, Lcom/google/research/reflection/layers/d$2;->eN:Lcom/google/research/reflection/layers/e;

    iget-object v2, v2, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v5, v2, p1

    mul-double v3, v3, v5

    aput-wide v3, v0, p1

    .line 183
    :cond_1
    :goto_0
    invoke-static {v1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object p1

    return-object p1

    .line 181
    :cond_2
    new-instance p1, Ljava/lang/RuntimeException;

    iget v0, p0, Lcom/google/research/reflection/layers/d$2;->eL:I

    const/16 v1, 0x2c

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2, v1}, Ljava/lang/StringBuilder;-><init>(I)V

    const-string v1, "Unsupported activation function: "

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-direct {p1, v0}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw p1
.end method
