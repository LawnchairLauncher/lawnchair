.class public Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter$IntentParser;
    }
.end annotation


# instance fields
.field private final X:Landroid/content/ContentResolver;

.field private final Y:Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter$IntentParser;

.field private final Z:Landroid/content/SharedPreferences;

.field private final aa:Lcom/google/android/apps/nexuslauncher/reflection/d/d;

.field private ab:Ljava/util/Set;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Set<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation
.end field

.field private final mContext:Landroid/content/Context;


# direct methods
.method public constructor <init>(Landroid/content/ContentResolver;Landroid/content/SharedPreferences;Lcom/google/android/apps/nexuslauncher/reflection/d/d;Landroid/content/Context;)V
    .locals 0

    .line 65
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 66
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->X:Landroid/content/ContentResolver;

    .line 67
    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->Z:Landroid/content/SharedPreferences;

    .line 68
    iput-object p3, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->aa:Lcom/google/android/apps/nexuslauncher/reflection/d/d;

    .line 69
    iput-object p4, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->mContext:Landroid/content/Context;

    .line 71
    new-instance p1, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter$IntentParser;

    invoke-direct {p1}, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter$IntentParser;-><init>()V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->Y:Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter$IntentParser;

    .line 72
    new-instance p1, Ljava/util/HashSet;

    invoke-direct {p1}, Ljava/util/HashSet;-><init>()V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->ab:Ljava/util/Set;

    .line 73
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->Z:Landroid/content/SharedPreferences;

    if-eqz p1, :cond_0

    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->Z:Landroid/content/SharedPreferences;

    const-string p2, "model_subtracted_events"

    const/4 p3, 0x0

    invoke-interface {p1, p2, p3}, Landroid/content/SharedPreferences;->getStringSet(Ljava/lang/String;Ljava/util/Set;)Ljava/util/Set;

    move-result-object p1

    if-eqz p1, :cond_0

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->ab:Ljava/util/Set;

    :cond_0
    return-void
.end method


# virtual methods
.method public final declared-synchronized update()V
    .locals 13

    monitor-enter p0

    .line 128
    :try_start_0
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    .line 129
    new-instance v0, Ljava/util/HashSet;

    invoke-direct {v0}, Ljava/util/HashSet;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->ab:Ljava/util/Set;

    .line 130
    sget-object v0, Ljava/util/Locale;->ENGLISH:Ljava/util/Locale;

    const-string v1, "(SELECT %s from %s ORDER BY %s ASC LIMIT 1)"

    const/4 v2, 0x3

    new-array v3, v2, [Ljava/lang/Object;

    const-string v4, "_id"

    const/4 v5, 0x0

    aput-object v4, v3, v5

    const-string v4, "workspaceScreens"

    const/4 v6, 0x1

    aput-object v4, v3, v6

    const-string v4, "screenRank"

    const/4 v7, 0x2

    aput-object v4, v3, v7

    .line 131
    invoke-static {v0, v1, v3}, Ljava/lang/String;->format(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v0

    .line 138
    sget-object v1, Ljava/util/Locale;->ENGLISH:Ljava/util/Locale;

    const-string v3, "%s = %d AND (%s = %d OR (%s = %d AND %s = %s))"

    const/16 v4, 0x8

    new-array v4, v4, [Ljava/lang/Object;

    const-string v8, "itemType"

    aput-object v8, v4, v5

    .line 143
    invoke-static {v5}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v8

    aput-object v8, v4, v6

    const-string v8, "container"

    aput-object v8, v4, v7

    const/16 v7, -0x65

    .line 145
    invoke-static {v7}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v7

    aput-object v7, v4, v2

    const/4 v2, 0x4

    const-string v7, "container"

    aput-object v7, v4, v2

    const/4 v2, 0x5

    const/16 v7, -0x64

    .line 147
    invoke-static {v7}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v7

    aput-object v7, v4, v2

    const/4 v2, 0x6

    const-string v7, "screen"

    aput-object v7, v4, v2

    const/4 v2, 0x7

    aput-object v0, v4, v2

    .line 139
    invoke-static {v1, v3, v4}, Ljava/lang/String;->format(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v10

    .line 151
    iget-object v7, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->X:Landroid/content/ContentResolver;

    sget-object v8, Lcom/android/launcher3/LauncherSettings$Favorites;->CONTENT_URI:Landroid/net/Uri;

    const-string v0, "intent"

    const-string v1, "profileId"

    filled-new-array {v0, v1}, [Ljava/lang/String;

    move-result-object v9

    const/4 v11, 0x0

    const/4 v12, 0x0

    invoke-virtual/range {v7 .. v12}, Landroid/content/ContentResolver;->query(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;

    move-result-object v0

    .line 157
    invoke-interface {v0}, Landroid/database/Cursor;->moveToFirst()Z

    move-result v1
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    if-eqz v1, :cond_4

    .line 162
    :cond_0
    :try_start_1
    invoke-interface {v0, v5}, Landroid/database/Cursor;->getString(I)Ljava/lang/String;

    move-result-object v1

    .line 163
    invoke-interface {v0, v6}, Landroid/database/Cursor;->getLong(I)J

    move-result-wide v2
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_0
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    if-eqz v1, :cond_3

    .line 169
    :try_start_2
    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->Y:Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter$IntentParser;

    invoke-virtual {v4, v1}, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter$IntentParser;->b(Ljava/lang/String;)Landroid/content/Intent;

    move-result-object v1

    if-eqz v1, :cond_2

    .line 176
    invoke-virtual {v1}, Landroid/content/Intent;->getComponent()Landroid/content/ComponentName;

    move-result-object v4

    if-nez v4, :cond_1

    goto :goto_0

    .line 183
    :cond_1
    invoke-virtual {v1}, Landroid/content/Intent;->getComponent()Landroid/content/ComponentName;

    move-result-object v1

    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->mContext:Landroid/content/Context;

    .line 182
    invoke-static {v1, v2, v3, v4}, Lcom/google/android/apps/nexuslauncher/reflection/a/e;->a(Landroid/content/ComponentName;JLandroid/content/Context;)Ljava/lang/String;

    move-result-object v1

    .line 187
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->ab:Ljava/util/Set;

    invoke-interface {v2, v1}, Ljava/util/Set;->add(Ljava/lang/Object;)Z

    goto :goto_1

    :cond_2
    :goto_0
    const-string v1, "Reflection.1stPFilter"

    const-string v2, "No component retrieved from intent."

    .line 177
    invoke-static {v1, v2}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    goto :goto_1

    :catch_0
    move-exception v1

    const-string v2, "Reflection.1stPFilter"

    const-string v3, "Error in reading intent from cursor"

    .line 165
    invoke-static {v2, v3, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 189
    :cond_3
    :goto_1
    invoke-interface {v0}, Landroid/database/Cursor;->moveToNext()Z

    move-result v1

    if-nez v1, :cond_0

    .line 191
    :cond_4
    invoke-interface {v0}, Landroid/database/Cursor;->close()V
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    .line 192
    monitor-exit p0

    return-void

    :catchall_0
    move-exception v0

    .line 127
    monitor-exit p0

    throw v0
.end method
