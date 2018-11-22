.class public Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x9
    name = "Logger"
.end annotation

.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;
    }
.end annotation


# static fields
.field public static final CLICK_EVENT_TYPE:I = 0x1

.field public static final DISMISS_EVENT_TYPE:I = 0x3

.field public static final IMPRESSION_EVENT_TYPE:I = 0x2


# instance fields
.field private C:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;


# direct methods
.method constructor <init>(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)V
    .locals 0

    .line 574
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 575
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;->C:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;

    return-void
.end method


# virtual methods
.method public logClick(Ljava/lang/String;I)V
    .locals 4

    .line 603
    new-instance v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;

    const/4 v1, 0x0

    invoke-direct {v0, v1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;-><init>(B)V

    .line 604
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v2

    iput-wide v2, v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->ts:J

    const/4 v2, 0x1

    .line 605
    iput v2, v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->D:I

    if-eqz p1, :cond_0

    goto :goto_0

    :cond_0
    const-string p1, ""

    .line 606
    :goto_0
    iput-object p1, v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->E:Ljava/lang/String;

    add-int/2addr p2, v2

    .line 607
    iput p2, v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->F:I

    .line 608
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;->C:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;

    invoke-static {p1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->c(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)Landroid/os/Handler;

    move-result-object p1

    invoke-static {p1, v1, v1, v1, v0}, Landroid/os/Message;->obtain(Landroid/os/Handler;IIILjava/lang/Object;)Landroid/os/Message;

    move-result-object p1

    .line 609
    invoke-virtual {p1}, Landroid/os/Message;->sendToTarget()V

    return-void
.end method

.method public logDismiss(Ljava/lang/String;)V
    .locals 4

    .line 613
    new-instance v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;

    const/4 v1, 0x0

    invoke-direct {v0, v1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;-><init>(B)V

    .line 614
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v2

    iput-wide v2, v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->ts:J

    const/4 v2, 0x3

    .line 615
    iput v2, v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->D:I

    if-eqz p1, :cond_0

    goto :goto_0

    :cond_0
    const-string p1, ""

    .line 616
    :goto_0
    iput-object p1, v0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger$Data;->E:Ljava/lang/String;

    .line 617
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;->C:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;

    invoke-static {p1}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->c(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)Landroid/os/Handler;

    move-result-object p1

    invoke-static {p1, v1, v1, v1, v0}, Landroid/os/Message;->obtain(Landroid/os/Handler;IIILjava/lang/Object;)Landroid/os/Message;

    move-result-object p1

    .line 618
    invoke-virtual {p1}, Landroid/os/Message;->sendToTarget()V

    return-void
.end method

.method public logImpression()V
    .locals 14

    .line 580
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;->C:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;

    invoke-static {v0}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->d(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)Landroid/content/SharedPreferences;

    move-result-object v0

    .line 581
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v1

    .line 582
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;->C:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;

    invoke-static {v3}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->e(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)Ljava/util/ArrayList;

    move-result-object v3

    invoke-virtual {v3}, Ljava/util/ArrayList;->size()I

    move-result v3

    const/4 v4, 0x2

    invoke-static {v3, v4}, Ljava/lang/Math;->min(II)I

    move-result v3

    const/4 v4, 0x0

    const/4 v5, 0x0

    :goto_0
    if-ge v5, v3, :cond_3

    .line 584
    iget-object v6, p0, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController$Logger;->C:Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;

    invoke-static {v6}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->e(Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;)Ljava/util/ArrayList;

    move-result-object v6

    invoke-virtual {v6, v5}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Lcom/google/android/apps/nexuslauncher/allapps/Action;

    iget-object v6, v6, Lcom/google/android/apps/nexuslauncher/allapps/Action;->id:Ljava/lang/String;

    if-eqz v6, :cond_2

    .line 586
    invoke-interface {v0, v6}, Landroid/content/SharedPreferences;->contains(Ljava/lang/String;)Z

    move-result v7

    if-eqz v7, :cond_1

    const-string v7, ""

    .line 588
    invoke-interface {v0, v6, v7}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v7

    const-string v8, ","

    .line 589
    invoke-virtual {v7, v8}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;

    move-result-object v8

    .line 590
    aget-object v9, v8, v4

    invoke-static {v9}, Ljava/lang/Long;->parseLong(Ljava/lang/String;)J

    move-result-wide v9

    const/4 v11, 0x1

    .line 591
    :goto_1
    array-length v12, v8

    if-ge v11, v12, :cond_0

    .line 592
    aget-object v12, v8, v11

    invoke-static {v12}, Ljava/lang/Long;->parseLong(Ljava/lang/String;)J

    move-result-wide v12

    add-long/2addr v9, v12

    add-int/lit8 v11, v11, 0x1

    goto :goto_1

    :cond_0
    sub-long v8, v1, v9

    .line 595
    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v10

    new-instance v11, Ljava/lang/StringBuilder;

    invoke-direct {v11}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v11, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v7, ","

    invoke-virtual {v11, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v11, v8, v9}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    invoke-virtual {v11}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v7

    invoke-interface {v10, v6, v7}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object v6

    invoke-interface {v6}, Landroid/content/SharedPreferences$Editor;->apply()V

    goto :goto_2

    .line 597
    :cond_1
    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v7

    invoke-static {v1, v2}, Ljava/lang/String;->valueOf(J)Ljava/lang/String;

    move-result-object v8

    invoke-interface {v7, v6, v8}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object v6

    invoke-interface {v6}, Landroid/content/SharedPreferences$Editor;->apply()V

    :cond_2
    :goto_2
    add-int/lit8 v5, v5, 0x1

    goto :goto_0

    :cond_3
    return-void
.end method
