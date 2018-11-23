.class public abstract Lcom/google/research/reflection/b/f;
.super Ljava/lang/Object;
.source "SourceFile"


# instance fields
.field public dA:Lcom/google/research/reflection/b/l;

.field protected dB:Ljava/util/Map;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Ljava/lang/Boolean;",
            ">;"
        }
    .end annotation
.end field


# direct methods
.method public constructor <init>()V
    .locals 3

    .line 32
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 30
    new-instance v0, Ljava/util/HashMap;

    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V

    iput-object v0, p0, Lcom/google/research/reflection/b/f;->dB:Ljava/util/Map;

    .line 33
    iget-object v0, p0, Lcom/google/research/reflection/b/f;->dB:Ljava/util/Map;

    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/b/f;->getFeatureName()Ljava/lang/String;

    move-result-object v1

    const/4 v2, 0x1

    invoke-static {v2}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object v2

    invoke-interface {v0, v1, v2}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    return-void
.end method

.method public static b(Lcom/google/research/reflection/b/f;)Ljava/lang/String;
    .locals 1

    .line 69
    instance-of v0, p0, Lcom/google/research/reflection/b/a;

    if-eqz v0, :cond_0

    instance-of v0, p0, Lcom/google/research/reflection/b/b;

    if-nez v0, :cond_0

    const-string p0, "app_launch_extractor"

    return-object p0

    .line 71
    :cond_0
    instance-of v0, p0, Lcom/google/research/reflection/b/b;

    if-eqz v0, :cond_1

    const-string p0, "app_usage_extractor"

    return-object p0

    .line 73
    :cond_1
    instance-of v0, p0, Lcom/google/research/reflection/b/d;

    if-eqz v0, :cond_2

    const-string p0, "dl_usage_extractor"

    return-object p0

    .line 75
    :cond_2
    instance-of v0, p0, Lcom/google/research/reflection/b/c;

    if-eqz v0, :cond_3

    const-string p0, "day_extractor"

    return-object p0

    .line 77
    :cond_3
    instance-of v0, p0, Lcom/google/research/reflection/b/e;

    if-eqz v0, :cond_4

    const-string p0, "feature_aggregator"

    return-object p0

    .line 79
    :cond_4
    instance-of v0, p0, Lcom/google/research/reflection/b/g;

    if-eqz v0, :cond_5

    const-string p0, "headset_extractor"

    return-object p0

    .line 81
    :cond_5
    instance-of v0, p0, Lcom/google/research/reflection/b/h;

    if-eqz v0, :cond_6

    const-string p0, "hour_extractor"

    return-object p0

    .line 83
    :cond_6
    instance-of v0, p0, Lcom/google/research/reflection/b/j;

    if-eqz v0, :cond_7

    const-string p0, "place_extractor"

    return-object p0

    .line 85
    :cond_7
    instance-of v0, p0, Lcom/google/research/reflection/b/k;

    if-eqz v0, :cond_8

    const-string p0, "public_place_extractor"

    return-object p0

    .line 87
    :cond_8
    instance-of p0, p0, Lcom/google/research/reflection/b/i;

    if-eqz p0, :cond_9

    const-string p0, "lat_lng_extractor"

    return-object p0

    :cond_9
    const/4 p0, 0x0

    return-object p0
.end method

.method public static j(Ljava/lang/String;)Lcom/google/research/reflection/b/f;
    .locals 1

    .line 95
    invoke-virtual/range {p0 .. p0}, Ljava/lang/String;->hashCode()I

    move-result v0

    sparse-switch v0, :sswitch_data_0

    goto/16 :goto_0

    :sswitch_0
    const-string v0, "lat_lng_extractor"

    invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/16 p0, 0x9

    goto :goto_1

    :sswitch_1
    const-string v0, "feature_aggregator"

    invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/4 p0, 0x4

    goto :goto_1

    :sswitch_2
    const-string v0, "place_extractor"

    invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/4 p0, 0x7

    goto :goto_1

    :sswitch_3
    const-string v0, "headset_extractor"

    invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/4 p0, 0x6

    goto :goto_1

    :sswitch_4
    const-string v0, "app_usage_extractor"

    invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/4 p0, 0x1

    goto :goto_1

    :sswitch_5
    const-string v0, "day_extractor"

    invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/4 p0, 0x3

    goto :goto_1

    :sswitch_6
    const-string v0, "dl_usage_extractor"

    invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/4 p0, 0x2

    goto :goto_1

    :sswitch_7
    const-string v0, "public_place_extractor"

    invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/16 p0, 0x8

    goto :goto_1

    :sswitch_8
    const-string v0, "app_launch_extractor"

    invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/4 p0, 0x0

    goto :goto_1

    :sswitch_9
    const-string v0, "hour_extractor"

    invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    const/4 p0, 0x5

    goto :goto_1

    :cond_0
    :goto_0
    const/4 p0, -0x1

    :goto_1
    packed-switch p0, :pswitch_data_0

    const/4 p0, 0x0

    return-object p0

    .line 115
    :pswitch_0
    new-instance p0, Lcom/google/research/reflection/b/i;

    invoke-direct {p0}, Lcom/google/research/reflection/b/i;-><init>()V

    return-object p0

    .line 113
    :pswitch_1
    new-instance p0, Lcom/google/research/reflection/b/k;

    invoke-direct {p0}, Lcom/google/research/reflection/b/k;-><init>()V

    return-object p0

    .line 111
    :pswitch_2
    new-instance p0, Lcom/google/research/reflection/b/j;

    invoke-direct {p0}, Lcom/google/research/reflection/b/j;-><init>()V

    return-object p0

    .line 109
    :pswitch_3
    new-instance p0, Lcom/google/research/reflection/b/g;

    invoke-direct {p0}, Lcom/google/research/reflection/b/g;-><init>()V

    return-object p0

    .line 107
    :pswitch_4
    new-instance p0, Lcom/google/research/reflection/b/h;

    invoke-direct {p0}, Lcom/google/research/reflection/b/h;-><init>()V

    return-object p0

    .line 105
    :pswitch_5
    new-instance p0, Lcom/google/research/reflection/b/e;

    invoke-direct {p0}, Lcom/google/research/reflection/b/e;-><init>()V

    return-object p0

    .line 103
    :pswitch_6
    new-instance p0, Lcom/google/research/reflection/b/c;

    invoke-direct {p0}, Lcom/google/research/reflection/b/c;-><init>()V

    return-object p0

    .line 101
    :pswitch_7
    new-instance p0, Lcom/google/research/reflection/b/d;

    invoke-direct {p0}, Lcom/google/research/reflection/b/d;-><init>()V

    return-object p0

    .line 99
    :pswitch_8
    new-instance p0, Lcom/google/research/reflection/b/b;

    invoke-direct {p0}, Lcom/google/research/reflection/b/b;-><init>()V

    return-object p0

    .line 97
    :pswitch_9
    new-instance p0, Lcom/google/research/reflection/b/a;

    invoke-direct {p0}, Lcom/google/research/reflection/b/a;-><init>()V

    return-object p0

    nop

    :sswitch_data_0
    .sparse-switch
        -0x68df4e77 -> :sswitch_9
        -0x5725890a -> :sswitch_8
        -0x23c7a4ea -> :sswitch_7
        -0x23c73cb1 -> :sswitch_6
        0xdc57941 -> :sswitch_5
        0x4345dee8 -> :sswitch_4
        0x4816b0a7 -> :sswitch_3
        0x495144ec -> :sswitch_2
        0x532f40b2 -> :sswitch_1
        0x767732ea -> :sswitch_0
    .end sparse-switch

    :pswitch_data_0
    .packed-switch 0x0
        :pswitch_9
        :pswitch_8
        :pswitch_7
        :pswitch_6
        :pswitch_5
        :pswitch_4
        :pswitch_3
        :pswitch_2
        :pswitch_1
        :pswitch_0
    .end packed-switch
.end method


# virtual methods
.method public abstract T()I
.end method

.method public abstract U()Lcom/google/research/reflection/b/f;
.end method

.method public final Y()Ljava/util/Map;
    .locals 1
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Ljava/lang/Boolean;",
            ">;"
        }
    .end annotation

    .line 140
    iget-object v0, p0, Lcom/google/research/reflection/b/f;->dB:Ljava/util/Map;

    return-object v0
.end method

.method protected abstract a(Lcom/google/research/reflection/a/a;Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/layers/e;
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
.end method

.method public a(Ljava/io/DataInputStream;)V
    .locals 0
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    return-void
.end method

.method public a(Ljava/io/DataOutputStream;)V
    .locals 0
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    return-void
.end method

.method public final b(Lcom/google/research/reflection/a/a;Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/layers/e;
    .locals 6
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

    .line 44
    invoke-virtual/range {p0 .. p2}, Lcom/google/research/reflection/b/f;->a(Lcom/google/research/reflection/a/a;Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/layers/e;

    move-result-object p1

    const/4 p2, 0x0

    const/4 v0, 0x0

    .line 46
    :goto_0
    iget v1, p1, Lcom/google/research/reflection/layers/e;->rowCount:I

    if-ge v0, v1, :cond_2

    const/4 v1, 0x0

    :goto_1
    iget v2, p1, Lcom/google/research/reflection/layers/e;->columnCount:I

    if-ge v1, v2, :cond_1

    iget-object v2, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    iget v3, p1, Lcom/google/research/reflection/layers/e;->columnCount:I

    mul-int v3, v3, v0

    add-int/2addr v3, v1

    aget-wide v3, v2, v3

    invoke-static {v3, v4}, Ljava/lang/Math;->abs(D)D

    move-result-wide v2

    const-wide v4, 0x3e112e0be826d695L    # 1.0E-9

    cmpl-double v2, v2, v4

    if-lez v2, :cond_0

    goto :goto_2

    :cond_0
    add-int/lit8 v1, v1, 0x1

    goto :goto_1

    :cond_1
    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    :cond_2
    const/4 p2, 0x1

    :goto_2
    invoke-virtual {p0, p2}, Lcom/google/research/reflection/b/f;->e(Z)V

    return-object p1
.end method

.method public synthetic clone()Ljava/lang/Object;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/CloneNotSupportedException;
        }
    .end annotation

    .line 14
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/b/f;->U()Lcom/google/research/reflection/b/f;

    move-result-object v0

    return-object v0
.end method

.method protected e(Z)V
    .locals 2

    .line 136
    iget-object v0, p0, Lcom/google/research/reflection/b/f;->dB:Ljava/util/Map;

    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/b/f;->getFeatureName()Ljava/lang/String;

    move-result-object v1

    invoke-static/range {p1 .. p1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object p1

    invoke-interface {v0, v1, p1}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    return-void
.end method

.method public g(Ljava/util/List;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/List<",
            "Ljava/lang/String;",
            ">;)V"
        }
    .end annotation

    return-void
.end method

.method public abstract getFeatureName()Ljava/lang/String;
.end method
