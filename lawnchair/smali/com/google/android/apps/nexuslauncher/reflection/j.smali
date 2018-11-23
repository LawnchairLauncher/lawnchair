.class public Lcom/google/android/apps/nexuslauncher/reflection/j;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/android/apps/nexuslauncher/reflection/j$a;
    }
.end annotation


# static fields
.field static final PREF_KEY_BACKGROUND_MODEL_VERSION:Ljava/lang/String; = "background_model_version"

.field static final PREF_KEY_PROGRESS:Ljava/lang/String; = "staged_batch_training_progress"

.field private static final aK:Ljava/util/regex/Pattern;


# instance fields
.field private final aL:Lcom/google/android/apps/nexuslauncher/reflection/d/c;

.field private final aM:Landroid/content/SharedPreferences;

.field private final aN:Lcom/google/android/apps/nexuslauncher/reflection/g;

.field private final aO:Ljava/io/File;

.field private final aP:Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;

.field private aQ:Lcom/google/android/apps/nexuslauncher/reflection/j$a;

.field private final ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

.field private final mContext:Landroid/content/Context;


# direct methods
.method static constructor <clinit>()V
    .locals 1

    const-string v0, "^InProgress:(.+)$"

    .line 64
    invoke-static {v0}, Ljava/util/regex/Pattern;->compile(Ljava/lang/String;)Ljava/util/regex/Pattern;

    move-result-object v0

    sput-object v0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aK:Ljava/util/regex/Pattern;

    return-void
.end method

.method public constructor <init>(Landroid/content/Context;Lcom/google/android/apps/nexuslauncher/reflection/d/c;Landroid/content/SharedPreferences;Ljava/io/File;Lcom/google/android/apps/nexuslauncher/reflection/g;Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;Lcom/google/android/apps/nexuslauncher/reflection/b/b;)V
    .locals 0

    .line 100
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 101
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->mContext:Landroid/content/Context;

    .line 102
    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aL:Lcom/google/android/apps/nexuslauncher/reflection/d/c;

    .line 103
    iput-object p3, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aM:Landroid/content/SharedPreferences;

    .line 104
    iput-object p4, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aO:Ljava/io/File;

    .line 105
    iput-object p5, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aN:Lcom/google/android/apps/nexuslauncher/reflection/g;

    const/4 p1, 0x0

    .line 106
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aQ:Lcom/google/android/apps/nexuslauncher/reflection/j$a;

    .line 107
    iput-object p7, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    .line 108
    iput-object p6, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aP:Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;

    .line 109
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aP:Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;

    invoke-virtual {p1}, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->update()V

    return-void
.end method

.method static synthetic a(Lcom/google/android/apps/nexuslauncher/reflection/j;)Lcom/google/android/apps/nexuslauncher/reflection/j$a;
    .locals 0

    .line 36
    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aQ:Lcom/google/android/apps/nexuslauncher/reflection/j$a;

    return-object p0
.end method

.method private declared-synchronized a(Lcom/google/android/apps/nexuslauncher/reflection/g;Lcom/google/android/apps/nexuslauncher/reflection/j$a;)V
    .locals 1

    monitor-enter p0

    .line 150
    :try_start_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aQ:Lcom/google/android/apps/nexuslauncher/reflection/j$a;

    if-ne v0, p2, :cond_0

    .line 152
    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aN:Lcom/google/android/apps/nexuslauncher/reflection/g;

    invoke-virtual {p2, p1}, Lcom/google/android/apps/nexuslauncher/reflection/g;->a(Lcom/google/android/apps/nexuslauncher/reflection/g;)V

    .line 153
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aN:Lcom/google/android/apps/nexuslauncher/reflection/g;

    invoke-virtual {p1}, Lcom/google/android/apps/nexuslauncher/reflection/g;->j()Z

    .line 154
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aO:Ljava/io/File;

    invoke-virtual {p1}, Ljava/io/File;->delete()Z

    const/4 p1, 0x0

    .line 156
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aQ:Lcom/google/android/apps/nexuslauncher/reflection/j$a;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 158
    :cond_0
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 149
    monitor-exit p0

    throw p1
.end method

.method private declared-synchronized a(Lcom/google/android/apps/nexuslauncher/reflection/j$a;)V
    .locals 1

    monitor-enter p0

    .line 164
    :try_start_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aQ:Lcom/google/android/apps/nexuslauncher/reflection/j$a;

    if-ne v0, p1, :cond_0

    .line 165
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aM:Landroid/content/SharedPreferences;

    .line 166
    invoke-interface {p1}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object p1

    const-string v0, "background_model_version"

    .line 167
    invoke-interface {p1, v0}, Landroid/content/SharedPreferences$Editor;->remove(Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object p1

    const-string v0, "staged_batch_training_progress"

    .line 168
    invoke-interface {p1, v0}, Landroid/content/SharedPreferences$Editor;->remove(Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object p1

    .line 169
    invoke-interface {p1}, Landroid/content/SharedPreferences$Editor;->apply()V

    .line 170
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aO:Ljava/io/File;

    invoke-virtual {p1}, Ljava/io/File;->delete()Z

    const/4 p1, 0x0

    .line 172
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aQ:Lcom/google/android/apps/nexuslauncher/reflection/j$a;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 174
    :cond_0
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 163
    monitor-exit p0

    throw p1
.end method

.method static synthetic a(Lcom/google/android/apps/nexuslauncher/reflection/j;Lcom/google/android/apps/nexuslauncher/reflection/g;Lcom/google/android/apps/nexuslauncher/reflection/j$a;)V
    .locals 0

    .line 36
    invoke-direct/range {p0 .. p2}, Lcom/google/android/apps/nexuslauncher/reflection/j;->a(Lcom/google/android/apps/nexuslauncher/reflection/g;Lcom/google/android/apps/nexuslauncher/reflection/j$a;)V

    return-void
.end method

.method static synthetic a(Lcom/google/android/apps/nexuslauncher/reflection/j;Lcom/google/android/apps/nexuslauncher/reflection/j$a;)V
    .locals 0

    .line 36
    invoke-direct/range {p0 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/j;->a(Lcom/google/android/apps/nexuslauncher/reflection/j$a;)V

    return-void
.end method

.method static synthetic b(Lcom/google/android/apps/nexuslauncher/reflection/j;)Lcom/google/android/apps/nexuslauncher/reflection/d/c;
    .locals 0

    .line 36
    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aL:Lcom/google/android/apps/nexuslauncher/reflection/d/c;

    return-object p0
.end method

.method static synthetic c(Lcom/google/android/apps/nexuslauncher/reflection/j;)Landroid/content/SharedPreferences;
    .locals 0

    .line 36
    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aM:Landroid/content/SharedPreferences;

    return-object p0
.end method

.method static synthetic d(Lcom/google/android/apps/nexuslauncher/reflection/j;)Landroid/content/Context;
    .locals 0

    .line 36
    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->mContext:Landroid/content/Context;

    return-object p0
.end method

.method static synthetic e(Lcom/google/android/apps/nexuslauncher/reflection/j;)Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;
    .locals 0

    .line 36
    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aP:Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;

    return-object p0
.end method

.method static synthetic f(Lcom/google/android/apps/nexuslauncher/reflection/j;)Lcom/google/android/apps/nexuslauncher/reflection/b/b;
    .locals 0

    .line 36
    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    return-object p0
.end method

.method static synthetic g(Lcom/google/android/apps/nexuslauncher/reflection/j;)Ljava/io/File;
    .locals 0

    .line 36
    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aO:Ljava/io/File;

    return-object p0
.end method

.method static synthetic l()Ljava/util/regex/Pattern;
    .locals 1

    .line 36
    sget-object v0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aK:Ljava/util/regex/Pattern;

    return-object v0
.end method


# virtual methods
.method public final declared-synchronized b(Z)V
    .locals 2

    monitor-enter p0

    if-eqz p1, :cond_0

    .line 129
    :try_start_0
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aM:Landroid/content/SharedPreferences;

    .line 130
    invoke-interface {p1}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object p1

    const-string v0, "staged_batch_training_progress"

    const-string v1, "New"

    .line 131
    invoke-interface {p1, v0, v1}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object p1

    const-string v0, "background_model_version"

    const/16 v1, 0x2a

    .line 132
    invoke-interface {p1, v0, v1}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    move-result-object p1

    .line 133
    invoke-interface {p1}, Landroid/content/SharedPreferences$Editor;->apply()V

    .line 134
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aO:Ljava/io/File;

    invoke-virtual {p1}, Ljava/io/File;->delete()Z

    goto :goto_0

    :catchall_0
    move-exception p1

    goto :goto_1

    .line 135
    :cond_0
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aQ:Lcom/google/android/apps/nexuslauncher/reflection/j$a;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    if-eqz p1, :cond_1

    .line 136
    monitor-exit p0

    return-void

    .line 140
    :cond_1
    :goto_0
    :try_start_1
    new-instance p1, Lcom/google/android/apps/nexuslauncher/reflection/j$a;

    const/4 v0, 0x0

    invoke-direct {p1, p0, v0}, Lcom/google/android/apps/nexuslauncher/reflection/j$a;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/j;B)V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aQ:Lcom/google/android/apps/nexuslauncher/reflection/j$a;

    .line 143
    sget-object p1, Lcom/android/launcher3/Utilities;->THREAD_POOL_EXECUTOR:Ljava/util/concurrent/Executor;

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aQ:Lcom/google/android/apps/nexuslauncher/reflection/j$a;

    invoke-interface {p1, v0}, Ljava/util/concurrent/Executor;->execute(Ljava/lang/Runnable;)V
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 144
    monitor-exit p0

    return-void

    .line 127
    :goto_1
    monitor-exit p0

    throw p1
.end method

.method public final declared-synchronized isInProgress()Z
    .locals 3

    monitor-enter p0

    .line 113
    :try_start_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aM:Landroid/content/SharedPreferences;

    const-string v1, "staged_batch_training_progress"

    const/4 v2, 0x0

    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    if-eqz v0, :cond_0

    .line 114
    sget-object v1, Lcom/google/android/apps/nexuslauncher/reflection/j;->aK:Ljava/util/regex/Pattern;

    invoke-virtual {v1, v0}, Ljava/util/regex/Pattern;->matcher(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;

    move-result-object v0

    invoke-virtual {v0}, Ljava/util/regex/Matcher;->find()Z

    move-result v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    if-eqz v0, :cond_0

    const/4 v0, 0x1

    :goto_0
    monitor-exit p0

    return v0

    :cond_0
    const/4 v0, 0x0

    goto :goto_0

    :catchall_0
    move-exception v0

    .line 112
    monitor-exit p0

    throw v0
.end method

.method public final declared-synchronized k()I
    .locals 3

    monitor-enter p0

    .line 118
    :try_start_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j;->aM:Landroid/content/SharedPreferences;

    const-string v1, "background_model_version"

    const/4 v2, 0x0

    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getInt(Ljava/lang/String;I)I

    move-result v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    monitor-exit p0

    return v0

    :catchall_0
    move-exception v0

    monitor-exit p0

    throw v0
.end method
