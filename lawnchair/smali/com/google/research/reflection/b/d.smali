.class public Lcom/google/research/reflection/b/d;
.super Lcom/google/research/reflection/b/a;
.source "SourceFile"


# direct methods
.method public constructor <init>()V
    .locals 0

    .line 10
    invoke-direct/range {p0 .. p0}, Lcom/google/research/reflection/b/a;-><init>()V

    return-void
.end method

.method public constructor <init>(IJJI)V
    .locals 0

    .line 18
    invoke-direct/range {p0 .. p6}, Lcom/google/research/reflection/b/a;-><init>(IJJI)V

    return-void
.end method


# virtual methods
.method public final synthetic S()Lcom/google/research/reflection/b/a;
    .locals 1

    .line 6
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/b/d;->W()Lcom/google/research/reflection/b/d;

    move-result-object v0

    return-object v0
.end method

.method public final synthetic U()Lcom/google/research/reflection/b/f;
    .locals 1

    .line 6
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/b/d;->W()Lcom/google/research/reflection/b/d;

    move-result-object v0

    return-object v0
.end method

.method public final W()Lcom/google/research/reflection/b/d;
    .locals 8

    .line 28
    new-instance v7, Lcom/google/research/reflection/b/d;

    iget v1, p0, Lcom/google/research/reflection/b/d;->du:I

    iget-wide v2, p0, Lcom/google/research/reflection/b/d;->dv:J

    iget-wide v4, p0, Lcom/google/research/reflection/b/d;->dw:J

    iget v6, p0, Lcom/google/research/reflection/b/d;->dx:I

    move-object v0, v7

    invoke-direct/range {v0 .. v6}, Lcom/google/research/reflection/b/d;-><init>(IJJI)V

    .line 30
    invoke-virtual {v7, p0}, Lcom/google/research/reflection/b/d;->a(Lcom/google/research/reflection/b/a;)V

    return-object v7
.end method

.method public synthetic clone()Ljava/lang/Object;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/CloneNotSupportedException;
        }
    .end annotation

    .line 6
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/b/d;->W()Lcom/google/research/reflection/b/d;

    move-result-object v0

    return-object v0
.end method

.method public final f(Lcom/google/research/reflection/signal/ReflectionEvent;)Z
    .locals 1

    .line 36
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->C()Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    move-result-object p1

    sget-object v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fZ:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    if-ne p1, v0, :cond_0

    const/4 p1, 0x1

    return p1

    :cond_0
    const/4 p1, 0x0

    return p1
.end method

.method public final getFeatureName()Ljava/lang/String;
    .locals 1

    const-string v0, "deep_link_history"

    return-object v0
.end method
