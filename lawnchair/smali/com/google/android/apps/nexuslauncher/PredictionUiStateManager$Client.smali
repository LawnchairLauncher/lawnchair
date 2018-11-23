.class public final enum Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;
.super Ljava/lang/Enum;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x4019
    name = "Client"
.end annotation

.annotation system Ldalvik/annotation/Signature;
    value = {
        "Ljava/lang/Enum<",
        "Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;",
        ">;"
    }
.end annotation


# static fields
.field private static final synthetic $VALUES:[Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

.field public static final enum HOME:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

.field public static final enum OVERVIEW:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;


# instance fields
.field public final id:Ljava/lang/String;

.field private final mKeyConfg:Lcom/google/android/apps/nexuslauncher/reflection/a/d;


# direct methods
.method static constructor <clinit>()V
    .locals 5

    .line 58
    new-instance v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    const-string v1, "HOME"

    const-string v2, "GEL"

    const/4 v3, 0x0

    invoke-direct {v0, v1, v3, v2}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;-><init>(Ljava/lang/String;ILjava/lang/String;)V

    sput-object v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;->HOME:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    .line 59
    new-instance v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    const-string v1, "OVERVIEW"

    const-string v2, "OVERVIEW_GEL"

    const/4 v4, 0x1

    invoke-direct {v0, v1, v4, v2}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;-><init>(Ljava/lang/String;ILjava/lang/String;)V

    sput-object v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;->OVERVIEW:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    const/4 v0, 0x2

    .line 57
    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    sget-object v1, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;->HOME:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    aput-object v1, v0, v3

    sget-object v1, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;->OVERVIEW:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    aput-object v1, v0, v4

    sput-object v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;->$VALUES:[Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    return-void
.end method

.method private constructor <init>(Ljava/lang/String;ILjava/lang/String;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/String;",
            ")V"
        }
    .end annotation

    .line 64
    invoke-direct/range {p0 .. p2}, Ljava/lang/Enum;-><init>(Ljava/lang/String;I)V

    .line 65
    iput-object p3, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;->id:Ljava/lang/String;

    .line 66
    invoke-static/range {p3 .. p3}, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->d(Ljava/lang/String;)Lcom/google/android/apps/nexuslauncher/reflection/a/d;

    move-result-object p1

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;->mKeyConfg:Lcom/google/android/apps/nexuslauncher/reflection/a/d;

    return-void
.end method

.method static synthetic access$000(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;)Lcom/google/android/apps/nexuslauncher/reflection/a/d;
    .locals 0

    .line 57
    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;->mKeyConfg:Lcom/google/android/apps/nexuslauncher/reflection/a/d;

    return-object p0
.end method

.method public static valueOf(Ljava/lang/String;)Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;
    .locals 1

    .line 57
    const-class v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    invoke-static {v0, p0}, Ljava/lang/Enum;->valueOf(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;

    move-result-object p0

    check-cast p0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    return-object p0
.end method

.method public static values()[Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;
    .locals 1

    .line 57
    sget-object v0, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;->$VALUES:[Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    invoke-virtual {v0}, [Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;->clone()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, [Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$Client;

    return-object v0
.end method
