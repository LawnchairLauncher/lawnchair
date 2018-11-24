.class public Lcom/google/android/apps/nexuslauncher/reflection/i;
.super Ljava/lang/Object;
.source "SourceFile"


# static fields
.field static TAG:Ljava/lang/String; = "Reflection.SnsrFactory"

.field static aH:Z = false

.field public static aI:Lcom/google/android/apps/nexuslauncher/reflection/i;


# direct methods
.method static constructor <clinit>()V
    .locals 1

    .line 31
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/i;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/i;-><init>()V

    sput-object v0, Lcom/google/android/apps/nexuslauncher/reflection/i;->aI:Lcom/google/android/apps/nexuslauncher/reflection/i;

    return-void
.end method

.method public constructor <init>()V
    .locals 0

    .line 27
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method static a(Lcom/google/research/reflection/c/a;Ljava/util/List;)V
    .locals 1
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lcom/google/research/reflection/c/a;",
            "Ljava/util/List<",
            "Lcom/google/research/reflection/c/c;",
            ">;)V"
        }
    .end annotation

    .line 91
    invoke-interface/range {p1 .. p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :goto_0
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_0

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/google/research/reflection/c/c;

    .line 92
    invoke-interface {p0, v0}, Lcom/google/research/reflection/c/a;->a(Lcom/google/research/reflection/c/c;)Lcom/google/research/reflection/c/a;

    goto :goto_0

    :cond_0
    return-void
.end method
