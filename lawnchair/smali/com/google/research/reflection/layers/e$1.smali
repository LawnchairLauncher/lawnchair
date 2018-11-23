.class Lcom/google/research/reflection/layers/e$1;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/layers/h;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Z)Lcom/google/research/reflection/layers/e;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic eS:Z

.field final synthetic eT:Lcom/google/research/reflection/layers/e;

.field final synthetic eU:Lcom/google/research/reflection/layers/e;

.field final synthetic eV:Lcom/google/research/reflection/layers/e;


# direct methods
.method constructor <init>(ZLcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
    .locals 0

    .line 60
    iput-boolean p1, p0, Lcom/google/research/reflection/layers/e$1;->eS:Z

    iput-object p2, p0, Lcom/google/research/reflection/layers/e$1;->eT:Lcom/google/research/reflection/layers/e;

    iput-object p3, p0, Lcom/google/research/reflection/layers/e$1;->eU:Lcom/google/research/reflection/layers/e;

    iput-object p4, p0, Lcom/google/research/reflection/layers/e$1;->eV:Lcom/google/research/reflection/layers/e;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public final b(I)Ljava/lang/Boolean;
    .locals 8

    .line 63
    iget-boolean v0, p0, Lcom/google/research/reflection/layers/e$1;->eS:Z

    if-eqz v0, :cond_0

    .line 64
    iget-object v0, p0, Lcom/google/research/reflection/layers/e$1;->eT:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v1, v0, p1

    iget-object v3, p0, Lcom/google/research/reflection/layers/e$1;->eU:Lcom/google/research/reflection/layers/e;

    iget-object v3, v3, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v4, v3, p1

    iget-object v3, p0, Lcom/google/research/reflection/layers/e$1;->eV:Lcom/google/research/reflection/layers/e;

    iget-object v3, v3, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v6, v3, p1

    add-double/2addr v4, v6

    add-double/2addr v1, v4

    aput-wide v1, v0, p1

    goto :goto_0

    .line 66
    :cond_0
    iget-object v0, p0, Lcom/google/research/reflection/layers/e$1;->eT:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    iget-object v1, p0, Lcom/google/research/reflection/layers/e$1;->eU:Lcom/google/research/reflection/layers/e;

    iget-object v1, v1, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v2, v1, p1

    iget-object v1, p0, Lcom/google/research/reflection/layers/e$1;->eV:Lcom/google/research/reflection/layers/e;

    iget-object v1, v1, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v4, v1, p1

    add-double/2addr v2, v4

    aput-wide v2, v0, p1

    :goto_0
    const/4 p1, 0x1

    .line 68
    invoke-static {p1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object p1

    return-object p1
.end method
