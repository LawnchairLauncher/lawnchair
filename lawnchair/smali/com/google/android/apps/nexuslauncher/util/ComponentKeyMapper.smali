.class public Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;
.super Ljava/lang/Object;
.source "SourceFile"


# instance fields
.field protected final di:Lcom/android/launcher3/util/ComponentKey;

.field private final mContext:Landroid/content/Context;


# direct methods
.method public constructor <init>(Landroid/content/Context;Lcom/android/launcher3/util/ComponentKey;)V
    .locals 0

    .line 21
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 22
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->mContext:Landroid/content/Context;

    .line 23
    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->di:Lcom/android/launcher3/util/ComponentKey;

    return-void
.end method


# virtual methods
.method public getApp(Lcom/android/launcher3/allapps/AllAppsStore;)Lcom/android/launcher3/ItemInfoWithIcon;
    .locals 1

    .line 44
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->di:Lcom/android/launcher3/util/ComponentKey;

    invoke-virtual {p1, v0}, Lcom/android/launcher3/allapps/AllAppsStore;->getApp(Lcom/android/launcher3/util/ComponentKey;)Lcom/android/launcher3/AppInfo;

    move-result-object p1

    if-eqz p1, :cond_0

    return-object p1

    .line 47
    :cond_0
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->getComponentClass()Ljava/lang/String;

    move-result-object p1

    const-string v0, "@instantapp"

    invoke-virtual {p1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_1

    .line 48
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->mContext:Landroid/content/Context;

    invoke-static {p1}, Lcom/google/android/apps/nexuslauncher/b/b;->b(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/b/b;

    move-result-object p1

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->di:Lcom/android/launcher3/util/ComponentKey;

    iget-object v0, v0, Lcom/android/launcher3/util/ComponentKey;->componentName:Landroid/content/ComponentName;

    .line 49
    invoke-virtual {v0}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;

    move-result-object v0

    .line 48
    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/b/b;->J:Ljava/util/Map;

    invoke-interface {p1, v0}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lcom/google/android/apps/nexuslauncher/b/a;

    return-object p1

    .line 50
    :cond_1
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->di:Lcom/android/launcher3/util/ComponentKey;

    instance-of p1, p1, Lcom/android/launcher3/shortcuts/ShortcutKey;

    if-eqz p1, :cond_2

    .line 51
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->mContext:Landroid/content/Context;

    invoke-static {p1}, Lcom/google/android/apps/nexuslauncher/a/a;->a(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/a/a;

    move-result-object p1

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->di:Lcom/android/launcher3/util/ComponentKey;

    check-cast v0, Lcom/android/launcher3/shortcuts/ShortcutKey;

    .line 52
    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/a/a;->d:Ljava/util/Map;

    invoke-interface {p1, v0}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lcom/android/launcher3/ShortcutInfo;

    return-object p1

    :cond_2
    const/4 p1, 0x0

    return-object p1
.end method

.method public getComponentClass()Ljava/lang/String;
    .locals 1

    .line 31
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->di:Lcom/android/launcher3/util/ComponentKey;

    iget-object v0, v0, Lcom/android/launcher3/util/ComponentKey;->componentName:Landroid/content/ComponentName;

    invoke-virtual {v0}, Landroid/content/ComponentName;->getClassName()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method

.method public getComponentKey()Lcom/android/launcher3/util/ComponentKey;
    .locals 1

    .line 35
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->di:Lcom/android/launcher3/util/ComponentKey;

    return-object v0
.end method

.method public getPackage()Ljava/lang/String;
    .locals 1

    .line 27
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->di:Lcom/android/launcher3/util/ComponentKey;

    iget-object v0, v0, Lcom/android/launcher3/util/ComponentKey;->componentName:Landroid/content/ComponentName;

    invoke-virtual {v0}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method

.method public toString()Ljava/lang/String;
    .locals 1

    .line 40
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/util/ComponentKeyMapper;->di:Lcom/android/launcher3/util/ComponentKey;

    invoke-virtual {v0}, Lcom/android/launcher3/util/ComponentKey;->toString()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method
