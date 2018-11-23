.class Lcom/google/research/reflection/layers/i$a;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Ljava/util/concurrent/Callable;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/research/reflection/layers/i;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0xa
    name = "a"
.end annotation

.annotation system Ldalvik/annotation/Signature;
    value = {
        "Ljava/lang/Object;",
        "Ljava/util/concurrent/Callable<",
        "Ljava/lang/Boolean;",
        ">;"
    }
.end annotation


# instance fields
.field fl:I

.field fm:Lcom/google/research/reflection/layers/h;

.field max:I

.field private taskId:I


# direct methods
.method public constructor <init>(IIILcom/google/research/reflection/layers/h;)V
    .locals 0

    .line 50
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 51
    iput p1, p0, Lcom/google/research/reflection/layers/i$a;->taskId:I

    .line 52
    iput p2, p0, Lcom/google/research/reflection/layers/i$a;->fl:I

    .line 53
    iput p3, p0, Lcom/google/research/reflection/layers/i$a;->max:I

    .line 54
    iput-object p4, p0, Lcom/google/research/reflection/layers/i$a;->fm:Lcom/google/research/reflection/layers/h;

    return-void
.end method


# virtual methods
.method public synthetic call()Ljava/lang/Object;
    .locals 5
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/Exception;
        }
    .end annotation

    .line 43
    iget v0, p0, Lcom/google/research/reflection/layers/i$a;->fl:I

    iget v1, p0, Lcom/google/research/reflection/layers/i$a;->taskId:I

    mul-int v0, v0, v1

    iget v1, p0, Lcom/google/research/reflection/layers/i$a;->max:I

    iget v2, p0, Lcom/google/research/reflection/layers/i$a;->taskId:I

    const/4 v3, 0x1

    add-int/2addr v2, v3

    iget v4, p0, Lcom/google/research/reflection/layers/i$a;->fl:I

    mul-int v2, v2, v4

    invoke-static {v1, v2}, Ljava/lang/Math;->min(II)I

    move-result v1

    :goto_0
    if-ge v0, v1, :cond_0

    iget-object v2, p0, Lcom/google/research/reflection/layers/i$a;->fm:Lcom/google/research/reflection/layers/h;

    invoke-interface {v2, v0}, Lcom/google/research/reflection/layers/h;->b(I)Ljava/lang/Boolean;

    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    :cond_0
    invoke-static {v3}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object v0

    return-object v0
.end method
