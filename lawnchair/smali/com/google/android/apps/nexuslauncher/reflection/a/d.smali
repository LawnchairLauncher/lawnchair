.class public Lcom/google/android/apps/nexuslauncher/reflection/a/d;
.super Ljava/lang/Object;
.source "SourceFile"


# static fields
.field private static final aX:Lcom/google/android/apps/nexuslauncher/reflection/a/d;

.field private static final aY:Lcom/google/android/apps/nexuslauncher/reflection/a/d;


# instance fields
.field public final aZ:Ljava/lang/String;

.field public final ba:Ljava/lang/String;

.field public final bb:Ljava/lang/String;

.field public final bc:Ljava/lang/String;


# direct methods
.method static constructor <clinit>()V
    .locals 2

    .line 17
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/a/d;

    const-string v1, "OVERVIEW_GEL"

    invoke-direct {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/a/d;-><init>(Ljava/lang/String;)V

    sput-object v0, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->aX:Lcom/google/android/apps/nexuslauncher/reflection/a/d;

    .line 20
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/a/d;

    const-string v1, "GEL"

    invoke-direct {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/a/d;-><init>(Ljava/lang/String;)V

    sput-object v0, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->aY:Lcom/google/android/apps/nexuslauncher/reflection/a/d;

    return-void
.end method

.method private constructor <init>(Ljava/lang/String;)V
    .locals 2

    .line 28
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 29
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, "_reflection_last_predictions"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->aZ:Ljava/lang/String;

    .line 30
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, "_reflection_last_predictions_timestamp"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->ba:Ljava/lang/String;

    .line 31
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, "_prediction_order"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->bb:Ljava/lang/String;

    .line 32
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string p1, "_prediction_order_by_rank"

    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->bc:Ljava/lang/String;

    return-void
.end method

.method public static d(Ljava/lang/String;)Lcom/google/android/apps/nexuslauncher/reflection/a/d;
    .locals 1

    const-string v0, "OVERVIEW_GEL"

    .line 37
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_0

    .line 38
    sget-object p0, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->aX:Lcom/google/android/apps/nexuslauncher/reflection/a/d;

    return-object p0

    .line 40
    :cond_0
    sget-object p0, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->aY:Lcom/google/android/apps/nexuslauncher/reflection/a/d;

    return-object p0
.end method
