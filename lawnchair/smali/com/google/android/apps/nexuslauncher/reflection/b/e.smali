.class public Lcom/google/android/apps/nexuslauncher/reflection/b/e;
.super Ljava/lang/Object;
.source "SourceFile"


# instance fields
.field private final bo:I


# direct methods
.method public constructor <init>(I)V
    .locals 0

    .line 16
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 17
    iput p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/e;->bo:I

    return-void
.end method


# virtual methods
.method public final a(Ljava/util/List;Ljava/util/List;)V
    .locals 4
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/List<",
            "Lcom/google/research/reflection/predictor/k$a;",
            ">;",
            "Ljava/util/List<",
            "Lcom/google/research/reflection/predictor/k$a;",
            ">;)V"
        }
    .end annotation

    .line 27
    invoke-interface/range {p1 .. p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p1

    const/4 v0, 0x0

    .line 29
    :cond_0
    :goto_0
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_1

    .line 30
    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/predictor/k$a;

    .line 31
    iget-object v2, v1, Lcom/google/research/reflection/predictor/k$a;->id:Ljava/lang/String;

    const-string v3, "_"

    invoke-virtual {v2, v3}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v2

    if-eqz v2, :cond_0

    add-int/lit8 v0, v0, 0x1

    .line 33
    iget v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/e;->bo:I

    if-le v0, v2, :cond_0

    .line 34
    invoke-interface {p1}, Ljava/util/Iterator;->remove()V

    if-eqz p2, :cond_0

    .line 36
    invoke-interface {p2, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    goto :goto_0

    :cond_1
    return-void
.end method
