.class Lcom/google/research/reflection/layers/e$3;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/layers/h;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/google/research/reflection/layers/e;->a(D)Lcom/google/research/reflection/layers/e;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic eX:D

.field final synthetic eY:Lcom/google/research/reflection/layers/e;


# direct methods
.method constructor <init>(Lcom/google/research/reflection/layers/e;D)V
    .locals 0

    .line 242
    iput-object p1, p0, Lcom/google/research/reflection/layers/e$3;->eY:Lcom/google/research/reflection/layers/e;

    iput-wide p2, p0, Lcom/google/research/reflection/layers/e$3;->eX:D

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public final b(I)Ljava/lang/Boolean;
    .locals 5

    .line 245
    iget-object v0, p0, Lcom/google/research/reflection/layers/e$3;->eY:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v1, v0, p1

    const-wide/16 v3, 0x0

    cmpl-double v0, v1, v3

    if-eqz v0, :cond_0

    .line 246
    iget-object v0, p0, Lcom/google/research/reflection/layers/e$3;->eY:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v1, v0, p1

    iget-wide v3, p0, Lcom/google/research/reflection/layers/e$3;->eX:D

    mul-double v1, v1, v3

    aput-wide v1, v0, p1

    :cond_0
    const/4 p1, 0x1

    .line 248
    invoke-static {p1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object p1

    return-object p1
.end method
