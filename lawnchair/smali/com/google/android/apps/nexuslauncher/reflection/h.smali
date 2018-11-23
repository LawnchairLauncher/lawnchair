.class public Lcom/google/android/apps/nexuslauncher/reflection/h;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/android/apps/nexuslauncher/reflection/h$a;
    }
.end annotation


# instance fields
.field private final aA:Lcom/google/android/apps/nexuslauncher/reflection/b/f;

.field private final aB:Lcom/google/android/apps/nexuslauncher/reflection/e;

.field private final aC:Lcom/google/android/apps/nexuslauncher/reflection/d/e;

.field final aD:Lcom/google/android/apps/nexuslauncher/reflection/b;

.field final aE:Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;

.field private final aF:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

.field private final aG:Lcom/google/android/apps/nexuslauncher/reflection/j;

.field private final aa:Lcom/google/android/apps/nexuslauncher/reflection/d/d;

.field private final ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

.field final au:Ljava/util/ArrayList;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/ArrayList<",
            "Lcom/google/android/apps/nexuslauncher/reflection/h$a;",
            ">;"
        }
    .end annotation
.end field

.field final av:Lcom/google/research/reflection/c/d;

.field private final aw:Lcom/google/android/apps/nexuslauncher/reflection/b/d;

.field private final ax:Lcom/google/android/apps/nexuslauncher/reflection/b/a;

.field final ay:Lcom/google/android/apps/nexuslauncher/reflection/b/c;

.field private final az:Lcom/google/android/apps/nexuslauncher/reflection/b/e;

.field final mContext:Landroid/content/Context;

.field final mEngine:Lcom/google/android/apps/nexuslauncher/reflection/g;


# direct methods
.method public constructor <init>(Landroid/content/Context;Lcom/google/android/apps/nexuslauncher/reflection/g;Lcom/google/android/apps/nexuslauncher/reflection/j;Lcom/google/research/reflection/c/d;Lcom/google/android/apps/nexuslauncher/reflection/b/b;Lcom/google/android/apps/nexuslauncher/reflection/b/d;Lcom/google/android/apps/nexuslauncher/reflection/b/c;Lcom/google/android/apps/nexuslauncher/reflection/e;Lcom/google/android/apps/nexuslauncher/reflection/d/e;Lcom/google/android/apps/nexuslauncher/reflection/d/d;Lcom/google/android/apps/nexuslauncher/reflection/b;Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;)V
    .locals 1

    .line 101
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 63
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->au:Ljava/util/ArrayList;

    .line 102
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->mContext:Landroid/content/Context;

    .line 103
    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->mEngine:Lcom/google/android/apps/nexuslauncher/reflection/g;

    .line 104
    iput-object p3, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aG:Lcom/google/android/apps/nexuslauncher/reflection/j;

    .line 105
    iput-object p4, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->av:Lcom/google/research/reflection/c/d;

    .line 106
    iput-object p5, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    .line 107
    iput-object p6, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aw:Lcom/google/android/apps/nexuslauncher/reflection/b/d;

    .line 108
    iput-object p7, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->ay:Lcom/google/android/apps/nexuslauncher/reflection/b/c;

    .line 109
    new-instance p2, Lcom/google/android/apps/nexuslauncher/reflection/b/f;

    invoke-direct {p2, p1}, Lcom/google/android/apps/nexuslauncher/reflection/b/f;-><init>(Landroid/content/Context;)V

    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aA:Lcom/google/android/apps/nexuslauncher/reflection/b/f;

    .line 110
    new-instance p2, Lcom/google/android/apps/nexuslauncher/reflection/b/a;

    invoke-direct {p2, p1}, Lcom/google/android/apps/nexuslauncher/reflection/b/a;-><init>(Landroid/content/Context;)V

    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->ax:Lcom/google/android/apps/nexuslauncher/reflection/b/a;

    .line 111
    iput-object p8, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aB:Lcom/google/android/apps/nexuslauncher/reflection/e;

    .line 112
    iput-object p9, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aC:Lcom/google/android/apps/nexuslauncher/reflection/d/e;

    .line 113
    iput-object p10, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aa:Lcom/google/android/apps/nexuslauncher/reflection/d/d;

    .line 114
    iput-object p11, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aD:Lcom/google/android/apps/nexuslauncher/reflection/b;

    .line 115
    new-instance p2, Lcom/google/android/apps/nexuslauncher/reflection/b/e;

    const/4 p3, 0x0

    invoke-direct {p2, p3}, Lcom/google/android/apps/nexuslauncher/reflection/b/e;-><init>(I)V

    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->az:Lcom/google/android/apps/nexuslauncher/reflection/b/e;

    .line 116
    invoke-static/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->e(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    move-result-object p1

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aF:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    .line 117
    iput-object p12, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aE:Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;

    return-void
.end method

.method private d(Ljava/util/List;)[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;
    .locals 4
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/List<",
            "Lcom/google/research/reflection/predictor/k$a;",
            ">;)[",
            "Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;"
        }
    .end annotation

    .line 351
    invoke-interface/range {p1 .. p1}, Ljava/util/List;->size()I

    move-result v0

    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    const/4 v1, 0x0

    .line 352
    :goto_0
    invoke-interface/range {p1 .. p1}, Ljava/util/List;->size()I

    move-result v2

    if-ge v1, v2, :cond_0

    .line 353
    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;-><init>()V

    .line 354
    invoke-interface {p1, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Lcom/google/research/reflection/predictor/k$a;

    iget-object v3, v3, Lcom/google/research/reflection/predictor/k$a;->id:Ljava/lang/String;

    iput-object v3, v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->bZ:Ljava/lang/String;

    .line 355
    invoke-interface {p1, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Lcom/google/research/reflection/predictor/k$a;

    iget v3, v3, Lcom/google/research/reflection/predictor/k$a;->ca:F

    iput v3, v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->ca:F

    .line 356
    aput-object v2, v0, v1

    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    :cond_0
    return-object v0
.end method

.method static stabilizePredictedAppOrder(Ljava/util/List;Ljava/util/Map;)Ljava/util/List;
    .locals 5
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/List<",
            "Lcom/google/research/reflection/predictor/k$a;",
            ">;",
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Ljava/lang/Integer;",
            ">;)",
            "Ljava/util/List<",
            "Lcom/google/research/reflection/predictor/k$a;",
            ">;"
        }
    .end annotation

    .line 404
    invoke-interface/range {p0 .. p0}, Ljava/util/List;->size()I

    move-result v0

    new-array v0, v0, [Lcom/google/research/reflection/predictor/k$a;

    .line 405
    new-instance v1, Ljava/util/ArrayList;

    invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V

    .line 407
    invoke-interface/range {p0 .. p0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p0

    :goto_0
    invoke-interface {p0}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_2

    invoke-interface {p0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/google/research/reflection/predictor/k$a;

    .line 408
    iget-object v3, v2, Lcom/google/research/reflection/predictor/k$a;->id:Ljava/lang/String;

    invoke-interface {p1, v3}, Ljava/util/Map;->containsKey(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_1

    .line 409
    iget-object v3, v2, Lcom/google/research/reflection/predictor/k$a;->id:Ljava/lang/String;

    invoke-interface {p1, v3}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Ljava/lang/Integer;

    invoke-virtual {v3}, Ljava/lang/Integer;->intValue()I

    move-result v3

    .line 410
    array-length v4, v0

    if-ge v3, v4, :cond_0

    .line 411
    aput-object v2, v0, v3

    goto :goto_0

    .line 421
    :cond_0
    invoke-interface {v1, v2}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    goto :goto_0

    .line 424
    :cond_1
    invoke-interface {v1, v2}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    goto :goto_0

    .line 429
    :cond_2
    invoke-interface/range {p1 .. p1}, Ljava/util/Map;->clear()V

    const/4 p0, 0x0

    const/4 p1, 0x0

    .line 430
    :goto_1
    array-length v2, v0

    if-ge p0, v2, :cond_4

    .line 431
    aget-object v2, v0, p0

    if-nez v2, :cond_3

    add-int/lit8 v2, p1, 0x1

    .line 432
    invoke-interface {v1, p1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Lcom/google/research/reflection/predictor/k$a;

    aput-object p1, v0, p0

    move p1, v2

    :cond_3
    add-int/lit8 p0, p0, 0x1

    goto :goto_1

    .line 435
    :cond_4
    invoke-static {v0}, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;

    move-result-object p0

    return-object p0
.end method


# virtual methods
.method final a(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;)V
    .locals 6

    .line 164
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->mEngine:Lcom/google/android/apps/nexuslauncher/reflection/g;

    invoke-virtual {v0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/g;->b(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)V

    .line 165
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->mEngine:Lcom/google/android/apps/nexuslauncher/reflection/g;

    invoke-virtual {v0}, Lcom/google/android/apps/nexuslauncher/reflection/g;->j()Z

    .line 168
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->ay:Lcom/google/android/apps/nexuslauncher/reflection/b/c;

    iget-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bj:Ljava/util/LinkedList;

    invoke-virtual {v1}, Ljava/util/LinkedList;->iterator()Ljava/util/Iterator;

    move-result-object v1

    :goto_0
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    const/4 v3, 0x1

    if-eqz v2, :cond_0

    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;

    iget v4, v2, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;->bm:I

    add-int/2addr v4, v3

    iput v4, v2, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;->bm:I

    goto :goto_0

    :cond_0
    invoke-virtual {v0}, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->o()V

    .line 171
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aa:Lcom/google/android/apps/nexuslauncher/reflection/d/d;

    if-eqz v0, :cond_3

    .line 172
    invoke-static {}, Ljava/util/Calendar;->getInstance()Ljava/util/Calendar;

    move-result-object v0

    .line 173
    new-instance v1, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;

    invoke-direct {v1}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;-><init>()V

    .line 174
    invoke-virtual/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->C()Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    move-result-object v2

    invoke-virtual {v2}, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->toString()Ljava/lang/String;

    move-result-object v2

    iput-object v2, v1, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bA:Ljava/lang/String;

    .line 175
    invoke-virtual {v0}, Ljava/util/Calendar;->getTimeInMillis()J

    move-result-wide v4

    iput-wide v4, v1, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->timestamp:J

    .line 176
    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    iput-object p1, v1, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->packageName:Ljava/lang/String;

    if-eqz p2, :cond_2

    .line 178
    new-instance p1, Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;

    invoke-direct {p1}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;-><init>()V

    .line 179
    iget-object v0, p2, Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;->srcTarget:[Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;

    array-length v0, v0

    const/4 v2, 0x2

    if-lt v0, v2, :cond_1

    iget-object v0, p2, Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;->srcTarget:[Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;

    aget-object v0, v0, v3

    iget v0, v0, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;->containerType:I

    if-eqz v0, :cond_1

    .line 184
    iget-object v0, p2, Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;->srcTarget:[Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;

    aget-object v0, v0, v3

    iget v0, v0, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;->containerType:I

    invoke-static {v0}, Ljava/lang/Integer;->toString(I)Ljava/lang/String;

    move-result-object v0

    iput-object v0, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;->bS:Ljava/lang/String;

    .line 187
    iget-object p2, p2, Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;->srcTarget:[Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;

    const/4 v0, 0x0

    aget-object p2, p2, v0

    iget p2, p2, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;->pageIndex:I

    invoke-static {p2}, Ljava/lang/Integer;->toString(I)Ljava/lang/String;

    move-result-object p2

    iput-object p2, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;->bT:Ljava/lang/String;

    .line 189
    :cond_1
    iput-object p1, v1, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bW:Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;

    .line 191
    :cond_2
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aa:Lcom/google/android/apps/nexuslauncher/reflection/d/d;

    invoke-virtual {p1, v1}, Lcom/google/android/apps/nexuslauncher/reflection/d/d;->a(Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;)V

    :cond_3
    return-void
.end method

.method final a(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;Z)V
    .locals 2

    .line 208
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aF:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    if-eqz v0, :cond_1

    if-eqz p2, :cond_0

    .line 210
    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aF:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    invoke-virtual {p2, p1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->e(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)V

    .line 212
    :cond_0
    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aF:Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    invoke-virtual/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object p1

    invoke-interface {p1}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v0

    invoke-virtual {p2, v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->d(J)V

    :cond_1
    return-void
.end method

.method public final a(Ljava/lang/String;I)V
    .locals 6

    .line 217
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    .line 219
    sget-object v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fW:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    const-string v1, "predictionEvent"

    const-string v2, ""

    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aD:Lcom/google/android/apps/nexuslauncher/reflection/b;

    .line 223
    invoke-interface {v3}, Lcom/google/android/apps/nexuslauncher/reflection/b;->g()J

    move-result-wide v3

    iget-object v5, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->av:Lcom/google/research/reflection/c/d;

    .line 224
    invoke-virtual {v5}, Lcom/google/research/reflection/c/d;->au()Lcom/google/research/reflection/signal/b;

    move-result-object v5

    .line 219
    invoke-static/range {v0 .. v5}, Lcom/google/android/apps/nexuslauncher/reflection/a/e;->a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;Ljava/lang/String;Ljava/lang/String;JLcom/google/research/reflection/signal/b;)Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    move-result-object v0

    const/4 v1, 0x0

    .line 225
    invoke-virtual {p0, v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/h;->a(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;Z)V

    .line 226
    invoke-virtual {p0, p1, p2, v0}, Lcom/google/android/apps/nexuslauncher/reflection/h;->predict(Ljava/lang/String;ILcom/google/android/apps/nexuslauncher/reflection/signal/a;)V

    return-void
.end method

.method public final declared-synchronized a(Z)V
    .locals 1

    monitor-enter p0

    .line 362
    :try_start_0
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    if-eqz p1, :cond_0

    .line 364
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aC:Lcom/google/android/apps/nexuslauncher/reflection/d/e;

    invoke-virtual {p1}, Lcom/google/android/apps/nexuslauncher/reflection/d/e;->u()V

    .line 365
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->mContext:Landroid/content/Context;

    const-string v0, "reflection.events"

    invoke-virtual {p1, v0}, Landroid/content/Context;->deleteDatabase(Ljava/lang/String;)Z

    .line 366
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->mEngine:Lcom/google/android/apps/nexuslauncher/reflection/g;

    invoke-virtual {p1}, Lcom/google/android/apps/nexuslauncher/reflection/g;->reset()V

    .line 368
    :cond_0
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->au:Ljava/util/ArrayList;

    invoke-virtual {p1}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :goto_0
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_1

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/google/android/apps/nexuslauncher/reflection/h$a;

    .line 369
    invoke-interface {v0}, Lcom/google/android/apps/nexuslauncher/reflection/h$a;->h()V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    goto :goto_0

    .line 371
    :cond_1
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 361
    monitor-exit p0

    throw p1
.end method

.method predict(Ljava/lang/String;ILcom/google/android/apps/nexuslauncher/reflection/signal/a;)V
    .locals 11

    .line 233
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aa:Lcom/google/android/apps/nexuslauncher/reflection/d/d;

    const/4 v1, 0x0

    if-eqz v0, :cond_0

    .line 234
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;-><init>()V

    const-string v2, "prediction_update"

    .line 235
    iput-object v2, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bA:Ljava/lang/String;

    .line 236
    invoke-virtual/range {p3 .. p3}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v2

    invoke-interface {v2}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v2

    iput-wide v2, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->timestamp:J

    .line 237
    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;-><init>()V

    .line 238
    iput-object v2, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bX:Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;

    goto :goto_0

    :cond_0
    move-object v0, v1

    move-object v2, v0

    .line 240
    :goto_0
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->ax:Lcom/google/android/apps/nexuslauncher/reflection/b/a;

    iput p2, v3, Lcom/google/android/apps/nexuslauncher/reflection/b/a;->bd:I

    .line 242
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->mEngine:Lcom/google/android/apps/nexuslauncher/reflection/g;

    invoke-virtual {v3, p1, p3}, Lcom/google/android/apps/nexuslauncher/reflection/g;->a(Ljava/lang/String;Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)Lcom/google/research/reflection/predictor/k;

    move-result-object p3

    iget-object v3, p3, Lcom/google/research/reflection/predictor/k;->fO:[D

    iget-object p3, p3, Lcom/google/research/reflection/predictor/k;->fR:Ljava/util/ArrayList;

    if-eqz v2, :cond_1

    new-instance v4, Ljava/util/ArrayList;

    invoke-direct {v4}, Ljava/util/ArrayList;-><init>()V

    new-instance v4, Ljava/util/ArrayList;

    invoke-direct {v4}, Ljava/util/ArrayList;-><init>()V

    new-instance v5, Ljava/util/ArrayList;

    invoke-direct {v5, p3}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V

    new-instance v6, Ljava/util/ArrayList;

    invoke-direct {v6}, Ljava/util/ArrayList;-><init>()V

    new-instance v7, Ljava/util/ArrayList;

    invoke-direct {v7}, Ljava/util/ArrayList;-><init>()V

    goto :goto_1

    :cond_1
    move-object v4, v1

    move-object v5, v4

    move-object v6, v5

    move-object v7, v6

    :goto_1
    iget-object v8, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->ay:Lcom/google/android/apps/nexuslauncher/reflection/b/c;

    invoke-virtual {v8, p3, v4}, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->a(Ljava/util/List;Ljava/util/List;)V

    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aw:Lcom/google/android/apps/nexuslauncher/reflection/b/d;

    invoke-virtual {v4, p3, v6}, Lcom/google/android/apps/nexuslauncher/reflection/b/d;->a(Ljava/util/List;Ljava/util/List;)V

    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->ae:Lcom/google/android/apps/nexuslauncher/reflection/b/b;

    invoke-virtual {v4, p3, v7}, Lcom/google/android/apps/nexuslauncher/reflection/b/b;->a(Ljava/util/List;Ljava/util/List;)V

    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aA:Lcom/google/android/apps/nexuslauncher/reflection/b/f;

    invoke-virtual {v4, p3, v1}, Lcom/google/android/apps/nexuslauncher/reflection/b/f;->a(Ljava/util/List;Ljava/util/List;)V

    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->az:Lcom/google/android/apps/nexuslauncher/reflection/b/e;

    invoke-virtual {v4, p3, v1}, Lcom/google/android/apps/nexuslauncher/reflection/b/e;->a(Ljava/util/List;Ljava/util/List;)V

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->ax:Lcom/google/android/apps/nexuslauncher/reflection/b/a;

    invoke-virtual {v1, p3}, Lcom/google/android/apps/nexuslauncher/reflection/b/a;->e(Ljava/util/List;)Z

    move-result v4

    if-nez v4, :cond_5

    iget-object v4, v1, Lcom/google/android/apps/nexuslauncher/reflection/b/a;->mContext:Landroid/content/Context;

    invoke-static {v4}, Lcom/google/android/apps/nexuslauncher/b/b;->c(Landroid/content/Context;)Ljava/lang/String;

    move-result-object v4

    if-eqz v4, :cond_5

    invoke-virtual {v1, p3, v4}, Lcom/google/android/apps/nexuslauncher/reflection/b/a;->a(Ljava/util/List;Ljava/lang/String;)I

    move-result v8

    const/4 v9, 0x0

    if-ltz v8, :cond_2

    invoke-interface {p3, v8}, Ljava/util/List;->remove(I)Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Lcom/google/research/reflection/predictor/k$a;

    iget-object v8, v4, Lcom/google/research/reflection/predictor/k$a;->fS:Ljava/util/Set;

    const-string v10, "instant_app_filter"

    invoke-interface {v8, v10}, Ljava/util/Set;->add(Ljava/lang/Object;)Z

    goto :goto_2

    :cond_2
    new-instance v8, Landroid/content/ComponentName;

    const-string v10, "@instantapp"

    invoke-direct {v8, v4, v10}, Landroid/content/ComponentName;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    new-instance v4, Lcom/google/research/reflection/predictor/k$a;

    invoke-virtual {v8}, Landroid/content/ComponentName;->flattenToString()Ljava/lang/String;

    move-result-object v8

    const-string v10, "instant_app_filter"

    invoke-direct {v4, v8, v9, v10}, Lcom/google/research/reflection/predictor/k$a;-><init>(Ljava/lang/String;FLjava/lang/String;)V

    :goto_2
    invoke-interface {p3}, Ljava/util/List;->size()I

    move-result v8

    iget v10, v1, Lcom/google/android/apps/nexuslauncher/reflection/b/a;->bd:I

    if-lez v10, :cond_3

    iget v1, v1, Lcom/google/android/apps/nexuslauncher/reflection/b/a;->bd:I

    add-int/lit8 v1, v1, -0x1

    goto :goto_3

    :cond_3
    const/4 v1, 0x4

    :goto_3
    invoke-static {v8, v1}, Ljava/lang/Math;->min(II)I

    move-result v1

    invoke-interface {p3}, Ljava/util/List;->size()I

    move-result v8

    if-ge v1, v8, :cond_4

    invoke-interface {p3, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v8

    check-cast v8, Lcom/google/research/reflection/predictor/k$a;

    iget v9, v8, Lcom/google/research/reflection/predictor/k$a;->ca:F

    :cond_4
    iput v9, v4, Lcom/google/research/reflection/predictor/k$a;->ca:F

    invoke-interface {p3, v1, v4}, Ljava/util/List;->add(ILjava/lang/Object;)V

    :cond_5
    if-eqz v2, :cond_7

    if-eqz v3, :cond_6

    new-instance v1, Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;

    invoke-direct {v1}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;-><init>()V

    iput-object v1, v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ch:Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;

    iget-object v1, v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ch:Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;

    iput-object v3, v1, Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;->bG:[D

    :cond_6
    invoke-direct {p0, p3}, Lcom/google/android/apps/nexuslauncher/reflection/h;->d(Ljava/util/List;)[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    move-result-object v1

    iput-object v1, v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cc:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-direct {p0, v5}, Lcom/google/android/apps/nexuslauncher/reflection/h;->d(Ljava/util/List;)[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    move-result-object v1

    iput-object v1, v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cd:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-direct {p0, v6}, Lcom/google/android/apps/nexuslauncher/reflection/h;->d(Ljava/util/List;)[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    move-result-object v1

    iput-object v1, v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cg:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-direct {p0, v7}, Lcom/google/android/apps/nexuslauncher/reflection/h;->d(Ljava/util/List;)[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    move-result-object v1

    iput-object v1, v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ce:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    :cond_7
    invoke-virtual {p3}, Ljava/util/ArrayList;->size()I

    move-result v1

    const/4 v2, 0x0

    const/16 v3, 0xc

    if-le v1, v3, :cond_8

    new-instance v1, Ljava/util/ArrayList;

    invoke-virtual {p3, v2, v3}, Ljava/util/ArrayList;->subList(II)Ljava/util/List;

    move-result-object p3

    invoke-direct {v1, p3}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V

    move-object p3, v1

    .line 250
    :cond_8
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aB:Lcom/google/android/apps/nexuslauncher/reflection/e;

    invoke-virtual {v1, p1}, Lcom/google/android/apps/nexuslauncher/reflection/e;->c(Ljava/lang/String;)Ljava/util/Map;

    move-result-object v1

    .line 252
    invoke-interface {p3}, Ljava/util/List;->size()I

    move-result v3

    if-le v3, p2, :cond_9

    .line 259
    invoke-interface {p3, v2, p2}, Ljava/util/List;->subList(II)Ljava/util/List;

    move-result-object v2

    .line 260
    invoke-interface {p3}, Ljava/util/List;->size()I

    move-result v3

    invoke-interface {p3, p2, v3}, Ljava/util/List;->subList(II)Ljava/util/List;

    move-result-object p2

    goto :goto_4

    .line 263
    :cond_9
    invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;

    move-result-object p2

    move-object v2, p3

    .line 266
    :goto_4
    invoke-interface {v1}, Ljava/util/Map;->isEmpty()Z

    move-result v3

    if-nez v3, :cond_a

    .line 267
    invoke-static {v2, v1}, Lcom/google/android/apps/nexuslauncher/reflection/h;->stabilizePredictedAppOrder(Ljava/util/List;Ljava/util/Map;)Ljava/util/List;

    move-result-object v2

    .line 270
    :cond_a
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aB:Lcom/google/android/apps/nexuslauncher/reflection/e;

    new-instance v3, Ljava/util/ArrayList;

    invoke-interface {v2}, Ljava/util/List;->size()I

    move-result v4

    invoke-interface {p2}, Ljava/util/List;->size()I

    move-result v5

    add-int/2addr v4, v5

    invoke-direct {v3, v4}, Ljava/util/ArrayList;-><init>(I)V

    invoke-interface {v3, v2}, Ljava/util/List;->addAll(Ljava/util/Collection;)Z

    invoke-interface {v3, p2}, Ljava/util/List;->addAll(Ljava/util/Collection;)Z

    invoke-static {v3}, Lcom/google/android/apps/nexuslauncher/reflection/e;->c(Ljava/util/List;)Ljava/lang/String;

    move-result-object p2

    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v3

    invoke-static {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e;->b(Ljava/util/List;)Ljava/lang/String;

    move-result-object v2

    invoke-static {p3}, Lcom/google/android/apps/nexuslauncher/reflection/e;->c(Ljava/util/List;)Ljava/lang/String;

    move-result-object p3

    invoke-static/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->d(Ljava/lang/String;)Lcom/google/android/apps/nexuslauncher/reflection/a/d;

    move-result-object p1

    iget-object v5, v1, Lcom/google/android/apps/nexuslauncher/reflection/e;->o:Landroid/content/SharedPreferences;

    iget-object v6, p1, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->bc:Ljava/lang/String;

    const-string v7, ""

    invoke-interface {v5, v6, v7}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v5

    invoke-virtual {p3, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v5

    if-nez v5, :cond_b

    iget-object v1, v1, Lcom/google/android/apps/nexuslauncher/reflection/e;->o:Landroid/content/SharedPreferences;

    invoke-interface {v1}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v1

    iget-object v5, p1, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->aZ:Ljava/lang/String;

    invoke-interface {v1, v5, p2}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object p2

    iget-object v1, p1, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->ba:Ljava/lang/String;

    invoke-interface {p2, v1, v3, v4}, Landroid/content/SharedPreferences$Editor;->putLong(Ljava/lang/String;J)Landroid/content/SharedPreferences$Editor;

    move-result-object p2

    iget-object v1, p1, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->bb:Ljava/lang/String;

    invoke-interface {p2, v1, v2}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object p2

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/a/d;->bc:Ljava/lang/String;

    invoke-interface {p2, p1, p3}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object p1

    invoke-interface {p1}, Landroid/content/SharedPreferences$Editor;->apply()V

    :cond_b
    if-eqz v0, :cond_c

    .line 273
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->aa:Lcom/google/android/apps/nexuslauncher/reflection/d/d;

    invoke-virtual {p1, v0}, Lcom/google/android/apps/nexuslauncher/reflection/d/d;->a(Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;)V

    :cond_c
    return-void
.end method

.method updateNewEventTesting(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)V
    .locals 1

    .line 130
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    .line 131
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/h;->mEngine:Lcom/google/android/apps/nexuslauncher/reflection/g;

    invoke-virtual {v0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/g;->b(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)V

    return-void
.end method
