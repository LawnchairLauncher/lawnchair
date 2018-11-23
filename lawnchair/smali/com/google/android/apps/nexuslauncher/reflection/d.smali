.class public Lcom/google/android/apps/nexuslauncher/reflection/d;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/android/launcher3/compat/LauncherAppsCompat$OnAppsChangedCallbackCompat;
.implements Lcom/google/android/apps/nexuslauncher/reflection/h$a;


# instance fields
.field private final ac:Lcom/android/launcher3/compat/UserManagerCompat;

.field private final ad:Lcom/google/android/apps/nexuslauncher/reflection/a/b;

.field private final ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

.field private final mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

.field private final v:Lcom/android/launcher3/compat/LauncherAppsCompat;


# direct methods
.method public constructor <init>(Landroid/content/Context;Lcom/google/android/apps/nexuslauncher/reflection/h;Lcom/google/android/apps/nexuslauncher/reflection/b/b;Lcom/google/android/apps/nexuslauncher/reflection/a/b;)V
    .locals 1

    .line 34
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 35
    invoke-static/range {p1 .. p1}, Lcom/android/launcher3/compat/UserManagerCompat;->getInstance(Landroid/content/Context;)Lcom/android/launcher3/compat/UserManagerCompat;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ac:Lcom/android/launcher3/compat/UserManagerCompat;

    .line 36
    invoke-static/range {p1 .. p1}, Lcom/android/launcher3/compat/LauncherAppsCompat;->getInstance(Landroid/content/Context;)Lcom/android/launcher3/compat/LauncherAppsCompat;

    move-result-object p1

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->v:Lcom/android/launcher3/compat/LauncherAppsCompat;

    .line 37
    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    .line 39
    iput-object p3, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    .line 40
    iput-object p4, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ad:Lcom/google/android/apps/nexuslauncher/reflection/a/b;

    return-void
.end method

.method private a(Ljava/util/List;)V
    .locals 4
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/List<",
            "Landroid/content/pm/LauncherActivityInfo;",
            ">;)V"
        }
    .end annotation

    .line 67
    invoke-interface/range {p1 .. p1}, Ljava/util/List;->size()I

    move-result v0

    const/4 v1, 0x1

    sub-int/2addr v0, v1

    :goto_0
    if-ltz v0, :cond_0

    .line 68
    invoke-interface {p1, v0}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Landroid/content/pm/LauncherActivityInfo;

    .line 69
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ad:Lcom/google/android/apps/nexuslauncher/reflection/a/b;

    invoke-virtual {v3, v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->a(ILandroid/content/pm/LauncherActivityInfo;)V

    .line 70
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    invoke-virtual {v3, v2}, Lcom/google/android/apps/nexuslauncher/reflection/b/b;->a(Landroid/content/pm/LauncherActivityInfo;)V

    add-int/lit8 v0, v0, -0x1

    goto :goto_0

    :cond_0
    return-void
.end method


# virtual methods
.method public final h()V
    .locals 1

    .line 116
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->v:Lcom/android/launcher3/compat/LauncherAppsCompat;

    invoke-virtual {v0, p0}, Lcom/android/launcher3/compat/LauncherAppsCompat;->removeOnAppsChangedCallback(Lcom/android/launcher3/compat/LauncherAppsCompat$OnAppsChangedCallbackCompat;)V

    return-void
.end method

.method public final initialize()V
    .locals 4

    .line 47
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    .line 48
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ac:Lcom/android/launcher3/compat/UserManagerCompat;

    invoke-virtual {v0}, Lcom/android/launcher3/compat/UserManagerCompat;->getUserProfiles()Ljava/util/List;

    move-result-object v0

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_0

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Landroid/os/UserHandle;

    .line 49
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->v:Lcom/android/launcher3/compat/LauncherAppsCompat;

    const/4 v3, 0x0

    invoke-virtual {v2, v3, v1}, Lcom/android/launcher3/compat/LauncherAppsCompat;->getActivityList(Ljava/lang/String;Landroid/os/UserHandle;)Ljava/util/List;

    move-result-object v1

    invoke-direct {p0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/d;->a(Ljava/util/List;)V

    goto :goto_0

    .line 51
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->v:Lcom/android/launcher3/compat/LauncherAppsCompat;

    invoke-virtual {v0, p0}, Lcom/android/launcher3/compat/LauncherAppsCompat;->addOnAppsChangedCallback(Lcom/android/launcher3/compat/LauncherAppsCompat$OnAppsChangedCallbackCompat;)V

    return-void
.end method

.method public onPackageAdded(Ljava/lang/String;Landroid/os/UserHandle;)V
    .locals 1

    .line 59
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->v:Lcom/android/launcher3/compat/LauncherAppsCompat;

    invoke-virtual {v0, p1, p2}, Lcom/android/launcher3/compat/LauncherAppsCompat;->getActivityList(Ljava/lang/String;Landroid/os/UserHandle;)Ljava/util/List;

    move-result-object p1

    invoke-direct {p0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/d;->a(Ljava/util/List;)V

    return-void
.end method

.method public onPackageChanged(Ljava/lang/String;Landroid/os/UserHandle;)V
    .locals 2

    .line 86
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ad:Lcom/google/android/apps/nexuslauncher/reflection/a/b;

    const/4 v1, -0x1

    invoke-virtual {v0, v1, p1, p2}, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->a(ILjava/lang/String;Landroid/os/UserHandle;)V

    .line 87
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    invoke-virtual {v0, p1, p2}, Lcom/google/android/apps/nexuslauncher/reflection/b/b;->a(Ljava/lang/String;Landroid/os/UserHandle;)V

    return-void
.end method

.method public onPackageRemoved(Ljava/lang/String;Landroid/os/UserHandle;)V
    .locals 5

    .line 76
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ad:Lcom/google/android/apps/nexuslauncher/reflection/a/b;

    const/4 v1, 0x0

    invoke-virtual {v0, v1, p1, p2}, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->a(ILjava/lang/String;Landroid/os/UserHandle;)V

    .line 77
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    invoke-virtual {v0, p1, p2}, Lcom/google/android/apps/nexuslauncher/reflection/b/b;->a(Ljava/lang/String;Landroid/os/UserHandle;)V

    .line 78
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->mServiceHandler:Lcom/google/android/apps/nexuslauncher/reflection/h;

    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ac:Lcom/android/launcher3/compat/UserManagerCompat;

    .line 79
    invoke-virtual {v2, p2}, Lcom/android/launcher3/compat/UserManagerCompat;->getSerialNumberForUser(Landroid/os/UserHandle;)J

    .line 78
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    const-string p2, "%s/"

    const/4 v2, 0x1

    new-array v3, v2, [Ljava/lang/Object;

    aput-object p1, v3, v1

    invoke-static {p2, v3}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object p2

    iget-object v3, v0, Lcom/google/android/apps/nexuslauncher/reflection/h;->mEngine:Lcom/google/android/apps/nexuslauncher/reflection/g;

    const-string v4, "system"

    invoke-virtual {v3, v4, p2}, Lcom/google/android/apps/nexuslauncher/reflection/g;->b(Ljava/lang/String;Ljava/lang/String;)V

    const-string p2, "%s%s/"

    const/4 v3, 0x2

    new-array v3, v3, [Ljava/lang/Object;

    const-string v4, "_"

    aput-object v4, v3, v1

    aput-object p1, v3, v2

    invoke-static {p2, v3}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object p1

    iget-object p2, v0, Lcom/google/android/apps/nexuslauncher/reflection/h;->mEngine:Lcom/google/android/apps/nexuslauncher/reflection/g;

    const-string v0, "system"

    invoke-virtual {p2, v0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/g;->b(Ljava/lang/String;Ljava/lang/String;)V

    return-void
.end method

.method public onPackagesAvailable([Ljava/lang/String;Landroid/os/UserHandle;Z)V
    .locals 1

    .line 93
    iget-object p3, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ad:Lcom/google/android/apps/nexuslauncher/reflection/a/b;

    const/4 v0, -0x1

    invoke-virtual {p3, v0, p1, p2}, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->a(I[Ljava/lang/String;Landroid/os/UserHandle;)V

    .line 94
    iget-object p3, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    invoke-virtual {p3, p1, p2}, Lcom/google/android/apps/nexuslauncher/reflection/b/b;->a([Ljava/lang/String;Landroid/os/UserHandle;)V

    return-void
.end method

.method public onPackagesSuspended([Ljava/lang/String;Landroid/os/UserHandle;)V
    .locals 2

    .line 105
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ad:Lcom/google/android/apps/nexuslauncher/reflection/a/b;

    const/4 v1, 0x0

    invoke-virtual {v0, v1, p1, p2}, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->a(I[Ljava/lang/String;Landroid/os/UserHandle;)V

    .line 106
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    invoke-virtual {v0, p1, p2}, Lcom/google/android/apps/nexuslauncher/reflection/b/b;->a([Ljava/lang/String;Landroid/os/UserHandle;)V

    return-void
.end method

.method public onPackagesUnavailable([Ljava/lang/String;Landroid/os/UserHandle;Z)V
    .locals 1

    .line 100
    iget-object p3, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ad:Lcom/google/android/apps/nexuslauncher/reflection/a/b;

    const/4 v0, 0x0

    invoke-virtual {p3, v0, p1, p2}, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->a(I[Ljava/lang/String;Landroid/os/UserHandle;)V

    .line 101
    iget-object p3, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    invoke-virtual {p3, p1, p2}, Lcom/google/android/apps/nexuslauncher/reflection/b/b;->a([Ljava/lang/String;Landroid/os/UserHandle;)V

    return-void
.end method

.method public onPackagesUnsuspended([Ljava/lang/String;Landroid/os/UserHandle;)V
    .locals 2

    .line 110
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ad:Lcom/google/android/apps/nexuslauncher/reflection/a/b;

    const/4 v1, -0x1

    invoke-virtual {v0, v1, p1, p2}, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->a(I[Ljava/lang/String;Landroid/os/UserHandle;)V

    .line 111
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d;->ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    invoke-virtual {v0, p1, p2}, Lcom/google/android/apps/nexuslauncher/reflection/b/b;->a([Ljava/lang/String;Landroid/os/UserHandle;)V

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
