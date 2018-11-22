.class Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$1;
.super Landroid/database/ContentObserver;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic y:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;


# direct methods
.method constructor <init>(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;Landroid/os/Handler;)V
    .locals 0

    .line 132
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$1;->y:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;

    invoke-direct {p0, p2}, Landroid/database/ContentObserver;-><init>(Landroid/os/Handler;)V

    return-void
.end method


# virtual methods
.method public onChange(Z)V
    .locals 0

    .line 138
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$1;->y:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;

    invoke-static {p1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)V

    return-void
.end method
