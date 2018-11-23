.class public Lcom/google/android/apps/nexuslauncher/reflection/g;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/android/apps/nexuslauncher/reflection/g$a;
    }
.end annotation


# instance fields
.field private final ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

.field final al:Lcom/google/research/reflection/c/b;

.field final am:Lcom/google/android/apps/nexuslauncher/reflection/d/b;

.field private final an:Lcom/google/research/reflection/a/c;

.field private final ao:Ljava/util/Map;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Lcom/google/android/apps/nexuslauncher/reflection/g$a;",
            ">;"
        }
    .end annotation
.end field

.field private final ap:Lcom/google/android/apps/nexuslauncher/reflection/d/c;

.field private final aq:Ljava/lang/Runnable;

.field private final ar:Landroid/content/SharedPreferences;

.field private final as:Ljava/lang/String;

.field private at:Ljava/io/File;

.field private final mContext:Landroid/content/Context;

.field final mEventBuffer:Lcom/google/research/reflection/predictor/b;

.field mRankers:Ljava/util/Map;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Lcom/google/research/reflection/predictor/i;",
            ">;"
        }
    .end annotation
.end field


# direct methods
.method public constructor <init>(Landroid/content/Context;Lcom/google/android/apps/nexuslauncher/reflection/d/c;Lcom/google/research/reflection/a/c;Landroid/content/SharedPreferences;Ljava/lang/String;Ljava/lang/Runnable;Lcom/google/android/apps/nexuslauncher/reflection/b/b;)V
    .locals 1

    .line 106
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 81
    new-instance v0, Ljava/util/HashMap;

    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mRankers:Ljava/util/Map;

    .line 83
    new-instance v0, Ljava/util/HashMap;

    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->ao:Ljava/util/Map;

    const/4 v0, 0x0

    .line 95
    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->at:Ljava/io/File;

    .line 107
    invoke-static/range {p3 .. p3}, Lcom/android/launcher3/util/Preconditions;->assertNotNull(Ljava/lang/Object;)V

    .line 108
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mContext:Landroid/content/Context;

    .line 109
    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->ap:Lcom/google/android/apps/nexuslauncher/reflection/d/c;

    .line 110
    iput-object p3, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->an:Lcom/google/research/reflection/a/c;

    .line 111
    iput-object p4, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->ar:Landroid/content/SharedPreferences;

    .line 112
    iput-object p5, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->as:Ljava/lang/String;

    .line 113
    iput-object p6, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->aq:Ljava/lang/Runnable;

    .line 115
    new-instance p1, Lcom/google/research/reflection/predictor/b;

    invoke-direct {p1}, Lcom/google/research/reflection/predictor/b;-><init>()V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mEventBuffer:Lcom/google/research/reflection/predictor/b;

    .line 116
    new-instance p1, Lcom/google/research/reflection/c/b;

    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mEventBuffer:Lcom/google/research/reflection/predictor/b;

    invoke-direct {p1, p2}, Lcom/google/research/reflection/c/b;-><init>(Lcom/google/research/reflection/predictor/b;)V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->al:Lcom/google/research/reflection/c/b;

    .line 117
    new-instance p1, Lcom/google/android/apps/nexuslauncher/reflection/d/b;

    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->ap:Lcom/google/android/apps/nexuslauncher/reflection/d/c;

    invoke-direct {p1, p2}, Lcom/google/android/apps/nexuslauncher/reflection/d/b;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/d/c;)V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->am:Lcom/google/android/apps/nexuslauncher/reflection/d/b;

    .line 118
    iput-object p7, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    .line 120
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->ao:Ljava/util/Map;

    const-string p2, "OVERVIEW_GEL"

    new-instance p3, Lcom/google/android/apps/nexuslauncher/reflection/-$$Lambda$g$g05WmdZyQsohc3GSlV9BvF6XKw4;

    invoke-direct {p3, p0}, Lcom/google/android/apps/nexuslauncher/reflection/-$$Lambda$g$g05WmdZyQsohc3GSlV9BvF6XKw4;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/g;)V

    invoke-interface {p1, p2, p3}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    .line 122
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->ao:Ljava/util/Map;

    const-string p2, "GEL"

    sget-object p3, Lcom/google/android/apps/nexuslauncher/reflection/-$$Lambda$9p4NvobT-pRxN5zQFIT3MtxNYRA;->INSTANCE:Lcom/google/android/apps/nexuslauncher/reflection/-$$Lambda$9p4NvobT-pRxN5zQFIT3MtxNYRA;

    invoke-interface {p1, p2, p3}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    return-void
.end method

.method private a(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)Z
    .locals 5

    const/4 v0, 0x1

    const/4 v1, 0x0

    if-eqz p1, :cond_1

    .line 127
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->F()Ljava/util/List;

    move-result-object v2

    if-eqz v2, :cond_0

    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->F()Ljava/util/List;

    move-result-object v2

    invoke-interface {v2}, Ljava/util/List;->isEmpty()Z

    move-result v2

    if-eqz v2, :cond_1

    :cond_0
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->C()Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    move-result-object v2

    sget-object v3, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fY:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    if-ne v2, v3, :cond_1

    const/4 v2, 0x1

    goto :goto_0

    :cond_1
    const/4 v2, 0x0

    :goto_0
    if-eqz v2, :cond_2

    return v0

    .line 130
    :cond_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    invoke-static {}, Ljava/util/Collections;->emptySet()Ljava/util/Set;

    move-result-object v2

    invoke-virtual/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->C()Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    move-result-object v3

    sget-object v4, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fY:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    invoke-virtual {v3, v4}, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_3

    invoke-virtual {v0}, Lcom/google/android/apps/nexuslauncher/reflection/b/b;->n()Ljava/util/Set;

    move-result-object v2

    :cond_3
    iget-object v3, p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object v3, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    invoke-virtual {v0, v3, v2}, Lcom/google/android/apps/nexuslauncher/reflection/b/b;->a(Ljava/lang/String;Ljava/util/Set;)Z

    move-result v0

    if-nez v0, :cond_4

    return v1

    :cond_4
    if-eqz p1, :cond_6

    .line 136
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->F()Ljava/util/List;

    move-result-object v0

    if-eqz v0, :cond_6

    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->F()Ljava/util/List;

    move-result-object v0

    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v0

    const/4 v2, 0x2

    if-ge v0, v2, :cond_5

    goto :goto_1

    :cond_5
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->F()Ljava/util/List;

    move-result-object p1

    invoke-interface {p1, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Ljava/lang/String;

    const-string v0, "GEL"

    invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    return p1

    :cond_6
    :goto_1
    return v1
.end method

.method public static synthetic lambda$g05WmdZyQsohc3GSlV9BvF6XKw4(Lcom/google/android/apps/nexuslauncher/reflection/g;Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)Z
    .locals 0

    invoke-direct/range {p0 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/g;->a(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)Z

    move-result p0

    return p0
.end method


# virtual methods
.method public final declared-synchronized a(Ljava/lang/String;Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)Lcom/google/research/reflection/predictor/k;
    .locals 1

    monitor-enter p0

    .line 328
    :try_start_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mRankers:Ljava/util/Map;

    invoke-interface {v0, p1}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lcom/google/research/reflection/predictor/i;

    if-nez p1, :cond_0

    .line 333
    new-instance p1, Lcom/google/research/reflection/predictor/k;

    invoke-direct {p1}, Lcom/google/research/reflection/predictor/k;-><init>()V

    .line 334
    new-instance p2, Ljava/util/ArrayList;

    invoke-direct {p2}, Ljava/util/ArrayList;-><init>()V

    iput-object p2, p1, Lcom/google/research/reflection/predictor/k;->fR:Ljava/util/ArrayList;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 335
    monitor-exit p0

    return-object p1

    .line 339
    :cond_0
    :try_start_1
    invoke-virtual {p1, p2}, Lcom/google/research/reflection/predictor/i;->l(Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/k;

    move-result-object p1
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 358
    monitor-exit p0

    return-object p1

    :catchall_0
    move-exception p1

    .line 327
    monitor-exit p0

    throw p1
.end method

.method public final declared-synchronized a(Lcom/google/android/apps/nexuslauncher/reflection/g;)V
    .locals 3

    monitor-enter p0

    .line 148
    :try_start_0
    monitor-enter p1
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_1

    .line 149
    :try_start_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mRankers:Ljava/util/Map;

    invoke-interface {v0}, Ljava/util/Map;->clear()V

    .line 150
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mRankers:Ljava/util/Map;

    iget-object v1, p1, Lcom/google/android/apps/nexuslauncher/reflection/g;->mRankers:Ljava/util/Map;

    invoke-interface {v0, v1}, Ljava/util/Map;->putAll(Ljava/util/Map;)V

    .line 151
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mRankers:Ljava/util/Map;

    invoke-interface {v0}, Ljava/util/Map;->values()Ljava/util/Collection;

    move-result-object v0

    invoke-interface {v0}, Ljava/util/Collection;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_0

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/predictor/i;

    .line 152
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mEventBuffer:Lcom/google/research/reflection/predictor/b;

    invoke-virtual {v1, v2}, Lcom/google/research/reflection/predictor/i;->c(Lcom/google/research/reflection/predictor/b;)V

    goto :goto_0

    .line 154
    :cond_0
    monitor-exit p1
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    monitor-exit p0

    return-void

    :catchall_0
    move-exception v0

    :try_start_2
    monitor-exit p1
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    :try_start_3
    throw v0
    :try_end_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_1

    :catchall_1
    move-exception p1

    .line 147
    monitor-exit p0

    throw p1
.end method

.method public final declared-synchronized a(Ljava/io/File;)V
    .locals 0

    monitor-enter p0

    .line 141
    :try_start_0
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->at:Ljava/io/File;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 142
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 140
    monitor-exit p0

    throw p1
.end method

.method public final declared-synchronized b(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)V
    .locals 3

    monitor-enter p0

    .line 285
    :try_start_0
    invoke-virtual/range {p0 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/g;->c(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)V

    .line 286
    iget-object v0, p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    const-string v1, "/deleted_app/"

    invoke-virtual {v0, v1}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v0

    if-nez v0, :cond_0

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mEventBuffer:Lcom/google/research/reflection/predictor/b;

    if-eqz v0, :cond_0

    .line 288
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->al:Lcom/google/research/reflection/c/b;

    invoke-virtual {v0, p1}, Lcom/google/research/reflection/c/b;->c(Lcom/google/research/reflection/signal/ReflectionEvent;)V

    .line 289
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->am:Lcom/google/android/apps/nexuslauncher/reflection/d/b;

    invoke-virtual {v0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/d/b;->c(Lcom/google/research/reflection/signal/ReflectionEvent;)V

    .line 298
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->ap:Lcom/google/android/apps/nexuslauncher/reflection/d/c;

    invoke-virtual/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object p1

    invoke-interface {p1}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v1

    invoke-virtual {v0, v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->a(J)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 299
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 284
    monitor-exit p0

    throw p1
.end method

.method public final declared-synchronized b(Ljava/lang/String;Ljava/lang/String;)V
    .locals 3

    monitor-enter p0

    .line 365
    :try_start_0
    new-instance v0, Ljava/lang/StringBuilder;

    const-string v1, "/deleted_app/"

    invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v1

    invoke-virtual {v0, v1, v2}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    const-string v1, "/"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    .line 366
    new-instance v1, Ljava/util/HashMap;

    invoke-direct {v1}, Ljava/util/HashMap;-><init>()V

    .line 367
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mRankers:Ljava/util/Map;

    invoke-interface {v2, p1}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lcom/google/research/reflection/predictor/i;

    if-eqz p1, :cond_0

    .line 369
    invoke-virtual {p1, p2, v0, v1}, Lcom/google/research/reflection/predictor/i;->a(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V

    .line 370
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/g;->j()Z

    .line 372
    :cond_0
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->ap:Lcom/google/android/apps/nexuslauncher/reflection/d/c;

    invoke-virtual {p1, p2, v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->a(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 373
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 364
    monitor-exit p0

    throw p1
.end method

.method final declared-synchronized c(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)V
    .locals 5

    monitor-enter p0

    .line 302
    :try_start_0
    iget-object v0, p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    const-string v1, "/deleted_app/"

    .line 303
    invoke-virtual {v0, v1}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    if-eqz v0, :cond_0

    .line 304
    monitor-exit p0

    return-void

    .line 306
    :cond_0
    :try_start_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->ao:Ljava/util/Map;

    invoke-interface {v0}, Ljava/util/Map;->keySet()Ljava/util/Set;

    move-result-object v0

    invoke-interface {v0}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :cond_1
    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_5

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Ljava/lang/String;

    .line 307
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->ao:Ljava/util/Map;

    invoke-interface {v2, v1}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/google/android/apps/nexuslauncher/reflection/g$a;

    invoke-interface {v2, p1}, Lcom/google/android/apps/nexuslauncher/reflection/g$a;->shouldLearnOn(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)Z

    move-result v2

    if-eqz v2, :cond_1

    .line 308
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mRankers:Ljava/util/Map;

    invoke-interface {v2, v1}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/google/research/reflection/predictor/i;

    if-nez v2, :cond_2

    .line 315
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mEventBuffer:Lcom/google/research/reflection/predictor/b;

    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->an:Lcom/google/research/reflection/a/c;

    invoke-static {v2, v3}, Lcom/google/research/reflection/utils/a;->a(Lcom/google/research/reflection/predictor/b;Lcom/google/research/reflection/a/c;)Lcom/google/research/reflection/predictor/i;

    move-result-object v2

    .line 318
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mRankers:Ljava/util/Map;

    invoke-interface {v3, v1, v2}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    .line 320
    :cond_2
    iget-object v1, v2, Lcom/google/research/reflection/predictor/i;->fL:Ljava/util/List;

    invoke-interface {v1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v1

    :goto_1
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z

    move-result v3

    if-eqz v3, :cond_4

    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Lcom/google/research/reflection/predictor/g;

    invoke-virtual {v3, p1}, Lcom/google/research/reflection/predictor/g;->k(Lcom/google/research/reflection/signal/ReflectionEvent;)Z

    move-result v4

    if-eqz v4, :cond_3

    invoke-virtual {v3, p1}, Lcom/google/research/reflection/predictor/g;->h(Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/k;

    goto :goto_1

    :cond_3
    new-instance v3, Lcom/google/research/reflection/predictor/k;

    invoke-direct {v3}, Lcom/google/research/reflection/predictor/k;-><init>()V

    goto :goto_1

    :cond_4
    iget v1, v2, Lcom/google/research/reflection/predictor/i;->fK:I

    add-int/lit8 v1, v1, 0x1

    iput v1, v2, Lcom/google/research/reflection/predictor/i;->fK:I

    new-instance v1, Lcom/google/research/reflection/predictor/k;

    invoke-direct {v1}, Lcom/google/research/reflection/predictor/k;-><init>()V
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    goto :goto_0

    .line 322
    :cond_5
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 301
    monitor-exit p0

    throw p1
.end method

.method getPredictors()Ljava/util/Map;
    .locals 1
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Lcom/google/research/reflection/predictor/i;",
            ">;"
        }
    .end annotation

    .line 392
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mRankers:Ljava/util/Map;

    return-object v0
.end method

.method final declared-synchronized i()Z
    .locals 28

    move-object/from16 v1, p0

    monitor-enter p0

    .line 163
    :try_start_0
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    .line 164
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/reflection/g;->ar:Landroid/content/SharedPreferences;

    const/4 v2, 0x0

    const/4 v3, 0x0

    if-eqz v0, :cond_3

    .line 166
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/reflection/g;->ar:Landroid/content/SharedPreferences;

    iget-object v4, v1, Lcom/google/android/apps/nexuslauncher/reflection/g;->as:Ljava/lang/String;

    .line 167
    invoke-interface {v0, v4, v2}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    new-instance v4, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-direct {v4}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>()V

    invoke-static {v0, v4}, Lcom/google/research/reflection/predictor/b;->a(Ljava/lang/String;Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/b;

    move-result-object v0

    .line 171
    invoke-virtual {v0}, Lcom/google/research/reflection/predictor/b;->size()I

    move-result v4

    if-eqz v4, :cond_0

    .line 172
    invoke-virtual {v0}, Lcom/google/research/reflection/predictor/b;->ar()Lcom/google/research/reflection/a/a;

    move-result-object v4

    invoke-virtual {v4, v3}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Lcom/google/research/reflection/signal/ReflectionEvent;

    invoke-interface {v4}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v4

    invoke-interface {v4}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v4
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_5

    const-wide/16 v6, 0x0

    cmp-long v4, v4, v6

    if-nez v4, :cond_1

    .line 174
    :cond_0
    :try_start_1
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/reflection/g;->ar:Landroid/content/SharedPreferences;

    iget-object v4, v1, Lcom/google/android/apps/nexuslauncher/reflection/g;->as:Ljava/lang/String;

    .line 175
    invoke-interface {v0, v4, v2}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    .line 174
    invoke-static {v0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils;->f(Ljava/lang/String;)Lcom/google/research/reflection/predictor/b;

    move-result-object v0
    :try_end_1
    .catch Ljava/io/IOException; {:try_start_1 .. :try_end_1} :catch_0
    .catchall {:try_start_1 .. :try_end_1} :catchall_5

    goto :goto_0

    .line 181
    :catch_0
    :try_start_2
    new-instance v0, Lcom/google/research/reflection/predictor/b;

    invoke-direct {v0}, Lcom/google/research/reflection/predictor/b;-><init>()V

    .line 185
    :cond_1
    :goto_0
    iget-object v4, v1, Lcom/google/android/apps/nexuslauncher/reflection/g;->mEventBuffer:Lcom/google/research/reflection/predictor/b;

    monitor-enter v4
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_5

    .line 186
    :try_start_3
    iget-object v5, v1, Lcom/google/android/apps/nexuslauncher/reflection/g;->mEventBuffer:Lcom/google/research/reflection/predictor/b;

    invoke-virtual {v5}, Lcom/google/research/reflection/predictor/b;->clear()V

    const/4 v5, 0x0

    .line 187
    :goto_1
    invoke-virtual {v0}, Lcom/google/research/reflection/predictor/b;->size()I

    move-result v6

    if-ge v5, v6, :cond_2

    .line 188
    iget-object v6, v1, Lcom/google/android/apps/nexuslauncher/reflection/g;->mEventBuffer:Lcom/google/research/reflection/predictor/b;

    invoke-virtual {v0}, Lcom/google/research/reflection/predictor/b;->ar()Lcom/google/research/reflection/a/a;

    move-result-object v7

    invoke-virtual {v7, v5}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Lcom/google/research/reflection/signal/ReflectionEvent;

    invoke-virtual {v6, v7}, Lcom/google/research/reflection/predictor/b;->c(Lcom/google/research/reflection/signal/ReflectionEvent;)V

    add-int/lit8 v5, v5, 0x1

    goto :goto_1

    .line 190
    :cond_2
    monitor-exit v4

    goto :goto_2

    :catchall_0
    move-exception v0

    monitor-exit v4
    :try_end_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_0

    :try_start_4
    throw v0

    .line 195
    :cond_3
    :goto_2
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/reflection/g;->at:Ljava/io/File;
    :try_end_4
    .catchall {:try_start_4 .. :try_end_4} :catchall_5

    if-nez v0, :cond_4

    .line 199
    monitor-exit p0

    return v3

    .line 201
    :cond_4
    :try_start_5
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/reflection/g;->mRankers:Ljava/util/Map;

    invoke-interface {v0}, Ljava/util/Map;->clear()V

    .line 202
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/reflection/g;->at:Ljava/io/File;

    invoke-virtual {v0}, Ljava/io/File;->exists()Z

    move-result v0
    :try_end_5
    .catchall {:try_start_5 .. :try_end_5} :catchall_5

    if-nez v0, :cond_5

    .line 206
    monitor-exit p0

    return v3

    .line 208
    :cond_5
    :try_start_6
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/reflection/g;->at:Ljava/io/File;

    invoke-static {v0}, Lcom/android/launcher3/util/IOUtils;->toByteArray(Ljava/io/File;)[B

    move-result-object v0

    .line 211
    invoke-static {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->g([B)Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;

    move-result-object v0

    .line 212
    iget-object v4, v1, Lcom/google/android/apps/nexuslauncher/reflection/g;->mRankers:Ljava/util/Map;

    iget-object v5, v1, Lcom/google/android/apps/nexuslauncher/reflection/g;->mEventBuffer:Lcom/google/research/reflection/predictor/b;

    iget-object v6, v1, Lcom/google/android/apps/nexuslauncher/reflection/g;->an:Lcom/google/research/reflection/a/c;

    invoke-interface {v4}, Ljava/util/Map;->clear()V

    iget-object v0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    array-length v7, v0

    const/4 v8, 0x0

    :goto_3
    if-ge v8, v7, :cond_d

    aget-object v9, v0, v8

    new-instance v10, Lcom/google/research/reflection/predictor/i;

    invoke-direct {v10}, Lcom/google/research/reflection/predictor/i;-><init>()V

    iget-object v11, v9, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    array-length v12, v11

    const/4 v13, 0x0

    :goto_4
    if-ge v13, v12, :cond_c

    aget-object v14, v11, v13

    iget-object v15, v14, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->cs:Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;

    iget-object v3, v15, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cw:Ljava/lang/String;

    invoke-static {v3, v6}, Lcom/google/research/reflection/predictor/g;->a(Ljava/lang/String;Lcom/google/research/reflection/a/c;)Lcom/google/research/reflection/predictor/g;

    move-result-object v3
    :try_end_6
    .catch Ljava/io/IOException; {:try_start_6 .. :try_end_6} :catch_2
    .catchall {:try_start_6 .. :try_end_6} :catchall_3

    if-eqz v3, :cond_b

    :try_start_7
    new-instance v2, Ljava/io/DataInputStream;

    move-object/from16 v16, v0

    new-instance v0, Ljava/io/ByteArrayInputStream;

    move-object/from16 v17, v6

    iget-object v6, v15, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cx:[B

    invoke-direct {v0, v6}, Ljava/io/ByteArrayInputStream;-><init>([B)V

    invoke-direct {v2, v0}, Ljava/io/DataInputStream;-><init>(Ljava/io/InputStream;)V

    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>()V

    invoke-virtual {v3, v2, v0}, Lcom/google/research/reflection/predictor/g;->a(Ljava/io/DataInputStream;Lcom/google/research/reflection/signal/ReflectionEvent;)V

    invoke-virtual {v2}, Ljava/io/DataInputStream;->close()V

    instance-of v0, v3, Lcom/google/research/reflection/predictor/AbstractEventEstimator;

    if-nez v0, :cond_6

    move/from16 v20, v7

    move-object/from16 v23, v11

    move/from16 v24, v12

    goto/16 :goto_9

    :cond_6
    check-cast v3, Lcom/google/research/reflection/predictor/AbstractEventEstimator;

    iget v0, v15, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cz:I

    invoke-virtual {v3, v0}, Lcom/google/research/reflection/predictor/AbstractEventEstimator;->d(I)V

    iget v0, v15, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cy:I

    invoke-virtual {v3, v0}, Lcom/google/research/reflection/predictor/AbstractEventEstimator;->c(I)V

    iget-object v0, v15, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    array-length v2, v0

    const/4 v6, 0x0

    :goto_5
    if-ge v6, v2, :cond_7

    move/from16 v18, v2

    aget-object v2, v0, v6

    move-object/from16 v19, v0

    invoke-virtual {v3}, Lcom/google/research/reflection/predictor/AbstractEventEstimator;->aq()Ljava/util/HashMap;

    move-result-object v0

    move/from16 v20, v7

    iget-object v7, v2, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->name:Ljava/lang/String;

    iget v2, v2, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->id:I

    invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v2

    invoke-virtual {v0, v7, v2}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    add-int/lit8 v6, v6, 0x1

    move/from16 v2, v18

    move-object/from16 v0, v19

    move/from16 v7, v20

    goto :goto_5

    :cond_7
    move/from16 v20, v7

    iget-object v0, v15, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v2, v0

    const/4 v6, 0x0

    :goto_6
    if-ge v6, v2, :cond_8

    aget-object v7, v0, v6

    move-object/from16 v21, v0

    invoke-virtual {v3}, Lcom/google/research/reflection/predictor/AbstractEventEstimator;->am()Ljava/util/HashMap;

    move-result-object v0

    move/from16 v22, v2

    iget v2, v7, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->key:I

    invoke-static {v2}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v2

    move-object/from16 v23, v11

    move/from16 v24, v12

    iget-wide v11, v7, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->value:J

    long-to-int v7, v11

    invoke-static {v7}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v7

    invoke-virtual {v0, v2, v7}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    add-int/lit8 v6, v6, 0x1

    move-object/from16 v0, v21

    move/from16 v2, v22

    move-object/from16 v11, v23

    move/from16 v12, v24

    goto :goto_6

    :cond_8
    move-object/from16 v23, v11

    move/from16 v24, v12

    iget-object v0, v15, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v2, v0

    const/4 v6, 0x0

    :goto_7
    if-ge v6, v2, :cond_9

    aget-object v7, v0, v6

    invoke-virtual {v3}, Lcom/google/research/reflection/predictor/AbstractEventEstimator;->al()Ljava/util/HashMap;

    move-result-object v11

    iget v12, v7, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->key:I

    invoke-static {v12}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v12
    :try_end_7
    .catch Ljava/io/IOException; {:try_start_7 .. :try_end_7} :catch_2
    .catchall {:try_start_7 .. :try_end_7} :catchall_4

    move-object/from16 v25, v0

    :try_start_8
    iget-wide v0, v7, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->value:J

    invoke-static {v0, v1}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object v0

    invoke-virtual {v11, v12, v0}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    add-int/lit8 v6, v6, 0x1

    move-object/from16 v0, v25

    move-object/from16 v1, p0

    goto :goto_7

    :cond_9
    iget-object v0, v15, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v1, v0

    const/4 v2, 0x0

    :goto_8
    if-ge v2, v1, :cond_a

    aget-object v6, v0, v2

    invoke-virtual {v3}, Lcom/google/research/reflection/predictor/AbstractEventEstimator;->an()Ljava/util/HashMap;

    move-result-object v7

    iget v11, v6, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->key:I

    invoke-static {v11}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v11

    move-object/from16 v26, v0

    move/from16 v27, v1

    iget-wide v0, v6, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->value:J

    invoke-static {v0, v1}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object v0

    invoke-virtual {v7, v11, v0}, Ljava/util/HashMap;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    add-int/lit8 v2, v2, 0x1

    move-object/from16 v0, v26

    move/from16 v1, v27

    goto :goto_8

    :cond_a
    :goto_9
    const-string v0, "reflection"

    invoke-virtual {v3}, Lcom/google/research/reflection/predictor/g;->getName()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    iget v0, v14, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->ct:I

    invoke-virtual {v10, v3, v0}, Lcom/google/research/reflection/predictor/i;->a(Lcom/google/research/reflection/predictor/g;I)V

    add-int/lit8 v13, v13, 0x1

    move-object/from16 v0, v16

    move-object/from16 v6, v17

    move/from16 v7, v20

    move-object/from16 v11, v23

    move/from16 v12, v24

    move-object/from16 v1, p0

    const/4 v2, 0x0

    const/4 v3, 0x0

    goto/16 :goto_4

    :cond_b
    new-instance v0, Ljava/io/IOException;

    new-instance v1, Ljava/lang/StringBuilder;

    const-string v2, "Cannot find predictor with name ("

    invoke-direct {v1, v2}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    iget-object v2, v15, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cw:Ljava/lang/String;

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v2, ")"

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-direct {v0, v1}, Ljava/io/IOException;-><init>(Ljava/lang/String;)V

    throw v0

    :cond_c
    move-object/from16 v16, v0

    move-object/from16 v17, v6

    move/from16 v20, v7

    invoke-virtual {v10, v5}, Lcom/google/research/reflection/predictor/i;->c(Lcom/google/research/reflection/predictor/b;)V

    iget-object v0, v9, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->bp:Ljava/lang/String;

    invoke-interface {v4, v0, v10}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    :try_end_8
    .catch Ljava/io/IOException; {:try_start_8 .. :try_end_8} :catch_1
    .catchall {:try_start_8 .. :try_end_8} :catchall_1

    add-int/lit8 v8, v8, 0x1

    move-object/from16 v0, v16

    move-object/from16 v6, v17

    move/from16 v7, v20

    move-object/from16 v1, p0

    const/4 v2, 0x0

    const/4 v3, 0x0

    goto/16 :goto_3

    :catchall_1
    move-exception v0

    move-object/from16 v1, p0

    goto :goto_b

    :catch_1
    move-exception v0

    move-object/from16 v1, p0

    goto :goto_a

    :cond_d
    move-object v1, v2

    .line 222
    :try_start_9
    invoke-static {v1}, Lcom/android/launcher3/Utilities;->closeSilently(Ljava/io/Closeable;)V
    :try_end_9
    .catchall {:try_start_9 .. :try_end_9} :catchall_2

    move-object/from16 v1, p0

    .line 224
    monitor-exit p0

    const/4 v0, 0x1

    return v0

    :catchall_2
    move-exception v0

    move-object/from16 v1, p0

    goto :goto_d

    :catchall_3
    move-exception v0

    goto :goto_c

    :catch_2
    move-exception v0

    :goto_a
    :try_start_a
    const-string v2, "Reflection.Engine"

    const-string v3, "Failed to load models, starting a fresh model."

    .line 219
    invoke-static {v2, v3, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    :try_end_a
    .catchall {:try_start_a .. :try_end_a} :catchall_4

    const/4 v2, 0x0

    .line 220
    :try_start_b
    invoke-static {v2}, Lcom/android/launcher3/Utilities;->closeSilently(Ljava/io/Closeable;)V
    :try_end_b
    .catchall {:try_start_b .. :try_end_b} :catchall_5

    monitor-exit p0

    const/4 v2, 0x0

    return v2

    :catchall_4
    move-exception v0

    :goto_b
    const/4 v2, 0x0

    .line 222
    :goto_c
    :try_start_c
    invoke-static {v2}, Lcom/android/launcher3/Utilities;->closeSilently(Ljava/io/Closeable;)V

    throw v0
    :try_end_c
    .catchall {:try_start_c .. :try_end_c} :catchall_5

    :catchall_5
    move-exception v0

    .line 162
    :goto_d
    monitor-exit p0

    throw v0
.end method

.method public final declared-synchronized j()Z
    .locals 13

    monitor-enter p0

    .line 233
    :try_start_0
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    .line 234
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mEventBuffer:Lcom/google/research/reflection/predictor/b;

    if-eqz v0, :cond_0

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->ar:Landroid/content/SharedPreferences;

    if-eqz v0, :cond_0

    .line 235
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mEventBuffer:Lcom/google/research/reflection/predictor/b;

    invoke-static {v0}, Lcom/google/research/reflection/predictor/b;->b(Lcom/google/research/reflection/predictor/b;)Ljava/lang/String;

    move-result-object v0

    .line 236
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->ar:Landroid/content/SharedPreferences;

    const-string v2, "reflection_most_recent_usage_buffer"

    const-wide/16 v3, 0x0

    .line 237
    invoke-interface {v1, v2, v3, v4}, Landroid/content/SharedPreferences;->getLong(Ljava/lang/String;J)J

    move-result-wide v1

    .line 239
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->ar:Landroid/content/SharedPreferences;

    invoke-interface {v3}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v3

    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->as:Ljava/lang/String;

    .line 240
    invoke-interface {v3, v4, v0}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v3, "reflection_most_recent_usage"

    .line 241
    invoke-interface {v0, v3, v1, v2}, Landroid/content/SharedPreferences$Editor;->putLong(Ljava/lang/String;J)Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    .line 243
    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    .line 248
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->at:Ljava/io/File;
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_2

    const/4 v1, 0x0

    if-nez v0, :cond_1

    .line 252
    monitor-exit p0

    return v1

    :cond_1
    const/4 v0, 0x0

    .line 256
    :try_start_1
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mRankers:Ljava/util/Map;

    .line 257
    new-instance v3, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;

    invoke-direct {v3}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;-><init>()V

    invoke-static {}, Ljava/util/Calendar;->getInstance()Ljava/util/Calendar;

    move-result-object v4

    invoke-virtual {v4}, Ljava/util/Calendar;->getTimeInMillis()J

    move-result-wide v4

    iput-wide v4, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->timestamp:J

    const/4 v4, -0x1

    iput v4, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->version:I

    const-string v4, ""

    iput-object v4, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cu:Ljava/lang/String;

    invoke-interface {v2}, Ljava/util/Map;->size()I

    move-result v4

    new-array v4, v4, [Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    iput-object v4, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    invoke-interface {v2}, Ljava/util/Map;->entrySet()Ljava/util/Set;

    move-result-object v2

    invoke-interface {v2}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object v2

    const/4 v4, 0x0

    :goto_0
    invoke-interface {v2}, Ljava/util/Iterator;->hasNext()Z

    move-result v5

    if-eqz v5, :cond_3

    invoke-interface {v2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Ljava/util/Map$Entry;

    new-instance v6, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    invoke-direct {v6}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;-><init>()V

    invoke-interface {v5}, Ljava/util/Map$Entry;->getKey()Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Ljava/lang/String;

    iput-object v7, v6, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->bp:Ljava/lang/String;

    invoke-interface {v5}, Ljava/util/Map$Entry;->getValue()Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Lcom/google/research/reflection/predictor/i;

    iget-object v7, v5, Lcom/google/research/reflection/predictor/i;->fL:Ljava/util/List;

    invoke-interface {v7}, Ljava/util/List;->size()I

    move-result v7

    new-array v7, v7, [Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    iput-object v7, v6, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    iget-object v7, v5, Lcom/google/research/reflection/predictor/i;->fL:Ljava/util/List;

    invoke-interface {v7}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v7

    const/4 v8, 0x0

    :goto_1
    invoke-interface {v7}, Ljava/util/Iterator;->hasNext()Z

    move-result v9

    if-eqz v9, :cond_2

    invoke-interface {v7}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v9

    check-cast v9, Lcom/google/research/reflection/predictor/g;

    iget-object v10, v6, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    new-instance v11, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    invoke-direct {v11}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;-><init>()V

    aput-object v11, v10, v8

    iget-object v10, v6, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    aget-object v10, v10, v8

    invoke-static {v9}, Lcom/google/android/apps/nexuslauncher/reflection/a/c;->a(Lcom/google/research/reflection/predictor/g;)Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;

    move-result-object v11

    iput-object v11, v10, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->cs:Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;

    invoke-static {v9}, Lcom/google/android/apps/nexuslauncher/reflection/a/c;->a(Lcom/google/research/reflection/predictor/g;)Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;

    iget-object v10, v6, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    aget-object v10, v10, v8

    iget-object v11, v5, Lcom/google/research/reflection/predictor/i;->fM:Ljava/util/HashMap;

    invoke-virtual {v11, v9}, Ljava/util/HashMap;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v9

    check-cast v9, Ljava/lang/Integer;

    invoke-virtual {v9}, Ljava/lang/Integer;->intValue()I

    move-result v9

    iput v9, v10, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->ct:I

    add-int/lit8 v8, v8, 0x1

    goto :goto_1

    :cond_2
    iget-object v5, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    add-int/lit8 v7, v4, 0x1

    aput-object v6, v5, v4

    move v4, v7

    goto :goto_0

    .line 258
    :cond_3
    invoke-static {}, Ljava/util/Calendar;->getInstance()Ljava/util/Calendar;

    move-result-object v2

    invoke-virtual {v2}, Ljava/util/Calendar;->getTimeInMillis()J

    move-result-wide v4

    iput-wide v4, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->timestamp:J

    const/16 v2, 0x2a

    .line 259
    iput v2, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->version:I

    .line 260
    new-instance v2, Ljava/lang/StringBuffer;

    invoke-direct {v2}, Ljava/lang/StringBuffer;-><init>()V

    const-string v4, "LatLong=%b "

    const/4 v5, 0x1

    new-array v6, v5, [Ljava/lang/Object;

    invoke-static {v5}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object v7

    aput-object v7, v6, v1

    invoke-static {v4, v6}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v2, v4}, Ljava/lang/StringBuffer;->append(Ljava/lang/String;)Ljava/lang/StringBuffer;

    const-string v4, "Privateplace=%b "

    new-array v6, v5, [Ljava/lang/Object;

    invoke-static {v1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object v7

    aput-object v7, v6, v1

    invoke-static {v4, v6}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v2, v4}, Ljava/lang/StringBuffer;->append(Ljava/lang/String;)Ljava/lang/StringBuffer;

    const-string v4, "Publicplace=%b "

    new-array v6, v5, [Ljava/lang/Object;

    invoke-static {v5}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object v7

    aput-object v7, v6, v1

    invoke-static {v4, v6}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v2, v4}, Ljava/lang/StringBuffer;->append(Ljava/lang/String;)Ljava/lang/StringBuffer;

    const-string v4, "Install=%b "

    new-array v6, v5, [Ljava/lang/Object;

    invoke-static {v5}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object v7

    aput-object v7, v6, v1

    invoke-static {v4, v6}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v2, v4}, Ljava/lang/StringBuffer;->append(Ljava/lang/String;)Ljava/lang/StringBuffer;

    const-string v4, "Headset=%b "

    new-array v6, v5, [Ljava/lang/Object;

    invoke-static {v5}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object v7

    aput-object v7, v6, v1

    invoke-static {v4, v6}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v2, v4}, Ljava/lang/StringBuffer;->append(Ljava/lang/String;)Ljava/lang/StringBuffer;

    invoke-virtual {v2}, Ljava/lang/StringBuffer;->toString()Ljava/lang/String;

    move-result-object v2

    iput-object v2, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cu:Ljava/lang/String;

    .line 261
    new-instance v2, Ljava/io/DataOutputStream;

    new-instance v4, Ljava/io/BufferedOutputStream;

    new-instance v6, Ljava/io/FileOutputStream;

    iget-object v7, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->at:Ljava/io/File;

    invoke-direct {v6, v7}, Ljava/io/FileOutputStream;-><init>(Ljava/io/File;)V

    invoke-direct {v4, v6}, Ljava/io/BufferedOutputStream;-><init>(Ljava/io/OutputStream;)V

    invoke-direct {v2, v4}, Ljava/io/DataOutputStream;-><init>(Ljava/io/OutputStream;)V
    :try_end_1
    .catch Ljava/io/IOException; {:try_start_1 .. :try_end_1} :catch_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_1

    .line 262
    :try_start_2
    invoke-static {v3}, Lcom/google/protobuf/nano/MessageNano;->toByteArray(Lcom/google/protobuf/nano/MessageNano;)[B

    move-result-object v0

    invoke-virtual {v2, v0}, Ljava/io/DataOutputStream;->write([B)V

    .line 264
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->aq:Ljava/lang/Runnable;

    if-eqz v0, :cond_4

    .line 265
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->aq:Ljava/lang/Runnable;

    invoke-interface {v0}, Ljava/lang/Runnable;->run()V
    :try_end_2
    .catch Ljava/io/IOException; {:try_start_2 .. :try_end_2} :catch_0
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    .line 271
    :cond_4
    :try_start_3
    invoke-static {v2}, Lcom/android/launcher3/Utilities;->closeSilently(Ljava/io/Closeable;)V
    :try_end_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_2

    .line 273
    monitor-exit p0

    return v5

    :catchall_0
    move-exception v1

    move-object v0, v2

    goto :goto_3

    :catch_0
    move-exception v0

    move-object v12, v2

    move-object v2, v0

    move-object v0, v12

    goto :goto_2

    :catchall_1
    move-exception v1

    goto :goto_3

    :catch_1
    move-exception v2

    :goto_2
    :try_start_4
    const-string v3, "Reflection.Engine"

    const-string v4, "Failed to save models"

    .line 268
    invoke-static {v3, v4, v2}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    :try_end_4
    .catchall {:try_start_4 .. :try_end_4} :catchall_1

    .line 269
    :try_start_5
    invoke-static {v0}, Lcom/android/launcher3/Utilities;->closeSilently(Ljava/io/Closeable;)V
    :try_end_5
    .catchall {:try_start_5 .. :try_end_5} :catchall_2

    monitor-exit p0

    return v1

    .line 271
    :goto_3
    :try_start_6
    invoke-static {v0}, Lcom/android/launcher3/Utilities;->closeSilently(Ljava/io/Closeable;)V

    throw v1
    :try_end_6
    .catchall {:try_start_6 .. :try_end_6} :catchall_2

    :catchall_2
    move-exception v0

    .line 232
    monitor-exit p0

    throw v0
.end method

.method final declared-synchronized reset()V
    .locals 1

    monitor-enter p0

    .line 277
    :try_start_0
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    .line 278
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/g;->mRankers:Ljava/util/Map;

    invoke-interface {v0}, Ljava/util/Map;->clear()V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 279
    monitor-exit p0

    return-void

    :catchall_0
    move-exception v0

    .line 276
    monitor-exit p0

    throw v0
.end method
