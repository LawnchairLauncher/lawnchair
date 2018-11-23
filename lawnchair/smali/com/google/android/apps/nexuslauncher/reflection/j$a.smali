.class Lcom/google/android/apps/nexuslauncher/reflection/j$a;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Ljava/lang/Runnable;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/j;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x2
    name = "a"
.end annotation


# instance fields
.field private aR:Lcom/google/android/apps/nexuslauncher/reflection/g;

.field final synthetic aS:Lcom/google/android/apps/nexuslauncher/reflection/j;


# direct methods
.method private constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/j;)V
    .locals 0

    .line 179
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method synthetic constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/j;B)V
    .locals 0

    .line 179
    invoke-direct/range {p0 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/j$a;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/j;)V

    return-void
.end method

.method private m()Lcom/google/android/apps/nexuslauncher/reflection/g;
    .locals 12

    .line 271
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    .line 275
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    monitor-enter v0

    .line 277
    :try_start_0
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    invoke-static {v1}, Lcom/google/android/apps/nexuslauncher/reflection/j;->c(Lcom/google/android/apps/nexuslauncher/reflection/j;)Landroid/content/SharedPreferences;

    move-result-object v1

    const-string v2, "staged_batch_training_progress"

    const-string v3, "Success"

    invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    const-string v2, "Success"

    .line 278
    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    const/4 v3, 0x0

    if-eqz v2, :cond_0

    .line 282
    monitor-exit v0

    return-object v3

    .line 286
    :cond_0
    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/g;

    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    invoke-static {v4}, Lcom/google/android/apps/nexuslauncher/reflection/j;->d(Lcom/google/android/apps/nexuslauncher/reflection/j;)Landroid/content/Context;

    move-result-object v5

    const/4 v6, 0x0

    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    invoke-static {v4}, Lcom/google/android/apps/nexuslauncher/reflection/j;->b(Lcom/google/android/apps/nexuslauncher/reflection/j;)Lcom/google/android/apps/nexuslauncher/reflection/d/c;

    move-result-object v7

    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    .line 287
    invoke-static {v4}, Lcom/google/android/apps/nexuslauncher/reflection/j;->c(Lcom/google/android/apps/nexuslauncher/reflection/j;)Landroid/content/SharedPreferences;

    move-result-object v8

    const-string v9, "background_evt_buf.properties"

    const/4 v10, 0x0

    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    .line 288
    invoke-static {v4}, Lcom/google/android/apps/nexuslauncher/reflection/j;->e(Lcom/google/android/apps/nexuslauncher/reflection/j;)Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;

    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    .line 289
    invoke-static {v4}, Lcom/google/android/apps/nexuslauncher/reflection/j;->f(Lcom/google/android/apps/nexuslauncher/reflection/j;)Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    move-result-object v11

    move-object v4, v2

    invoke-direct/range {v4 .. v11}, Lcom/google/android/apps/nexuslauncher/reflection/g;-><init>(Landroid/content/Context;Lcom/google/android/apps/nexuslauncher/reflection/d/c;Lcom/google/research/reflection/a/c;Landroid/content/SharedPreferences;Ljava/lang/String;Ljava/lang/Runnable;Lcom/google/android/apps/nexuslauncher/reflection/b/b;)V

    iput-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aR:Lcom/google/android/apps/nexuslauncher/reflection/g;

    .line 290
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aR:Lcom/google/android/apps/nexuslauncher/reflection/g;

    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    invoke-static {v4}, Lcom/google/android/apps/nexuslauncher/reflection/j;->g(Lcom/google/android/apps/nexuslauncher/reflection/j;)Ljava/io/File;

    move-result-object v4

    invoke-virtual {v2, v4}, Lcom/google/android/apps/nexuslauncher/reflection/g;->a(Ljava/io/File;)V

    const-string v2, "New"

    .line 291
    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v2

    const/4 v4, 0x1

    if-nez v2, :cond_2

    .line 292
    invoke-static {}, Lcom/google/android/apps/nexuslauncher/reflection/j;->l()Ljava/util/regex/Pattern;

    move-result-object v2

    invoke-virtual {v2, v1}, Ljava/util/regex/Pattern;->matcher(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;

    move-result-object v1

    .line 293
    invoke-virtual {v1}, Ljava/util/regex/Matcher;->find()Z

    move-result v2

    if-nez v2, :cond_1

    const-string v1, "Reflection.StBatchTrain"

    const-string v2, "Invalid progress string."

    .line 294
    invoke-static {v1, v2}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    .line 295
    monitor-exit v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_4

    return-object v3

    .line 299
    :cond_1
    :try_start_1
    invoke-virtual {v1, v4}, Ljava/util/regex/Matcher;->group(I)Ljava/lang/String;

    move-result-object v1

    invoke-static {v1}, Ljava/lang/Long;->parseLong(Ljava/lang/String;)J

    move-result-wide v1
    :try_end_1
    .catch Ljava/lang/NumberFormatException; {:try_start_1 .. :try_end_1} :catch_0
    .catchall {:try_start_1 .. :try_end_1} :catchall_4

    .line 305
    :try_start_2
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aR:Lcom/google/android/apps/nexuslauncher/reflection/g;

    invoke-virtual {v3}, Lcom/google/android/apps/nexuslauncher/reflection/g;->i()Z

    goto :goto_0

    :catch_0
    move-exception v1

    const-string v2, "Reflection.StBatchTrain"

    const-string v4, "Invalid progress string."

    .line 301
    invoke-static {v2, v4, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I

    .line 302
    monitor-exit v0

    return-object v3

    :cond_2
    const-wide/16 v1, 0x0

    .line 307
    :goto_0
    monitor-exit v0
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_4

    .line 310
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v5

    const-string v0, "Reflection.StBatchTrain"

    const-string v3, "Start loading events from logs..."

    invoke-static {v0, v3}, Lcom/android/launcher3/logging/FileLog;->d(Ljava/lang/String;Ljava/lang/String;)V

    :goto_1
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    monitor-enter v3

    :try_start_3
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    invoke-static {v0}, Lcom/google/android/apps/nexuslauncher/reflection/j;->a(Lcom/google/android/apps/nexuslauncher/reflection/j;)Lcom/google/android/apps/nexuslauncher/reflection/j$a;

    move-result-object v0

    if-eq v0, p0, :cond_3

    monitor-exit v3

    goto/16 :goto_3

    :cond_3
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    invoke-static {v0}, Lcom/google/android/apps/nexuslauncher/reflection/j;->b(Lcom/google/android/apps/nexuslauncher/reflection/j;)Lcom/google/android/apps/nexuslauncher/reflection/d/c;

    move-result-object v0

    const/16 v7, 0x3e8

    invoke-virtual {v0, v1, v2, v7}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->a(JI)Lcom/google/android/apps/nexuslauncher/reflection/d/c$a;

    move-result-object v0

    monitor-exit v3
    :try_end_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_3

    const/4 v1, 0x0

    if-eqz v0, :cond_8

    iget-object v2, v0, Lcom/google/android/apps/nexuslauncher/reflection/d/c$a;->bN:Ljava/util/List;

    invoke-interface {v2}, Ljava/util/List;->isEmpty()Z

    move-result v2

    if-nez v2, :cond_8

    iget-object v2, v0, Lcom/google/android/apps/nexuslauncher/reflection/d/c$a;->bN:Ljava/util/List;

    const-string v3, "Reflection.StBatchTrain"

    const-string v7, "Num events loaded: %d, time taken so far: %dms"

    const/4 v8, 0x2

    new-array v8, v8, [Ljava/lang/Object;

    invoke-interface {v2}, Ljava/util/List;->size()I

    move-result v9

    invoke-static {v9}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v9

    aput-object v9, v8, v1

    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v9

    sub-long/2addr v9, v5

    invoke-static {v9, v10}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object v9

    aput-object v9, v8, v4

    invoke-static {v7, v8}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v7

    invoke-static {v3, v7}, Lcom/android/launcher3/logging/FileLog;->d(Ljava/lang/String;Ljava/lang/String;)V

    invoke-interface {v2}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v2

    :goto_2
    invoke-interface {v2}, Ljava/util/Iterator;->hasNext()Z

    move-result v3

    if-eqz v3, :cond_6

    invoke-interface {v2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    iget-object v7, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    monitor-enter v7

    :try_start_4
    iget-object v8, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    invoke-static {v8}, Lcom/google/android/apps/nexuslauncher/reflection/j;->a(Lcom/google/android/apps/nexuslauncher/reflection/j;)Lcom/google/android/apps/nexuslauncher/reflection/j$a;

    move-result-object v8

    if-ne v8, p0, :cond_5

    iget-object v8, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aR:Lcom/google/android/apps/nexuslauncher/reflection/g;

    invoke-virtual {v8, v3}, Lcom/google/android/apps/nexuslauncher/reflection/g;->c(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)V

    iget-object v8, v3, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object v8, v8, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    const-string v9, "/deleted_app/"

    invoke-virtual {v8, v9}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v8

    if-nez v8, :cond_4

    iget-object v8, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aR:Lcom/google/android/apps/nexuslauncher/reflection/g;

    iget-object v8, v8, Lcom/google/android/apps/nexuslauncher/reflection/g;->al:Lcom/google/research/reflection/c/b;

    if-eqz v8, :cond_4

    invoke-virtual {v8, v3}, Lcom/google/research/reflection/c/b;->c(Lcom/google/research/reflection/signal/ReflectionEvent;)V

    :cond_4
    monitor-exit v7

    goto :goto_2

    :cond_5
    monitor-exit v7

    goto :goto_3

    :catchall_0
    move-exception v0

    monitor-exit v7
    :try_end_4
    .catchall {:try_start_4 .. :try_end_4} :catchall_0

    throw v0

    :cond_6
    iget-wide v2, v0, Lcom/google/android/apps/nexuslauncher/reflection/d/c$a;->bM:J

    sget-object v0, Ljava/util/Locale;->US:Ljava/util/Locale;

    const-string v7, "InProgress:%d"

    new-array v8, v4, [Ljava/lang/Object;

    invoke-static {v2, v3}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object v9

    aput-object v9, v8, v1

    invoke-static {v0, v7, v8}, Ljava/lang/String;->format(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v0

    const-string v7, "Reflection.StBatchTrain"

    const-string v8, "Progress: %s"

    new-array v9, v4, [Ljava/lang/Object;

    aput-object v0, v9, v1

    invoke-static {v8, v9}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v1

    invoke-static {v7, v1}, Lcom/android/launcher3/logging/FileLog;->d(Ljava/lang/String;Ljava/lang/String;)V

    iget-object v7, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    monitor-enter v7

    :try_start_5
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    invoke-static {v1}, Lcom/google/android/apps/nexuslauncher/reflection/j;->a(Lcom/google/android/apps/nexuslauncher/reflection/j;)Lcom/google/android/apps/nexuslauncher/reflection/j$a;

    move-result-object v1

    if-ne v1, p0, :cond_7

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    invoke-static {v1}, Lcom/google/android/apps/nexuslauncher/reflection/j;->c(Lcom/google/android/apps/nexuslauncher/reflection/j;)Landroid/content/SharedPreferences;

    move-result-object v1

    invoke-interface {v1}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v1

    const-string v8, "staged_batch_training_progress"

    invoke-interface {v1, v8, v0}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aR:Lcom/google/android/apps/nexuslauncher/reflection/g;

    invoke-virtual {v0}, Lcom/google/android/apps/nexuslauncher/reflection/g;->j()Z

    monitor-exit v7

    move-wide v1, v2

    goto/16 :goto_1

    :cond_7
    monitor-exit v7

    goto :goto_3

    :catchall_1
    move-exception v0

    monitor-exit v7
    :try_end_5
    .catchall {:try_start_5 .. :try_end_5} :catchall_1

    throw v0

    :cond_8
    const-string v0, "Reflection.StBatchTrain"

    const-string v2, "Retrain finished, total time including loading: %dms"

    new-array v3, v4, [Ljava/lang/Object;

    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v7

    sub-long/2addr v7, v5

    invoke-static {v7, v8}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object v4

    aput-object v4, v3, v1

    invoke-static {v2, v3}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Lcom/android/launcher3/logging/FileLog;->d(Ljava/lang/String;Ljava/lang/String;)V

    .line 312
    :goto_3
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    monitor-enter v0

    .line 313
    :try_start_6
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    invoke-static {v1}, Lcom/google/android/apps/nexuslauncher/reflection/j;->a(Lcom/google/android/apps/nexuslauncher/reflection/j;)Lcom/google/android/apps/nexuslauncher/reflection/j$a;

    move-result-object v1

    if-ne v1, p0, :cond_9

    .line 314
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    invoke-static {v1}, Lcom/google/android/apps/nexuslauncher/reflection/j;->c(Lcom/google/android/apps/nexuslauncher/reflection/j;)Landroid/content/SharedPreferences;

    move-result-object v1

    invoke-interface {v1}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v1

    const-string v2, "staged_batch_training_progress"

    const-string v3, "Success"

    invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object v1

    invoke-interface {v1}, Landroid/content/SharedPreferences$Editor;->apply()V

    .line 316
    :cond_9
    monitor-exit v0
    :try_end_6
    .catchall {:try_start_6 .. :try_end_6} :catchall_2

    .line 317
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aR:Lcom/google/android/apps/nexuslauncher/reflection/g;

    return-object v0

    :catchall_2
    move-exception v1

    .line 316
    :try_start_7
    monitor-exit v0
    :try_end_7
    .catchall {:try_start_7 .. :try_end_7} :catchall_2

    throw v1

    :catchall_3
    move-exception v0

    .line 310
    :try_start_8
    monitor-exit v3
    :try_end_8
    .catchall {:try_start_8 .. :try_end_8} :catchall_3

    throw v0

    :catchall_4
    move-exception v1

    .line 307
    :try_start_9
    monitor-exit v0
    :try_end_9
    .catchall {:try_start_9 .. :try_end_9} :catchall_4

    throw v1
.end method


# virtual methods
.method public run()V
    .locals 2

    .line 186
    :try_start_0
    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->m()Lcom/google/android/apps/nexuslauncher/reflection/g;

    move-result-object v0

    .line 187
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    invoke-static {v1, v0, p0}, Lcom/google/android/apps/nexuslauncher/reflection/j;->a(Lcom/google/android/apps/nexuslauncher/reflection/j;Lcom/google/android/apps/nexuslauncher/reflection/g;Lcom/google/android/apps/nexuslauncher/reflection/j$a;)V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    .line 189
    :catch_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/j$a;->aS:Lcom/google/android/apps/nexuslauncher/reflection/j;

    invoke-static {v0, p0}, Lcom/google/android/apps/nexuslauncher/reflection/j;->a(Lcom/google/android/apps/nexuslauncher/reflection/j;Lcom/google/android/apps/nexuslauncher/reflection/j$a;)V

    return-void
.end method
