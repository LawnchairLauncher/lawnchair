.class public Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;
.super Lcom/android/quickstep/logging/UserEventDispatcherExtension;
.source "SourceFile"


# instance fields
.field protected final P:Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;

.field protected final Q:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;

.field protected final R:Lcom/android/quickstep/OverviewInteractionState;

.field protected final S:Ljava/lang/ThreadLocal;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/lang/ThreadLocal<",
            "Lcom/android/launcher3/util/ComponentKey;",
            ">;"
        }
    .end annotation
.end field

.field protected final mContext:Landroid/content/Context;


# direct methods
.method public constructor <init>(Landroid/content/Context;)V
    .locals 1

    .line 54
    invoke-direct/range {p0 .. p1}, Lcom/android/quickstep/logging/UserEventDispatcherExtension;-><init>(Landroid/content/Context;)V

    .line 51
    new-instance v0, Ljava/lang/ThreadLocal;

    invoke-direct {v0}, Ljava/lang/ThreadLocal;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->S:Ljava/lang/ThreadLocal;

    .line 55
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->mContext:Landroid/content/Context;

    .line 56
    invoke-static/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->getInstance(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->P:Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;

    .line 57
    invoke-static/range {p1 .. p1}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->getInstance(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->Q:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;

    .line 58
    invoke-static/range {p1 .. p1}, Lcom/android/quickstep/OverviewInteractionState;->getInstance(Landroid/content/Context;)Lcom/android/quickstep/OverviewInteractionState;

    move-result-object p1

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->R:Lcom/android/quickstep/OverviewInteractionState;

    return-void
.end method


# virtual methods
.method public dispatchUserEvent(Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;Landroid/content/Intent;)V
    .locals 4

    .line 131
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->S:Ljava/lang/ThreadLocal;

    invoke-virtual {v0}, Ljava/lang/ThreadLocal;->get()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/android/launcher3/util/ComponentKey;

    .line 132
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->Q:Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;

    invoke-virtual {v1}, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager;->getCurrentState()Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;

    move-result-object v1

    const/4 v2, 0x0

    if-eqz v0, :cond_0

    .line 134
    iget-boolean v3, v1, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->isEnabled:Z

    if-eqz v3, :cond_0

    .line 135
    iget-object v3, p1, Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;->srcTarget:[Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;

    aget-object v3, v3, v2

    iget-object v1, v1, Lcom/google/android/apps/nexuslauncher/PredictionUiStateManager$PredictionState;->orderedApps:Ljava/util/List;

    invoke-interface {v1, v0}, Ljava/util/List;->indexOf(Ljava/lang/Object;)I

    move-result v1

    iput v1, v3, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;->predictedRank:I

    .line 137
    :cond_0
    invoke-super/range {p0 .. p2}, Lcom/android/quickstep/logging/UserEventDispatcherExtension;->dispatchUserEvent(Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;Landroid/content/Intent;)V

    if-eqz v0, :cond_3

    .line 141
    instance-of p2, v0, Lcom/android/launcher3/shortcuts/ShortcutKey;

    if-eqz p2, :cond_1

    .line 142
    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->P:Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;

    check-cast v0, Lcom/android/launcher3/shortcuts/ShortcutKey;

    invoke-virtual {p2, v0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->onShortcutLaunch(Lcom/android/launcher3/shortcuts/ShortcutKey;Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;)V

    goto :goto_0

    .line 143
    :cond_1
    iget-object p2, v0, Lcom/android/launcher3/util/ComponentKey;->componentName:Landroid/content/ComponentName;

    invoke-virtual {p2}, Landroid/content/ComponentName;->getClassName()Ljava/lang/String;

    move-result-object p2

    const-string v1, "@instantapp"

    invoke-virtual {p2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p2

    if-eqz p2, :cond_2

    .line 144
    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->P:Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;

    invoke-virtual {p2, v0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->onInstantAppLaunch(Lcom/android/launcher3/util/ComponentKey;Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;)V

    goto :goto_0

    .line 146
    :cond_2
    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->P:Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;

    invoke-virtual {p2, v0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/ReflectionClient;->onAppLaunch(Lcom/android/launcher3/util/ComponentKey;Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;)V

    .line 150
    :cond_3
    :goto_0
    iget-object p2, p1, Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;->action:Lcom/android/launcher3/userevent/nano/LauncherLogProto$Action;

    iget p2, p2, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Action;->command:I

    if-nez p2, :cond_4

    iget-object p2, p1, Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;->destTarget:[Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;

    array-length p2, p2

    if-lez p2, :cond_4

    iget-object p1, p1, Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;->destTarget:[Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;

    aget-object p1, p1, v2

    iget p1, p1, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;->containerType:I

    const/4 p2, 0x4

    if-ne p1, p2, :cond_4

    .line 154
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->mContext:Landroid/content/Context;

    invoke-static {p1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->get(Landroid/content/Context;)Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;

    move-result-object p1

    invoke-virtual {p1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->getLogger()Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;

    move-result-object p1

    invoke-virtual {p1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;->logImpression()V

    :cond_4
    return-void
.end method

.method public logActionTip(II)V
    .locals 5

    const/4 v0, 0x2

    if-eq p2, v0, :cond_0

    .line 103
    invoke-super/range {p0 .. p2}, Lcom/android/quickstep/logging/UserEventDispatcherExtension;->logActionTip(II)V

    return-void

    .line 106
    :cond_0
    new-instance p2, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Action;

    invoke-direct {p2}, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Action;-><init>()V

    .line 107
    new-instance v1, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;

    invoke-direct {v1}, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;-><init>()V

    const/16 v2, 0xe

    const/4 v3, 0x0

    packed-switch p1, :pswitch_data_0

    const-string v0, "UserEventConsumer"

    .line 121
    new-instance v2, Ljava/lang/StringBuilder;

    const-string v4, "Unexpected action type = "

    invoke-direct {v2, v4}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {v2, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-static {v0, p1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    goto :goto_0

    .line 115
    :pswitch_0
    iput v3, p2, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Action;->type:I

    .line 116
    iput v3, p2, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Action;->touch:I

    .line 117
    iput v0, v1, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;->type:I

    .line 118
    iput v2, v1, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;->controlType:I

    goto :goto_0

    :pswitch_1
    const/4 p1, 0x3

    .line 110
    iput p1, p2, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Action;->type:I

    .line 111
    iput p1, v1, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;->type:I

    .line 112
    iput v2, v1, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;->containerType:I

    :goto_0
    const/4 p1, 0x4

    .line 124
    iput p1, v1, Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;->tipType:I

    const/4 p1, 0x1

    .line 125
    new-array p1, p1, [Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;

    aput-object v1, p1, v3

    invoke-static {p2, p1}, Lcom/android/launcher3/logging/LoggerUtils;->newLauncherEvent(Lcom/android/launcher3/userevent/nano/LauncherLogProto$Action;[Lcom/android/launcher3/userevent/nano/LauncherLogProto$Target;)Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;

    move-result-object p1

    const/4 p2, 0x0

    .line 126
    invoke-virtual {p0, p1, p2}, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->dispatchUserEvent(Lcom/android/launcher3/userevent/nano/LauncherLogProto$LauncherEvent;Landroid/content/Intent;)V

    return-void

    :pswitch_data_0
    .packed-switch 0x0
        :pswitch_1
        :pswitch_0
    .end packed-switch
.end method

.method public logAppLaunch(Landroid/view/View;Landroid/content/Intent;)V
    .locals 6

    const/4 v0, 0x0

    if-eqz p1, :cond_3

    .line 63
    invoke-virtual/range {p1 .. p1}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v1

    instance-of v1, v1, Lcom/android/launcher3/ItemInfo;

    if-eqz v1, :cond_3

    if-eqz p2, :cond_3

    .line 64
    invoke-static/range {p2 .. p2}, Lcom/android/launcher3/Utilities;->isLauncherAppTarget(Landroid/content/Intent;)Z

    move-result v1

    if-eqz v1, :cond_0

    .line 65
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->S:Ljava/lang/ThreadLocal;

    new-instance v2, Lcom/android/launcher3/util/ComponentKey;

    .line 66
    invoke-virtual/range {p2 .. p2}, Landroid/content/Intent;->getComponent()Landroid/content/ComponentName;

    move-result-object v3

    invoke-virtual/range {p1 .. p1}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Lcom/android/launcher3/ItemInfo;

    iget-object v4, v4, Lcom/android/launcher3/ItemInfo;->user:Landroid/os/UserHandle;

    invoke-direct {v2, v3, v4}, Lcom/android/launcher3/util/ComponentKey;-><init>(Landroid/content/ComponentName;Landroid/os/UserHandle;)V

    .line 65
    invoke-virtual {v1, v2}, Ljava/lang/ThreadLocal;->set(Ljava/lang/Object;)V

    goto :goto_1

    .line 67
    :cond_0
    invoke-virtual/range {p1 .. p1}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/android/launcher3/ItemInfo;

    iget v1, v1, Lcom/android/launcher3/ItemInfo;->itemType:I

    const/4 v2, 0x6

    if-ne v1, v2, :cond_2

    .line 68
    invoke-virtual/range {p2 .. p2}, Landroid/content/Intent;->getExtras()Landroid/os/Bundle;

    move-result-object v1

    if-eqz v1, :cond_1

    const-string v2, "shortcut_id"

    .line 69
    invoke-virtual {v1, v2}, Landroid/os/Bundle;->getString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    goto :goto_0

    :cond_1
    move-object v1, v0

    :goto_0
    if-eqz v1, :cond_3

    .line 71
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->S:Ljava/lang/ThreadLocal;

    new-instance v3, Lcom/android/launcher3/shortcuts/ShortcutKey;

    .line 73
    invoke-virtual/range {p2 .. p2}, Landroid/content/Intent;->getPackage()Ljava/lang/String;

    move-result-object v4

    invoke-virtual/range {p1 .. p1}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v5

    check-cast v5, Lcom/android/launcher3/ItemInfo;

    iget-object v5, v5, Lcom/android/launcher3/ItemInfo;->user:Landroid/os/UserHandle;

    invoke-direct {v3, v4, v5, v1}, Lcom/android/launcher3/shortcuts/ShortcutKey;-><init>(Ljava/lang/String;Landroid/os/UserHandle;Ljava/lang/String;)V

    .line 71
    invoke-virtual {v2, v3}, Ljava/lang/ThreadLocal;->set(Ljava/lang/Object;)V

    goto :goto_1

    .line 76
    :cond_2
    invoke-virtual/range {p1 .. p1}, Landroid/view/View;->getTag()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/android/launcher3/ItemInfo;

    .line 77
    invoke-virtual {v1}, Lcom/android/launcher3/ItemInfo;->getTargetComponent()Landroid/content/ComponentName;

    move-result-object v2

    if-eqz v2, :cond_3

    .line 79
    invoke-virtual {v2}, Landroid/content/ComponentName;->getClassName()Ljava/lang/String;

    move-result-object v3

    const-string v4, "@instantapp"

    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_3

    .line 80
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->S:Ljava/lang/ThreadLocal;

    new-instance v4, Lcom/android/launcher3/util/ComponentKey;

    iget-object v1, v1, Lcom/android/launcher3/ItemInfo;->user:Landroid/os/UserHandle;

    invoke-direct {v4, v2, v1}, Lcom/android/launcher3/util/ComponentKey;-><init>(Landroid/content/ComponentName;Landroid/os/UserHandle;)V

    invoke-virtual {v3, v4}, Ljava/lang/ThreadLocal;->set(Ljava/lang/Object;)V

    .line 84
    :cond_3
    :goto_1
    invoke-super/range {p0 .. p2}, Lcom/android/quickstep/logging/UserEventDispatcherExtension;->logAppLaunch(Landroid/view/View;Landroid/content/Intent;)V

    .line 85
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->S:Ljava/lang/ThreadLocal;

    invoke-virtual {p1, v0}, Ljava/lang/ThreadLocal;->set(Ljava/lang/Object;)V

    return-void
.end method

.method public logTaskLaunchOrDismiss(IIILcom/android/launcher3/util/ComponentKey;)V
    .locals 1

    if-eqz p1, :cond_2

    const/4 v0, 0x3

    if-eq p1, v0, :cond_0

    const/4 v0, 0x4

    if-ne p1, v0, :cond_1

    :cond_0
    const/4 v0, 0x2

    if-ne p2, v0, :cond_1

    goto :goto_0

    :cond_1
    const/4 v0, 0x0

    goto :goto_1

    :cond_2
    :goto_0
    const/4 v0, 0x1

    :goto_1
    if-nez v0, :cond_3

    .line 92
    invoke-super/range {p0 .. p4}, Lcom/android/quickstep/logging/UserEventDispatcherExtension;->logTaskLaunchOrDismiss(IIILcom/android/launcher3/util/ComponentKey;)V

    return-void

    .line 95
    :cond_3
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->S:Ljava/lang/ThreadLocal;

    invoke-virtual {v0, p4}, Ljava/lang/ThreadLocal;->set(Ljava/lang/Object;)V

    .line 96
    invoke-super/range {p0 .. p4}, Lcom/android/quickstep/logging/UserEventDispatcherExtension;->logTaskLaunchOrDismiss(IIILcom/android/launcher3/util/ComponentKey;)V

    .line 97
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/logging/UserEventConsumer;->S:Ljava/lang/ThreadLocal;

    const/4 p2, 0x0

    invoke-virtual {p1, p2}, Ljava/lang/ThreadLocal;->set(Ljava/lang/Object;)V

    return-void
.end method
