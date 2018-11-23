.class public Lcom/google/research/reflection/b/g;
.super Lcom/google/research/reflection/b/f;
.source "SourceFile"


# instance fields
.field private dC:Z

.field private dD:Z


# direct methods
.method public constructor <init>()V
    .locals 4

    .line 29
    invoke-direct/range {p0 .. p0}, Lcom/google/research/reflection/b/f;-><init>()V

    const/4 v0, 0x1

    .line 25
    iput-boolean v0, p0, Lcom/google/research/reflection/b/g;->dC:Z

    .line 26
    iput-boolean v0, p0, Lcom/google/research/reflection/b/g;->dD:Z

    .line 30
    iget-object v1, p0, Lcom/google/research/reflection/b/g;->dB:Ljava/util/Map;

    invoke-interface {v1}, Ljava/util/Map;->clear()V

    .line 31
    iget-object v1, p0, Lcom/google/research/reflection/b/g;->dB:Ljava/util/Map;

    const-string v2, "headset_wired"

    invoke-static {v0}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object v3

    invoke-interface {v1, v2, v3}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    .line 32
    iget-object v1, p0, Lcom/google/research/reflection/b/g;->dB:Ljava/util/Map;

    const-string v2, "headset_bluetooth"

    invoke-static {v0}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object v0

    invoke-interface {v1, v2, v0}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    return-void
.end method


# virtual methods
.method public final T()I
    .locals 1

    const/4 v0, 0x4

    return v0
.end method

.method public final U()Lcom/google/research/reflection/b/f;
    .locals 1

    .line 76
    new-instance v0, Lcom/google/research/reflection/b/g;

    invoke-direct {v0}, Lcom/google/research/reflection/b/g;-><init>()V

    return-object v0
.end method

.method protected final a(Lcom/google/research/reflection/a/a;Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/layers/e;
    .locals 8
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lcom/google/research/reflection/a/a<",
            "Lcom/google/research/reflection/signal/ReflectionEvent;",
            ">;",
            "Lcom/google/research/reflection/signal/ReflectionEvent;",
            ")",
            "Lcom/google/research/reflection/layers/e;"
        }
    .end annotation

    .line 43
    new-instance v0, Lcom/google/research/reflection/layers/e;

    const/4 v1, 0x1

    const/4 v2, 0x4

    invoke-direct {v0, v1, v2}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    .line 44
    sget-object v2, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->ga:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    .line 45
    invoke-static {p1, v2}, Lcom/google/research/reflection/a/b;->a(Lcom/google/research/reflection/a/a;Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;)Ljava/util/List;

    move-result-object p1

    .line 46
    iput-boolean v1, p0, Lcom/google/research/reflection/b/g;->dC:Z

    .line 47
    iput-boolean v1, p0, Lcom/google/research/reflection/b/g;->dD:Z

    .line 49
    invoke-interface {p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :cond_0
    :goto_0
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_4

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 50
    invoke-interface/range {p2 .. p2}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v3

    invoke-interface {v3}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v3

    invoke-interface {v2}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v5

    invoke-interface {v5}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v5

    sub-long/2addr v3, v5

    const-wide/32 v5, 0x927c0

    cmp-long v3, v3, v5

    if-gez v3, :cond_0

    .line 51
    invoke-interface {v2}, Lcom/google/research/reflection/signal/ReflectionEvent;->getId()Ljava/lang/String;

    move-result-object v3

    const-string v4, "headset_wired_in"

    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    const-wide/high16 v4, 0x3ff0000000000000L    # 1.0

    const/4 v6, 0x0

    if-eqz v3, :cond_1

    .line 52
    iget-object v2, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    aput-wide v4, v2, v6

    .line 53
    iput-boolean v6, p0, Lcom/google/research/reflection/b/g;->dC:Z

    goto :goto_0

    .line 54
    :cond_1
    invoke-interface {v2}, Lcom/google/research/reflection/signal/ReflectionEvent;->getId()Ljava/lang/String;

    move-result-object v3

    const-string v7, "headset_wired_out"

    invoke-virtual {v3, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_2

    .line 55
    iget-object v2, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    aput-wide v4, v2, v1

    .line 56
    iput-boolean v6, p0, Lcom/google/research/reflection/b/g;->dC:Z

    goto :goto_0

    .line 57
    :cond_2
    invoke-interface {v2}, Lcom/google/research/reflection/signal/ReflectionEvent;->getId()Ljava/lang/String;

    move-result-object v3

    const-string v7, "headset_bluetooth_in"

    invoke-virtual {v3, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_3

    .line 58
    iget-object v2, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    const/4 v3, 0x2

    aput-wide v4, v2, v3

    .line 59
    iput-boolean v6, p0, Lcom/google/research/reflection/b/g;->dD:Z

    goto :goto_0

    .line 60
    :cond_3
    invoke-interface {v2}, Lcom/google/research/reflection/signal/ReflectionEvent;->getId()Ljava/lang/String;

    move-result-object v2

    const-string v3, "headset_bluetooth_out"

    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_0

    .line 61
    iget-object v2, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    const/4 v3, 0x3

    aput-wide v4, v2, v3

    .line 62
    iput-boolean v6, p0, Lcom/google/research/reflection/b/g;->dD:Z

    goto :goto_0

    :cond_4
    return-object v0
.end method

.method public synthetic clone()Ljava/lang/Object;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/CloneNotSupportedException;
        }
    .end annotation

    .line 12
    new-instance v0, Lcom/google/research/reflection/b/g;

    invoke-direct {v0}, Lcom/google/research/reflection/b/g;-><init>()V

    return-object v0
.end method

.method protected final e(Z)V
    .locals 2

    .line 81
    iget-object p1, p0, Lcom/google/research/reflection/b/g;->dB:Ljava/util/Map;

    const-string v0, "headset_wired"

    iget-boolean v1, p0, Lcom/google/research/reflection/b/g;->dC:Z

    invoke-static {v1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object v1

    invoke-interface {p1, v0, v1}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    .line 82
    iget-object p1, p0, Lcom/google/research/reflection/b/g;->dB:Ljava/util/Map;

    const-string v0, "headset_bluetooth"

    iget-boolean v1, p0, Lcom/google/research/reflection/b/g;->dD:Z

    invoke-static {v1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object v1

    invoke-interface {p1, v0, v1}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    return-void
.end method

.method public final getFeatureName()Ljava/lang/String;
    .locals 1

    const-string v0, "headset"

    return-object v0
.end method
