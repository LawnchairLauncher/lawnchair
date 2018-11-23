.class Lcom/google/android/apps/nexuslauncher/reflection/a$1;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Landroid/app/PendingIntent$OnFinished;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/google/android/apps/nexuslauncher/reflection/a;->initRecordedTime(Landroid/content/Context;I)J
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic U:Landroid/util/MutableLong;

.field final synthetic V:Ljava/util/concurrent/CountDownLatch;

.field final synthetic W:Lcom/google/android/apps/nexuslauncher/reflection/a;


# direct methods
.method constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/a;Landroid/util/MutableLong;Ljava/util/concurrent/CountDownLatch;)V
    .locals 0

    .line 59
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/a$1;->W:Lcom/google/android/apps/nexuslauncher/reflection/a;

    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/a$1;->U:Landroid/util/MutableLong;

    iput-object p3, p0, Lcom/google/android/apps/nexuslauncher/reflection/a$1;->V:Ljava/util/concurrent/CountDownLatch;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public onSendFinished(Landroid/app/PendingIntent;Landroid/content/Intent;ILjava/lang/String;Landroid/os/Bundle;)V
    .locals 0

    .line 63
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/a$1;->U:Landroid/util/MutableLong;

    const-string p3, "time"

    iget-object p4, p0, Lcom/google/android/apps/nexuslauncher/reflection/a$1;->U:Landroid/util/MutableLong;

    iget-wide p4, p4, Landroid/util/MutableLong;->value:J

    invoke-virtual {p2, p3, p4, p5}, Landroid/content/Intent;->getLongExtra(Ljava/lang/String;J)J

    move-result-wide p2

    iput-wide p2, p1, Landroid/util/MutableLong;->value:J

    .line 64
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/a$1;->V:Ljava/util/concurrent/CountDownLatch;

    invoke-virtual {p1}, Ljava/util/concurrent/CountDownLatch;->countDown()V

    return-void
.end method
