.class public final synthetic Lcom/google/android/apps/nexuslauncher/reflection/sensing/-$$Lambda$NotificationSensor$DJfaQHaP0-43iDKaKbOBJVymlqM;
.super Ljava/lang/Object;
.source "lambda"

# interfaces
.implements Ljava/lang/Runnable;


# instance fields
.field private final synthetic f$0:Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;

.field private final synthetic f$1:Landroid/service/notification/StatusBarNotification;


# direct methods
.method public synthetic constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;Landroid/service/notification/StatusBarNotification;)V
    .locals 0

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/-$$Lambda$NotificationSensor$DJfaQHaP0-43iDKaKbOBJVymlqM;->f$0:Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;

    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/-$$Lambda$NotificationSensor$DJfaQHaP0-43iDKaKbOBJVymlqM;->f$1:Landroid/service/notification/StatusBarNotification;

    return-void
.end method


# virtual methods
.method public final run()V
    .locals 2

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/-$$Lambda$NotificationSensor$DJfaQHaP0-43iDKaKbOBJVymlqM;->f$0:Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/sensing/-$$Lambda$NotificationSensor$DJfaQHaP0-43iDKaKbOBJVymlqM;->f$1:Landroid/service/notification/StatusBarNotification;

    invoke-static {v0, v1}, Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;->lambda$DJfaQHaP0-43iDKaKbOBJVymlqM(Lcom/google/android/apps/nexuslauncher/reflection/sensing/NotificationSensor;Landroid/service/notification/StatusBarNotification;)V

    return-void
.end method
