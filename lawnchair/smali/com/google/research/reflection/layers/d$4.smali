.class Lcom/google/research/reflection/layers/d$4;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/layers/h;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/google/research/reflection/layers/d;->a(ZLcom/google/research/reflection/layers/f;[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic eK:Lcom/google/research/reflection/layers/d;

.field final synthetic eO:Lcom/google/research/reflection/layers/f;

.field final synthetic eP:Lcom/google/research/reflection/layers/e;

.field final synthetic ej:[Ljava/util/ArrayList;

.field final synthetic ek:Lcom/google/research/reflection/layers/e;

.field final synthetic el:Lcom/google/research/reflection/layers/e;


# direct methods
.method constructor <init>(Lcom/google/research/reflection/layers/d;Lcom/google/research/reflection/layers/f;[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
    .locals 0

    .line 264
    iput-object p1, p0, Lcom/google/research/reflection/layers/d$4;->eK:Lcom/google/research/reflection/layers/d;

    iput-object p2, p0, Lcom/google/research/reflection/layers/d$4;->eO:Lcom/google/research/reflection/layers/f;

    iput-object p3, p0, Lcom/google/research/reflection/layers/d$4;->ej:[Ljava/util/ArrayList;

    iput-object p4, p0, Lcom/google/research/reflection/layers/d$4;->ek:Lcom/google/research/reflection/layers/e;

    iput-object p5, p0, Lcom/google/research/reflection/layers/d$4;->el:Lcom/google/research/reflection/layers/e;

    iput-object p6, p0, Lcom/google/research/reflection/layers/d$4;->eP:Lcom/google/research/reflection/layers/e;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public final b(I)Ljava/lang/Boolean;
    .locals 12
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    .line 267
    iget-object v0, p0, Lcom/google/research/reflection/layers/d$4;->eK:Lcom/google/research/reflection/layers/d;

    iget v0, v0, Lcom/google/research/reflection/layers/d;->ew:I

    div-int v0, p1, v0

    .line 268
    iget-object v1, p0, Lcom/google/research/reflection/layers/d$4;->eK:Lcom/google/research/reflection/layers/d;

    iget v1, v1, Lcom/google/research/reflection/layers/d;->ew:I

    rem-int/2addr p1, v1

    .line 269
    iget-object v1, p0, Lcom/google/research/reflection/layers/d$4;->eK:Lcom/google/research/reflection/layers/d;

    iget-object v2, p0, Lcom/google/research/reflection/layers/d$4;->eO:Lcom/google/research/reflection/layers/f;

    invoke-virtual {v1, v2}, Lcom/google/research/reflection/layers/d;->a(Lcom/google/research/reflection/layers/f;)Lcom/google/research/reflection/layers/e;

    move-result-object v1

    .line 270
    iget-object v2, p0, Lcom/google/research/reflection/layers/d$4;->eK:Lcom/google/research/reflection/layers/d;

    iget v2, v2, Lcom/google/research/reflection/layers/d;->ew:I

    mul-int v2, v2, v0

    .line 271
    iget-object v3, p0, Lcom/google/research/reflection/layers/d$4;->eK:Lcom/google/research/reflection/layers/d;

    invoke-static {v3}, Lcom/google/research/reflection/layers/d;->h(Lcom/google/research/reflection/layers/d;)Lcom/google/research/reflection/layers/e;

    move-result-object v3

    iget-object v3, v3, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v4, v3, p1

    .line 272
    iget-object v3, p0, Lcom/google/research/reflection/layers/d$4;->eK:Lcom/google/research/reflection/layers/d;

    iget-boolean v3, v3, Lcom/google/research/reflection/layers/d;->ev:Z

    const/4 v6, 0x0

    if-eqz v3, :cond_0

    .line 273
    iget-object v3, p0, Lcom/google/research/reflection/layers/d$4;->ej:[Ljava/util/ArrayList;

    aget-object v3, v3, v0

    invoke-virtual {v3}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object v3

    :goto_0
    invoke-interface {v3}, Ljava/util/Iterator;->hasNext()Z

    move-result v7

    if-eqz v7, :cond_1

    invoke-interface {v3}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Lcom/google/research/reflection/a/d;

    .line 274
    iget v8, v7, Lcom/google/research/reflection/a/d;->value:F

    float-to-double v8, v8

    iget-object v10, p0, Lcom/google/research/reflection/layers/d$4;->eK:Lcom/google/research/reflection/layers/d;

    invoke-static {v10}, Lcom/google/research/reflection/layers/d;->e(Lcom/google/research/reflection/layers/d;)Z

    move-result v10

    iget v7, v7, Lcom/google/research/reflection/a/d;->index:I

    invoke-virtual {v1, v10, v7, p1}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v10

    mul-double v8, v8, v10

    add-double/2addr v4, v8

    goto :goto_0

    :cond_0
    const/4 v3, 0x0

    .line 277
    :goto_1
    iget-object v7, p0, Lcom/google/research/reflection/layers/d$4;->eK:Lcom/google/research/reflection/layers/d;

    iget v7, v7, Lcom/google/research/reflection/layers/d;->dH:I

    if-ge v3, v7, :cond_1

    .line 278
    iget-object v7, p0, Lcom/google/research/reflection/layers/d$4;->ek:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v7, v6, v0, v3}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v7

    iget-object v9, p0, Lcom/google/research/reflection/layers/d$4;->eK:Lcom/google/research/reflection/layers/d;

    .line 279
    invoke-static {v9}, Lcom/google/research/reflection/layers/d;->e(Lcom/google/research/reflection/layers/d;)Z

    move-result v9

    invoke-virtual {v1, v9, v3, p1}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v9

    mul-double v7, v7, v9

    add-double/2addr v4, v7

    add-int/lit8 v3, v3, 0x1

    goto :goto_1

    .line 282
    :cond_1
    iget-object v1, p0, Lcom/google/research/reflection/layers/d$4;->eK:Lcom/google/research/reflection/layers/d;

    iget-boolean v1, v1, Lcom/google/research/reflection/layers/d;->ex:Z

    if-eqz v1, :cond_2

    iget-object v1, p0, Lcom/google/research/reflection/layers/d$4;->el:Lcom/google/research/reflection/layers/e;

    if-eqz v1, :cond_2

    const/4 v1, 0x0

    .line 283
    :goto_2
    iget-object v3, p0, Lcom/google/research/reflection/layers/d$4;->eK:Lcom/google/research/reflection/layers/d;

    iget v3, v3, Lcom/google/research/reflection/layers/d;->ew:I

    if-ge v1, v3, :cond_2

    .line 284
    iget-object v3, p0, Lcom/google/research/reflection/layers/d$4;->el:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v3, v6, v0, v1}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v7

    iget-object v3, p0, Lcom/google/research/reflection/layers/d$4;->eK:Lcom/google/research/reflection/layers/d;

    .line 285
    invoke-static {v3}, Lcom/google/research/reflection/layers/d;->g(Lcom/google/research/reflection/layers/d;)Lcom/google/research/reflection/layers/e;

    move-result-object v3

    invoke-virtual {v3, v6, v1, p1}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v9

    mul-double v7, v7, v9

    add-double/2addr v4, v7

    add-int/lit8 v1, v1, 0x1

    goto :goto_2

    .line 288
    :cond_2
    iget-object v0, p0, Lcom/google/research/reflection/layers/d$4;->eP:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    add-int/2addr v2, p1

    aput-wide v4, v0, v2

    const/4 p1, 0x1

    .line 289
    invoke-static {p1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object p1

    return-object p1
.end method
