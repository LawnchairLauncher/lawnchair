.class Lcom/google/research/reflection/layers/e$2;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/layers/h;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/google/research/reflection/layers/e;->a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;ZLcom/google/research/reflection/layers/e;Z)Lcom/google/research/reflection/layers/e;
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

.field final synthetic eW:Z


# direct methods
.method constructor <init>(ZLcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Z)V
    .locals 0

    .line 84
    iput-boolean p1, p0, Lcom/google/research/reflection/layers/e$2;->eS:Z

    iput-object p2, p0, Lcom/google/research/reflection/layers/e$2;->eT:Lcom/google/research/reflection/layers/e;

    iput-object p3, p0, Lcom/google/research/reflection/layers/e$2;->eU:Lcom/google/research/reflection/layers/e;

    iput-object p4, p0, Lcom/google/research/reflection/layers/e$2;->eV:Lcom/google/research/reflection/layers/e;

    iput-boolean p5, p0, Lcom/google/research/reflection/layers/e$2;->eW:Z

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method private a(II)D
    .locals 9

    .line 98
    iget-object v0, p0, Lcom/google/research/reflection/layers/e$2;->eU:Lcom/google/research/reflection/layers/e;

    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v0

    const-wide/16 v2, 0x0

    move-wide v3, v2

    const/4 v2, 0x0

    :goto_0
    if-ge v2, v0, :cond_0

    .line 101
    iget-object v5, p0, Lcom/google/research/reflection/layers/e$2;->eU:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v5, v1, p1, v2}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v5

    .line 102
    iget-object v7, p0, Lcom/google/research/reflection/layers/e$2;->eV:Lcom/google/research/reflection/layers/e;

    iget-boolean v8, p0, Lcom/google/research/reflection/layers/e$2;->eW:Z

    invoke-virtual {v7, v8, v2, p2}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v7

    mul-double v5, v5, v7

    add-double/2addr v3, v5

    add-int/lit8 v2, v2, 0x1

    goto :goto_0

    :cond_0
    return-wide v3
.end method


# virtual methods
.method public final b(I)Ljava/lang/Boolean;
    .locals 6

    .line 87
    iget-boolean v0, p0, Lcom/google/research/reflection/layers/e$2;->eS:Z

    const/4 v1, 0x0

    if-eqz v0, :cond_0

    .line 88
    iget-object v0, p0, Lcom/google/research/reflection/layers/e$2;->eT:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v2, v0, p1

    iget-object v4, p0, Lcom/google/research/reflection/layers/e$2;->eT:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v4, v1}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v4

    div-int v4, p1, v4

    iget-object v5, p0, Lcom/google/research/reflection/layers/e$2;->eT:Lcom/google/research/reflection/layers/e;

    .line 89
    invoke-virtual {v5, v1}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v1

    rem-int v1, p1, v1

    .line 88
    invoke-direct {p0, v4, v1}, Lcom/google/research/reflection/layers/e$2;->a(II)D

    move-result-wide v4

    add-double/2addr v2, v4

    aput-wide v2, v0, p1

    goto :goto_0

    .line 91
    :cond_0
    iget-object v0, p0, Lcom/google/research/reflection/layers/e$2;->eT:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    iget-object v2, p0, Lcom/google/research/reflection/layers/e$2;->eT:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v2, v1}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v2

    div-int v2, p1, v2

    iget-object v3, p0, Lcom/google/research/reflection/layers/e$2;->eT:Lcom/google/research/reflection/layers/e;

    .line 92
    invoke-virtual {v3, v1}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v1

    rem-int v1, p1, v1

    .line 91
    invoke-direct {p0, v2, v1}, Lcom/google/research/reflection/layers/e$2;->a(II)D

    move-result-wide v1

    aput-wide v1, v0, p1

    :goto_0
    const/4 p1, 0x1

    .line 94
    invoke-static {p1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object p1

    return-object p1
.end method
