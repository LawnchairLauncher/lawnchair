.class Lcom/google/research/reflection/layers/d$1;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/layers/h;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/google/research/reflection/layers/d;->a(Lcom/google/research/reflection/layers/f;ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic eJ:Lcom/google/research/reflection/layers/e;

.field final synthetic eK:Lcom/google/research/reflection/layers/d;

.field final synthetic ej:[Ljava/util/ArrayList;

.field final synthetic el:Lcom/google/research/reflection/layers/e;


# direct methods
.method constructor <init>(Lcom/google/research/reflection/layers/d;[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
    .locals 0

    .line 136
    iput-object p1, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    iput-object p2, p0, Lcom/google/research/reflection/layers/d$1;->ej:[Ljava/util/ArrayList;

    iput-object p3, p0, Lcom/google/research/reflection/layers/d$1;->eJ:Lcom/google/research/reflection/layers/e;

    iput-object p4, p0, Lcom/google/research/reflection/layers/d$1;->el:Lcom/google/research/reflection/layers/e;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public final b(I)Ljava/lang/Boolean;
    .locals 12

    .line 139
    iget-object v0, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    iget v0, v0, Lcom/google/research/reflection/layers/d;->ew:I

    div-int v0, p1, v0

    .line 140
    iget-object v1, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    iget v1, v1, Lcom/google/research/reflection/layers/d;->ew:I

    rem-int/2addr p1, v1

    .line 141
    iget-object v1, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    iget-object v1, v1, Lcom/google/research/reflection/layers/d;->es:Lcom/google/research/reflection/layers/e;

    iget-object v1, v1, Lcom/google/research/reflection/layers/e;->eR:[D

    iget-object v2, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    iget v2, v2, Lcom/google/research/reflection/layers/d;->ew:I

    mul-int v2, v2, v0

    add-int/2addr v2, p1

    aget-wide v2, v1, v2

    .line 143
    iget-object v1, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    iget-boolean v1, v1, Lcom/google/research/reflection/layers/d;->ev:Z

    const/4 v4, 0x0

    if-eqz v1, :cond_0

    .line 144
    iget-object v1, p0, Lcom/google/research/reflection/layers/d$1;->ej:[Ljava/util/ArrayList;

    aget-object v1, v1, v0

    invoke-virtual {v1}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object v1

    :goto_0
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z

    move-result v5

    if-eqz v5, :cond_1

    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Lcom/google/research/reflection/a/d;

    .line 145
    iget-object v6, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    invoke-static {v6}, Lcom/google/research/reflection/layers/d;->b(Lcom/google/research/reflection/layers/d;)Lcom/google/research/reflection/layers/e;

    move-result-object v6

    iget-object v6, v6, Lcom/google/research/reflection/layers/e;->eR:[D

    iget v7, v5, Lcom/google/research/reflection/a/d;->index:I

    iget-object v8, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    iget v8, v8, Lcom/google/research/reflection/layers/d;->ew:I

    mul-int v7, v7, v8

    add-int/2addr v7, p1

    aget-wide v8, v6, v7

    iget v5, v5, Lcom/google/research/reflection/a/d;->value:F

    float-to-double v10, v5

    mul-double v10, v10, v2

    add-double/2addr v8, v10

    aput-wide v8, v6, v7

    goto :goto_0

    :cond_0
    const/4 v1, 0x0

    .line 149
    :goto_1
    iget-object v5, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    iget v5, v5, Lcom/google/research/reflection/layers/d;->dH:I

    if-ge v1, v5, :cond_1

    .line 150
    iget-object v5, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    invoke-static {v5}, Lcom/google/research/reflection/layers/d;->b(Lcom/google/research/reflection/layers/d;)Lcom/google/research/reflection/layers/e;

    move-result-object v5

    iget-object v5, v5, Lcom/google/research/reflection/layers/e;->eR:[D

    iget-object v6, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    iget v6, v6, Lcom/google/research/reflection/layers/d;->ew:I

    mul-int v6, v6, v1

    add-int/2addr v6, p1

    aget-wide v7, v5, v6

    iget-object v9, p0, Lcom/google/research/reflection/layers/d$1;->eJ:Lcom/google/research/reflection/layers/e;

    iget-object v9, v9, Lcom/google/research/reflection/layers/e;->eR:[D

    iget-object v10, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    iget v10, v10, Lcom/google/research/reflection/layers/d;->dH:I

    mul-int v10, v10, v0

    add-int/2addr v10, v1

    aget-wide v10, v9, v10

    mul-double v10, v10, v2

    add-double/2addr v7, v10

    aput-wide v7, v5, v6

    add-int/lit8 v1, v1, 0x1

    goto :goto_1

    .line 155
    :cond_1
    iget-object v1, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    iget-boolean v1, v1, Lcom/google/research/reflection/layers/d;->ex:Z

    if-eqz v1, :cond_2

    iget-object v1, p0, Lcom/google/research/reflection/layers/d$1;->el:Lcom/google/research/reflection/layers/e;

    if-eqz v1, :cond_2

    .line 156
    :goto_2
    iget-object v1, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    iget v1, v1, Lcom/google/research/reflection/layers/d;->ew:I

    if-ge v4, v1, :cond_2

    .line 157
    iget-object v1, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    invoke-static {v1}, Lcom/google/research/reflection/layers/d;->c(Lcom/google/research/reflection/layers/d;)Lcom/google/research/reflection/layers/e;

    move-result-object v1

    iget-object v1, v1, Lcom/google/research/reflection/layers/e;->eR:[D

    iget-object v5, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    iget v5, v5, Lcom/google/research/reflection/layers/d;->ew:I

    mul-int v5, v5, v4

    add-int/2addr v5, p1

    aget-wide v6, v1, v5

    iget-object v8, p0, Lcom/google/research/reflection/layers/d$1;->el:Lcom/google/research/reflection/layers/e;

    iget-object v8, v8, Lcom/google/research/reflection/layers/e;->eR:[D

    iget-object v9, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    iget v9, v9, Lcom/google/research/reflection/layers/d;->ew:I

    mul-int v9, v9, v0

    add-int/2addr v9, v4

    aget-wide v9, v8, v9

    mul-double v9, v9, v2

    add-double/2addr v6, v9

    aput-wide v6, v1, v5

    add-int/lit8 v4, v4, 0x1

    goto :goto_2

    .line 161
    :cond_2
    iget-object v0, p0, Lcom/google/research/reflection/layers/d$1;->eK:Lcom/google/research/reflection/layers/d;

    invoke-static {v0}, Lcom/google/research/reflection/layers/d;->d(Lcom/google/research/reflection/layers/d;)Lcom/google/research/reflection/layers/e;

    move-result-object v0

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v4, v0, p1

    add-double/2addr v4, v2

    aput-wide v4, v0, p1

    const/4 p1, 0x1

    .line 162
    invoke-static {p1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object p1

    return-object p1
.end method
