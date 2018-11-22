.class Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback$PackageInfo;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x2
    name = "PackageInfo"
.end annotation


# instance fields
.field final synthetic H:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;

.field packageName:Ljava/lang/String;

.field user:Landroid/os/UserHandle;


# direct methods
.method constructor <init>(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;Ljava/lang/String;Landroid/os/UserHandle;)V
    .locals 0

    .line 487
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback$PackageInfo;->H:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 488
    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback$PackageInfo;->packageName:Ljava/lang/String;

    .line 489
    iput-object p3, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback$PackageInfo;->user:Landroid/os/UserHandle;

    return-void
.end method
