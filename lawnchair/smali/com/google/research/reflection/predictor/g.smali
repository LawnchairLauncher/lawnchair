.class public abstract Lcom/google/research/reflection/predictor/g;
.super Ljava/lang/Object;
.source "SourceFile"


# instance fields
.field fG:Lcom/google/research/reflection/predictor/b;


# direct methods
.method public constructor <init>()V
    .locals 0

    .line 12
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static a(Ljava/lang/String;Lcom/google/research/reflection/a/c;)Lcom/google/research/reflection/predictor/g;
    .locals 1

    const-string v0, "neural_predictor"

    .line 18
    invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_0

    .line 19
    new-instance p0, Lcom/google/research/reflection/predictor/d;

    invoke-direct {p0}, Lcom/google/research/reflection/predictor/d;-><init>()V

    return-object p0

    :cond_0
    const-string v0, "recency_event_predictor"

    .line 20
    invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_1

    .line 21
    new-instance p0, Lcom/google/research/reflection/predictor/h;

    invoke-direct {p0}, Lcom/google/research/reflection/predictor/h;-><init>()V

    return-object p0

    :cond_1
    const-string v0, "shortcut_neural_predictor"

    .line 22
    invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_2

    .line 23
    new-instance p0, Lcom/google/research/reflection/predictor/m;

    invoke-direct {p0}, Lcom/google/research/reflection/predictor/m;-><init>()V

    return-object p0

    :cond_2
    const-string v0, "Rule_Based_Predictor"

    .line 24
    invoke-virtual {p0, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_3

    .line 25
    new-instance p0, Lcom/google/research/reflection/predictor/j;

    invoke-direct {p0, p1}, Lcom/google/research/reflection/predictor/j;-><init>(Lcom/google/research/reflection/a/c;)V

    return-object p0

    :cond_3
    const/4 p0, 0x0

    return-object p0
.end method


# virtual methods
.method public Y()Ljava/util/Map;
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

    .line 31
    invoke-static {}, Ljava/util/Collections;->emptyMap()Ljava/util/Map;

    move-result-object v0

    return-object v0
.end method

.method public abstract a(Ljava/io/DataInputStream;Lcom/google/research/reflection/signal/ReflectionEvent;)V
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation
.end method

.method public abstract a(Ljava/lang/Integer;Ljava/lang/Integer;Ljava/lang/String;)V
.end method

.method public a(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            ">;)V"
        }
    .end annotation

    return-void
.end method

.method public abstract b(Ljava/io/DataOutputStream;)V
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation
.end method

.method public c(Lcom/google/research/reflection/predictor/b;)V
    .locals 0

    .line 68
    iput-object p1, p0, Lcom/google/research/reflection/predictor/g;->fG:Lcom/google/research/reflection/predictor/b;

    return-void
.end method

.method public abstract getName()Ljava/lang/String;
.end method

.method public abstract h(Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/k;
.end method

.method public abstract j(Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/k;
.end method

.method public k(Lcom/google/research/reflection/signal/ReflectionEvent;)Z
    .locals 0

    const/4 p1, 0x1

    return p1
.end method
