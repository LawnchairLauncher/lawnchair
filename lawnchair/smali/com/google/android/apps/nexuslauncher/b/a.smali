.class public Lcom/google/android/apps/nexuslauncher/b/a;
.super Lcom/android/launcher3/AppInfo;
.source "SourceFile"


# direct methods
.method public constructor <init>(Landroid/content/Intent;Ljava/lang/String;)V
    .locals 1

    .line 14
    invoke-direct/range {p0 .. p0}, Lcom/android/launcher3/AppInfo;-><init>()V

    .line 15
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/b/a;->intent:Landroid/content/Intent;

    .line 16
    new-instance p1, Landroid/content/ComponentName;

    const-string v0, "@instantapp"

    invoke-direct {p1, p2, v0}, Landroid/content/ComponentName;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/b/a;->componentName:Landroid/content/ComponentName;

    return-void
.end method


# virtual methods
.method public getTargetComponent()Landroid/content/ComponentName;
    .locals 1

    .line 21
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/b/a;->componentName:Landroid/content/ComponentName;

    return-object v0
.end method

.method public makeShortcut()Lcom/android/launcher3/ShortcutInfo;
    .locals 3

    .line 26
    invoke-super/range {p0 .. p0}, Lcom/android/launcher3/AppInfo;->makeShortcut()Lcom/android/launcher3/ShortcutInfo;

    move-result-object v0

    const/4 v1, 0x0

    .line 27
    iput v1, v0, Lcom/android/launcher3/ShortcutInfo;->itemType:I

    const/16 v1, 0x1a

    .line 28
    iput v1, v0, Lcom/android/launcher3/ShortcutInfo;->status:I

    .line 31
    iget-object v1, v0, Lcom/android/launcher3/ShortcutInfo;->intent:Landroid/content/Intent;

    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/b/a;->componentName:Landroid/content/ComponentName;

    invoke-virtual {v2}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v1, v2}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    return-object v0
.end method
