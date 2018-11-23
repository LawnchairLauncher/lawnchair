.class public Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;
.super Landroid/content/BroadcastReceiver;
.source "SourceFile"

# interfaces
.implements Lcom/google/android/apps/nexuslauncher/reflection/h$a;
.implements Lcom/google/research/reflection/c/a;


# instance fields
.field private cH:Z

.field private cI:J

.field private cJ:Z

.field private cK:J

.field private final cL:Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/List<",
            "Lcom/google/research/reflection/c/c;",
            ">;"
        }
    .end annotation
.end field

.field private final mContext:Landroid/content/Context;


# direct methods
.method protected constructor <init>()V
    .locals 2

    .line 173
    invoke-direct/range {p0 .. p0}, Landroid/content/BroadcastReceiver;-><init>()V

    const-wide/16 v0, 0x0

    .line 52
    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cI:J

    .line 54
    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cK:J

    .line 174
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cL:Ljava/util/List;

    const/4 v0, 0x0

    .line 175
    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->mContext:Landroid/content/Context;

    return-void
.end method

.method public constructor <init>(Landroid/content/Context;)V
    .locals 3

    .line 58
    invoke-direct/range {p0 .. p0}, Landroid/content/BroadcastReceiver;-><init>()V

    const-wide/16 v0, 0x0

    .line 52
    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cI:J

    .line 54
    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cK:J

    .line 59
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->mContext:Landroid/content/Context;

    .line 60
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cL:Ljava/util/List;

    .line 64
    new-instance v0, Landroid/content/IntentFilter;

    const-string v1, "android.intent.action.HEADSET_PLUG"

    invoke-direct {v0, v1}, Landroid/content/IntentFilter;-><init>(Ljava/lang/String;)V

    const-string v1, "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"

    .line 65
    invoke-virtual {v0, v1}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    .line 66
    new-instance v1, Landroid/os/Handler;

    invoke-direct {v1}, Landroid/os/Handler;-><init>()V

    const/4 v2, 0x0

    invoke-virtual {p1, p0, v0, v2, v1}, Landroid/content/Context;->registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;Ljava/lang/String;Landroid/os/Handler;)Landroid/content/Intent;

    const-string v0, "audio"

    .line 69
    invoke-virtual {p1, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Landroid/media/AudioManager;

    .line 70
    invoke-virtual {p1}, Landroid/media/AudioManager;->isWiredHeadsetOn()Z

    move-result v0

    iput-boolean v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cH:Z

    .line 71
    invoke-virtual {p1}, Landroid/media/AudioManager;->isBluetoothA2dpOn()Z

    move-result v0

    if-nez v0, :cond_1

    invoke-virtual {p1}, Landroid/media/AudioManager;->isBluetoothScoOn()Z

    move-result p1

    if-eqz p1, :cond_0

    goto :goto_0

    :cond_0
    const/4 p1, 0x0

    goto :goto_1

    :cond_1
    :goto_0
    const/4 p1, 0x1

    :goto_1
    iput-boolean p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cJ:Z

    return-void
.end method

.method private B()V
    .locals 2

    .line 163
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->mContext:Landroid/content/Context;

    invoke-static {v0}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->getInstance(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;

    move-result-object v0

    const-string v1, "GEL"

    invoke-virtual {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->updatePredictionsNow(Ljava/lang/String;)V

    .line 164
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->mContext:Landroid/content/Context;

    invoke-static {v0}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->getInstance(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;

    move-result-object v0

    const-string v1, "OVERVIEW_GEL"

    invoke-virtual {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->updatePredictionsNow(Ljava/lang/String;)V

    return-void
.end method

.method private b(J)V
    .locals 1

    .line 87
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->mContext:Landroid/content/Context;

    invoke-static {v0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->e(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;

    move-result-object v0

    if-eqz v0, :cond_0

    .line 89
    invoke-virtual {v0, p1, p2}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/UsageEventSensor;->d(J)V

    :cond_0
    return-void
.end method

.method private d(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)V
    .locals 3

    .line 81
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cL:Ljava/util/List;

    invoke-interface {v0}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_0
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_0

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/c/c;

    .line 82
    invoke-virtual/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->I()Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    move-result-object v2

    invoke-interface {v1, v2}, Lcom/google/research/reflection/c/c;->a(Lcom/google/research/reflection/signal/ReflectionEvent;)V

    goto :goto_0

    :cond_0
    return-void
.end method


# virtual methods
.method public final A()V
    .locals 6

    .line 95
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cI:J

    const-wide/16 v2, 0x0

    cmp-long v0, v0, v2

    if-lez v0, :cond_1

    .line 96
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>()V

    .line 97
    sget-object v1, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->ga:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    invoke-virtual {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;)Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 98
    new-instance v1, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;

    invoke-direct {v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;-><init>()V

    iget-wide v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cI:J

    invoke-virtual {v1, v4, v5}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->e(J)Lcom/google/research/reflection/signal/d;

    move-result-object v1

    invoke-virtual {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/d;)Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 99
    iget-boolean v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cH:Z

    if-eqz v1, :cond_0

    const-string v1, "headset_wired_in"

    goto :goto_0

    :cond_0
    const-string v1, "headset_wired_out"

    :goto_0
    invoke-virtual {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->g(Ljava/lang/String;)Lcom/google/research/reflection/signal/ReflectionEvent;

    const-string v1, "HeadsetStatusSensor"

    .line 100
    invoke-virtual {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->h(Ljava/lang/String;)Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    .line 101
    invoke-virtual {v0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v4

    invoke-direct {p0, v4, v5}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->b(J)V

    .line 102
    invoke-direct {p0, v0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->d(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)V

    .line 104
    :cond_1
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cK:J

    cmp-long v0, v0, v2

    if-lez v0, :cond_3

    .line 105
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>()V

    .line 106
    sget-object v1, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->ga:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    invoke-virtual {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;)Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 107
    new-instance v1, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;

    invoke-direct {v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;-><init>()V

    iget-wide v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cK:J

    invoke-virtual {v1, v2, v3}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->e(J)Lcom/google/research/reflection/signal/d;

    move-result-object v1

    invoke-virtual {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/d;)Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 108
    iget-boolean v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cJ:Z

    if-eqz v1, :cond_2

    const-string v1, "headset_bluetooth_in"

    goto :goto_1

    :cond_2
    const-string v1, "headset_bluetooth_out"

    :goto_1
    invoke-virtual {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->g(Ljava/lang/String;)Lcom/google/research/reflection/signal/ReflectionEvent;

    const-string v1, "HeadsetStatusSensor"

    .line 109
    invoke-virtual {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->h(Ljava/lang/String;)Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    .line 110
    invoke-virtual {v0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v1

    invoke-direct {p0, v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->b(J)V

    .line 111
    invoke-direct {p0, v0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->d(Lcom/google/android/apps/nexuslauncher/reflection/signal/a;)V

    :cond_3
    return-void
.end method

.method public final a(Lcom/google/research/reflection/c/c;)Lcom/google/research/reflection/c/a;
    .locals 1

    .line 76
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cL:Ljava/util/List;

    invoke-interface {v0, p1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    return-object p0
.end method

.method protected final c(Z)V
    .locals 2

    .line 149
    iput-boolean p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cH:Z

    .line 150
    invoke-static {}, Ljava/util/Calendar;->getInstance()Ljava/util/Calendar;

    move-result-object p1

    invoke-virtual {p1}, Ljava/util/Calendar;->getTimeInMillis()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cI:J

    .line 151
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->A()V

    .line 152
    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->B()V

    return-void
.end method

.method protected final d(Z)V
    .locals 2

    .line 156
    iput-boolean p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cJ:Z

    .line 157
    invoke-static {}, Ljava/util/Calendar;->getInstance()Ljava/util/Calendar;

    move-result-object p1

    invoke-virtual {p1}, Ljava/util/Calendar;->getTimeInMillis()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->cK:J

    .line 158
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->A()V

    .line 159
    invoke-direct/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->B()V

    return-void
.end method

.method public final h()V
    .locals 1

    .line 169
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->mContext:Landroid/content/Context;

    invoke-virtual {v0, p0}, Landroid/content/Context;->unregisterReceiver(Landroid/content/BroadcastReceiver;)V

    return-void
.end method

.method public onReceive(Landroid/content/Context;Landroid/content/Intent;)V
    .locals 4

    .line 123
    invoke-virtual/range {p2 .. p2}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object p1

    const-string v0, "android.intent.action.HEADSET_PLUG"

    invoke-virtual {p1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    const/4 v0, 0x1

    const/4 v1, 0x0

    const/4 v2, -0x1

    if-eqz p1, :cond_0

    .line 124
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->isInitialStickyBroadcast()Z

    move-result p1

    if-nez p1, :cond_0

    const-string p1, "state"

    .line 125
    invoke-virtual {p2, p1, v2}, Landroid/content/Intent;->getIntExtra(Ljava/lang/String;I)I

    move-result p1

    packed-switch p1, :pswitch_data_0

    goto :goto_0

    .line 128
    :pswitch_0
    invoke-virtual {p0, v0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->c(Z)V

    return-void

    .line 131
    :pswitch_1
    invoke-virtual {p0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->c(Z)V

    :goto_0
    return-void

    .line 134
    :cond_0
    invoke-virtual/range {p2 .. p2}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object p1

    const-string v3, "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"

    invoke-virtual {p1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_3

    .line 135
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->isInitialStickyBroadcast()Z

    move-result p1

    if-nez p1, :cond_3

    const-string p1, "android.bluetooth.profile.extra.STATE"

    .line 136
    invoke-virtual {p2, p1, v2}, Landroid/content/Intent;->getIntExtra(Ljava/lang/String;I)I

    move-result p1

    if-eqz p1, :cond_2

    const/4 p2, 0x2

    if-eq p1, p2, :cond_1

    goto :goto_1

    .line 139
    :cond_1
    invoke-virtual {p0, v0}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->d(Z)V

    return-void

    .line 142
    :cond_2
    invoke-virtual {p0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/a;->d(Z)V

    :cond_3
    :goto_1
    return-void

    nop

    :pswitch_data_0
    .packed-switch 0x0
        :pswitch_1
        :pswitch_0
    .end packed-switch
.end method
