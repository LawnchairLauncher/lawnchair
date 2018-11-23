.class public Lcom/google/research/reflection/predictor/k$a;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Ljava/lang/Comparable;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/research/reflection/predictor/k;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x9
    name = "a"
.end annotation

.annotation system Ldalvik/annotation/Signature;
    value = {
        "Ljava/lang/Object;",
        "Ljava/lang/Comparable<",
        "Lcom/google/research/reflection/predictor/k$a;",
        ">;"
    }
.end annotation


# instance fields
.field public ca:F

.field public final fS:Ljava/util/Set;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Set<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation
.end field

.field fT:I

.field public id:Ljava/lang/String;


# direct methods
.method public constructor <init>(Ljava/lang/String;FLjava/lang/String;)V
    .locals 1

    .line 102
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 103
    iput-object p1, p0, Lcom/google/research/reflection/predictor/k$a;->id:Ljava/lang/String;

    .line 104
    iput p2, p0, Lcom/google/research/reflection/predictor/k$a;->ca:F

    .line 105
    new-instance p1, Ljava/util/HashSet;

    const/4 p2, 0x1

    new-array p2, p2, [Ljava/lang/String;

    const/4 v0, 0x0

    aput-object p3, p2, v0

    invoke-static {p2}, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;

    move-result-object p2

    invoke-direct {p1, p2}, Ljava/util/HashSet;-><init>(Ljava/util/Collection;)V

    iput-object p1, p0, Lcom/google/research/reflection/predictor/k$a;->fS:Ljava/util/Set;

    .line 106
    iput v0, p0, Lcom/google/research/reflection/predictor/k$a;->fT:I

    return-void
.end method

.method public constructor <init>(Ljava/lang/String;FLjava/util/Set;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/String;",
            "F",
            "Ljava/util/Set<",
            "Ljava/lang/String;",
            ">;)V"
        }
    .end annotation

    .line 109
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 110
    iput-object p1, p0, Lcom/google/research/reflection/predictor/k$a;->id:Ljava/lang/String;

    .line 111
    iput p2, p0, Lcom/google/research/reflection/predictor/k$a;->ca:F

    .line 112
    new-instance p1, Ljava/util/HashSet;

    invoke-direct {p1, p3}, Ljava/util/HashSet;-><init>(Ljava/util/Collection;)V

    iput-object p1, p0, Lcom/google/research/reflection/predictor/k$a;->fS:Ljava/util/Set;

    const/4 p1, 0x0

    .line 113
    iput p1, p0, Lcom/google/research/reflection/predictor/k$a;->fT:I

    return-void
.end method


# virtual methods
.method public synthetic compareTo(Ljava/lang/Object;)I
    .locals 1

    .line 75
    check-cast p1, Lcom/google/research/reflection/predictor/k$a;

    iget v0, p0, Lcom/google/research/reflection/predictor/k$a;->ca:F

    iget p1, p1, Lcom/google/research/reflection/predictor/k$a;->ca:F

    invoke-static {v0, p1}, Ljava/lang/Float;->compare(FF)I

    move-result p1

    return p1
.end method
