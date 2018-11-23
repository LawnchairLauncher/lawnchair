.class public final Lcom/google/research/reflection/predictor/j;
.super Lcom/google/research/reflection/predictor/l;
.source "SourceFile"


# instance fields
.field private final fN:Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/List<",
            "Lcom/google/research/reflection/predictor/g;",
            ">;"
        }
    .end annotation
.end field


# direct methods
.method public constructor <init>(Lcom/google/research/reflection/a/c;)V
    .locals 2

    .line 14
    invoke-direct/range {p0 .. p0}, Lcom/google/research/reflection/predictor/l;-><init>()V

    .line 15
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/google/research/reflection/predictor/j;->fN:Ljava/util/List;

    .line 16
    iget-object v0, p0, Lcom/google/research/reflection/predictor/j;->fN:Ljava/util/List;

    new-instance v1, Lcom/google/research/reflection/predictor/c;

    invoke-direct {v1, p1}, Lcom/google/research/reflection/predictor/c;-><init>(Lcom/google/research/reflection/a/c;)V

    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    .line 17
    iget-object v0, p0, Lcom/google/research/reflection/predictor/j;->fN:Ljava/util/List;

    new-instance v1, Lcom/google/research/reflection/predictor/a;

    invoke-direct {v1, p1}, Lcom/google/research/reflection/predictor/a;-><init>(Lcom/google/research/reflection/a/c;)V

    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    return-void
.end method


# virtual methods
.method public final c(Lcom/google/research/reflection/predictor/b;)V
    .locals 2

    .line 38
    invoke-super/range {p0 .. p1}, Lcom/google/research/reflection/predictor/l;->c(Lcom/google/research/reflection/predictor/b;)V

    .line 39
    iget-object v0, p0, Lcom/google/research/reflection/predictor/j;->fN:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_0

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/predictor/g;

    .line 40
    invoke-virtual {v1, p1}, Lcom/google/research/reflection/predictor/g;->c(Lcom/google/research/reflection/predictor/b;)V

    goto :goto_0

    :cond_0
    return-void
.end method

.method public final getName()Ljava/lang/String;
    .locals 1

    const-string v0, "Rule_Based_Predictor"

    return-object v0
.end method

.method public final j(Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/k;
    .locals 3

    .line 22
    iget-object v0, p0, Lcom/google/research/reflection/predictor/j;->fN:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :cond_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_1

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/predictor/g;

    .line 23
    invoke-virtual {v1, p1}, Lcom/google/research/reflection/predictor/g;->j(Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/k;

    move-result-object v1

    .line 24
    iget-object v2, v1, Lcom/google/research/reflection/predictor/k;->fR:Ljava/util/ArrayList;

    if-eqz v2, :cond_0

    iget-object v2, v1, Lcom/google/research/reflection/predictor/k;->fR:Ljava/util/ArrayList;

    invoke-virtual {v2}, Ljava/util/ArrayList;->isEmpty()Z

    move-result v2

    if-nez v2, :cond_0

    return-object v1

    .line 28
    :cond_1
    new-instance p1, Lcom/google/research/reflection/predictor/k;

    invoke-direct {p1}, Lcom/google/research/reflection/predictor/k;-><init>()V

    return-object p1
.end method
