.class public Lcom/google/research/reflection/predictor/d;
.super Lcom/google/research/reflection/predictor/AbstractEventEstimator;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/b/l;


# instance fields
.field protected dA:Lcom/google/research/reflection/b/l;

.field protected fA:Lcom/google/research/reflection/layers/f;

.field protected fB:Lcom/google/research/reflection/b/f;

.field protected fC:Lcom/google/research/reflection/layers/e;


# direct methods
.method public constructor <init>()V
    .locals 15

    .line 50
    invoke-direct/range {p0 .. p0}, Lcom/google/research/reflection/predictor/AbstractEventEstimator;-><init>()V

    .line 51
    new-instance v0, Lcom/google/research/reflection/b/e;

    invoke-direct {v0}, Lcom/google/research/reflection/b/e;-><init>()V

    new-instance v1, Lcom/google/research/reflection/b/a;

    invoke-direct {v1}, Lcom/google/research/reflection/b/a;-><init>()V

    invoke-virtual {v0, v1}, Lcom/google/research/reflection/b/e;->a(Lcom/google/research/reflection/b/f;)V

    new-instance v1, Lcom/google/research/reflection/b/h;

    invoke-direct {v1}, Lcom/google/research/reflection/b/h;-><init>()V

    invoke-virtual {v0, v1}, Lcom/google/research/reflection/b/e;->a(Lcom/google/research/reflection/b/f;)V

    new-instance v1, Lcom/google/research/reflection/b/c;

    invoke-direct {v1}, Lcom/google/research/reflection/b/c;-><init>()V

    invoke-virtual {v0, v1}, Lcom/google/research/reflection/b/e;->a(Lcom/google/research/reflection/b/f;)V

    new-instance v1, Lcom/google/research/reflection/b/j;

    invoke-direct {v1}, Lcom/google/research/reflection/b/j;-><init>()V

    invoke-virtual {v0, v1}, Lcom/google/research/reflection/b/e;->a(Lcom/google/research/reflection/b/f;)V

    new-instance v1, Lcom/google/research/reflection/b/i;

    invoke-direct {v1}, Lcom/google/research/reflection/b/i;-><init>()V

    invoke-virtual {v0, v1}, Lcom/google/research/reflection/b/e;->a(Lcom/google/research/reflection/b/f;)V

    new-instance v1, Lcom/google/research/reflection/b/g;

    invoke-direct {v1}, Lcom/google/research/reflection/b/g;-><init>()V

    invoke-virtual {v0, v1}, Lcom/google/research/reflection/b/e;->a(Lcom/google/research/reflection/b/f;)V

    new-instance v1, Lcom/google/research/reflection/b/b;

    invoke-direct {v1}, Lcom/google/research/reflection/b/b;-><init>()V

    invoke-virtual {v0, v1}, Lcom/google/research/reflection/b/e;->a(Lcom/google/research/reflection/b/f;)V

    new-instance v1, Lcom/google/research/reflection/b/k;

    invoke-direct {v1}, Lcom/google/research/reflection/b/k;-><init>()V

    invoke-virtual {v0, v1}, Lcom/google/research/reflection/b/e;->a(Lcom/google/research/reflection/b/f;)V

    const/4 v1, 0x0

    sput-boolean v1, Lcom/google/research/reflection/layers/i;->fj:Z

    new-instance v1, Lcom/google/research/reflection/layers/e;

    const/4 v2, 0x1

    const/16 v3, 0x64

    invoke-direct {v1, v2, v3}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object v1, p0, Lcom/google/research/reflection/predictor/d;->fC:Lcom/google/research/reflection/layers/e;

    iput-object v0, p0, Lcom/google/research/reflection/predictor/d;->fB:Lcom/google/research/reflection/b/f;

    iput-object p0, v0, Lcom/google/research/reflection/b/f;->dA:Lcom/google/research/reflection/b/l;

    new-instance v1, Lcom/google/research/reflection/layers/f;

    invoke-direct {v1, v2}, Lcom/google/research/reflection/layers/f;-><init>(I)V

    iput-object v1, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    new-instance v1, Lcom/google/research/reflection/layers/d;

    invoke-virtual {v0}, Lcom/google/research/reflection/b/f;->T()I

    move-result v8

    const/4 v4, 0x0

    const/4 v5, 0x1

    const/4 v6, 0x1

    const/4 v7, 0x1

    const/16 v9, 0x64

    const/4 v10, -0x1

    const/4 v11, -0x1

    const/4 v12, 0x0

    const/4 v13, 0x0

    const/4 v14, 0x0

    move-object v3, v1

    invoke-direct/range {v3 .. v14}, Lcom/google/research/reflection/layers/d;-><init>(ZIIIIIIIZZF)V

    iget-object v0, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    invoke-virtual {v0, v1}, Lcom/google/research/reflection/layers/f;->b(Lcom/google/research/reflection/layers/c;)V

    new-instance v0, Lcom/google/research/reflection/layers/g;

    const/4 v3, 0x1

    const/4 v4, 0x1

    const/4 v5, 0x2

    const/16 v7, 0x64

    const/16 v8, 0x64

    const/4 v9, -0x1

    const/4 v11, 0x0

    move-object v2, v0

    invoke-direct/range {v2 .. v11}, Lcom/google/research/reflection/layers/g;-><init>(IIIIIIIIZ)V

    iget-object v1, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    invoke-virtual {v1, v0}, Lcom/google/research/reflection/layers/f;->b(Lcom/google/research/reflection/layers/c;)V

    return-void
.end method


# virtual methods
.method public final Y()Ljava/util/Map;
    .locals 2
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Ljava/lang/Boolean;",
            ">;"
        }
    .end annotation

    .line 259
    new-instance v0, Ljava/util/HashMap;

    iget-object v1, p0, Lcom/google/research/reflection/predictor/d;->fB:Lcom/google/research/reflection/b/f;

    invoke-virtual {v1}, Lcom/google/research/reflection/b/f;->Y()Ljava/util/Map;

    move-result-object v1

    invoke-direct {v0, v1}, Ljava/util/HashMap;-><init>(Ljava/util/Map;)V

    return-object v0
.end method

.method public final a([FLcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/k;
    .locals 6

    .line 130
    iget-object v0, p0, Lcom/google/research/reflection/predictor/d;->fB:Lcom/google/research/reflection/b/f;

    iget-object v1, p0, Lcom/google/research/reflection/predictor/g;->fG:Lcom/google/research/reflection/predictor/b;

    invoke-virtual {v1}, Lcom/google/research/reflection/predictor/b;->ar()Lcom/google/research/reflection/a/a;

    move-result-object v1

    invoke-virtual {v0, v1, p2}, Lcom/google/research/reflection/b/f;->b(Lcom/google/research/reflection/a/a;Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/layers/e;

    move-result-object p2

    .line 131
    new-instance v0, Lcom/google/research/reflection/predictor/k;

    invoke-direct {v0}, Lcom/google/research/reflection/predictor/k;-><init>()V

    .line 132
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/predictor/d;->aq()Ljava/util/HashMap;

    move-result-object v1

    invoke-virtual {v1}, Ljava/util/HashMap;->size()I

    move-result v1

    const/4 v2, 0x1

    const/4 v3, 0x0

    if-ne v1, v2, :cond_0

    const/high16 v1, 0x3f800000    # 1.0f

    .line 133
    aput v1, p1, v3

    goto :goto_1

    .line 134
    :cond_0
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/predictor/d;->aq()Ljava/util/HashMap;

    move-result-object v1

    invoke-virtual {v1}, Ljava/util/HashMap;->size()I

    move-result v1

    if-le v1, v2, :cond_1

    .line 135
    :try_start_0
    iget-object v1, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    const/4 v4, 0x0

    invoke-virtual {v1, v3, v4, p2, v2}, Lcom/google/research/reflection/layers/f;->a(Z[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;Z)Lcom/google/research/reflection/layers/e;

    move-result-object v1
    :try_end_0
    .catch Lcom/google/research/reflection/layers/InvalidValueException; {:try_start_0 .. :try_end_0} :catch_0

    const/4 v2, 0x0

    .line 141
    :goto_0
    array-length v4, p1

    if-ge v2, v4, :cond_1

    .line 142
    invoke-virtual {v1, v3, v3, v2}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v4

    double-to-float v4, v4

    aput v4, p1, v2

    add-int/lit8 v2, v2, 0x1

    goto :goto_0

    :catch_0
    return-object v0

    .line 145
    :cond_1
    :goto_1
    iput-object p1, v0, Lcom/google/research/reflection/predictor/k;->fP:[F

    .line 146
    iget-object p1, p2, Lcom/google/research/reflection/layers/e;->eR:[D

    iput-object p1, v0, Lcom/google/research/reflection/predictor/k;->fO:[D

    return-object v0
.end method

.method public final a(Lcom/google/research/reflection/b/f;I)V
    .locals 9

    .line 82
    iget-object v0, p0, Lcom/google/research/reflection/predictor/d;->dA:Lcom/google/research/reflection/b/l;

    if-eqz v0, :cond_0

    .line 83
    iget-object v0, p0, Lcom/google/research/reflection/predictor/d;->dA:Lcom/google/research/reflection/b/l;

    invoke-interface {v0, p1, p2}, Lcom/google/research/reflection/b/l;->a(Lcom/google/research/reflection/b/f;I)V

    .line 85
    :cond_0
    iget-object p1, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    iget-object p1, p1, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    const/4 v0, 0x0

    invoke-interface {p1, v0}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lcom/google/research/reflection/layers/d;

    .line 86
    iget-object v1, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    invoke-virtual {p1, v1}, Lcom/google/research/reflection/layers/d;->a(Lcom/google/research/reflection/layers/f;)Lcom/google/research/reflection/layers/e;

    move-result-object p1

    .line 87
    invoke-virtual {p1, v0}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v1

    const/4 v8, 0x0

    .line 88
    :goto_0
    invoke-virtual {p1, v0}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v2

    if-ge v8, v2, :cond_2

    const-wide/16 v2, 0x0

    move-wide v3, v2

    const/4 v2, 0x0

    :goto_1
    if-ge v2, v1, :cond_1

    .line 91
    invoke-virtual {p1, v0, v2, v8}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v5

    add-double/2addr v3, v5

    add-int/lit8 v2, v2, 0x1

    goto :goto_1

    :cond_1
    int-to-double v5, v1

    div-double v6, v3, v5

    const/4 v3, 0x0

    move-object v2, p1

    move v4, p2

    move v5, v8

    .line 94
    invoke-virtual/range {v2 .. v7}, Lcom/google/research/reflection/layers/e;->b(ZIID)V

    add-int/lit8 v8, v8, 0x1

    goto :goto_0

    :cond_2
    return-void
.end method

.method public final a(Ljava/io/DataInputStream;Lcom/google/research/reflection/signal/ReflectionEvent;)V
    .locals 3
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 197
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readUTF()Ljava/lang/String;

    move-result-object p2

    .line 198
    invoke-static {p2}, Lcom/google/research/reflection/b/f;->j(Ljava/lang/String;)Lcom/google/research/reflection/b/f;

    move-result-object v0

    if-nez v0, :cond_1

    .line 200
    new-instance p1, Ljava/io/IOException;

    const-string v0, "Cannot find extractor with "

    invoke-static {p2}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;

    move-result-object p2

    invoke-virtual {p2}, Ljava/lang/String;->length()I

    move-result v1

    if-eqz v1, :cond_0

    invoke-virtual {v0, p2}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;

    move-result-object p2

    goto :goto_0

    :cond_0
    new-instance p2, Ljava/lang/String;

    invoke-direct {p2, v0}, Ljava/lang/String;-><init>(Ljava/lang/String;)V

    :goto_0
    invoke-direct {p1, p2}, Ljava/io/IOException;-><init>(Ljava/lang/String;)V

    throw p1

    .line 202
    :cond_1
    invoke-virtual {v0, p1}, Lcom/google/research/reflection/b/f;->a(Ljava/io/DataInputStream;)V

    .line 203
    iput-object p0, v0, Lcom/google/research/reflection/b/f;->dA:Lcom/google/research/reflection/b/l;

    .line 205
    new-instance p2, Lcom/google/research/reflection/layers/f;

    const/4 v1, 0x1

    invoke-direct {p2, v1}, Lcom/google/research/reflection/layers/f;-><init>(I)V

    iput-object p2, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    .line 206
    iget-object p2, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    invoke-virtual {p2, p1}, Lcom/google/research/reflection/layers/f;->b(Ljava/io/DataInputStream;)V

    .line 207
    iget-object p1, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    invoke-virtual {p1}, Lcom/google/research/reflection/layers/f;->ah()I

    move-result p1

    invoke-virtual {v0}, Lcom/google/research/reflection/b/f;->T()I

    move-result p2

    if-ne p1, p2, :cond_3

    .line 214
    iget-object p1, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    invoke-virtual {p1}, Lcom/google/research/reflection/layers/f;->ai()Lcom/google/research/reflection/layers/c;

    move-result-object p1

    iget p1, p1, Lcom/google/research/reflection/layers/c;->ew:I

    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/predictor/d;->ao()I

    move-result p2

    if-ne p1, p2, :cond_2

    return-void

    .line 215
    :cond_2
    new-instance p1, Ljava/io/IOException;

    iget-object p2, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    .line 217
    invoke-virtual {p2}, Lcom/google/research/reflection/layers/f;->ai()Lcom/google/research/reflection/layers/c;

    move-result-object p2

    iget p2, p2, Lcom/google/research/reflection/layers/c;->ew:I

    .line 219
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/predictor/d;->ao()I

    move-result v0

    const/16 v1, 0x39

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2, v1}, Ljava/lang/StringBuilder;-><init>(I)V

    const-string v1, "Inconsistent model output size..."

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, p2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string p2, "!="

    invoke-virtual {v2, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p2

    invoke-direct {p1, p2}, Ljava/io/IOException;-><init>(Ljava/lang/String;)V

    throw p1

    .line 208
    :cond_3
    new-instance p1, Ljava/io/IOException;

    iget-object p2, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    .line 210
    invoke-virtual {p2}, Lcom/google/research/reflection/layers/f;->ah()I

    move-result p2

    .line 212
    invoke-virtual {v0}, Lcom/google/research/reflection/b/f;->T()I

    move-result v0

    const/16 v1, 0x4c

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2, v1}, Ljava/lang/StringBuilder;-><init>(I)V

    const-string v1, "Model to be loaded has an inconsistent input size:"

    invoke-virtual {v2, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, p2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string p2, " != "

    invoke-virtual {v2, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p2

    invoke-direct {p1, p2}, Ljava/io/IOException;-><init>(Ljava/lang/String;)V

    throw p1
.end method

.method public final a(Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;)V
    .locals 12

    .line 231
    iget-object p3, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    invoke-virtual {p3}, Lcom/google/research/reflection/layers/f;->ai()Lcom/google/research/reflection/layers/c;

    move-result-object p3

    check-cast p3, Lcom/google/research/reflection/layers/g;

    .line 232
    iget-object v0, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    invoke-virtual {p3, v0}, Lcom/google/research/reflection/layers/g;->a(Lcom/google/research/reflection/layers/f;)Lcom/google/research/reflection/layers/e;

    move-result-object v0

    const/4 v7, 0x0

    .line 233
    invoke-virtual {v0, v7}, Lcom/google/research/reflection/layers/e;->g(Z)I

    move-result v8

    const/4 v9, 0x0

    .line 234
    :goto_0
    invoke-virtual {v0, v7}, Lcom/google/research/reflection/layers/e;->f(Z)I

    move-result v1

    const-wide/16 v2, 0x0

    if-ge v9, v1, :cond_2

    const/4 v1, 0x0

    :goto_1
    if-ge v1, v8, :cond_0

    .line 237
    invoke-virtual {v0, v7, v9, v1}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v4

    add-double/2addr v2, v4

    add-int/lit8 v1, v1, 0x1

    goto :goto_1

    :cond_0
    int-to-double v4, v8

    div-double v10, v2, v4

    .line 240
    invoke-virtual/range {p1 .. p2}, Ljava/lang/Integer;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_1

    const/4 v2, 0x0

    .line 241
    invoke-virtual/range {p1 .. p1}, Ljava/lang/Integer;->intValue()I

    move-result v4

    invoke-virtual/range {p2 .. p2}, Ljava/lang/Integer;->intValue()I

    move-result v1

    invoke-virtual {v0, v7, v9, v1}, Lcom/google/research/reflection/layers/e;->b(ZII)D

    move-result-wide v5

    move-object v1, v0

    move v3, v9

    invoke-virtual/range {v1 .. v6}, Lcom/google/research/reflection/layers/e;->b(ZIID)V

    :cond_1
    const/4 v2, 0x0

    .line 243
    invoke-virtual/range {p2 .. p2}, Ljava/lang/Integer;->intValue()I

    move-result v4

    move-object v1, v0

    move v3, v9

    move-wide v5, v10

    invoke-virtual/range {v1 .. v6}, Lcom/google/research/reflection/layers/e;->b(ZIID)V

    add-int/lit8 v9, v9, 0x1

    goto :goto_0

    .line 245
    :cond_2
    iget-object p3, p3, Lcom/google/research/reflection/layers/d;->eE:Lcom/google/research/reflection/layers/e;

    :goto_2
    if-ge v7, v8, :cond_3

    .line 248
    iget-object v0, p3, Lcom/google/research/reflection/layers/e;->eR:[D

    aget-wide v4, v0, v7

    add-double/2addr v2, v4

    add-int/lit8 v7, v7, 0x1

    goto :goto_2

    :cond_3
    int-to-double v0, v8

    div-double/2addr v2, v0

    .line 251
    invoke-virtual/range {p1 .. p2}, Ljava/lang/Integer;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_4

    .line 252
    iget-object v0, p3, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-virtual/range {p1 .. p1}, Ljava/lang/Integer;->intValue()I

    move-result p1

    iget-object v1, p3, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-virtual/range {p2 .. p2}, Ljava/lang/Integer;->intValue()I

    move-result v4

    aget-wide v4, v1, v4

    aput-wide v4, v0, p1

    .line 254
    :cond_4
    iget-object p1, p3, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-virtual/range {p2 .. p2}, Ljava/lang/Integer;->intValue()I

    move-result p2

    aput-wide v2, p1, p2

    return-void
.end method

.method public final b(Ljava/io/DataOutputStream;)V
    .locals 3
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 190
    iget-object v0, p0, Lcom/google/research/reflection/predictor/d;->fB:Lcom/google/research/reflection/b/f;

    invoke-static {v0}, Lcom/google/research/reflection/b/f;->b(Lcom/google/research/reflection/b/f;)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeUTF(Ljava/lang/String;)V

    .line 191
    iget-object v0, p0, Lcom/google/research/reflection/predictor/d;->fB:Lcom/google/research/reflection/b/f;

    invoke-virtual {v0, p1}, Lcom/google/research/reflection/b/f;->a(Ljava/io/DataOutputStream;)V

    .line 192
    iget-object v0, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    iget-object v1, v0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v1}, Ljava/util/List;->size()I

    move-result v1

    invoke-virtual {p1, v1}, Ljava/io/DataOutputStream;->writeInt(I)V

    iget-object v0, v0, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_0

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/layers/c;

    invoke-virtual {v1}, Lcom/google/research/reflection/layers/c;->getName()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {p1, v2}, Ljava/io/DataOutputStream;->writeUTF(Ljava/lang/String;)V

    invoke-virtual {v1, p1}, Lcom/google/research/reflection/layers/c;->b(Ljava/io/DataOutputStream;)V

    goto :goto_0

    :cond_0
    const-string v0, "NeuralNet"

    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeUTF(Ljava/lang/String;)V

    return-void
.end method

.method public synthetic clone()Ljava/lang/Object;
    .locals 2
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/CloneNotSupportedException;
        }
    .end annotation

    .line 32
    new-instance v0, Lcom/google/research/reflection/predictor/d;

    invoke-direct {v0}, Lcom/google/research/reflection/predictor/d;-><init>()V

    iget-object v1, p0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    invoke-virtual {v1}, Lcom/google/research/reflection/layers/f;->ag()Lcom/google/research/reflection/layers/f;

    move-result-object v1

    iput-object v1, v0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    iget-object v1, p0, Lcom/google/research/reflection/predictor/d;->fC:Lcom/google/research/reflection/layers/e;

    invoke-virtual {v1}, Lcom/google/research/reflection/layers/e;->af()Lcom/google/research/reflection/layers/e;

    move-result-object v1

    iput-object v1, v0, Lcom/google/research/reflection/predictor/d;->fC:Lcom/google/research/reflection/layers/e;

    iget-object v1, p0, Lcom/google/research/reflection/predictor/d;->fB:Lcom/google/research/reflection/b/f;

    invoke-virtual {v1}, Lcom/google/research/reflection/b/f;->U()Lcom/google/research/reflection/b/f;

    move-result-object v1

    iput-object v1, v0, Lcom/google/research/reflection/predictor/d;->fB:Lcom/google/research/reflection/b/f;

    return-object v0
.end method

.method public getName()Ljava/lang/String;
    .locals 1

    const-string v0, "neural_predictor"

    return-object v0
.end method

.method public final i(Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/k;
    .locals 21

    move-object/from16 v0, p0

    .line 166
    invoke-virtual/range {p0 .. p1}, Lcom/google/research/reflection/predictor/d;->g(Lcom/google/research/reflection/signal/ReflectionEvent;)I

    move-result v1

    invoke-static {v1}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v1

    .line 167
    new-instance v2, Lcom/google/research/reflection/predictor/k;

    invoke-direct {v2}, Lcom/google/research/reflection/predictor/k;-><init>()V

    .line 169
    :try_start_0
    iget-object v3, v0, Lcom/google/research/reflection/predictor/d;->fB:Lcom/google/research/reflection/b/f;

    iget-object v4, v0, Lcom/google/research/reflection/predictor/g;->fG:Lcom/google/research/reflection/predictor/b;

    invoke-virtual {v4}, Lcom/google/research/reflection/predictor/b;->ar()Lcom/google/research/reflection/a/a;

    move-result-object v4

    move-object/from16 v5, p1

    invoke-virtual {v3, v4, v5}, Lcom/google/research/reflection/b/f;->b(Lcom/google/research/reflection/a/a;Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/layers/e;

    move-result-object v3

    .line 170
    iget-object v4, v0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    iget-object v5, v4, Lcom/google/research/reflection/layers/f;->eZ:Lcom/google/research/reflection/a/a;

    invoke-virtual {v5}, Lcom/google/research/reflection/a/a;->clear()V

    iget-object v5, v4, Lcom/google/research/reflection/layers/f;->fa:Lcom/google/research/reflection/a/a;

    invoke-virtual {v5}, Lcom/google/research/reflection/a/a;->clear()V

    iget-object v4, v4, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v4}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v4

    :goto_0
    invoke-interface {v4}, Ljava/util/Iterator;->hasNext()Z

    move-result v5

    if-eqz v5, :cond_0

    invoke-interface {v4}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Lcom/google/research/reflection/layers/c;

    invoke-virtual {v5}, Lcom/google/research/reflection/layers/c;->ab()V

    goto :goto_0

    .line 171
    :cond_0
    iget-object v4, v0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    const/4 v5, 0x0

    const/4 v6, 0x1

    invoke-virtual {v4, v6, v5, v3, v6}, Lcom/google/research/reflection/layers/f;->a(Z[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;Z)Lcom/google/research/reflection/layers/e;

    .line 172
    iget-object v4, v0, Lcom/google/research/reflection/predictor/d;->fC:Lcom/google/research/reflection/layers/e;

    iget-object v4, v4, Lcom/google/research/reflection/layers/e;->eR:[D

    const-wide/16 v7, 0x0

    invoke-static {v4, v7, v8}, Ljava/util/Arrays;->fill([DD)V

    .line 173
    iget-object v9, v0, Lcom/google/research/reflection/predictor/d;->fC:Lcom/google/research/reflection/layers/e;

    const/4 v10, 0x0

    const/4 v11, 0x0

    invoke-virtual {v1}, Ljava/lang/Integer;->intValue()I

    move-result v12

    const-wide/high16 v13, 0x3ff0000000000000L    # 1.0

    invoke-virtual/range {v9 .. v14}, Lcom/google/research/reflection/layers/e;->b(ZIID)V

    .line 174
    iget-object v1, v0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    iget-object v4, v0, Lcom/google/research/reflection/predictor/d;->fC:Lcom/google/research/reflection/layers/e;

    iget-object v7, v1, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    iget-object v8, v1, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v8}, Ljava/util/List;->size()I

    move-result v8

    sub-int/2addr v8, v6

    invoke-interface {v7, v8}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v7

    instance-of v7, v7, Lcom/google/research/reflection/layers/g;

    if-eqz v7, :cond_8

    if-nez v4, :cond_1

    new-instance v4, Lcom/google/research/reflection/layers/e;

    invoke-virtual {v1}, Lcom/google/research/reflection/layers/f;->ai()Lcom/google/research/reflection/layers/c;

    move-result-object v5

    iget v5, v5, Lcom/google/research/reflection/layers/c;->ew:I

    invoke-direct {v4, v6, v5}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    new-instance v5, Lcom/google/research/reflection/layers/e;

    invoke-virtual {v1}, Lcom/google/research/reflection/layers/f;->ai()Lcom/google/research/reflection/layers/c;

    move-result-object v7

    iget v7, v7, Lcom/google/research/reflection/layers/c;->ew:I

    invoke-direct {v5, v6, v7}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iget-object v6, v1, Lcom/google/research/reflection/layers/f;->eZ:Lcom/google/research/reflection/a/a;

    invoke-virtual {v6, v4}, Lcom/google/research/reflection/a/a;->a(Ljava/lang/Object;)Ljava/lang/Object;

    iget-object v1, v1, Lcom/google/research/reflection/layers/f;->fa:Lcom/google/research/reflection/a/a;

    invoke-virtual {v1, v5}, Lcom/google/research/reflection/a/a;->a(Ljava/lang/Object;)Ljava/lang/Object;

    goto/16 :goto_5

    :cond_1
    iget-object v7, v1, Lcom/google/research/reflection/layers/f;->eZ:Lcom/google/research/reflection/a/a;

    invoke-virtual {v7, v4}, Lcom/google/research/reflection/a/a;->a(Ljava/lang/Object;)Ljava/lang/Object;

    iget-object v4, v1, Lcom/google/research/reflection/layers/f;->fa:Lcom/google/research/reflection/a/a;

    invoke-virtual {v4, v5}, Lcom/google/research/reflection/a/a;->a(Ljava/lang/Object;)Ljava/lang/Object;

    iget-object v4, v1, Lcom/google/research/reflection/layers/f;->eZ:Lcom/google/research/reflection/a/a;

    iget v4, v4, Lcom/google/research/reflection/a/a;->dn:I

    invoke-virtual {v1}, Lcom/google/research/reflection/layers/f;->aa()V

    iget-object v5, v1, Lcom/google/research/reflection/layers/f;->eZ:Lcom/google/research/reflection/a/a;

    iget v5, v5, Lcom/google/research/reflection/a/a;->dk:I

    iget-object v7, v1, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v7}, Ljava/util/List;->size()I

    move-result v7

    sub-int/2addr v7, v6

    move v8, v7

    :goto_1
    if-ltz v8, :cond_5

    iget-object v9, v1, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v9, v8}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v9

    check-cast v9, Lcom/google/research/reflection/layers/c;

    iget-boolean v10, v9, Lcom/google/research/reflection/layers/c;->ev:Z

    if-eqz v10, :cond_3

    iget-object v9, v9, Lcom/google/research/reflection/layers/c;->er:Lcom/google/research/reflection/a/a;

    iget v9, v9, Lcom/google/research/reflection/a/a;->dn:I

    if-ne v9, v4, :cond_2

    goto :goto_2

    :cond_2
    new-instance v1, Ljava/lang/RuntimeException;

    const-string v3, "backward: sparse input vector has a different frame index from the target frame index"

    invoke-direct {v1, v3}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    :cond_3
    iget-object v10, v9, Lcom/google/research/reflection/layers/c;->eq:Lcom/google/research/reflection/a/a;

    iget v10, v10, Lcom/google/research/reflection/a/a;->dn:I

    if-ne v10, v4, :cond_4

    :goto_2
    add-int/lit8 v8, v8, -0x1

    goto :goto_1

    :cond_4
    new-instance v1, Ljava/lang/RuntimeException;

    iget-object v3, v9, Lcom/google/research/reflection/layers/c;->eq:Lcom/google/research/reflection/a/a;

    iget v3, v3, Lcom/google/research/reflection/a/a;->dn:I

    const/16 v5, 0x6e

    new-instance v6, Ljava/lang/StringBuilder;

    invoke-direct {v6, v5}, Ljava/lang/StringBuilder;-><init>(I)V

    const-string v5, "backward: dense input vector has a different frame index from the target frame index: "

    invoke-virtual {v6, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v6, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v3, "!="

    invoke-virtual {v6, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v6, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v6}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-direct {v1, v3}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1

    :cond_5
    sub-int/2addr v5, v6

    move v4, v5

    :goto_3
    if-ltz v4, :cond_7

    sub-int v6, v5, v4

    if-gtz v6, :cond_7

    iget-object v6, v1, Lcom/google/research/reflection/layers/f;->eZ:Lcom/google/research/reflection/a/a;

    invoke-virtual {v6, v4}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Lcom/google/research/reflection/layers/e;

    iget-object v8, v1, Lcom/google/research/reflection/layers/f;->fa:Lcom/google/research/reflection/a/a;

    invoke-virtual {v8, v4}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v8

    check-cast v8, Lcom/google/research/reflection/layers/e;

    move-object/from16 v18, v6

    move v6, v7

    :goto_4
    if-ltz v6, :cond_6

    iget-object v9, v1, Lcom/google/research/reflection/layers/f;->fb:Ljava/util/List;

    invoke-interface {v9, v6}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v9

    check-cast v9, Lcom/google/research/reflection/layers/c;

    iget-object v10, v9, Lcom/google/research/reflection/layers/c;->eu:Lcom/google/research/reflection/layers/e;

    move-object v15, v9

    move-object/from16 v16, v1

    move/from16 v17, v4

    move-object/from16 v19, v10

    move-object/from16 v20, v8

    invoke-virtual/range {v15 .. v20}, Lcom/google/research/reflection/layers/c;->a(Lcom/google/research/reflection/layers/f;ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V

    iget-object v9, v9, Lcom/google/research/reflection/layers/c;->et:Lcom/google/research/reflection/layers/e;

    add-int/lit8 v6, v6, -0x1

    move-object/from16 v18, v9

    goto :goto_4

    :cond_6
    add-int/lit8 v4, v4, -0x1

    goto :goto_3

    .line 175
    :cond_7
    :goto_5
    iget-object v1, v0, Lcom/google/research/reflection/predictor/d;->fA:Lcom/google/research/reflection/layers/f;

    invoke-virtual {v1}, Lcom/google/research/reflection/layers/f;->update()V

    .line 176
    iget-object v1, v3, Lcom/google/research/reflection/layers/e;->eR:[D

    iput-object v1, v2, Lcom/google/research/reflection/predictor/k;->fO:[D

    goto :goto_6

    .line 174
    :cond_8
    new-instance v1, Ljava/lang/RuntimeException;

    const-string v3, "Lacks outputlayer"

    invoke-direct {v1, v3}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v1
    :try_end_0
    .catch Lcom/google/research/reflection/layers/InvalidValueException; {:try_start_0 .. :try_end_0} :catch_0

    :catch_0
    :goto_6
    return-object v2
.end method

.method public final k(Ljava/lang/String;)V
    .locals 4

    .line 225
    iget-object v0, p0, Lcom/google/research/reflection/predictor/d;->fB:Lcom/google/research/reflection/b/f;

    const/4 v1, 0x2

    new-array v1, v1, [Ljava/lang/String;

    const-string v2, ".*"

    const/4 v3, 0x0

    aput-object v2, v1, v3

    const/4 v2, 0x1

    aput-object p1, v1, v2

    invoke-static {v1}, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;

    move-result-object p1

    invoke-virtual {v0, p1}, Lcom/google/research/reflection/b/f;->g(Ljava/util/List;)V

    return-void
.end method

.method public k(Lcom/google/research/reflection/signal/ReflectionEvent;)Z
    .locals 2

    .line 160
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->C()Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    move-result-object v0

    sget-object v1, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fW:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    if-eq v0, v1, :cond_1

    .line 161
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
