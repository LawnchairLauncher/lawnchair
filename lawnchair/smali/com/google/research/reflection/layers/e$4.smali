.class Lcom/google/research/reflection/layers/e$4;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/layers/h;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic eV:Lcom/google/research/reflection/layers/e;

.field final synthetic eY:Lcom/google/research/reflection/layers/e;


# direct methods
.method constructor <init>(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
    .locals 0

    .line 260
    iput-object p1, p0, Lcom/google/research/reflection/layers/e$4;->eY:Lcom/google/research/reflection/layers/e;

    iput-object p2, p0, Lcom/google/research/reflection/layers/e$4;->eV:Lcom/google/research/reflection/layers/e;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public final b(I)Ljava/lang/Boolean;
    .locals 6

    .line 263
    iget-object v0, p0, Lcom/google/research/reflection/layers/e$4;->eV:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v1, v0, p1

    const-wide/16 v3, 0x0

    cmpl-double v0, v1, v3

    if-eqz v0, :cond_0

    .line 264
    iget-object v0, p0, Lcom/google/research/reflection/layers/e$4;->eY:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v1, v0, p1

    iget-object v3, p0, Lcom/google/research/reflection/layers/e$4;->eV:Lcom/google/research/reflection/layers/e;

    iget-object v3, v3, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v4, v3, p1

    add-double/2addr v1, v4

    aput-wide v1, v0, p1

    :cond_0
    const/4 p1, 0x1

    .line 266
    invoke-static {p1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object p1

    return-object p1
.end method
