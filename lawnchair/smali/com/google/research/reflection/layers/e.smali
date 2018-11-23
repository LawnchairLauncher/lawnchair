.class public Lcom/google/research/reflection/layers/e;
.super Ljava/lang/Object;
.source "SourceFile"


# instance fields
.field public columnCount:I

.field public eR:[D

.field public rowCount:I


# direct methods
.method public constructor <init>()V
    .locals 0

    .line 26
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public constructor <init>(II)V
    .locals 0

    .line 20
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 21
    iput p1, p0, Lcom/google/research/reflection/layers/e;->rowCount:I

    .line 22
    iput p2, p0, Lcom/google/research/reflection/layers/e;->columnCount:I

    mul-int p1, p1, p2

    .line 23
    new-array p1, p1, [D

    iput-object p1, p0, Lcom/google/research/reflection/layers/e;->eR:[D

    return-void
.end method

.method public static a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;
    .locals 10

    const/4 v0, 0x0

    .line 201
    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v1

    invoke-virtual {p1, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v2

    if-ne v1, v2, :cond_3

    .line 204
    new-instance v1, Lcom/google/research/reflection/layers/e;

    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v2

    .line 205
    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v3

    invoke-virtual {p1, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v4

    add-int/2addr v3, v4

    invoke-direct {v1, v2, v3}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    const/4 v2, 0x0

    .line 206
    :goto_0
    invoke-virtual {v1, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v3

    if-ge v2, v3, :cond_2

    const/4 v9, 0x0

    .line 207
    :goto_1
    invoke-virtual {v1, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v3

    if-ge v9, v3, :cond_1

    .line 208
    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v3

    if-ge v9, v3, :cond_0

    const/4 v4, 0x0

    .line 209
    invoke-virtual {p0, v0, v2, v9}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v7

    move-object v3, v1

    move v5, v2

    move v6, v9

    invoke-virtual/range {v3 .. v8}, Lcom/google/research/reflection/layers/e;->b(ZIID)V

    goto :goto_2

    :cond_0
    const/4 v4, 0x0

    .line 212
    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v3

    sub-int v3, v9, v3

    invoke-virtual {p1, v0, v2, v3}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v7

    move-object v3, v1

    move v5, v2

    move v6, v9

    .line 211
    invoke-virtual/range {v3 .. v8}, Lcom/google/research/reflection/layers/e;->b(ZIID)V

    :goto_2
    add-int/lit8 v9, v9, 0x1

    goto :goto_1

    :cond_1
    add-int/lit8 v2, v2, 0x1

    goto :goto_0

    :cond_2
    return-object v1

    .line 202
    :cond_3
    new-instance p0, Ljava/lang/RuntimeException;

    invoke-direct {p0}, Ljava/lang/RuntimeException;-><init>()V

    throw p0
.end method

.method public static a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Z)Lcom/google/research/reflection/layers/e;
    .locals 5
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    const/4 v0, 0x0

    .line 51
    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v1

    invoke-virtual {p1, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v2

    if-ne v1, v2, :cond_0

    .line 52
    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v1

    invoke-virtual {p1, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v2

    if-ne v1, v2, :cond_0

    .line 53
    invoke-virtual {p2, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v1

    invoke-virtual {p1, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v2

    if-ne v1, v2, :cond_0

    .line 54
    invoke-virtual {p2, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v1

    invoke-virtual {p1, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v2

    if-ne v1, v2, :cond_0

    .line 60
    invoke-static {}, Lcom/google/research/reflection/layers/i;->ak()Lcom/google/research/reflection/layers/i;

    move-result-object v0

    iget-object v1, p2, Lcom/google/research/reflection/layers/e;->eR:[D

    array-length v1, v1

    new-instance v2, Lcom/google/research/reflection/layers/e$1;

    invoke-direct {v2, p3, p2, p0, p1}, Lcom/google/research/reflection/layers/e$1;-><init>(ZLcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V

    invoke-virtual {v0, v1, v2}, Lcom/google/research/reflection/layers/i;->a(ILcom/google/research/reflection/layers/h;)V

    return-object p2

    .line 55
    :cond_0
    new-instance p3, Ljava/lang/RuntimeException;

    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v1

    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result p0

    .line 56
    invoke-virtual {p1, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v2

    invoke-virtual {p1, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result p1

    .line 57
    invoke-virtual {p2, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v3

    invoke-virtual {p2, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result p2

    const/16 v0, 0x47

    new-instance v4, Ljava/lang/StringBuilder;

    invoke-direct {v4, v0}, Ljava/lang/StringBuilder;-><init>(I)V

    invoke-virtual {v4, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v0, "x"

    invoke-virtual {v4, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v4, p0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string p0, " "

    invoke-virtual {v4, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v4, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string p0, "x"

    invoke-virtual {v4, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v4, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string p0, " "

    invoke-virtual {v4, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v4, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string p0, "x"

    invoke-virtual {v4, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v4, p2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v4}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-direct {p3, p0}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw p3
.end method

.method public static a(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;ZLcom/google/research/reflection/layers/e;Z)Lcom/google/research/reflection/layers/e;
    .locals 9
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    const/4 v0, 0x0

    .line 76
    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v1

    invoke-virtual/range {p1 .. p2}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v2

    if-ne v1, v2, :cond_0

    .line 77
    invoke-virtual {p3, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v1

    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v2

    if-ne v1, v2, :cond_0

    .line 78
    invoke-virtual {p3, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v1

    invoke-virtual/range {p1 .. p2}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v2

    if-ne v1, v2, :cond_0

    .line 84
    invoke-static {}, Lcom/google/research/reflection/layers/i;->ak()Lcom/google/research/reflection/layers/i;

    move-result-object v0

    iget-object v1, p3, Lcom/google/research/reflection/layers/e;->eR:[D

    array-length v1, v1

    new-instance v8, Lcom/google/research/reflection/layers/e$2;

    move-object v2, v8

    move v3, p4

    move-object v4, p3

    move-object v5, p0

    move-object v6, p1

    move v7, p2

    invoke-direct/range {v2 .. v7}, Lcom/google/research/reflection/layers/e$2;-><init>(ZLcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Z)V

    invoke-virtual {v0, v1, v8}, Lcom/google/research/reflection/layers/i;->a(ILcom/google/research/reflection/layers/h;)V

    return-object p3

    .line 79
    :cond_0
    new-instance p4, Ljava/lang/RuntimeException;

    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v1

    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result p0

    .line 80
    invoke-virtual/range {p1 .. p2}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v2

    invoke-virtual/range {p1 .. p2}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result p1

    .line 81
    invoke-virtual {p3, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result p2

    invoke-virtual {p3, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result p3

    const/16 v0, 0x47

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3, v0}, Ljava/lang/StringBuilder;-><init>(I)V

    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v0, "x"

    invoke-virtual {v3, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, p0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string p0, " "

    invoke-virtual {v3, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string p0, "x"

    invoke-virtual {v3, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string p0, " "

    invoke-virtual {v3, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, p2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string p0, "x"

    invoke-virtual {v3, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, p3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-direct {p4, p0}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw p4
.end method


# virtual methods
.method public final a(ZII)I
    .locals 0

    if-eqz p1, :cond_0

    .line 125
    iget p1, p0, Lcom/google/research/reflection/layers/e;->columnCount:I

    mul-int p3, p3, p1

    add-int/2addr p3, p2

    return p3

    .line 127
    :cond_0
    iget p1, p0, Lcom/google/research/reflection/layers/e;->columnCount:I

    mul-int p2, p2, p1

    add-int/2addr p2, p3

    return p2
.end method

.method public final a(D)Lcom/google/research/reflection/layers/e;
    .locals 3
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    .line 242
    invoke-static {}, Lcom/google/research/reflection/layers/i;->ak()Lcom/google/research/reflection/layers/i;

    move-result-object v0

    iget-object v1, p0, Lcom/google/research/reflection/layers/e;->eR:[D

    array-length v1, v1

    new-instance v2, Lcom/google/research/reflection/layers/e$3;

    invoke-direct {v2, p0, p1, p2}, Lcom/google/research/reflection/layers/e$3;-><init>(Lcom/google/research/reflection/layers/e;D)V

    invoke-virtual {v0, v1, v2}, Lcom/google/research/reflection/layers/i;->a(ILcom/google/research/reflection/layers/h;)V

    return-object p0
.end method

.method public final a(Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;
    .locals 6
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    const/4 v0, 0x0

    .line 255
    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v1

    invoke-virtual {p1, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v2

    if-ne v1, v2, :cond_0

    .line 256
    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v1

    invoke-virtual {p1, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v2

    if-ne v1, v2, :cond_0

    .line 260
    invoke-static {}, Lcom/google/research/reflection/layers/i;->ak()Lcom/google/research/reflection/layers/i;

    move-result-object v0

    iget-object v1, p0, Lcom/google/research/reflection/layers/e;->eR:[D

    array-length v1, v1

    new-instance v2, Lcom/google/research/reflection/layers/e$4;

    invoke-direct {v2, p0, p1}, Lcom/google/research/reflection/layers/e$4;-><init>(Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V

    invoke-virtual {v0, v1, v2}, Lcom/google/research/reflection/layers/i;->a(ILcom/google/research/reflection/layers/h;)V

    return-object p0

    .line 257
    :cond_0
    new-instance v1, Ljava/lang/RuntimeException;

    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v2

    invoke-virtual {p0, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v3

    .line 258
    invoke-virtual {p1, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v4

    invoke-virtual {p1, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result p1

    const/16 v0, 0x2f

    new-instance v5, Ljava/lang/StringBuilder;

    invoke-direct {v5, v0}, Ljava/lang/StringBuilder;-><init>(I)V

    invoke-virtual {v5, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v0, "x"

    invoke-virtual {v5, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v0, " "

    invoke-virtual {v5, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v0, "x"

    invoke-virtual {v5, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v5, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v5}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-direct {v1, p1}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1
.end method

.method public final a(ZIID)V
    .locals 1

    .line 142
    iget-object v0, p0, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-virtual/range {p0 .. p3}, Lcom/google/research/reflection/layers/e;->a(ZII)I

    move-result p1

    aget-wide p2, v0, p1

    add-double/2addr p2, p4

    aput-wide p2, v0, p1

    return-void
.end method

.method public final af()Lcom/google/research/reflection/layers/e;
    .locals 6

    .line 42
    new-instance v0, Lcom/google/research/reflection/layers/e;

    iget v1, p0, Lcom/google/research/reflection/layers/e;->rowCount:I

    iget v2, p0, Lcom/google/research/reflection/layers/e;->columnCount:I

    invoke-direct {v0, v1, v2}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    const/4 v1, 0x0

    .line 43
    :goto_0
    iget-object v2, p0, Lcom/google/research/reflection/layers/e;->eR:[D

    array-length v2, v2

    if-ge v1, v2, :cond_0

    .line 44
    iget-object v2, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    iget-object v3, p0, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v4, v3, v1

    aput-wide v4, v2, v1

    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    :cond_0
    return-object v0
.end method

.method public final b(ZII)D
    .locals 2

    .line 132
    invoke-virtual/range {p0 .. p1}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v0

    const/16 v1, 0x29

    if-ge p2, v0, :cond_1

    .line 135
    invoke-virtual/range {p0 .. p1}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v0

    if-ge p3, v0, :cond_0

    .line 138
    iget-object v0, p0, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-virtual/range {p0 .. p3}, Lcom/google/research/reflection/layers/e;->a(ZII)I

    move-result p1

    aget-wide p1, v0, p1

    return-wide p1

    .line 136
    :cond_0
    new-instance p2, Ljava/lang/RuntimeException;

    invoke-virtual/range {p0 .. p1}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result p1

    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(I)V

    const-string v1, "requested col: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string p3, " >= "

    invoke-virtual {v0, p3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-direct {p2, p1}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw p2

    .line 133
    :cond_1
    new-instance p3, Ljava/lang/RuntimeException;

    invoke-virtual/range {p0 .. p1}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result p1

    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(I)V

    const-string v1, "requested row: "

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string p2, " >= "

    invoke-virtual {v0, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-direct {p3, p1}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw p3
.end method

.method public final b(Ljava/io/DataInputStream;)V
    .locals 4
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 158
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readInt()I

    move-result v0

    iput v0, p0, Lcom/google/research/reflection/layers/e;->rowCount:I

    .line 159
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readInt()I

    move-result v0

    iput v0, p0, Lcom/google/research/reflection/layers/e;->columnCount:I

    .line 160
    iget v0, p0, Lcom/google/research/reflection/layers/e;->rowCount:I

    iget v1, p0, Lcom/google/research/reflection/layers/e;->columnCount:I

    mul-int v0, v0, v1

    new-array v0, v0, [D

    iput-object v0, p0, Lcom/google/research/reflection/layers/e;->eR:[D

    const/4 v0, 0x0

    .line 161
    :goto_0
    iget-object v1, p0, Lcom/google/research/reflection/layers/e;->eR:[D

    array-length v1, v1

    if-ge v0, v1, :cond_0

    .line 162
    iget-object v1, p0, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readDouble()D

    move-result-wide v2

    aput-wide v2, v1, v0

    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    :cond_0
    return-void
.end method

.method public final b(Ljava/io/DataOutputStream;)V
    .locals 4
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 150
    iget v0, p0, Lcom/google/research/reflection/layers/e;->rowCount:I

    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeInt(I)V

    .line 151
    iget v0, p0, Lcom/google/research/reflection/layers/e;->columnCount:I

    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeInt(I)V

    const/4 v0, 0x0

    .line 152
    :goto_0
    iget-object v1, p0, Lcom/google/research/reflection/layers/e;->eR:[D

    array-length v1, v1

    if-ge v0, v1, :cond_0

    .line 153
    iget-object v1, p0, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v2, v1, v0

    invoke-virtual {p1, v2, v3}, Ljava/io/DataOutputStream;->writeDouble(D)V

    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    :cond_0
    return-void
.end method

.method public final b(ZIID)V
    .locals 1

    .line 146
    iget-object v0, p0, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-virtual/range {p0 .. p3}, Lcom/google/research/reflection/layers/e;->a(ZII)I

    move-result p1

    aput-wide p4, v0, p1

    return-void
.end method

.method public synthetic clone()Ljava/lang/Object;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/CloneNotSupportedException;
        }
    .end annotation

    .line 10
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/layers/e;->af()Lcom/google/research/reflection/layers/e;

    move-result-object v0

    return-object v0
.end method

.method public final f(Z)I
    .locals 0

    if-eqz p1, :cond_0

    .line 168
    iget p1, p0, Lcom/google/research/reflection/layers/e;->columnCount:I

    return p1

    .line 170
    :cond_0
    iget p1, p0, Lcom/google/research/reflection/layers/e;->rowCount:I

    return p1
.end method

.method public final g(Z)I
    .locals 0

    if-eqz p1, :cond_0

    .line 176
    iget p1, p0, Lcom/google/research/reflection/layers/e;->rowCount:I

    return p1

    .line 178
    :cond_0
    iget p1, p0, Lcom/google/research/reflection/layers/e;->columnCount:I

    return p1
.end method

.method public toString()Ljava/lang/String;
    .locals 8

    .line 113
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const/4 v1, 0x0

    const/4 v2, 0x0

    .line 114
    :goto_0
    invoke-virtual {p0, v1}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v3

    if-ge v2, v3, :cond_1

    const/4 v3, 0x0

    .line 115
    :goto_1
    invoke-virtual {p0, v1}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v4

    if-ge v3, v4, :cond_0

    .line 116
    invoke-virtual {p0, v1, v2, v3}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v4

    const/16 v6, 0x19

    new-instance v7, Ljava/lang/StringBuilder;

    invoke-direct {v7, v6}, Ljava/lang/StringBuilder;-><init>(I)V

    invoke-virtual {v7, v4, v5}, Ljava/lang/StringBuilder;->append(D)Ljava/lang/StringBuilder;

    const-string v4, " "

    invoke-virtual {v7, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v7}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v0, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    add-int/lit8 v3, v3, 0x1

    goto :goto_1

    :cond_0
    const-string v3, "\n"

    .line 118
    invoke-virtual {v0, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    add-int/lit8 v2, v2, 0x1

    goto :goto_0

    .line 120
    :cond_1
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method
