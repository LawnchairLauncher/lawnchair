.class public Lcom/google/research/reflection/predictor/h;
.super Lcom/google/research/reflection/predictor/g;
.source "SourceFile"


# instance fields
.field fH:Ljava/util/Map;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Lcom/google/research/reflection/signal/ReflectionEvent;",
            ">;"
        }
    .end annotation
.end field

.field private fI:Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/List<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation
.end field


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 19
    invoke-direct/range {p0 .. p0}, Lcom/google/research/reflection/predictor/g;-><init>()V

    .line 26
    new-instance v0, Ljava/util/HashMap;

    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V

    iput-object v0, p0, Lcom/google/research/reflection/predictor/h;->fH:Ljava/util/Map;

    .line 29
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/google/research/reflection/predictor/h;->fI:Ljava/util/List;

    return-void
.end method

.method private at()V
    .locals 2

    .line 52
    iget-object v0, p0, Lcom/google/research/reflection/predictor/h;->fI:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->clear()V

    .line 53
    iget-object v0, p0, Lcom/google/research/reflection/predictor/h;->fI:Ljava/util/List;

    iget-object v1, p0, Lcom/google/research/reflection/predictor/h;->fH:Ljava/util/Map;

    invoke-interface {v1}, Ljava/util/Map;->keySet()Ljava/util/Set;

    move-result-object v1

    invoke-interface {v0, v1}, Ljava/util/List;->addAll(Ljava/util/Collection;)Z

    .line 54
    iget-object v0, p0, Lcom/google/research/reflection/predictor/h;->fI:Ljava/util/List;

    new-instance v1, Lcom/google/research/reflection/predictor/h$1;

    invoke-direct {v1, p0}, Lcom/google/research/reflection/predictor/h$1;-><init>(Lcom/google/research/reflection/predictor/h;)V

    invoke-static {v0, v1}, Ljava/util/Collections;->sort(Ljava/util/List;Ljava/util/Comparator;)V

    return-void
.end method


# virtual methods
.method public final a(Ljava/io/DataInputStream;Lcom/google/research/reflection/signal/ReflectionEvent;)V
    .locals 7
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 156
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readInt()I

    move-result v0

    new-instance v1, Ljava/util/HashMap;

    invoke-direct {v1}, Ljava/util/HashMap;-><init>()V

    const/4 v2, 0x0

    const/4 v3, 0x0

    :goto_0
    if-ge v3, v0, :cond_0

    const-class v4, Ljava/lang/String;

    invoke-static {p1, v4}, Lcom/google/research/reflection/a/f;->a(Ljava/io/DataInputStream;Ljava/lang/Class;)Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Ljava/lang/String;

    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readInt()I

    move-result v5

    new-array v6, v5, [B

    invoke-virtual {p1, v6, v2, v5}, Ljava/io/DataInputStream;->read([BII)I

    invoke-interface {p2, v6, v5}, Lcom/google/research/reflection/signal/ReflectionEvent;->a([BI)Lcom/google/research/reflection/signal/ReflectionEvent;

    move-result-object v5

    invoke-interface {v1, v4, v5}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    add-int/lit8 v3, v3, 0x1

    goto :goto_0

    :cond_0
    iput-object v1, p0, Lcom/google/research/reflection/predictor/h;->fH:Ljava/util/Map;

    .line 157
    invoke-direct/range {p0 .. p0}, Lcom/google/research/reflection/predictor/h;->at()V

    return-void
.end method

.method public final a(Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;)V
    .locals 0

    .line 134
    iget-object p1, p0, Lcom/google/research/reflection/predictor/h;->fH:Ljava/util/Map;

    invoke-interface {p1, p3}, Ljava/util/Map;->remove(Ljava/lang/Object;)Ljava/lang/Object;

    .line 135
    iget-object p1, p0, Lcom/google/research/reflection/predictor/h;->fI:Ljava/util/List;

    invoke-interface {p1, p3}, Ljava/util/List;->remove(Ljava/lang/Object;)Z

    return-void
.end method

.method public final b(Ljava/io/DataOutputStream;)V
    .locals 3
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 151
    iget-object v0, p0, Lcom/google/research/reflection/predictor/h;->fH:Ljava/util/Map;

    invoke-interface {v0}, Ljava/util/Map;->size()I

    move-result v1

    invoke-virtual {p1, v1}, Ljava/io/DataOutputStream;->writeInt(I)V

    invoke-interface {v0}, Ljava/util/Map;->entrySet()Ljava/util/Set;

    move-result-object v0

    invoke-interface {v0}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_0

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Ljava/util/Map$Entry;

    invoke-interface {v1}, Ljava/util/Map$Entry;->getKey()Ljava/lang/Object;

    move-result-object v2

    invoke-static {p1, v2}, Lcom/google/research/reflection/a/f;->a(Ljava/io/DataOutputStream;Ljava/lang/Object;)V

    invoke-interface {v1}, Ljava/util/Map$Entry;->getValue()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/signal/ReflectionEvent;

    invoke-interface {v1}, Lcom/google/research/reflection/signal/ReflectionEvent;->G()[B

    move-result-object v1

    array-length v2, v1

    invoke-virtual {p1, v2}, Ljava/io/DataOutputStream;->writeInt(I)V

    invoke-virtual {p1, v1}, Ljava/io/DataOutputStream;->write([B)V

    goto :goto_0

    :cond_0
    return-void
.end method

.method public final getName()Ljava/lang/String;
    .locals 1

    const-string v0, "recency_event_predictor"

    return-object v0
.end method

.method public final h(Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/k;
    .locals 6

    .line 97
    iget-object v0, p0, Lcom/google/research/reflection/predictor/h;->fI:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->isEmpty()Z

    move-result v0

    if-nez v0, :cond_0

    .line 99
    iget-object v0, p0, Lcom/google/research/reflection/predictor/h;->fI:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v0

    add-int/lit8 v0, v0, -0x1

    :goto_0
    if-ltz v0, :cond_0

    .line 100
    iget-object v1, p0, Lcom/google/research/reflection/predictor/h;->fI:Ljava/util/List;

    invoke-interface {v1, v0}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Ljava/lang/String;

    .line 101
    iget-object v2, p0, Lcom/google/research/reflection/predictor/h;->fH:Ljava/util/Map;

    .line 102
    invoke-interface {v2, v1}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/google/research/reflection/signal/ReflectionEvent;

    invoke-static {v2, p1}, Lcom/google/research/reflection/a/e;->a(Lcom/google/research/reflection/signal/ReflectionEvent;Lcom/google/research/reflection/signal/ReflectionEvent;)J

    move-result-wide v2

    const-wide/32 v4, 0x1499700

    cmp-long v2, v2, v4

    if-lez v2, :cond_0

    .line 104
    iget-object v2, p0, Lcom/google/research/reflection/predictor/h;->fH:Ljava/util/Map;

    invoke-interface {v2, v1}, Ljava/util/Map;->remove(Ljava/lang/Object;)Ljava/lang/Object;

    add-int/lit8 v0, v0, -0x1

    goto :goto_0

    .line 111
    :cond_0
    iget-object v0, p0, Lcom/google/research/reflection/predictor/h;->fH:Ljava/util/Map;

    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->getId()Ljava/lang/String;

    move-result-object v1

    invoke-interface {v0, v1, p1}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    .line 112
    invoke-direct/range {p0 .. p0}, Lcom/google/research/reflection/predictor/h;->at()V

    .line 113
    new-instance p1, Lcom/google/research/reflection/predictor/k;

    invoke-direct {p1}, Lcom/google/research/reflection/predictor/k;-><init>()V

    return-object p1
.end method

.method public final j(Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/k;
    .locals 11

    .line 67
    new-instance v0, Lcom/google/research/reflection/predictor/k;

    invoke-direct {v0}, Lcom/google/research/reflection/predictor/k;-><init>()V

    .line 68
    iget-object v1, p0, Lcom/google/research/reflection/predictor/h;->fI:Ljava/util/List;

    invoke-interface {v1}, Ljava/util/List;->isEmpty()Z

    move-result v1

    const/4 v2, 0x1

    const-wide/32 v3, 0x1499700

    const/4 v5, 0x0

    if-eqz v1, :cond_0

    :goto_0
    const/4 v1, 0x0

    goto :goto_1

    :cond_0
    iget-object v1, p0, Lcom/google/research/reflection/predictor/h;->fH:Ljava/util/Map;

    invoke-interface {v1}, Ljava/util/Map;->isEmpty()Z

    move-result v1

    if-eqz v1, :cond_1

    goto :goto_0

    :cond_1
    iget-object v1, p0, Lcom/google/research/reflection/predictor/h;->fH:Ljava/util/Map;

    iget-object v6, p0, Lcom/google/research/reflection/predictor/h;->fI:Ljava/util/List;

    invoke-interface {v6, v5}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v6

    invoke-interface {v1, v6}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/signal/ReflectionEvent;

    invoke-static {v1, p1}, Lcom/google/research/reflection/a/e;->a(Lcom/google/research/reflection/signal/ReflectionEvent;Lcom/google/research/reflection/signal/ReflectionEvent;)J

    move-result-wide v6

    cmp-long v1, v6, v3

    if-lez v1, :cond_2

    goto :goto_0

    :cond_2
    const/4 v1, 0x1

    :goto_1
    if-nez v1, :cond_3

    return-object v0

    .line 72
    :cond_3
    iget-object v1, p0, Lcom/google/research/reflection/predictor/h;->fI:Ljava/util/List;

    invoke-interface {v1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v1

    :goto_2
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z

    move-result v6

    if-eqz v6, :cond_4

    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Ljava/lang/String;

    .line 74
    iget-object v7, p0, Lcom/google/research/reflection/predictor/h;->fH:Ljava/util/Map;

    invoke-interface {v7, v6}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Lcom/google/research/reflection/signal/ReflectionEvent;

    invoke-static {v7, p1}, Lcom/google/research/reflection/a/e;->a(Lcom/google/research/reflection/signal/ReflectionEvent;Lcom/google/research/reflection/signal/ReflectionEvent;)J

    move-result-wide v7

    cmp-long v7, v7, v3

    if-gtz v7, :cond_4

    .line 76
    iget-object v7, v0, Lcom/google/research/reflection/predictor/k;->fR:Ljava/util/ArrayList;

    new-instance v8, Lcom/google/research/reflection/predictor/k$a;

    const/high16 v9, 0x3f800000    # 1.0f

    add-int/2addr v5, v2

    int-to-float v10, v5

    div-float/2addr v9, v10

    const-string v10, "recency_event_predictor"

    invoke-direct {v8, v6, v9, v10}, Lcom/google/research/reflection/predictor/k$a;-><init>(Ljava/lang/String;FLjava/lang/String;)V

    invoke-virtual {v7, v8}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    goto :goto_2

    :cond_4
    return-object v0
.end method

.method public final k(Lcom/google/research/reflection/signal/ReflectionEvent;)Z
    .locals 2

    .line 91
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->C()Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    move-result-object v0

    sget-object v1, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fW:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    if-eq v0, v1, :cond_1

    .line 92
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->C()Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    move-result-object p1

    sget-object v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fY:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    if-ne p1, v0, :cond_0

    goto :goto_0

    :cond_0
    const/4 p1, 0x0

    return p1

    :cond_1
    :goto_0
    const/4 p1, 0x1

    return p1
.end method
