.class public Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;
.implements Landroid/os/Handler$Callback;


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;,
        Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;,
        Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;,
        Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$UpdateListener;
    }
.end annotation


# static fields
.field public static final AIAI_PACKAGE:Ljava/lang/String; = "com.google.android.as"

.field public static final MAX_ITEMS:I = 0x2

.field private static final i:Landroid/net/Uri;

.field private static final j:Landroid/net/Uri;

.field private static final k:Landroid/net/Uri;

.field private static final l:[Ljava/lang/String;

.field private static m:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;


# instance fields
.field private final b:Landroid/os/Handler;

.field private final c:Landroid/os/Handler;

.field private final n:Landroid/content/Context;

.field private final o:Landroid/content/SharedPreferences;

.field private final p:Landroid/content/SharedPreferences;

.field private final q:Ljava/util/ArrayList;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/ArrayList<",
            "Lcom/google/android/apps/nexuslauncher/allapps/Action;",
            ">;"
        }
    .end annotation
.end field

.field private final r:Ljava/util/ArrayList;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/ArrayList<",
            "Lcom/google/android/apps/nexuslauncher/allapps/Action;",
            ">;"
        }
    .end annotation
.end field

.field private final s:Landroid/database/ContentObserver;

.field private final t:Ljava/util/Comparator;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Comparator<",
            "Lcom/google/android/apps/nexuslauncher/allapps/Action;",
            ">;"
        }
    .end annotation
.end field

.field private final u:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;

.field private final v:Lcom/android/launcher3/compat/LauncherAppsCompat;

.field private w:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$UpdateListener;

.field private x:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;


# direct methods
.method static constructor <clinit>()V
    .locals 7

    .line 79
    new-instance v0, Landroid/net/Uri$Builder;

    invoke-direct {v0}, Landroid/net/Uri$Builder;-><init>()V

    const-string v1, "content"

    .line 80
    invoke-virtual {v0, v1}, Landroid/net/Uri$Builder;->scheme(Ljava/lang/String;)Landroid/net/Uri$Builder;

    move-result-object v0

    const-string v1, "com.google.android.as.allapps.actionsuggestprovider"

    .line 81
    invoke-virtual {v0, v1}, Landroid/net/Uri$Builder;->authority(Ljava/lang/String;)Landroid/net/Uri$Builder;

    move-result-object v0

    .line 82
    invoke-virtual {v0}, Landroid/net/Uri$Builder;->build()Landroid/net/Uri;

    move-result-object v0

    sput-object v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->i:Landroid/net/Uri;

    .line 84
    new-instance v0, Landroid/net/Uri$Builder;

    invoke-direct {v0}, Landroid/net/Uri$Builder;-><init>()V

    const-string v1, "content"

    .line 85
    invoke-virtual {v0, v1}, Landroid/net/Uri$Builder;->scheme(Ljava/lang/String;)Landroid/net/Uri$Builder;

    move-result-object v0

    const-string v1, "com.google.android.as.allapps.actionloggingprovider"

    .line 86
    invoke-virtual {v0, v1}, Landroid/net/Uri$Builder;->authority(Ljava/lang/String;)Landroid/net/Uri$Builder;

    move-result-object v0

    .line 87
    invoke-virtual {v0}, Landroid/net/Uri$Builder;->build()Landroid/net/Uri;

    move-result-object v0

    sput-object v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->j:Landroid/net/Uri;

    .line 89
    new-instance v0, Landroid/net/Uri$Builder;

    invoke-direct {v0}, Landroid/net/Uri$Builder;-><init>()V

    const-string v1, "content"

    .line 90
    invoke-virtual {v0, v1}, Landroid/net/Uri$Builder;->scheme(Ljava/lang/String;)Landroid/net/Uri$Builder;

    move-result-object v0

    const-string v1, "com.google.android.as.allapps.actionsettingprovider"

    .line 91
    invoke-virtual {v0, v1}, Landroid/net/Uri$Builder;->authority(Ljava/lang/String;)Landroid/net/Uri$Builder;

    move-result-object v0

    .line 92
    invoke-virtual {v0}, Landroid/net/Uri$Builder;->build()Landroid/net/Uri;

    move-result-object v0

    sput-object v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->k:Landroid/net/Uri;

    const-string v1, "action_id"

    const-string v2, "shortcut_id"

    const-string v3, "expiration_time_millis"

    const-string v4, "publisher_package"

    const-string v5, "badge_package"

    const-string v6, "position"

    .line 94
    filled-new-array/range {v1 .. v6}, [Ljava/lang/String;

    move-result-object v0

    sput-object v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->l:[Ljava/lang/String;

    return-void
.end method

.method private constructor <init>(Landroid/content/Context;)V
    .locals 7

    .line 149
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 125
    new-instance v0, Landroid/os/Handler;

    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;

    move-result-object v1

    invoke-direct {v0, v1, p0}, Landroid/os/Handler;-><init>(Landroid/os/Looper;Landroid/os/Handler$Callback;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->c:Landroid/os/Handler;

    .line 128
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->q:Ljava/util/ArrayList;

    .line 130
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->r:Ljava/util/ArrayList;

    .line 132
    new-instance v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$1;

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->c:Landroid/os/Handler;

    invoke-direct {v0, p0, v1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$1;-><init>(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;Landroid/os/Handler;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->s:Landroid/database/ContentObserver;

    .line 141
    sget-object v0, Lcom/google/android/apps/nexuslauncher/allapps/-$$Lambda$ActionsController$l_tTKsBoaYeLbkSChO-IgfkLKcU;->INSTANCE:Lcom/google/android/apps/nexuslauncher/allapps/-$$Lambda$ActionsController$l_tTKsBoaYeLbkSChO-IgfkLKcU;

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->t:Ljava/util/Comparator;

    .line 144
    new-instance v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;

    invoke-direct {v0, p0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;-><init>(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->u:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;

    .line 147
    new-instance v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;

    const/4 v1, 0x0

    invoke-direct {v0, p0, v1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;-><init>(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;B)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->x:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;

    .line 150
    new-instance v0, Landroid/os/Handler;

    invoke-static {}, Lcom/android/launcher3/LauncherModel;->getWorkerLooper()Landroid/os/Looper;

    move-result-object v2

    invoke-direct {v0, v2, p0}, Landroid/os/Handler;-><init>(Landroid/os/Looper;Landroid/os/Handler$Callback;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->b:Landroid/os/Handler;

    .line 151
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->n:Landroid/content/Context;

    .line 152
    invoke-static/range {p1 .. p1}, Lcom/android/launcher3/Utilities;->getPrefs(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->o:Landroid/content/SharedPreferences;

    const-string v0, "pref_file_impressions"

    .line 153
    invoke-virtual {p1, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->p:Landroid/content/SharedPreferences;

    .line 154
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->o:Landroid/content/SharedPreferences;

    invoke-interface {v0, p0}, Landroid/content/SharedPreferences;->registerOnSharedPreferenceChangeListener(Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;)V

    .line 155
    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->b()V

    .line 156
    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->c()V

    .line 157
    invoke-static/range {p1 .. p1}, Lcom/android/launcher3/compat/LauncherAppsCompat;->getInstance(Landroid/content/Context;)Lcom/android/launcher3/compat/LauncherAppsCompat;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->v:Lcom/android/launcher3/compat/LauncherAppsCompat;

    .line 158
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->v:Lcom/android/launcher3/compat/LauncherAppsCompat;

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->x:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback;

    invoke-virtual {v0, v1}, Lcom/android/launcher3/compat/LauncherAppsCompat;->addOnAppsChangedCallback(Lcom/android/launcher3/compat/LauncherAppsCompat$OnAppsChangedCallbackCompat;)V

    .line 159
    new-instance v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$2;

    invoke-direct {v0, p0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$2;-><init>(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)V

    const-string v1, "com.google.android.as"

    const-string v2, "android.intent.action.PACKAGE_ADDED"

    const-string v3, "android.intent.action.PACKAGE_CHANGED"

    const-string v4, "android.intent.action.PACKAGE_REMOVED"

    const-string v5, "android.intent.action.PACKAGE_DATA_CLEARED"

    const-string v6, "android.intent.action.PACKAGE_RESTARTED"

    filled-new-array {v2, v3, v4, v5, v6}, [Ljava/lang/String;

    move-result-object v2

    .line 164
    invoke-static {v1, v2}, Lcom/google/android/apps/nexuslauncher/util/b;->a(Ljava/lang/String;[Ljava/lang/String;)Landroid/content/IntentFilter;

    move-result-object v1

    .line 159
    invoke-virtual {p1, v0, v1}, Landroid/content/Context;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;

    return-void
.end method

.method private static synthetic a(Lcom/google/android/apps/nexuslauncher/allapps/Action;Lcom/google/android/apps/nexuslauncher/allapps/Action;)I
    .locals 2

    .line 142
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/Action;->position:J

    iget-wide p0, p1, Lcom/google/android/apps/nexuslauncher/allapps/Action;->position:J

    invoke-static {v0, v1, p0, p1}, Ljava/lang/Long;->compare(JJ)I

    move-result p0

    return p0
.end method

.method private a(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;)Landroid/content/ContentValues;
    .locals 4

    .line 355
    new-instance v0, Landroid/content/ContentValues;

    invoke-direct {v0}, Landroid/content/ContentValues;-><init>()V

    const-string v1, "timestamp"

    .line 356
    iget-wide v2, p1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->ts:J

    invoke-static {v2, v3}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/Long;)V

    const-string v1, "event_type"

    .line 357
    iget v2, p1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->D:I

    invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/Integer;)V

    const-string v1, "clicked_type"

    const/4 v2, 0x0

    .line 358
    invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/Integer;)V

    const-string v1, "clicked_id"

    .line 359
    iget-object v2, p1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->E:Ljava/lang/String;

    invoke-virtual {v0, v1, v2}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/String;)V

    const-string v1, "clicked_position"

    .line 360
    iget v2, p1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->F:I

    invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/Integer;)V

    const-string v1, "top_suggestions"

    .line 361
    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->G:Ljava/lang/String;

    invoke-virtual {v0, v1, p1}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/String;)V

    return-object v0
.end method

.method private a(Ljava/util/List;Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;)Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;
    .locals 3
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/List<",
            "Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;",
            ">;",
            "Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;",
            ")",
            "Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;"
        }
    .end annotation

    if-eqz p2, :cond_1

    .line 466
    invoke-interface/range {p1 .. p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :cond_0
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_1

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;

    .line 467
    iget-object v1, v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->shortcutId:Ljava/lang/String;

    invoke-virtual/range {p2 .. p2}, Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;->getId()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_0

    return-object v0

    :cond_1
    const/4 p1, 0x0

    return-object p1
.end method

.method private static a(Ljava/util/ArrayList;)Ljava/util/ArrayList;
    .locals 3
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/ArrayList<",
            "Lcom/google/android/apps/nexuslauncher/allapps/Action;",
            ">;)",
            "Ljava/util/ArrayList<",
            "Lcom/google/android/apps/nexuslauncher/allapps/Action;",
            ">;"
        }
    .end annotation

    .line 202
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    .line 203
    invoke-virtual/range {p0 .. p0}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object p0

    .line 204
    :cond_0
    :goto_0
    invoke-interface {p0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_1

    .line 205
    invoke-interface {p0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/android/apps/nexuslauncher/allapps/Action;

    .line 206
    iget-boolean v2, v1, Lcom/google/android/apps/nexuslauncher/allapps/Action;->isEnabled:Z

    if-eqz v2, :cond_0

    .line 207
    invoke-virtual {v0, v1}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    goto :goto_0

    :cond_1
    return-object v0
.end method

.method private a(Lcom/google/android/apps/nexuslauncher/allapps/Action;)V
    .locals 4

    const/4 v0, 0x0

    .line 437
    iput-boolean v0, p1, Lcom/google/android/apps/nexuslauncher/allapps/Action;->isEnabled:Z

    .line 438
    iget-object v1, p1, Lcom/google/android/apps/nexuslauncher/allapps/Action;->shortcut:Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;

    invoke-virtual {v1}, Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;->isEnabled()Z

    move-result v1

    if-eqz v1, :cond_0

    .line 439
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->v:Lcom/android/launcher3/compat/LauncherAppsCompat;

    iget-object v2, p1, Lcom/google/android/apps/nexuslauncher/allapps/Action;->badgePackage:Ljava/lang/String;

    iget-object v3, p1, Lcom/google/android/apps/nexuslauncher/allapps/Action;->shortcut:Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;

    .line 440
    invoke-virtual {v3}, Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;->getUserHandle()Landroid/os/UserHandle;

    move-result-object v3

    .line 439
    invoke-virtual {v1, v2, v0, v3}, Lcom/android/launcher3/compat/LauncherAppsCompat;->getApplicationInfo(Ljava/lang/String;ILandroid/os/UserHandle;)Landroid/content/pm/ApplicationInfo;

    move-result-object v0

    if-eqz v0, :cond_0

    .line 441
    iget-boolean v1, v0, Landroid/content/pm/ApplicationInfo;->enabled:Z

    if-eqz v1, :cond_0

    .line 442
    invoke-static {v0}, Lcom/android/launcher3/util/PackageManagerHelper;->isAppSuspended(Landroid/content/pm/ApplicationInfo;)Z

    move-result v0

    if-nez v0, :cond_0

    const/4 v0, 0x1

    .line 443
    iput-boolean v0, p1, Lcom/google/android/apps/nexuslauncher/allapps/Action;->isEnabled:Z

    :cond_0
    return-void
.end method

.method static synthetic a(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)V
    .locals 0

    .line 49
    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->d()V

    return-void
.end method

.method private static synthetic a(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V
    .locals 0

    if-eqz p0, :cond_0

    .line 347
    :try_start_0
    invoke-interface/range {p1 .. p1}, Ljava/lang/AutoCloseable;->close()V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception p1

    invoke-virtual {p0, p1}, Ljava/lang/Throwable;->addSuppressed(Ljava/lang/Throwable;)V

    return-void

    :cond_0
    invoke-interface/range {p1 .. p1}, Ljava/lang/AutoCloseable;->close()V

    return-void
.end method

.method private a()Z
    .locals 3

    .line 173
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->o:Landroid/content/SharedPreferences;

    const-string v1, "pref_show_suggested_actions"

    const/4 v2, 0x1

    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v0

    return v0
.end method

.method private b()V
    .locals 3

    .line 177
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->b:Landroid/os/Handler;

    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a()Z

    move-result v1

    invoke-static {v1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object v1

    const/4 v2, 0x2

    invoke-static {v0, v2, v1}, Landroid/os/Message;->obtain(Landroid/os/Handler;ILjava/lang/Object;)Landroid/os/Message;

    move-result-object v0

    .line 178
    invoke-virtual {v0}, Landroid/os/Message;->sendToTarget()V

    return-void
.end method

.method static synthetic b(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)V
    .locals 0

    .line 49
    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->c()V

    return-void
.end method

.method private b(Ljava/util/ArrayList;)V
    .locals 3
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/ArrayList<",
            "Lcom/google/android/apps/nexuslauncher/allapps/Action;",
            ">;)V"
        }
    .end annotation

    .line 423
    invoke-virtual/range {p1 .. p1}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object v0

    .line 424
    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_2

    .line 425
    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/android/apps/nexuslauncher/allapps/Action;

    .line 426
    iget-object v2, v1, Lcom/google/android/apps/nexuslauncher/allapps/Action;->shortcut:Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;

    if-eqz v2, :cond_1

    iget-object v2, v1, Lcom/google/android/apps/nexuslauncher/allapps/Action;->shortcutInfo:Lcom/android/launcher3/ShortcutInfo;

    if-nez v2, :cond_0

    goto :goto_1

    .line 429
    :cond_0
    invoke-direct {p0, v1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Lcom/google/android/apps/nexuslauncher/allapps/Action;)V

    goto :goto_0

    .line 427
    :cond_1
    :goto_1
    invoke-interface {v0}, Ljava/util/Iterator;->remove()V

    goto :goto_0

    .line 433
    :cond_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->t:Ljava/util/Comparator;

    invoke-static {p1, v0}, Ljava/util/Collections;->sort(Ljava/util/List;Ljava/util/Comparator;)V

    return-void
.end method

.method static synthetic c(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)Landroid/os/Handler;
    .locals 0

    .line 49
    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->b:Landroid/os/Handler;

    return-object p0
.end method

.method private c()V
    .locals 4

    .line 190
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->n:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    .line 191
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->s:Landroid/database/ContentObserver;

    invoke-virtual {v0, v1}, Landroid/content/ContentResolver;->unregisterContentObserver(Landroid/database/ContentObserver;)V

    .line 193
    :try_start_0
    sget-object v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->i:Landroid/net/Uri;

    const/4 v2, 0x1

    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->s:Landroid/database/ContentObserver;

    invoke-virtual {v0, v1, v2, v3}, Landroid/content/ContentResolver;->registerContentObserver(Landroid/net/Uri;ZLandroid/database/ContentObserver;)V

    .line 194
    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->d()V

    .line 195
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->b:Landroid/os/Handler;

    const/4 v1, 0x3

    invoke-static {v0, v1}, Landroid/os/Message;->obtain(Landroid/os/Handler;I)Landroid/os/Message;

    move-result-object v0

    invoke-virtual {v0}, Landroid/os/Message;->sendToTarget()V
    :try_end_0
    .catch Ljava/lang/SecurityException; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception v0

    const-string v1, "ActionsController"

    const-string v2, "content provider not found"

    .line 197
    invoke-static {v1, v2, v0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    return-void
.end method

.method static synthetic d(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)Landroid/content/SharedPreferences;
    .locals 0

    .line 49
    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->p:Landroid/content/SharedPreferences;

    return-object p0
.end method

.method private d()V
    .locals 2

    .line 237
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->b:Landroid/os/Handler;

    const/4 v1, 0x1

    invoke-static {v0, v1}, Landroid/os/Message;->obtain(Landroid/os/Handler;I)Landroid/os/Message;

    move-result-object v0

    invoke-virtual {v0}, Landroid/os/Message;->sendToTarget()V

    return-void
.end method

.method private e()Ljava/util/ArrayList;
    .locals 28
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/ArrayList<",
            "Lcom/google/android/apps/nexuslauncher/allapps/Action;",
            ">;"
        }
    .end annotation

    move-object/from16 v1, p0

    .line 285
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->q:Ljava/util/ArrayList;

    invoke-virtual {v0}, Ljava/util/ArrayList;->clear()V

    .line 286
    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a()Z

    move-result v0

    if-nez v0, :cond_0

    .line 287
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->q:Ljava/util/ArrayList;

    return-object v0

    .line 290
    :cond_0
    :try_start_0
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->n:Landroid/content/Context;

    invoke-static {v0}, Lcom/android/launcher3/graphics/LauncherIcons;->obtain(Landroid/content/Context;)Lcom/android/launcher3/graphics/LauncherIcons;

    move-result-object v2
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_9

    const/4 v3, 0x0

    .line 291
    :try_start_1
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->n:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    sget-object v4, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->i:Landroid/net/Uri;

    .line 292
    invoke-virtual {v0, v4}, Landroid/content/ContentResolver;->acquireUnstableContentProviderClient(Landroid/net/Uri;)Landroid/content/ContentProviderClient;

    move-result-object v4
    :try_end_1
    .catch Ljava/lang/Throwable; {:try_start_1 .. :try_end_1} :catch_8
    .catchall {:try_start_1 .. :try_end_1} :catchall_8

    .line 293
    :try_start_2
    sget-object v6, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->i:Landroid/net/Uri;

    sget-object v7, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->l:[Ljava/lang/String;

    const/4 v8, 0x0

    const/4 v9, 0x0

    const/4 v10, 0x0

    move-object v5, v4

    invoke-virtual/range {v5 .. v10}, Landroid/content/ContentProviderClient;->query(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;

    move-result-object v0
    :try_end_2
    .catch Ljava/lang/Throwable; {:try_start_2 .. :try_end_2} :catch_6
    .catchall {:try_start_2 .. :try_end_2} :catchall_6

    if-nez v0, :cond_3

    :try_start_3
    const-string v0, "ActionsController"

    const-string v5, "no cursor"

    .line 295
    invoke-static {v0, v5}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    .line 296
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->q:Ljava/util/ArrayList;
    :try_end_3
    .catch Ljava/lang/Throwable; {:try_start_3 .. :try_end_3} :catch_1
    .catchall {:try_start_3 .. :try_end_3} :catchall_0

    if-eqz v4, :cond_1

    .line 347
    :try_start_4
    invoke-static {v3, v4}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V
    :try_end_4
    .catch Ljava/lang/Throwable; {:try_start_4 .. :try_end_4} :catch_0
    .catchall {:try_start_4 .. :try_end_4} :catchall_8

    goto :goto_0

    :catch_0
    move-exception v0

    move-object v3, v0

    move-object v4, v2

    goto/16 :goto_f

    :cond_1
    :goto_0
    if-eqz v2, :cond_2

    :try_start_5
    invoke-static {v3, v2}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V
    :try_end_5
    .catch Ljava/lang/Exception; {:try_start_5 .. :try_end_5} :catch_9

    :cond_2
    return-object v0

    :catchall_0
    move-exception v0

    move-object v5, v3

    :goto_1
    move-object/from16 v27, v4

    move-object v4, v2

    move-object/from16 v2, v27

    goto/16 :goto_c

    :catch_1
    move-exception v0

    move-object v5, v0

    move-object/from16 v27, v4

    move-object v4, v2

    move-object/from16 v2, v27

    goto/16 :goto_b

    .line 298
    :cond_3
    :try_start_6
    new-instance v5, Lcom/android/launcher3/util/MultiHashMap;

    invoke-direct {v5}, Lcom/android/launcher3/util/MultiHashMap;-><init>()V

    .line 299
    :goto_2
    invoke-interface {v0}, Landroid/database/Cursor;->moveToNext()Z

    move-result v6
    :try_end_6
    .catch Ljava/lang/Throwable; {:try_start_6 .. :try_end_6} :catch_6
    .catchall {:try_start_6 .. :try_end_6} :catchall_6

    const/4 v7, 0x1

    if-eqz v6, :cond_5

    .line 300
    :try_start_7
    new-instance v6, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;

    const/4 v8, 0x0

    invoke-direct {v6, v8}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;-><init>(B)V

    .line 301
    invoke-interface {v0, v8}, Landroid/database/Cursor;->getString(I)Ljava/lang/String;

    move-result-object v8

    iput-object v8, v6, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->id:Ljava/lang/String;

    .line 302
    invoke-interface {v0, v7}, Landroid/database/Cursor;->getString(I)Ljava/lang/String;

    move-result-object v7

    iput-object v7, v6, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->shortcutId:Ljava/lang/String;

    const/4 v7, 0x3

    .line 303
    invoke-interface {v0, v7}, Landroid/database/Cursor;->getString(I)Ljava/lang/String;

    move-result-object v7

    iput-object v7, v6, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->z:Ljava/lang/String;

    const/4 v7, 0x4

    .line 304
    invoke-interface {v0, v7}, Landroid/database/Cursor;->getString(I)Ljava/lang/String;

    move-result-object v7

    iput-object v7, v6, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->A:Ljava/lang/String;

    const/4 v7, 0x2

    .line 305
    invoke-interface {v0, v7}, Landroid/database/Cursor;->getLong(I)J

    move-result-wide v7

    iput-wide v7, v6, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->B:J

    const/4 v7, 0x5

    .line 306
    invoke-interface {v0, v7}, Landroid/database/Cursor;->getLong(I)J

    move-result-wide v7

    iput-wide v7, v6, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->position:J

    .line 307
    iget-wide v7, v6, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->B:J

    const-wide/16 v9, 0x0

    cmp-long v7, v7, v9

    if-lez v7, :cond_4

    iget-wide v7, v6, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->B:J

    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v9

    cmp-long v7, v7, v9

    if-gez v7, :cond_4

    const-string v7, "ActionsController"

    .line 308
    new-instance v8, Ljava/lang/StringBuilder;

    const-string v9, "shortcut expired id="

    invoke-direct {v8, v9}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    iget-object v9, v6, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->shortcutId:Ljava/lang/String;

    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v9, " ts="

    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-wide v9, v6, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->B:J

    invoke-virtual {v8, v9, v10}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    invoke-virtual {v8}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v6

    invoke-static {v7, v6}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto :goto_2

    .line 311
    :cond_4
    iget-object v7, v6, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->z:Ljava/lang/String;

    invoke-virtual {v5, v7, v6}, Lcom/android/launcher3/util/MultiHashMap;->addToList(Ljava/lang/Object;Ljava/lang/Object;)V
    :try_end_7
    .catch Ljava/lang/Throwable; {:try_start_7 .. :try_end_7} :catch_1
    .catchall {:try_start_7 .. :try_end_7} :catchall_0

    goto :goto_2

    .line 313
    :cond_5
    :try_start_8
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->n:Landroid/content/Context;

    invoke-static {v0}, Lcom/android/launcher3/shortcuts/DeepShortcutManager;->getInstance(Landroid/content/Context;)Lcom/android/launcher3/shortcuts/DeepShortcutManager;

    move-result-object v0

    .line 314
    invoke-virtual {v5}, Lcom/android/launcher3/util/MultiHashMap;->entrySet()Ljava/util/Set;

    move-result-object v5

    invoke-interface {v5}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object v5

    :cond_6
    invoke-interface {v5}, Ljava/util/Iterator;->hasNext()Z

    move-result v6
    :try_end_8
    .catch Ljava/lang/Throwable; {:try_start_8 .. :try_end_8} :catch_6
    .catchall {:try_start_8 .. :try_end_8} :catchall_6

    if-eqz v6, :cond_a

    :try_start_9
    invoke-interface {v5}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Ljava/util/Map$Entry;

    .line 315
    invoke-interface {v6}, Ljava/util/Map$Entry;->getKey()Ljava/lang/Object;

    move-result-object v8

    check-cast v8, Ljava/lang/String;

    .line 316
    invoke-interface {v6}, Ljava/util/Map$Entry;->getValue()Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Ljava/util/ArrayList;

    .line 317
    new-instance v9, Ljava/util/ArrayList;

    invoke-direct {v9}, Ljava/util/ArrayList;-><init>()V

    .line 318
    invoke-virtual {v6}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object v10

    :goto_3
    invoke-interface {v10}, Ljava/util/Iterator;->hasNext()Z

    move-result v11
    :try_end_9
    .catch Ljava/lang/Throwable; {:try_start_9 .. :try_end_9} :catch_1
    .catchall {:try_start_9 .. :try_end_9} :catchall_3

    if-eqz v11, :cond_7

    :try_start_a
    invoke-interface {v10}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v11

    check-cast v11, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;

    .line 319
    iget-object v11, v11, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->shortcutId:Ljava/lang/String;

    invoke-interface {v9, v11}, Ljava/util/List;->add(Ljava/lang/Object;)Z
    :try_end_a
    .catch Ljava/lang/Throwable; {:try_start_a .. :try_end_a} :catch_1
    .catchall {:try_start_a .. :try_end_a} :catchall_0

    goto :goto_3

    .line 322
    :cond_7
    :try_start_b
    invoke-static {}, Landroid/os/Process;->myUserHandle()Landroid/os/UserHandle;

    move-result-object v10

    .line 321
    invoke-virtual {v0, v8, v9, v10}, Lcom/android/launcher3/shortcuts/DeepShortcutManager;->queryForFullDetails(Ljava/lang/String;Ljava/util/List;Landroid/os/UserHandle;)Ljava/util/List;

    move-result-object v8

    .line 323
    invoke-interface {v8}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v8

    :goto_4
    invoke-interface {v8}, Ljava/util/Iterator;->hasNext()Z

    move-result v9

    if-eqz v9, :cond_6

    invoke-interface {v8}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v9

    check-cast v9, Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;

    .line 324
    invoke-direct {v1, v6, v9}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Ljava/util/List;Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;)Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;

    move-result-object v10

    if-eqz v10, :cond_9

    .line 327
    invoke-virtual {v9}, Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;->getShortLabel()Ljava/lang/CharSequence;

    move-result-object v11

    invoke-static {v11}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z

    move-result v11
    :try_end_b
    .catch Ljava/lang/Throwable; {:try_start_b .. :try_end_b} :catch_1
    .catchall {:try_start_b .. :try_end_b} :catchall_3

    if-eqz v11, :cond_8

    :try_start_c
    const-string v11, "ActionsController"

    .line 328
    new-instance v12, Ljava/lang/StringBuilder;

    const-string v13, "Empty shortcut label: shortcut="

    invoke-direct {v12, v13}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v12, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v12}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v12

    invoke-static {v11, v12}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_c
    .catch Ljava/lang/Throwable; {:try_start_c .. :try_end_c} :catch_1
    .catchall {:try_start_c .. :try_end_c} :catchall_0

    .line 331
    :cond_8
    :try_start_d
    new-instance v15, Lcom/android/launcher3/ShortcutInfo;

    iget-object v11, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->n:Landroid/content/Context;

    invoke-direct {v15, v9, v11}, Lcom/android/launcher3/ShortcutInfo;-><init>(Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;Landroid/content/Context;)V

    .line 332
    iget v11, v15, Lcom/android/launcher3/ShortcutInfo;->runtimeStatusFlags:I

    or-int/lit16 v11, v11, 0x200

    iput v11, v15, Lcom/android/launcher3/ShortcutInfo;->runtimeStatusFlags:I

    .line 333
    invoke-virtual {v2, v9, v7, v3}, Lcom/android/launcher3/graphics/LauncherIcons;->createShortcutIcon(Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;ZLcom/android/launcher3/util/Provider;)Lcom/android/launcher3/graphics/BitmapInfo;

    move-result-object v11

    invoke-virtual {v11, v15}, Lcom/android/launcher3/graphics/BitmapInfo;->applyTo(Lcom/android/launcher3/ItemInfoWithIcon;)V

    .line 334
    iget-object v11, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->n:Landroid/content/Context;

    .line 335
    invoke-static {v11}, Lcom/android/launcher3/LauncherAppState;->getInstance(Landroid/content/Context;)Lcom/android/launcher3/LauncherAppState;

    move-result-object v11

    invoke-virtual {v11}, Lcom/android/launcher3/LauncherAppState;->getIconCache()Lcom/android/launcher3/IconCache;

    move-result-object v11

    .line 334
    invoke-virtual {v2, v9, v11}, Lcom/android/launcher3/graphics/LauncherIcons;->getShortcutInfoBadge(Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;Lcom/android/launcher3/IconCache;)Lcom/android/launcher3/ItemInfoWithIcon;

    move-result-object v11

    .line 336
    iget-object v13, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->q:Ljava/util/ArrayList;

    new-instance v14, Lcom/google/android/apps/nexuslauncher/allapps/Action;

    iget-object v12, v10, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->id:Ljava/lang/String;

    iget-object v7, v10, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->shortcutId:Ljava/lang/String;
    :try_end_d
    .catch Ljava/lang/Throwable; {:try_start_d .. :try_end_d} :catch_1
    .catchall {:try_start_d .. :try_end_d} :catchall_3

    move-object/from16 v22, v4

    :try_start_e
    iget-wide v3, v10, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->B:J

    move-object/from16 v23, v0

    iget-object v0, v10, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->z:Ljava/lang/String;

    move-object/from16 v24, v5

    iget-object v5, v10, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->A:Ljava/lang/String;

    iget-object v11, v11, Lcom/android/launcher3/ItemInfoWithIcon;->contentDescription:Ljava/lang/CharSequence;
    :try_end_e
    .catch Ljava/lang/Throwable; {:try_start_e .. :try_end_e} :catch_2
    .catchall {:try_start_e .. :try_end_e} :catchall_1

    move-object/from16 v25, v2

    :try_start_f
    iget-wide v1, v10, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$ActionData;->position:J

    move-object v10, v14

    move-object/from16 v17, v11

    move-object v11, v12

    move-object v12, v7

    move-object/from16 v26, v6

    move-object v7, v13

    move-object v6, v14

    move-wide v13, v3

    move-object v3, v15

    move-object v15, v0

    move-object/from16 v16, v5

    move-object/from16 v18, v9

    move-object/from16 v19, v3

    move-wide/from16 v20, v1

    invoke-direct/range {v10 .. v21}, Lcom/google/android/apps/nexuslauncher/allapps/Action;-><init>(Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;Ljava/lang/CharSequence;Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;Lcom/android/launcher3/ShortcutInfo;J)V

    invoke-virtual {v7, v6}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    goto :goto_5

    :catchall_1
    move-exception v0

    move-object v4, v2

    move-object/from16 v2, v22

    goto :goto_6

    :catch_2
    move-exception v0

    move-object v5, v0

    move-object v4, v2

    move-object/from16 v2, v22

    goto :goto_7

    :cond_9
    move-object/from16 v23, v0

    move-object/from16 v25, v2

    move-object/from16 v22, v4

    move-object/from16 v24, v5

    move-object/from16 v26, v6

    const-string v0, "ActionsController"

    .line 339
    new-instance v1, Ljava/lang/StringBuilder;

    const-string v2, "shortcut details not found: shortcut="

    invoke-direct {v1, v2}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v1, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_f
    .catch Ljava/lang/Throwable; {:try_start_f .. :try_end_f} :catch_3
    .catchall {:try_start_f .. :try_end_f} :catchall_2

    :goto_5
    move-object/from16 v4, v22

    move-object/from16 v0, v23

    move-object/from16 v5, v24

    move-object/from16 v2, v25

    move-object/from16 v6, v26

    move-object/from16 v1, p0

    const/4 v3, 0x0

    const/4 v7, 0x1

    goto/16 :goto_4

    :catchall_2
    move-exception v0

    move-object/from16 v2, v22

    move-object/from16 v4, v25

    move-object/from16 v1, p0

    :goto_6
    const/4 v3, 0x0

    const/4 v5, 0x0

    goto/16 :goto_c

    :catch_3
    move-exception v0

    move-object v5, v0

    move-object/from16 v2, v22

    move-object/from16 v4, v25

    move-object/from16 v1, p0

    :goto_7
    const/4 v3, 0x0

    goto :goto_b

    :catchall_3
    move-exception v0

    const/4 v3, 0x0

    const/4 v5, 0x0

    goto/16 :goto_1

    :cond_a
    move-object/from16 v25, v2

    move-object/from16 v22, v4

    .line 346
    :try_start_10
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->q:Ljava/util/ArrayList;

    invoke-direct {v1, v0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->b(Ljava/util/ArrayList;)V
    :try_end_10
    .catch Ljava/lang/Throwable; {:try_start_10 .. :try_end_10} :catch_5
    .catchall {:try_start_10 .. :try_end_10} :catchall_5

    if-eqz v22, :cond_b

    move-object/from16 v2, v22

    const/4 v3, 0x0

    .line 347
    :try_start_11
    invoke-static {v3, v2}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V
    :try_end_11
    .catch Ljava/lang/Throwable; {:try_start_11 .. :try_end_11} :catch_4
    .catchall {:try_start_11 .. :try_end_11} :catchall_4

    goto :goto_8

    :catchall_4
    move-exception v0

    move-object/from16 v4, v25

    goto :goto_10

    :catch_4
    move-exception v0

    move-object v3, v0

    move-object/from16 v4, v25

    goto :goto_f

    :cond_b
    const/4 v3, 0x0

    :goto_8
    if-eqz v25, :cond_e

    move-object/from16 v4, v25

    :try_start_12
    invoke-static {v3, v4}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V
    :try_end_12
    .catch Ljava/lang/Exception; {:try_start_12 .. :try_end_12} :catch_9

    goto :goto_11

    :catchall_5
    move-exception v0

    move-object/from16 v2, v22

    move-object/from16 v4, v25

    const/4 v3, 0x0

    goto :goto_9

    :catch_5
    move-exception v0

    move-object/from16 v2, v22

    move-object/from16 v4, v25

    const/4 v3, 0x0

    goto :goto_a

    :catchall_6
    move-exception v0

    move-object/from16 v27, v4

    move-object v4, v2

    move-object/from16 v2, v27

    :goto_9
    move-object v5, v3

    goto :goto_c

    :catch_6
    move-exception v0

    move-object/from16 v27, v4

    move-object v4, v2

    move-object/from16 v2, v27

    :goto_a
    move-object v5, v0

    .line 290
    :goto_b
    :try_start_13
    throw v5
    :try_end_13
    .catchall {:try_start_13 .. :try_end_13} :catchall_7

    :catchall_7
    move-exception v0

    :goto_c
    if-eqz v2, :cond_c

    .line 347
    :try_start_14
    invoke-static {v5, v2}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V

    goto :goto_d

    :catch_7
    move-exception v0

    goto :goto_e

    :cond_c
    :goto_d
    throw v0
    :try_end_14
    .catch Ljava/lang/Throwable; {:try_start_14 .. :try_end_14} :catch_7
    .catchall {:try_start_14 .. :try_end_14} :catchall_9

    :catchall_8
    move-exception v0

    move-object v4, v2

    goto :goto_10

    :catch_8
    move-exception v0

    move-object v4, v2

    :goto_e
    move-object v3, v0

    .line 290
    :goto_f
    :try_start_15
    throw v3
    :try_end_15
    .catchall {:try_start_15 .. :try_end_15} :catchall_9

    :catchall_9
    move-exception v0

    :goto_10
    if-eqz v4, :cond_d

    .line 347
    :try_start_16
    invoke-static {v3, v4}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V

    :cond_d
    throw v0
    :try_end_16
    .catch Ljava/lang/Exception; {:try_start_16 .. :try_end_16} :catch_9

    :catch_9
    move-exception v0

    const-string v2, "ActionsController"

    const-string v3, "error loading actions"

    .line 348
    invoke-static {v2, v3, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 350
    :cond_e
    :goto_11
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->q:Ljava/util/ArrayList;

    return-object v0
.end method

.method static synthetic e(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)Ljava/util/ArrayList;
    .locals 0

    .line 49
    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->r:Ljava/util/ArrayList;

    return-object p0
.end method

.method private f()V
    .locals 11

    .line 378
    :try_start_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->p:Landroid/content/SharedPreferences;

    invoke-interface {v0}, Landroid/content/SharedPreferences;->getAll()Ljava/util/Map;

    move-result-object v0

    .line 379
    new-instance v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;

    const/4 v2, 0x0

    invoke-direct {v1, v2}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;-><init>(B)V

    const/4 v3, 0x2

    .line 380
    iput v3, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->D:I

    .line 381
    invoke-interface {v0}, Ljava/util/Map;->keySet()Ljava/util/Set;

    move-result-object v3

    .line 382
    new-instance v4, Ljava/util/ArrayList;

    invoke-direct {v4}, Ljava/util/ArrayList;-><init>()V

    .line 383
    invoke-interface {v3}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object v3

    :cond_0
    invoke-interface {v3}, Ljava/util/Iterator;->hasNext()Z

    move-result v5

    if-eqz v5, :cond_1

    invoke-interface {v3}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Ljava/lang/String;

    .line 384
    invoke-interface {v0, v5}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Ljava/lang/String;

    const-string v7, ","

    .line 385
    invoke-virtual {v6, v7}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;

    move-result-object v6

    .line 386
    aget-object v7, v6, v2

    invoke-static {v7}, Ljava/lang/Long;->parseLong(Ljava/lang/String;)J

    move-result-wide v7

    iput-wide v7, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->ts:J

    .line 387
    iput-object v5, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->G:Ljava/lang/String;

    .line 388
    invoke-direct {p0, v1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;)Landroid/content/ContentValues;

    move-result-object v5

    invoke-virtual {v4, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    const/4 v5, 0x1

    .line 389
    :goto_0
    array-length v7, v6

    if-ge v5, v7, :cond_0

    .line 390
    iget-wide v7, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->ts:J

    aget-object v9, v6, v5

    invoke-static {v9}, Ljava/lang/Long;->parseLong(Ljava/lang/String;)J

    move-result-wide v9

    add-long/2addr v7, v9

    iput-wide v7, v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->ts:J

    .line 391
    invoke-direct {p0, v1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;)Landroid/content/ContentValues;

    move-result-object v7

    invoke-virtual {v4, v7}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    add-int/lit8 v5, v5, 0x1

    goto :goto_0

    .line 394
    :cond_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->n:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    sget-object v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->j:Landroid/net/Uri;

    .line 395
    invoke-virtual {v4}, Ljava/util/ArrayList;->size()I

    move-result v2

    new-array v2, v2, [Landroid/content/ContentValues;

    invoke-virtual {v4, v2}, Ljava/util/ArrayList;->toArray([Ljava/lang/Object;)[Ljava/lang/Object;

    move-result-object v2

    check-cast v2, [Landroid/content/ContentValues;

    .line 394
    invoke-virtual {v0, v1, v2}, Landroid/content/ContentResolver;->bulkInsert(Landroid/net/Uri;[Landroid/content/ContentValues;)I
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 399
    :goto_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->p:Landroid/content/SharedPreferences;

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->clear()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    return-void

    :catchall_0
    move-exception v0

    goto :goto_2

    :catch_0
    move-exception v0

    :try_start_1
    const-string v1, "ActionsController"

    const-string v2, "write impression logs"

    .line 397
    invoke-static {v1, v2, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    goto :goto_1

    .line 399
    :goto_2
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->p:Landroid/content/SharedPreferences;

    invoke-interface {v1}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v1

    invoke-interface {v1}, Landroid/content/SharedPreferences$Editor;->clear()Landroid/content/SharedPreferences$Editor;

    move-result-object v1

    invoke-interface {v1}, Landroid/content/SharedPreferences$Editor;->apply()V

    throw v0
.end method

.method public static get(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;
    .locals 1

    .line 114
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertUIThread()V

    .line 115
    sget-object v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->m:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;

    if-nez v0, :cond_0

    .line 116
    new-instance v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;

    invoke-virtual/range {p0 .. p0}, Landroid/content/Context;->getApplicationContext()Landroid/content/Context;

    move-result-object p0

    invoke-direct {v0, p0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;-><init>(Landroid/content/Context;)V

    sput-object v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->m:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;

    .line 118
    :cond_0
    sget-object p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->m:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;

    return-object p0
.end method

.method public static synthetic lambda$l_tTKsBoaYeLbkSChO-IgfkLKcU(Lcom/google/android/apps/nexuslauncher/allapps/Action;Lcom/google/android/apps/nexuslauncher/allapps/Action;)I
    .locals 0

    invoke-static/range {p0 .. p1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Lcom/google/android/apps/nexuslauncher/allapps/Action;Lcom/google/android/apps/nexuslauncher/allapps/Action;)I

    move-result p0

    return p0
.end method


# virtual methods
.method public getActions()Ljava/util/ArrayList;
    .locals 1
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/ArrayList<",
            "Lcom/google/android/apps/nexuslauncher/allapps/Action;",
            ">;"
        }
    .end annotation

    .line 417
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->r:Ljava/util/ArrayList;

    return-object v0
.end method

.method public getLogger()Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;
    .locals 1

    .line 457
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->u:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;

    return-object v0
.end method

.method public handleMessage(Landroid/os/Message;)Z
    .locals 3

    .line 242
    iget v0, p1, Landroid/os/Message;->what:I

    packed-switch v0, :pswitch_data_0

    goto/16 :goto_0

    .line 260
    :pswitch_0
    iget-object p1, p1, Landroid/os/Message;->obj:Ljava/lang/Object;

    check-cast p1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback$PackageInfo;

    invoke-virtual {p0, p1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->updateActionsOnPackageChange(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback$PackageInfo;)V

    goto/16 :goto_0

    .line 252
    :pswitch_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->r:Ljava/util/ArrayList;

    invoke-virtual {v0}, Ljava/util/ArrayList;->clear()V

    .line 253
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->r:Ljava/util/ArrayList;

    iget-object p1, p1, Landroid/os/Message;->obj:Ljava/lang/Object;

    check-cast p1, Ljava/util/ArrayList;

    invoke-virtual {v0, p1}, Ljava/util/ArrayList;->addAll(Ljava/util/Collection;)Z

    .line 254
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->w:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$UpdateListener;

    if-eqz p1, :cond_0

    .line 255
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->w:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$UpdateListener;

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->r:Ljava/util/ArrayList;

    invoke-interface {p1, v0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$UpdateListener;->onUpdated(Ljava/util/ArrayList;)V

    goto :goto_0

    .line 272
    :pswitch_2
    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->f()V

    goto :goto_0

    .line 268
    :pswitch_3
    iget-object p1, p1, Landroid/os/Message;->obj:Ljava/lang/Object;

    check-cast p1, Ljava/lang/Boolean;

    invoke-virtual {p1}, Ljava/lang/Boolean;->booleanValue()Z

    move-result p1

    new-instance v0, Landroid/content/ContentValues;

    invoke-direct {v0}, Landroid/content/ContentValues;-><init>()V

    const-string v1, "enable_action_suggest"

    invoke-static {p1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object p1

    invoke-virtual {v0, v1, p1}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/Boolean;)V

    :try_start_0
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->n:Landroid/content/Context;

    invoke-virtual {p1}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object p1

    sget-object v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->k:Landroid/net/Uri;

    invoke-virtual {p1, v1, v0}, Landroid/content/ContentResolver;->insert(Landroid/net/Uri;Landroid/content/ContentValues;)Landroid/net/Uri;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception p1

    const-string v0, "ActionsController"

    const-string v1, "write setting failed"

    invoke-static {v0, v1, p1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    goto :goto_0

    .line 244
    :pswitch_4
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->c:Landroid/os/Handler;

    const/4 v0, 0x4

    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->e()Ljava/util/ArrayList;

    move-result-object v1

    invoke-static {v1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Ljava/util/ArrayList;)Ljava/util/ArrayList;

    move-result-object v1

    const/4 v2, 0x0

    invoke-static {p1, v0, v2, v2, v1}, Landroid/os/Message;->obtain(Landroid/os/Handler;IIILjava/lang/Object;)Landroid/os/Message;

    move-result-object p1

    .line 245
    invoke-virtual {p1}, Landroid/os/Message;->sendToTarget()V

    goto :goto_0

    .line 264
    :pswitch_5
    iget-object p1, p1, Landroid/os/Message;->obj:Ljava/lang/Object;

    check-cast p1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;

    :try_start_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->n:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    sget-object v1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->j:Landroid/net/Uri;

    invoke-direct {p0, p1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;)Landroid/content/ContentValues;

    move-result-object p1

    invoke-virtual {v0, v1, p1}, Landroid/content/ContentResolver;->insert(Landroid/net/Uri;Landroid/content/ContentValues;)Landroid/net/Uri;
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    goto :goto_0

    :catch_1
    move-exception p1

    const-string v0, "ActionsController"

    const-string v1, "write log failed"

    invoke-static {v0, v1, p1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    :cond_0
    :goto_0
    const/4 p1, 0x1

    return p1

    nop

    :pswitch_data_0
    .packed-switch 0x0
        :pswitch_5
        :pswitch_4
        :pswitch_3
        :pswitch_2
        :pswitch_1
        :pswitch_0
    .end packed-switch
.end method

.method public onActionDismissed(Lcom/google/android/apps/nexuslauncher/allapps/Action;)V
    .locals 1

    if-nez p1, :cond_0

    return-void

    .line 479
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->u:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/allapps/Action;->id:Ljava/lang/String;

    invoke-virtual {v0, p1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;->logDismiss(Ljava/lang/String;)V

    return-void
.end method

.method public onSharedPreferenceChanged(Landroid/content/SharedPreferences;Ljava/lang/String;)V
    .locals 0

    const-string p1, "pref_show_suggested_actions"

    .line 183
    invoke-virtual {p1, p2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_0

    .line 184
    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->b()V

    .line 185
    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->d()V

    :cond_0
    return-void
.end method

.method public setListener(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$UpdateListener;)V
    .locals 0

    .line 453
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->w:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$UpdateListener;

    return-void
.end method

.method public updateActionsOnPackageChange(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback$PackageInfo;)V
    .locals 6

    .line 216
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->q:Ljava/util/ArrayList;

    invoke-virtual {v0}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object v0

    const/4 v1, 0x0

    const/4 v2, 0x0

    .line 217
    :cond_0
    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v3

    if-eqz v3, :cond_1

    .line 218
    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Lcom/google/android/apps/nexuslauncher/allapps/Action;

    .line 219
    iget-object v4, v3, Lcom/google/android/apps/nexuslauncher/allapps/Action;->shortcut:Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;

    if-eqz v4, :cond_0

    iget-object v4, v3, Lcom/google/android/apps/nexuslauncher/allapps/Action;->shortcutInfo:Lcom/android/launcher3/ShortcutInfo;

    if-eqz v4, :cond_0

    iget-object v4, v3, Lcom/google/android/apps/nexuslauncher/allapps/Action;->badgePackage:Ljava/lang/String;

    iget-object v5, p1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback$PackageInfo;->packageName:Ljava/lang/String;

    .line 220
    invoke-virtual {v4, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v4

    if-eqz v4, :cond_0

    iget-object v4, v3, Lcom/google/android/apps/nexuslauncher/allapps/Action;->shortcut:Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;

    invoke-virtual {v4}, Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;->getUserHandle()Landroid/os/UserHandle;

    move-result-object v4

    iget-object v5, p1, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$PackageChangeCallback$PackageInfo;->user:Landroid/os/UserHandle;

    invoke-virtual {v4, v5}, Landroid/os/UserHandle;->equals(Ljava/lang/Object;)Z

    move-result v4

    if-eqz v4, :cond_0

    .line 221
    invoke-direct {p0, v3}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Lcom/google/android/apps/nexuslauncher/allapps/Action;)V

    const/4 v2, 0x1

    goto :goto_0

    :cond_1
    if-eqz v2, :cond_2

    .line 227
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->c:Landroid/os/Handler;

    const/4 v0, 0x4

    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->q:Ljava/util/ArrayList;

    invoke-static {v2}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->a(Ljava/util/ArrayList;)Ljava/util/ArrayList;

    move-result-object v2

    invoke-static {p1, v0, v1, v1, v2}, Landroid/os/Message;->obtain(Landroid/os/Handler;IIILjava/lang/Object;)Landroid/os/Message;

    move-result-object p1

    .line 228
    invoke-virtual {p1}, Landroid/os/Message;->sendToTarget()V

    :cond_2
    return-void
.end method
