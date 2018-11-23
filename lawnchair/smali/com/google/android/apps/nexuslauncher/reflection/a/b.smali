.class public Lcom/google/android/apps/nexuslauncher/reflection/a/b;
.super Ljava/lang/Object;
.source "SourceFile"


# static fields
.field public static final aT:[Ljava/lang/String;


# instance fields
.field public final aU:[Lcom/google/android/apps/nexuslauncher/reflection/a/a;

.field public final aV:Ljava/util/Map;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Lcom/google/android/apps/nexuslauncher/reflection/a/a;",
            ">;"
        }
    .end annotation
.end field

.field private final aW:Landroid/os/UserHandle;

.field public final mPackageManager:Landroid/content/pm/PackageManager;


# direct methods
.method static constructor <clinit>()V
    .locals 35

    const-string v0, "com.google.android.apps.photos"

    const-string v1, "com.google.android.apps.maps"

    const-string v2, "com.google.android.gm"

    const-string v3, "com.google.android.deskclock"

    const-string v4, "com.android.settings"

    const-string v5, "com.whatsapp"

    const-string v6, "com.facebook.katana"

    const-string v7, "com.facebook.orca"

    const-string v8, "com.google.android.youtube"

    const-string v9, "com.yodo1.crossyroad"

    const-string v10, "com.spotify.music"

    const-string v11, "com.android.chrome"

    const-string v12, "com.instagram.android"

    const-string v13, "com.skype.raider"

    const-string v14, "com.snapchat.android"

    const-string v15, "com.viber.voip"

    const-string v16, "com.twitter.android"

    const-string v17, "com.android.phone"

    const-string v18, "com.google.android.music"

    const-string v19, "com.google.android.calendar"

    const-string v20, "com.google.android.apps.genie.geniewidget"

    const-string v21, "com.netflix.mediaclient"

    const-string v22, "bbc.iplayer.android"

    const-string v23, "com.google.android.videos"

    const-string v24, "com.amazon.mShop.android.shopping"

    const-string v25, "com.microsoft.office.word"

    const-string v26, "com.google.android.apps.docs"

    const-string v27, "com.google.android.keep"

    const-string v28, "com.google.android.apps.plus"

    const-string v29, "com.google.android.talk"

    const-string v30, "com.pandora.android"

    const-string v31, "com.sdu.didi.psnger"

    const-string v32, "com.amazon.mp3"

    const-string v33, "com.ubercab"

    const-string v34, "com.olacabs.customer"

    .line 35
    filled-new-array/range {v0 .. v34}, [Ljava/lang/String;

    move-result-object v0

    sput-object v0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aT:[Ljava/lang/String;

    return-void
.end method

.method public constructor <init>(Landroid/content/Context;)V
    .locals 4

    .line 88
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 84
    sget-object v0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aT:[Ljava/lang/String;

    array-length v0, v0

    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/a/a;

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aU:[Lcom/google/android/apps/nexuslauncher/reflection/a/a;

    .line 85
    new-instance v0, Ljava/util/HashMap;

    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aV:Ljava/util/Map;

    .line 89
    invoke-virtual/range {p1 .. p1}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;

    move-result-object p1

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->mPackageManager:Landroid/content/pm/PackageManager;

    .line 90
    invoke-static {}, Landroid/os/Process;->myUserHandle()Landroid/os/UserHandle;

    move-result-object p1

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aW:Landroid/os/UserHandle;

    const/4 p1, 0x0

    .line 92
    :goto_0
    sget-object v0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aT:[Ljava/lang/String;

    array-length v0, v0

    if-ge p1, v0, :cond_0

    .line 95
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/a/a;

    sget-object v1, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aT:[Ljava/lang/String;

    aget-object v1, v1, p1

    const-string v2, ""

    const/4 v3, -0x1

    invoke-direct {v0, v1, v2, v3}, Lcom/google/android/apps/nexuslauncher/reflection/a/a;-><init>(Ljava/lang/String;Ljava/lang/String;I)V

    .line 96
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aU:[Lcom/google/android/apps/nexuslauncher/reflection/a/a;

    aput-object v0, v1, p1

    .line 97
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aV:Ljava/util/Map;

    sget-object v2, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aT:[Ljava/lang/String;

    aget-object v2, v2, p1

    invoke-interface {v1, v2, v0}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    add-int/lit8 p1, p1, 0x1

    goto :goto_0

    :cond_0
    return-void
.end method


# virtual methods
.method public final declared-synchronized a(ILandroid/content/pm/LauncherActivityInfo;)V
    .locals 2

    monitor-enter p0

    .line 187
    :try_start_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aW:Landroid/os/UserHandle;

    invoke-virtual/range {p2 .. p2}, Landroid/content/pm/LauncherActivityInfo;->getUser()Landroid/os/UserHandle;

    move-result-object v1

    invoke-virtual {v0, v1}, Landroid/os/UserHandle;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_0

    .line 188
    invoke-virtual/range {p2 .. p2}, Landroid/content/pm/LauncherActivityInfo;->getComponentName()Landroid/content/ComponentName;

    move-result-object v0

    invoke-virtual {v0}, Landroid/content/ComponentName;->getPackageName()Ljava/lang/String;

    move-result-object v0

    .line 193
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aV:Ljava/util/Map;

    invoke-interface {v1, v0}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/google/android/apps/nexuslauncher/reflection/a/a;

    if-eqz v0, :cond_0

    .line 195
    iput p1, v0, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->state:I

    .line 196
    invoke-virtual/range {p2 .. p2}, Landroid/content/pm/LauncherActivityInfo;->getComponentName()Landroid/content/ComponentName;

    move-result-object p1

    invoke-virtual {p1}, Landroid/content/ComponentName;->getClassName()Ljava/lang/String;

    move-result-object p1

    iput-object p1, v0, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->className:Ljava/lang/String;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 199
    :cond_0
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 186
    monitor-exit p0

    throw p1
.end method

.method public final a(ILjava/lang/String;Landroid/os/UserHandle;)V
    .locals 1

    .line 158
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aW:Landroid/os/UserHandle;

    invoke-virtual {v0, p3}, Landroid/os/UserHandle;->equals(Ljava/lang/Object;)Z

    move-result p3

    if-eqz p3, :cond_0

    .line 163
    iget-object p3, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aV:Ljava/util/Map;

    invoke-interface {p3, p2}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p2

    check-cast p2, Lcom/google/android/apps/nexuslauncher/reflection/a/a;

    if-eqz p2, :cond_0

    .line 165
    iput p1, p2, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->state:I

    :cond_0
    return-void
.end method

.method public final a(I[Ljava/lang/String;Landroid/os/UserHandle;)V
    .locals 3

    .line 149
    array-length v0, p2

    const/4 v1, 0x0

    :goto_0
    if-ge v1, v0, :cond_0

    aget-object v2, p2, v1

    .line 150
    invoke-virtual {p0, p1, v2, p3}, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->a(ILjava/lang/String;Landroid/os/UserHandle;)V

    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    :cond_0
    return-void
.end method
