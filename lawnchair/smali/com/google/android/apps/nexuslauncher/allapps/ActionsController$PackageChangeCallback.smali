.class Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/android/launcher3/compat/LauncherAppsCompat$OnAppsChangedCallbackCompat;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x2
    name = "PackageChangeCallback"
.end annotation

.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback$PackageInfo;
    }
.end annotation


# instance fields
.field final synthetic y:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;


# direct methods
.method private constructor <init>(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)V
    .locals 0

    .line 482
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;->y:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method synthetic constructor <init>(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;B)V
    .locals 0

    .line 482
    invoke-direct/range {p0 .. p1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;-><init>(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)V

    return-void
.end method


# virtual methods
.method public onPackageAdded(Ljava/lang/String;Landroid/os/UserHandle;)V
    .locals 0

    .line 500
    invoke-virtual/range {p0 .. p2}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;->onPackageChanged(Ljava/lang/String;Landroid/os/UserHandle;)V

    return-void
.end method

.method public onPackageChanged(Ljava/lang/String;Landroid/os/UserHandle;)V
    .locals 2

    .line 505
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;->y:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;

    invoke-static {v0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->c(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)Landroid/os/Handler;

    move-result-object v0

    new-instance v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback$PackageInfo;

    invoke-direct {v1, p0, p1, p2}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback$PackageInfo;-><init>(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;Ljava/lang/String;Landroid/os/UserHandle;)V

    const/4 p1, 0x0

    const/4 p2, 0x5

    invoke-static {v0, p2, p1, p1, v1}, Landroid/os/Message;->obtain(Landroid/os/Handler;IIILjava/lang/Object;)Landroid/os/Message;

    move-result-object p1

    .line 506
    invoke-virtual {p1}, Landroid/os/Message;->sendToTarget()V

    return-void
.end method

.method public onPackageRemoved(Ljava/lang/String;Landroid/os/UserHandle;)V
    .locals 0

    .line 495
    invoke-virtual/range {p0 .. p2}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;->onPackageChanged(Ljava/lang/String;Landroid/os/UserHandle;)V

    return-void
.end method

.method public onPackagesAvailable([Ljava/lang/String;Landroid/os/UserHandle;Z)V
    .locals 1

    const/4 p3, 0x0

    .line 511
    :goto_0
    array-length v0, p1

    if-ge p3, v0, :cond_0

    .line 512
    aget-object v0, p1, p3

    invoke-virtual {p0, v0, p2}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;->onPackageChanged(Ljava/lang/String;Landroid/os/UserHandle;)V

    add-int/lit8 p3, p3, 0x1

    goto :goto_0

    :cond_0
    return-void
.end method

.method public onPackagesSuspended([Ljava/lang/String;Landroid/os/UserHandle;)V
    .locals 2

    const/4 v0, 0x0

    .line 526
    :goto_0
    array-length v1, p1

    if-ge v0, v1, :cond_0

    .line 527
    aget-object v1, p1, v0

    invoke-virtual {p0, v1, p2}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;->onPackageChanged(Ljava/lang/String;Landroid/os/UserHandle;)V

    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    :cond_0
    return-void
.end method

.method public onPackagesUnavailable([Ljava/lang/String;Landroid/os/UserHandle;Z)V
    .locals 1

    const/4 p3, 0x0

    .line 519
    :goto_0
    array-length v0, p1

    if-ge p3, v0, :cond_0

    .line 520
    aget-object v0, p1, p3

    invoke-virtual {p0, v0, p2}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;->onPackageChanged(Ljava/lang/String;Landroid/os/UserHandle;)V

    add-int/lit8 p3, p3, 0x1

    goto :goto_0

    :cond_0
    return-void
.end method

.method public onPackagesUnsuspended([Ljava/lang/String;Landroid/os/UserHandle;)V
    .locals 2

    const/4 v0, 0x0

    .line 533
    :goto_0
    array-length v1, p1

    if-ge v0, v1, :cond_0

    .line 534
    aget-object v1, p1, v0

    invoke-virtual {p0, v1, p2}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;->onPackageChanged(Ljava/lang/String;Landroid/os/UserHandle;)V

    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    :cond_0
    return-void
.end method

.method public onShortcutsChanged(Ljava/lang/String;Ljava/util/List;Landroid/os/UserHandle;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/String;",
            "Ljava/util/List<",
            "Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;",
            ">;",
            "Landroid/os/UserHandle;",
            ")V"
        }
    .end annotation

    return-void
.end method
