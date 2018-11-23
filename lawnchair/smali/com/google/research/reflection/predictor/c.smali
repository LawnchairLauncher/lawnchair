.class public Lcom/google/research/reflection/predictor/c;
.super Lcom/google/research/reflection/predictor/l;
.source "SourceFile"


# static fields
.field static final ft:J


# instance fields
.field private final fu:Lcom/google/research/reflection/a/c;


# direct methods
.method static constructor <clinit>()V
    .locals 3

    .line 10
    sget-object v0, Ljava/util/concurrent/TimeUnit;->MINUTES:Ljava/util/concurrent/TimeUnit;

    const-wide/16 v1, 0x5a

    invoke-virtual {v0, v1, v2}, Ljava/util/concurrent/TimeUnit;->toMillis(J)J

    move-result-wide v0

    sput-wide v0, Lcom/google/research/reflection/predictor/c;->ft:J

    return-void
.end method

.method public constructor <init>(Lcom/google/research/reflection/a/c;)V
    .locals 0

    .line 21
    invoke-direct/range {p0 .. p0}, Lcom/google/research/reflection/predictor/l;-><init>()V

    .line 22
    iput-object p1, p0, Lcom/google/research/reflection/predictor/c;->fu:Lcom/google/research/reflection/a/c;

    return-void
.end method


# virtual methods
.method public final getName()Ljava/lang/String;
    .locals 1

    const-string v0, "Location_Rule_Predictor"

    return-object v0
.end method

.method public final j(Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/k;
    .locals 5

    .line 27
    new-instance v0, Lcom/google/research/reflection/predictor/k;

    invoke-direct {v0}, Lcom/google/research/reflection/predictor/k;-><init>()V

    .line 28
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    if-eqz v1, :cond_7

    .line 29
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/b;->K()Lcom/google/research/reflection/signal/c;

    move-result-object v1

    if-eqz v1, :cond_7

    .line 30
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/b;->K()Lcom/google/research/reflection/signal/c;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/c;->N()Ljava/lang/String;

    move-result-object v1

    if-nez v1, :cond_0

    goto :goto_2

    .line 33
    :cond_0
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v1

    .line 34
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->E()Lcom/google/research/reflection/signal/b;

    move-result-object v3

    invoke-interface {v3}, Lcom/google/research/reflection/signal/b;->K()Lcom/google/research/reflection/signal/c;

    move-result-object v3

    invoke-interface {v3}, Lcom/google/research/reflection/signal/c;->getTime()J

    move-result-wide v3

    sub-long/2addr v1, v3

    const-wide/16 v3, 0x0

    cmp-long v3, v1, v3

    if-ltz v3, :cond_6

    .line 35
    sget-wide v3, Lcom/google/research/reflection/predictor/c;->ft:J

    cmp-long v1, v1, v3

    if-lez v1, :cond_1

    goto :goto_1

    .line 38
    :cond_1
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->E()Lcom/google/research/reflection/signal/b;

    move-result-object p1

    invoke-interface {p1}, Lcom/google/research/reflection/signal/b;->K()Lcom/google/research/reflection/signal/c;

    move-result-object p1

    invoke-interface {p1}, Lcom/google/research/reflection/signal/c;->N()Ljava/lang/String;

    move-result-object p1

    const/4 v1, 0x0

    const-string v2, "Place.TYPE_AIRPORT"

    .line 40
    invoke-virtual {v2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-eqz v2, :cond_2

    .line 41
    iget-object p1, p0, Lcom/google/research/reflection/predictor/c;->fu:Lcom/google/research/reflection/a/c;

    invoke-interface {p1}, Lcom/google/research/reflection/a/c;->r()Ljava/lang/String;

    move-result-object v1

    goto :goto_0

    :cond_2
    const-string v2, "Place.TYPE_RESTAURANT"

    .line 42
    invoke-virtual {v2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_3

    const-string v2, "Place.TYPE_CAFE"

    .line 43
    invoke-virtual {v2, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_4

    .line 44
    :cond_3
    iget-object p1, p0, Lcom/google/research/reflection/predictor/c;->fu:Lcom/google/research/reflection/a/c;

    invoke-interface {p1}, Lcom/google/research/reflection/a/c;->s()Ljava/lang/String;

    move-result-object v1

    :cond_4
    :goto_0
    if-eqz v1, :cond_5

    .line 48
    iget-object p1, v0, Lcom/google/research/reflection/predictor/k;->fR:Ljava/util/ArrayList;

    new-instance v2, Lcom/google/research/reflection/predictor/k$a;

    const/high16 v3, 0x3f800000    # 1.0f

    const-string v4, "Location_Rule_Predictor"

    invoke-direct {v2, v1, v3, v4}, Lcom/google/research/reflection/predictor/k$a;-><init>(Ljava/lang/String;FLjava/lang/String;)V

    invoke-virtual {p1, v2}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    :cond_5
    return-object v0

    :cond_6
    :goto_1
    return-object v0

    :cond_7
    :goto_2
    return-object v0
.end method
