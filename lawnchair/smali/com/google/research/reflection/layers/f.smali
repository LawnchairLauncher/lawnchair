.class public Lcom/google/research/reflection/layers/f;
.super Ljava/lang/Object;
.source "SourceFile"


# instance fields
.field public eZ:Lcom/google/research/reflection/a/a;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lcom/google/research/reflection/a/a<",
            "Lcom/google/research/reflection/layers/e;",
            ">;"
        }
    .end annotation
.end field

.field public fa:Lcom/google/research/reflection/a/a;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lcom/google/research/reflection/a/a<",
            "Lcom/google/research/reflection/layers/e;",
            ">;"
        }
    .end annotation
.end field

.field public fb:Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/List<",
            "Lcom/google/research/reflection/layers/c;",
            ">;"
        }
    .end annotation
.end field


# direct methods
.method public constructor <init>(I)V
    .locals 2

    .line 28
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 26
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    .line 29
    new-instance v0, Lcom/google/research/reflection/a/a;

    const/4 v1, 0x1

    invoke-direct {v0, p1, v1}, Lcom/google/research/reflection/a/a;-><init>(IZ)V

    iput-object v0, p0, Lcom/google/research/reflection/layers/f;->eZ:Lcom/google/research/reflection/a/a;

    .line 30
    new-instance v0, Lcom/google/research/reflection/a/a;

    invoke-direct {v0, p1, v1}, Lcom/google/research/reflection/a/a;-><init>(IZ)V

    iput-object v0, p0, Lcom/google/research/reflection/layers/f;->fa:Lcom/google/research/reflection/a/a;

    return-void
.end method


# virtual methods
.method public final a(Z[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;Z)Lcom/google/research/reflection/layers/e;
    .locals 3
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(Z[",
            "Ljava/util/ArrayList<",
            "Lcom/google/research/reflection/a/d;",
            ">;",
            "Lcom/google/research/reflection/layers/e;",
            "Z)",
            "Lcom/google/research/reflection/layers/e;"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    if-eqz p4, :cond_1

    .line 69
    iget-object p4, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    iget-object v0, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v0

    add-int/lit8 v0, v0, -0x1

    invoke-interface {p4, v0}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object p4

    instance-of p4, p4, Lcom/google/research/reflection/layers/g;

    if-eqz p4, :cond_0

    goto :goto_0

    .line 70
    :cond_0
    new-instance p1, Ljava/lang/RuntimeException;

    const-string p2, "Lacks outputlayer"

    invoke-direct {p1, p2}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw p1

    :cond_1
    :goto_0
    const/4 p4, 0x0

    const/4 v0, 0x0

    move-object v1, v0

    .line 73
    :goto_1
    iget-object v2, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v2}, Ljava/util/List;->size()I

    move-result v2

    if-ge p4, v2, :cond_2

    .line 74
    iget-object v1, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v1, p4}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/layers/c;

    invoke-virtual {v1, p1, p0, p2, p3}, Lcom/google/research/reflection/layers/c;->a(ZLcom/google/research/reflection/layers/f;[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;

    move-result-object v1

    add-int/lit8 p4, p4, 0x1

    move-object p2, v0

    move-object p3, v1

    goto :goto_1

    :cond_2
    return-object v1
.end method

.method public final aa()V
    .locals 2

    const/4 v0, 0x0

    .line 136
    :goto_0
    iget-object v1, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v1}, Ljava/util/List;->size()I

    move-result v1

    if-ge v0, v1, :cond_0

    .line 137
    iget-object v1, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v1, v0}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/layers/c;

    invoke-virtual {v1}, Lcom/google/research/reflection/layers/c;->aa()V

    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    :cond_0
    return-void
.end method

.method public final ag()Lcom/google/research/reflection/layers/f;
    .locals 4

    .line 39
    new-instance v0, Lcom/google/research/reflection/layers/f;

    iget-object v1, p0, Lcom/google/research/reflection/layers/f;->eZ:Lcom/google/research/reflection/a/a;

    iget-object v1, v1, Lcom/google/research/reflection/a/a;->dl:[Ljava/lang/Object;

    array-length v1, v1

    invoke-direct {v0, v1}, Lcom/google/research/reflection/layers/f;-><init>(I)V

    .line 40
    iget-object v1, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v1

    :goto_0
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_0

    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/google/research/reflection/layers/c;

    .line 41
    iget-object v3, v0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-virtual {v2}, Lcom/google/research/reflection/layers/c;->Z()Lcom/google/research/reflection/layers/c;

    move-result-object v2

    invoke-interface {v3, v2}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    goto :goto_0

    :cond_0
    return-object v0
.end method

.method public final ah()I
    .locals 2

    .line 160
    iget-object v0, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    const/4 v1, 0x0

    invoke-interface {v0, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/google/research/reflection/layers/c;

    iget v0, v0, Lcom/google/research/reflection/layers/c;->dH:I

    return v0
.end method

.method public final ai()Lcom/google/research/reflection/layers/c;
    .locals 2

    .line 210
    iget-object v0, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    iget-object v1, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v1}, Ljava/util/List;->size()I

    move-result v1

    add-int/lit8 v1, v1, -0x1

    invoke-interface {v0, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/google/research/reflection/layers/c;

    return-object v0
.end method

.method public final b(Lcom/google/research/reflection/layers/c;)V
    .locals 4

    .line 47
    invoke-virtual/range {p1 .. p1}, Lcom/google/research/reflection/layers/c;->ac()I

    move-result v0

    iget-object v1, p0, Lcom/google/research/reflection/layers/f;->eZ:Lcom/google/research/reflection/a/a;

    iget-object v1, v1, Lcom/google/research/reflection/a/a;->dl:[Ljava/lang/Object;

    array-length v1, v1

    if-ne v0, v1, :cond_2

    .line 51
    instance-of v0, p1, Lcom/google/research/reflection/layers/d;

    if-eqz v0, :cond_1

    .line 52
    move-object v0, p1

    check-cast v0, Lcom/google/research/reflection/layers/d;

    .line 53
    iget v0, v0, Lcom/google/research/reflection/layers/d;->eG:I

    iget-object v1, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v1}, Ljava/util/List;->size()I

    move-result v1

    if-eq v0, v1, :cond_0

    goto :goto_0

    .line 54
    :cond_0
    new-instance p1, Ljava/lang/RuntimeException;

    invoke-direct {p1}, Ljava/lang/RuntimeException;-><init>()V

    throw p1

    .line 57
    :cond_1
    :goto_0
    iget-object v0, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v0, p1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    return-void

    .line 48
    :cond_2
    new-instance v0, Ljava/lang/RuntimeException;

    iget-object v1, p0, Lcom/google/research/reflection/layers/f;->eZ:Lcom/google/research/reflection/a/a;

    .line 49
    iget-object v1, v1, Lcom/google/research/reflection/a/a;->dl:[Ljava/lang/Object;

    array-length v1, v1

    invoke-virtual/range {p1 .. p1}, Lcom/google/research/reflection/layers/c;->ac()I

    move-result p1

    const/16 v2, 0x66

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3, v2}, Ljava/lang/StringBuilder;-><init>(I)V

    const-string v2, "Inconsistent framebuffer size with the added layer: targetsize="

    invoke-virtual {v3, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v1, " layerbuffersize="

    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-direct {v0, p1}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v0
.end method

.method public final b(Ljava/io/DataInputStream;)V
    .locals 4
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 173
    iget-object v0, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->clear()V

    .line 174
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readInt()I

    move-result v0

    const/4 v1, 0x0

    :goto_0
    if-ge v1, v0, :cond_4

    .line 176
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readUTF()Ljava/lang/String;

    move-result-object v2

    const-string v3, "LinearLayer"

    .line 177
    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_0

    .line 179
    new-instance v2, Lcom/google/research/reflection/layers/d;

    invoke-direct {v2}, Lcom/google/research/reflection/layers/d;-><init>()V

    goto :goto_1

    :cond_0
    const-string v3, "OutputLayer"

    .line 180
    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_1

    .line 181
    new-instance v2, Lcom/google/research/reflection/layers/g;

    invoke-direct {v2}, Lcom/google/research/reflection/layers/g;-><init>()V

    goto :goto_1

    :cond_1
    const-string v3, "LSTMLayer"

    .line 182
    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_2

    .line 183
    new-instance v2, Lcom/google/research/reflection/layers/b;

    invoke-direct {v2}, Lcom/google/research/reflection/layers/b;-><init>()V

    .line 187
    :goto_1
    invoke-virtual {v2, p1}, Lcom/google/research/reflection/layers/c;->b(Ljava/io/DataInputStream;)V

    .line 188
    iget-object v3, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v3, v2}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    .line 185
    :cond_2
    new-instance p1, Ljava/io/IOException;

    const-string v0, "Unsupported layer type: "

    invoke-static {v2}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/String;->length()I

    move-result v2

    if-eqz v2, :cond_3

    invoke-virtual {v0, v1}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    goto :goto_2

    :cond_3
    new-instance v1, Ljava/lang/String;

    invoke-direct {v1, v0}, Ljava/lang/String;-><init>(Ljava/lang/String;)V

    move-object v0, v1

    :goto_2
    invoke-direct {p1, v0}, Ljava/io/IOException;-><init>(Ljava/lang/String;)V

    throw p1

    .line 190
    :cond_4
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readUTF()Ljava/lang/String;

    move-result-object p1

    const-string v0, "NeuralNet"

    .line 191
    invoke-virtual {p1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_5

    return-void

    .line 192
    :cond_5
    new-instance v0, Ljava/io/IOException;

    invoke-static {p1}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/String;->length()I

    move-result v1

    add-int/lit8 v1, v1, 0x2d

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2, v1}, Ljava/lang/StringBuilder;-><init>(I)V

    const-string v1, "Inconsistent ending: ["

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string p1, "] expected: [NeuralNet]"

    invoke-virtual {v2, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-direct {v0, p1}, Ljava/io/IOException;-><init>(Ljava/lang/String;)V

    throw v0
.end method

.method public synthetic clone()Ljava/lang/Object;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/CloneNotSupportedException;
        }
    .end annotation

    .line 14
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/layers/f;->ag()Lcom/google/research/reflection/layers/f;

    move-result-object v0

    return-object v0
.end method

.method public final update()V
    .locals 2
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    .line 142
    iget-object v0, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v0

    add-int/lit8 v0, v0, -0x1

    :goto_0
    if-ltz v0, :cond_0

    .line 144
    iget-object v1, p0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v1, v0}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/layers/c;

    .line 145
    invoke-virtual {v1}, Lcom/google/research/reflection/layers/c;->update()V

    add-int/lit8 v0, v0, -0x1

    goto :goto_0

    :cond_0
    return-void
.end method
