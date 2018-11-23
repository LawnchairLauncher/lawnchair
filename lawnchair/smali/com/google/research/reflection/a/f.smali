.class public Lcom/google/research/reflection/a/f;
.super Ljava/lang/Object;
.source "SourceFile"


# direct methods
.method public static a(Ljava/io/DataInputStream;Ljava/lang/Class;)Ljava/lang/Object;
    .locals 3
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "<T:",
            "Ljava/lang/Object;",
            ">(",
            "Ljava/io/DataInputStream;",
            "Ljava/lang/Class<",
            "TT;>;)",
            "Ljava/lang/Object;"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 154
    const-class v0, Ljava/lang/Integer;

    if-ne p1, v0, :cond_0

    .line 155
    invoke-virtual/range {p0 .. p0}, Ljava/io/DataInputStream;->readInt()I

    move-result p0

    invoke-static {p0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object p0

    return-object p0

    .line 156
    :cond_0
    const-class v0, Ljava/lang/Long;

    if-ne p1, v0, :cond_1

    .line 157
    invoke-virtual/range {p0 .. p0}, Ljava/io/DataInputStream;->readLong()J

    move-result-wide p0

    invoke-static {p0, p1}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object p0

    return-object p0

    .line 158
    :cond_1
    const-class v0, Ljava/lang/Float;

    if-ne p1, v0, :cond_2

    .line 159
    invoke-virtual/range {p0 .. p0}, Ljava/io/DataInputStream;->readFloat()F

    move-result p0

    invoke-static {p0}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;

    move-result-object p0

    return-object p0

    .line 160
    :cond_2
    const-class v0, Ljava/lang/String;

    if-ne p1, v0, :cond_3

    .line 161
    invoke-virtual/range {p0 .. p0}, Ljava/io/DataInputStream;->readUTF()Ljava/lang/String;

    move-result-object p0

    return-object p0

    .line 162
    :cond_3
    const-class v0, [I

    const/4 v1, 0x0

    if-ne p1, v0, :cond_5

    .line 163
    invoke-virtual/range {p0 .. p0}, Ljava/io/DataInputStream;->readInt()I

    move-result p1

    .line 164
    new-array v0, p1, [I

    :goto_0
    if-ge v1, p1, :cond_4

    .line 166
    invoke-virtual/range {p0 .. p0}, Ljava/io/DataInputStream;->readInt()I

    move-result v2

    aput v2, v0, v1

    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    :cond_4
    return-object v0

    .line 169
    :cond_5
    const-class v0, [F

    if-ne p1, v0, :cond_7

    .line 170
    invoke-virtual/range {p0 .. p0}, Ljava/io/DataInputStream;->readInt()I

    move-result p1

    .line 171
    new-array v0, p1, [F

    :goto_1
    if-ge v1, p1, :cond_6

    .line 173
    invoke-virtual/range {p0 .. p0}, Ljava/io/DataInputStream;->readFloat()F

    move-result v2

    aput v2, v0, v1

    add-int/lit8 v1, v1, 0x1

    goto :goto_1

    :cond_6
    return-object v0

    :cond_7
    const/4 p0, 0x0

    return-object p0
.end method

.method public static a(Ljava/io/DataInputStream;Ljava/lang/Class;Ljava/lang/Class;)Ljava/util/HashMap;
    .locals 5
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "<V:",
            "Ljava/lang/Object;",
            "K:",
            "Ljava/lang/Object;",
            ">(",
            "Ljava/io/DataInputStream;",
            "Ljava/lang/Class<",
            "TK;>;",
            "Ljava/lang/Class<",
            "TV;>;)",
            "Ljava/util/HashMap<",
            "TK;TV;>;"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 128
    new-instance v0, Ljava/util/HashMap;

    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V

    .line 129
    invoke-virtual/range {p0 .. p0}, Ljava/io/DataInputStream;->readInt()I

    move-result v1

    const/4 v2, 0x0

    :goto_0
    if-ge v2, v1, :cond_0

    .line 131
    invoke-static/range {p0 .. p1}, Lcom/google/research/reflection/a/f;->a(Ljava/io/DataInputStream;Ljava/lang/Class;)Ljava/lang/Object;

    move-result-object v3

    .line 132
    invoke-static {p0, p2}, Lcom/google/research/reflection/a/f;->a(Ljava/io/DataInputStream;Ljava/lang/Class;)Ljava/lang/Object;

    move-result-object v4

    .line 133
    invoke-virtual {v0, v3, v4}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    add-int/lit8 v2, v2, 0x1

    goto :goto_0

    :cond_0
    return-object v0
.end method

.method public static a(Ljava/io/DataOutputStream;Ljava/lang/Object;)V
    .locals 2
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "<T:",
            "Ljava/lang/Object;",
            "K:",
            "Ljava/lang/Object;",
            "V:",
            "Ljava/lang/Object;",
            ">(",
            "Ljava/io/DataOutputStream;",
            "TT;)V"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 101
    instance-of v0, p1, Ljava/lang/Integer;

    if-eqz v0, :cond_0

    .line 102
    check-cast p1, Ljava/lang/Integer;

    invoke-virtual {p1}, Ljava/lang/Integer;->intValue()I

    move-result p1

    invoke-virtual {p0, p1}, Ljava/io/DataOutputStream;->writeInt(I)V

    return-void

    .line 103
    :cond_0
    instance-of v0, p1, Ljava/lang/Long;

    if-eqz v0, :cond_1

    .line 104
    check-cast p1, Ljava/lang/Long;

    invoke-virtual {p1}, Ljava/lang/Long;->longValue()J

    move-result-wide v0

    invoke-virtual {p0, v0, v1}, Ljava/io/DataOutputStream;->writeLong(J)V

    return-void

    .line 105
    :cond_1
    instance-of v0, p1, Ljava/lang/Float;

    if-eqz v0, :cond_2

    .line 106
    check-cast p1, Ljava/lang/Float;

    invoke-virtual {p1}, Ljava/lang/Float;->floatValue()F

    move-result p1

    invoke-virtual {p0, p1}, Ljava/io/DataOutputStream;->writeFloat(F)V

    return-void

    .line 107
    :cond_2
    instance-of v0, p1, Ljava/lang/String;

    if-eqz v0, :cond_3

    .line 108
    check-cast p1, Ljava/lang/String;

    invoke-virtual {p0, p1}, Ljava/io/DataOutputStream;->writeUTF(Ljava/lang/String;)V

    return-void

    .line 109
    :cond_3
    instance-of v0, p1, Ljava/util/HashMap;

    if-eqz v0, :cond_4

    .line 110
    check-cast p1, Ljava/util/HashMap;

    invoke-static {p0, p1}, Lcom/google/research/reflection/a/f;->a(Ljava/io/DataOutputStream;Ljava/util/Map;)V

    return-void

    .line 111
    :cond_4
    instance-of v0, p1, [I

    const/4 v1, 0x0

    if-eqz v0, :cond_6

    .line 112
    check-cast p1, [I

    .line 113
    array-length v0, p1

    invoke-virtual {p0, v0}, Ljava/io/DataOutputStream;->writeInt(I)V

    .line 114
    :goto_0
    array-length v0, p1

    if-ge v1, v0, :cond_5

    .line 115
    aget v0, p1, v1

    invoke-virtual {p0, v0}, Ljava/io/DataOutputStream;->writeInt(I)V

    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    :cond_5
    return-void

    .line 117
    :cond_6
    instance-of v0, p1, [F

    if-eqz v0, :cond_7

    .line 118
    check-cast p1, [F

    .line 119
    array-length v0, p1

    invoke-virtual {p0, v0}, Ljava/io/DataOutputStream;->writeInt(I)V

    .line 120
    :goto_1
    array-length v0, p1

    if-ge v1, v0, :cond_7

    .line 121
    aget v0, p1, v1

    invoke-virtual {p0, v0}, Ljava/io/DataOutputStream;->writeFloat(F)V

    add-int/lit8 v1, v1, 0x1

    goto :goto_1

    :cond_7
    return-void
.end method

.method public static a(Ljava/io/DataOutputStream;Ljava/util/Map;)V
    .locals 2
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "<V:",
            "Ljava/lang/Object;",
            "K:",
            "Ljava/lang/Object;",
            ">(",
            "Ljava/io/DataOutputStream;",
            "Ljava/util/Map<",
            "TK;TV;>;)V"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 81
    invoke-interface/range {p1 .. p1}, Ljava/util/Map;->size()I

    move-result v0

    invoke-virtual {p0, v0}, Ljava/io/DataOutputStream;->writeInt(I)V

    .line 82
    invoke-interface/range {p1 .. p1}, Ljava/util/Map;->entrySet()Ljava/util/Set;

    move-result-object p1

    invoke-interface {p1}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :goto_0
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_0

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Ljava/util/Map$Entry;

    .line 83
    invoke-interface {v0}, Ljava/util/Map$Entry;->getKey()Ljava/lang/Object;

    move-result-object v1

    invoke-static {p0, v1}, Lcom/google/research/reflection/a/f;->a(Ljava/io/DataOutputStream;Ljava/lang/Object;)V

    .line 84
    invoke-interface {v0}, Ljava/util/Map$Entry;->getValue()Ljava/lang/Object;

    move-result-object v0

    invoke-static {p0, v0}, Lcom/google/research/reflection/a/f;->a(Ljava/io/DataOutputStream;Ljava/lang/Object;)V

    goto :goto_0

    :cond_0
    return-void
.end method
