.class public Lcom/google/research/reflection/predictor/i;
.super Ljava/lang/Object;
.source "SourceFile"


# instance fields
.field public fK:I

.field public final fL:Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/List<",
            "Lcom/google/research/reflection/predictor/g;",
            ">;"
        }
    .end annotation
.end field

.field public final fM:Ljava/util/HashMap;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/HashMap<",
            "Lcom/google/research/reflection/predictor/g;",
            "Ljava/lang/Integer;",
            ">;"
        }
    .end annotation
.end field


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 14
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    const/4 v0, 0x0

    .line 16
    iput v0, p0, Lcom/google/research/reflection/predictor/i;->fK:I

    .line 18
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/google/research/reflection/predictor/i;->fL:Ljava/util/List;

    .line 20
    new-instance v0, Ljava/util/HashMap;

    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V

    iput-object v0, p0, Lcom/google/research/reflection/predictor/i;->fM:Ljava/util/HashMap;

    return-void
.end method


# virtual methods
.method public final a(Lcom/google/research/reflection/predictor/g;I)V
    .locals 1

    .line 23
    iget-object v0, p0, Lcom/google/research/reflection/predictor/i;->fL:Ljava/util/List;

    invoke-interface {v0, p1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 24
    iget-object v0, p0, Lcom/google/research/reflection/predictor/i;->fM:Ljava/util/HashMap;

    invoke-static/range {p2 .. p2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object p2

    invoke-virtual {v0, p1, p2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    return-void
.end method

.method public final a(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V
    .locals 2
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            ">;)V"
        }
    .end annotation

    .line 85
    iget-object v0, p0, Lcom/google/research/reflection/predictor/i;->fL:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_0

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/predictor/g;

    .line 86
    invoke-virtual {v1, p1, p2, p3}, Lcom/google/research/reflection/predictor/g;->a(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V

    goto :goto_0

    :cond_0
    return-void
.end method

.method public final c(Lcom/google/research/reflection/predictor/b;)V
    .locals 2

    .line 99
    iget-object v0, p0, Lcom/google/research/reflection/predictor/i;->fL:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_0

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/predictor/g;

    .line 100
    invoke-virtual {v1, p1}, Lcom/google/research/reflection/predictor/g;->c(Lcom/google/research/reflection/predictor/b;)V

    goto :goto_0

    :cond_0
    return-void
.end method

.method public final l(Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/k;
    .locals 9

    .line 29
    new-instance v0, Lcom/google/research/reflection/predictor/k;

    invoke-direct {v0}, Lcom/google/research/reflection/predictor/k;-><init>()V

    .line 30
    new-instance v1, Ljava/util/HashMap;

    invoke-direct {v1}, Ljava/util/HashMap;-><init>()V

    .line 31
    iget-object v2, p0, Lcom/google/research/reflection/predictor/i;->fL:Ljava/util/List;

    invoke-interface {v2}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v2

    :cond_0
    invoke-interface {v2}, Ljava/util/Iterator;->hasNext()Z

    move-result v3

    if-eqz v3, :cond_2

    invoke-interface {v2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Lcom/google/research/reflection/predictor/g;

    .line 32
    invoke-virtual {v3, p1}, Lcom/google/research/reflection/predictor/g;->j(Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/k;

    move-result-object v4

    iget-object v4, v4, Lcom/google/research/reflection/predictor/k;->fR:Ljava/util/ArrayList;

    const/4 v5, 0x0

    .line 35
    invoke-interface {v4}, Ljava/util/List;->size()I

    move-result v6

    iget-object v7, p0, Lcom/google/research/reflection/predictor/i;->fM:Ljava/util/HashMap;

    invoke-virtual {v7, v3}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Ljava/lang/Integer;

    invoke-virtual {v3}, Ljava/lang/Integer;->intValue()I

    move-result v3

    invoke-static {v6, v3}, Ljava/lang/Math;->min(II)I

    move-result v3

    .line 34
    invoke-interface {v4, v5, v3}, Ljava/util/List;->subList(II)Ljava/util/List;

    move-result-object v3

    .line 36
    invoke-interface {v3}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v3

    :goto_0
    invoke-interface {v3}, Ljava/util/Iterator;->hasNext()Z

    move-result v4

    if-eqz v4, :cond_0

    invoke-interface {v3}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Lcom/google/research/reflection/predictor/k$a;

    .line 39
    iget-object v5, v4, Lcom/google/research/reflection/predictor/k$a;->id:Ljava/lang/String;

    invoke-interface {v1, v5}, Ljava/util/Map;->containsKey(Ljava/lang/Object;)Z

    move-result v5

    if-nez v5, :cond_1

    .line 40
    new-instance v5, Lcom/google/research/reflection/predictor/k$a;

    iget-object v6, v4, Lcom/google/research/reflection/predictor/k$a;->id:Ljava/lang/String;

    iget v7, v4, Lcom/google/research/reflection/predictor/k$a;->ca:F

    iget-object v8, v4, Lcom/google/research/reflection/predictor/k$a;->fS:Ljava/util/Set;

    invoke-direct {v5, v6, v7, v8}, Lcom/google/research/reflection/predictor/k$a;-><init>(Ljava/lang/String;FLjava/util/Set;)V

    .line 41
    iget-object v6, v0, Lcom/google/research/reflection/predictor/k;->fR:Ljava/util/ArrayList;

    invoke-virtual {v6, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 42
    iget-object v4, v4, Lcom/google/research/reflection/predictor/k$a;->id:Ljava/lang/String;

    invoke-interface {v1, v4, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    goto :goto_0

    .line 44
    :cond_1
    iget-object v5, v4, Lcom/google/research/reflection/predictor/k$a;->id:Ljava/lang/String;

    invoke-interface {v1, v5}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Lcom/google/research/reflection/predictor/k$a;

    iget-object v5, v5, Lcom/google/research/reflection/predictor/k$a;->fS:Ljava/util/Set;

    iget-object v4, v4, Lcom/google/research/reflection/predictor/k$a;->fS:Ljava/util/Set;

    invoke-interface {v5, v4}, Ljava/util/Set;->addAll(Ljava/util/Collection;)Z

    goto :goto_0

    .line 49
    :cond_2
    new-instance p1, Lcom/google/research/reflection/predictor/f;

    invoke-direct {p1}, Lcom/google/research/reflection/predictor/f;-><init>()V

    invoke-virtual {p1, p0}, Lcom/google/research/reflection/predictor/f;->a(Lcom/google/research/reflection/predictor/i;)Lcom/google/research/reflection/predictor/f;

    move-result-object p1

    .line 50
    iget-object v1, v0, Lcom/google/research/reflection/predictor/k;->fR:Ljava/util/ArrayList;

    invoke-virtual {v1}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object v1

    :goto_1
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_3

    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/google/research/reflection/predictor/k$a;

    .line 51
    invoke-virtual {p1, v2}, Lcom/google/research/reflection/predictor/f;->a(Lcom/google/research/reflection/predictor/k$a;)Lcom/google/research/reflection/predictor/f;

    move-result-object v3

    invoke-virtual {v3}, Lcom/google/research/reflection/predictor/f;->as()I

    move-result v3

    iput v3, v2, Lcom/google/research/reflection/predictor/k$a;->fT:I

    goto :goto_1

    :cond_3
    return-object v0
.end method
