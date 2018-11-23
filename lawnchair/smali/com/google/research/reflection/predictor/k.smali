.class public Lcom/google/research/reflection/predictor/k;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/research/reflection/predictor/k$a;
    }
.end annotation


# instance fields
.field public fO:[D

.field fP:[F

.field private fQ:F

.field public fR:Ljava/util/ArrayList;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/ArrayList<",
            "Lcom/google/research/reflection/predictor/k$a;",
            ">;"
        }
    .end annotation
.end field


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 13
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    const/4 v0, 0x0

    .line 14
    iput-object v0, p0, Lcom/google/research/reflection/predictor/k;->fO:[D

    .line 15
    iput-object v0, p0, Lcom/google/research/reflection/predictor/k;->fP:[F

    const/high16 v0, -0x40800000    # -1.0f

    .line 16
    iput v0, p0, Lcom/google/research/reflection/predictor/k;->fQ:F

    .line 17
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/google/research/reflection/predictor/k;->fR:Ljava/util/ArrayList;

    return-void
.end method
