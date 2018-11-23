.class public Lcom/google/research/reflection/utils/a;
.super Ljava/lang/Object;
.source "SourceFile"


# static fields
.field public static final gs:Ljava/util/LinkedHashMap;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/LinkedHashMap<",
            "Ljava/lang/String;",
            "Ljava/lang/Integer;",
            ">;"
        }
    .end annotation
.end field


# direct methods
.method static constructor <clinit>()V
    .locals 1

    .line 17
    new-instance v0, Lcom/google/research/reflection/utils/PredictorFactory$1;

    invoke-direct {v0}, Lcom/google/research/reflection/utils/PredictorFactory$1;-><init>()V

    sput-object v0, Lcom/google/research/reflection/utils/a;->gs:Ljava/util/LinkedHashMap;

    return-void
.end method

.method public static a(Lcom/google/research/reflection/predictor/b;Lcom/google/research/reflection/a/c;)Lcom/google/research/reflection/predictor/i;
    .locals 5

    .line 35
    new-instance v0, Lcom/google/research/reflection/predictor/i;

    invoke-direct {v0}, Lcom/google/research/reflection/predictor/i;-><init>()V

    .line 36
    sget-object v1, Lcom/google/research/reflection/utils/a;->gs:Ljava/util/LinkedHashMap;

    invoke-virtual {v1}, Ljava/util/LinkedHashMap;->keySet()Ljava/util/Set;

    move-result-object v1

    invoke-interface {v1}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object v1

    :goto_0
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_0

    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/lang/String;

    .line 37
    invoke-static {v2, p1}, Lcom/google/research/reflection/predictor/g;->a(Ljava/lang/String;Lcom/google/research/reflection/a/c;)Lcom/google/research/reflection/predictor/g;

    move-result-object v3

    .line 38
    sget-object v4, Lcom/google/research/reflection/utils/a;->gs:Ljava/util/LinkedHashMap;

    invoke-virtual {v4, v2}, Ljava/util/LinkedHashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/lang/Integer;

    invoke-virtual {v2}, Ljava/lang/Integer;->intValue()I

    move-result v2

    invoke-virtual {v0, v3, v2}, Lcom/google/research/reflection/predictor/i;->a(Lcom/google/research/reflection/predictor/g;I)V

    goto :goto_0

    .line 41
    :cond_0
    invoke-virtual {v0, p0}, Lcom/google/research/reflection/predictor/i;->c(Lcom/google/research/reflection/predictor/b;)V

    return-object v0
.end method
