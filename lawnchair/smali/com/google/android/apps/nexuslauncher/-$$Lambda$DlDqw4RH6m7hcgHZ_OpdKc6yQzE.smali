.class public final synthetic Lcom/google/android/apps/nexuslauncher/-$$Lambda$DlDqw4RH6m7hcgHZ_OpdKc6yQzE;
.super Ljava/lang/Object;
.source "lambda"

# interfaces
.implements Lcom/google/android/apps/nexuslauncher/a;


# instance fields
.field private final synthetic f$0:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;


# direct methods
.method public synthetic constructor <init>(Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;)V
    .locals 0

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/-$$Lambda$DlDqw4RH6m7hcgHZ_OpdKc6yQzE;->f$0:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;

    return-void
.end method


# virtual methods
.method public final onUpdateUI()V
    .locals 1

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/-$$Lambda$DlDqw4RH6m7hcgHZ_OpdKc6yQzE;->f$0:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;

    invoke-virtual {v0}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->dispatchOnChange()V

    return-void
.end method
