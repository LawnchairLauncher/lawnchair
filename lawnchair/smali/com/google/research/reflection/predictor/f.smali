.class public Lcom/google/research/reflection/predictor/f;
.super Ljava/lang/Object;
.source "SourceFile"


# instance fields
.field private final fF:Ljava/util/BitSet;


# direct methods
.method public constructor <init>()V
    .locals 2

    .line 22
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 25
    new-instance v0, Ljava/util/BitSet;

    const/16 v1, 0x20

    invoke-direct {v0, v1}, Ljava/util/BitSet;-><init>(I)V

    iput-object v0, p0, Lcom/google/research/reflection/predictor/f;->fF:Ljava/util/BitSet;

    return-void
.end method


# virtual methods
.method public final a(Lcom/google/research/reflection/predictor/i;)Lcom/google/research/reflection/predictor/f;
    .locals 4

    .line 127
    iget-object v0, p0, Lcom/google/research/reflection/predictor/f;->fF:Ljava/util/BitSet;

    const/16 v1, 0x10

    const/16 v2, 0x16

    invoke-virtual {v0, v1, v2}, Ljava/util/BitSet;->clear(II)V

    .line 128
    iget-object p1, p1, Lcom/google/research/reflection/predictor/i;->fL:Ljava/util/List;

    invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :cond_0
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_7

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/google/research/reflection/predictor/g;

    .line 129
    invoke-virtual {v0}, Lcom/google/research/reflection/predictor/g;->Y()Ljava/util/Map;

    move-result-object v0

    invoke-interface {v0}, Ljava/util/Map;->entrySet()Ljava/util/Set;

    move-result-object v0

    invoke-interface {v0}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :cond_1
    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_0

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/util/Map$Entry;

    invoke-interface {v2}, Ljava/util/Map$Entry;->getValue()Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Ljava/lang/Boolean;

    invoke-virtual {v3}, Ljava/lang/Boolean;->booleanValue()Z

    move-result v3

    if-nez v3, :cond_1

    invoke-interface {v2}, Ljava/util/Map$Entry;->getKey()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/lang/String;

    const-string v3, "local_app_launch_history"

    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_2

    iget-object v2, p0, Lcom/google/research/reflection/predictor/f;->fF:Ljava/util/BitSet;

    invoke-virtual {v2, v1}, Ljava/util/BitSet;->set(I)V

    goto :goto_0

    :cond_2
    const-string v3, "private_place"

    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_3

    iget-object v2, p0, Lcom/google/research/reflection/predictor/f;->fF:Ljava/util/BitSet;

    const/16 v3, 0x11

    :goto_1
    invoke-virtual {v2, v3}, Ljava/util/BitSet;->set(I)V

    goto :goto_0

    :cond_3
    const-string v3, "lat_lng"

    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_4

    iget-object v2, p0, Lcom/google/research/reflection/predictor/f;->fF:Ljava/util/BitSet;

    const/16 v3, 0x12

    goto :goto_1

    :cond_4
    const-string v3, "headset_wired"

    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_5

    iget-object v2, p0, Lcom/google/research/reflection/predictor/f;->fF:Ljava/util/BitSet;

    const/16 v3, 0x13

    goto :goto_1

    :cond_5
    const-string v3, "headset_bluetooth"

    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_6

    iget-object v2, p0, Lcom/google/research/reflection/predictor/f;->fF:Ljava/util/BitSet;

    const/16 v3, 0x14

    goto :goto_1

    :cond_6
    const-string v3, "local_app_usage_history"

    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_1

    iget-object v2, p0, Lcom/google/research/reflection/predictor/f;->fF:Ljava/util/BitSet;

    const/16 v3, 0x15

    goto :goto_1

    :cond_7
    return-object p0
.end method

.method public final a(Lcom/google/research/reflection/predictor/k$a;)Lcom/google/research/reflection/predictor/f;
    .locals 3

    .line 111
    iget-object v0, p0, Lcom/google/research/reflection/predictor/f;->fF:Ljava/util/BitSet;

    const/4 v1, 0x0

    const/4 v2, 0x2

    invoke-virtual {v0, v1, v2}, Ljava/util/BitSet;->clear(II)V

    .line 112
    iget-object p1, p1, Lcom/google/research/reflection/predictor/k$a;->fS:Ljava/util/Set;

    invoke-interface {p1}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :cond_0
    :goto_0
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_2

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Ljava/lang/String;

    const-string v2, "neural_predictor"

    .line 113
    invoke-virtual {v0, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_1

    .line 114
    iget-object v0, p0, Lcom/google/research/reflection/predictor/f;->fF:Ljava/util/BitSet;

    invoke-virtual {v0, v1}, Ljava/util/BitSet;->set(I)V

    goto :goto_0

    :cond_1
    const-string v2, "recency_event_predictor"

    .line 115
    invoke-virtual {v0, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_0

    .line 116
    iget-object v0, p0, Lcom/google/research/reflection/predictor/f;->fF:Ljava/util/BitSet;

    const/4 v2, 0x1

    invoke-virtual {v0, v2}, Ljava/util/BitSet;->set(I)V

    goto :goto_0

    :cond_2
    return-object p0
.end method

.method public final as()I
    .locals 4

    const/4 v0, 0x0

    const/4 v1, 0x0

    const/4 v2, 0x0

    .line 158
    :goto_0
    iget-object v3, p0, Lcom/google/research/reflection/predictor/f;->fF:Ljava/util/BitSet;

    invoke-virtual {v3}, Ljava/util/BitSet;->length()I

    move-result v3

    if-ge v1, v3, :cond_1

    .line 159
    iget-object v3, p0, Lcom/google/research/reflection/predictor/f;->fF:Ljava/util/BitSet;

    invoke-virtual {v3, v1}, Ljava/util/BitSet;->get(I)Z

    move-result v3

    if-eqz v3, :cond_0

    const/4 v3, 0x1

    shl-int/2addr v3, v1

    goto :goto_1

    :cond_0
    const/4 v3, 0x0

    :goto_1
    or-int/2addr v2, v3

    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    :cond_1
    return v2
.end method
