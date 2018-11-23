.class public Lcom/google/android/apps/nexuslauncher/reflection/signal/a;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/signal/ReflectionEvent;


# instance fields
.field public df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;


# direct methods
.method public constructor <init>()V
    .locals 2

    .line 35
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 36
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    .line 37
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    new-instance v1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    invoke-direct {v1}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;-><init>()V

    iput-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    .line 38
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    new-instance v1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    invoke-direct {v1}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;-><init>()V

    iput-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ci:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    return-void
.end method

.method public constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;)V
    .locals 1

    .line 23
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 24
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    .line 25
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    invoke-static {p1}, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;

    move-result-object p1

    invoke-virtual {p0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->f(Ljava/util/List;)Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 27
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    if-nez p1, :cond_0

    .line 28
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;-><init>()V

    iput-object v0, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    .line 30
    :cond_0
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ci:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    if-nez p1, :cond_1

    .line 31
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;-><init>()V

    iput-object v0, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ci:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    :cond_1
    return-void
.end method


# virtual methods
.method public final C()Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;
    .locals 2

    .line 54
    invoke-static {}, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->values()[Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    move-result-object v0

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget v1, v1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->type:I

    aget-object v0, v0, v1

    return-object v0
.end method

.method public final D()Lcom/google/research/reflection/signal/d;
    .locals 2

    .line 88
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object v1, v1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ci:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    invoke-direct {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;)V

    return-object v0
.end method

.method public final E()Lcom/google/research/reflection/signal/b;
    .locals 2

    .line 99
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object v1, v1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    invoke-direct {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;)V

    return-object v0
.end method

.method public final F()Ljava/util/List;
    .locals 1
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/List<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation

    .line 121
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    invoke-static {v0}, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;

    move-result-object v0

    return-object v0
.end method

.method public final G()[B
    .locals 1

    .line 146
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    if-nez v0, :cond_0

    const/4 v0, 0x0

    return-object v0

    .line 149
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    invoke-static {v0}, Lcom/google/protobuf/nano/MessageNano;->toByteArray(Lcom/google/protobuf/nano/MessageNano;)[B

    move-result-object v0

    return-object v0
.end method

.method public final H()Ljava/lang/String;
    .locals 1

    .line 187
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cl:Ljava/lang/String;

    return-object v0
.end method

.method public final I()Lcom/google/android/apps/nexuslauncher/reflection/signal/a;
    .locals 2

    .line 191
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->G()[B

    move-result-object v0

    if-eqz v0, :cond_0

    .line 193
    array-length v1, v0

    invoke-virtual {p0, v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a([BI)Lcom/google/research/reflection/signal/ReflectionEvent;

    move-result-object v0

    check-cast v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    return-object v0

    :cond_0
    const/4 v0, 0x0

    return-object v0
.end method

.method public final a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;)Lcom/google/research/reflection/signal/ReflectionEvent;
    .locals 1

    if-eqz p1, :cond_0

    .line 60
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    invoke-virtual/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->ordinal()I

    move-result p1

    iput p1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->type:I

    :cond_0
    return-object p0
.end method

.method public final a(Lcom/google/research/reflection/signal/b;)Lcom/google/research/reflection/signal/ReflectionEvent;
    .locals 1

    .line 104
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    check-cast p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    iput-object p1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    return-object p0
.end method

.method public final a(Lcom/google/research/reflection/signal/d;)Lcom/google/research/reflection/signal/ReflectionEvent;
    .locals 1

    .line 93
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    check-cast p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->dh:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    iput-object p1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ci:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    return-object p0
.end method

.method public final a([BI)Lcom/google/research/reflection/signal/ReflectionEvent;
    .locals 2

    .line 156
    :try_start_0
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    const/4 v1, 0x0

    invoke-static {p1, v1, p2}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->newInstance([BII)Lcom/google/protobuf/nano/CodedInputByteBufferNano;

    move-result-object p1

    invoke-static {p1}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->f(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    move-result-object p1

    invoke-direct {v0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;)V
    :try_end_0
    .catch Ljava/io/IOException; {:try_start_0 .. :try_end_0} :catch_0

    return-object v0

    :catch_0
    const-string p1, "Reflection"

    const-string p2, "deserialize event failed!"

    .line 158
    invoke-static {p1, p2}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    const/4 p1, 0x0

    return-object p1
.end method

.method public equals(Ljava/lang/Object;)Z
    .locals 2

    if-nez p1, :cond_0

    .line 165
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    if-nez v0, :cond_0

    const/4 p1, 0x1

    return p1

    :cond_0
    const/4 v0, 0x0

    if-eqz p1, :cond_3

    .line 168
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    if-nez v1, :cond_1

    goto :goto_0

    .line 171
    :cond_1
    instance-of v1, p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    if-eqz v1, :cond_2

    .line 172
    check-cast p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    .line 173
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    invoke-virtual {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->toString()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p1}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    return p1

    :cond_2
    return v0

    :cond_3
    :goto_0
    return v0
.end method

.method public final f(Ljava/util/List;)Lcom/google/research/reflection/signal/ReflectionEvent;
    .locals 3
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/List<",
            "Ljava/lang/String;",
            ">;)",
            "Lcom/google/research/reflection/signal/ReflectionEvent;"
        }
    .end annotation

    .line 126
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    invoke-interface/range {p1 .. p1}, Ljava/util/List;->size()I

    move-result v1

    new-array v1, v1, [Ljava/lang/String;

    iput-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    const/4 v0, 0x0

    .line 127
    :goto_0
    invoke-interface/range {p1 .. p1}, Ljava/util/List;->size()I

    move-result v1

    if-ge v0, v1, :cond_0

    .line 128
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object v1, v1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    invoke-interface {p1, v0}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/lang/String;

    aput-object v2, v1, v0

    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    :cond_0
    return-object p0
.end method

.method public final g(Ljava/lang/String;)Lcom/google/research/reflection/signal/ReflectionEvent;
    .locals 1

    .line 48
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iput-object p1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    return-object p0
.end method

.method public final getDuration()J
    .locals 2

    .line 67
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-wide v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->duration:J

    return-wide v0
.end method

.method public final getId()Ljava/lang/String;
    .locals 1

    .line 43
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    return-object v0
.end method

.method public final h(Ljava/lang/String;)Lcom/google/android/apps/nexuslauncher/reflection/signal/a;
    .locals 1

    .line 181
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iput-object p1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cl:Ljava/lang/String;

    return-object p0
.end method

.method public toString()Ljava/lang/String;
    .locals 3

    .line 204
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "Event [id: "

    .line 205
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object v1, v1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, ", type: "

    .line 206
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->C()Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    const-string v1, "\n"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 207
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v1

    if-eqz v1, :cond_0

    const-string v1, "Timestamp: "

    .line 208
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v1

    invoke-virtual {v0, v1, v2}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    const-string v1, ", bootTime: "

    .line 209
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/d;->g()J

    move-result-wide v1

    invoke-virtual {v0, v1, v2}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    const-string v1, ", elapsedTime: "

    .line 210
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/d;->O()J

    move-result-wide v1

    invoke-virtual {v0, v1, v2}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    const-string v1, ", timezone: "

    .line 211
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/d;->getTimeZone()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, ", time offset: "

    .line 212
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/d;->P()J

    move-result-wide v1

    invoke-virtual {v0, v1, v2}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    const-string v1, "\n"

    .line 213
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 215
    :cond_0
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    if-eqz v1, :cond_3

    .line 216
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/b;->L()Lcom/google/research/reflection/signal/a;

    move-result-object v1

    if-eqz v1, :cond_1

    const-string v1, "Latitude: "

    .line 217
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/b;->L()Lcom/google/research/reflection/signal/a;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/a;->getLatitude()D

    move-result-wide v1

    invoke-virtual {v0, v1, v2}, Ljava/lang/StringBuilder;->append(D)Ljava/lang/StringBuilder;

    const-string v1, ", Longitude: "

    .line 218
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/b;->L()Lcom/google/research/reflection/signal/a;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/a;->getLongitude()D

    move-result-wide v1

    invoke-virtual {v0, v1, v2}, Ljava/lang/StringBuilder;->append(D)Ljava/lang/StringBuilder;

    const-string v1, "\n"

    .line 219
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 221
    :cond_1
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/b;->J()Lcom/google/research/reflection/signal/ReflectionPrivatePlace;

    move-result-object v1

    if-eqz v1, :cond_2

    const-string v1, "Private place alias: "

    .line 222
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 223
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/b;->J()Lcom/google/research/reflection/signal/ReflectionPrivatePlace;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/ReflectionPrivatePlace;->M()Ljava/util/List;

    move-result-object v1

    const/4 v2, 0x0

    invoke-interface {v1, v2}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    const-string v1, ", time: "

    .line 224
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/b;->J()Lcom/google/research/reflection/signal/ReflectionPrivatePlace;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/ReflectionPrivatePlace;->getTime()J

    move-result-wide v1

    invoke-virtual {v0, v1, v2}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    const-string v1, "\n"

    .line 225
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 227
    :cond_2
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/b;->K()Lcom/google/research/reflection/signal/c;

    move-result-object v1

    if-eqz v1, :cond_3

    const-string v1, "Public place alias: "

    .line 228
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 229
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/b;->K()Lcom/google/research/reflection/signal/c;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/c;->N()Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, ", time: "

    .line 230
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/b;->K()Lcom/google/research/reflection/signal/c;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/c;->getTime()J

    move-result-wide v1

    invoke-virtual {v0, v1, v2}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    const-string v1, "\n"

    .line 231
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    :cond_3
    const-string v1, "Event source: "

    .line 234
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->F()Ljava/util/List;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    .line 235
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method
