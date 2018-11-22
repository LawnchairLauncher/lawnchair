.class public Lcom/google/android/apps/nexuslauncher/allapps/Action;
.super Ljava/lang/Object;
.source "SourceFile"


# instance fields
.field public final badgePackage:Ljava/lang/String;

.field public final contentDescription:Ljava/lang/CharSequence;

.field public final expirationTimeMillis:J

.field public final id:Ljava/lang/String;

.field public isEnabled:Z

.field public final openingPackageDescription:Ljava/lang/CharSequence;

.field public final position:J

.field public final publisherPackage:Ljava/lang/String;

.field public final shortcut:Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;

.field public final shortcutId:Ljava/lang/String;

.field public final shortcutInfo:Lcom/android/launcher3/ShortcutInfo;


# direct methods
.method public constructor <init>(Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;Ljava/lang/String;Ljava/lang/CharSequence;Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;Lcom/android/launcher3/ShortcutInfo;J)V
    .locals 1

    .line 22
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    const/4 v0, 0x0

    .line 18
    iput-boolean v0, p0, Lcom/google/android/apps/nexuslauncher/allapps/Action;->isEnabled:Z

    .line 23
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/Action;->id:Ljava/lang/String;

    .line 24
    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/allapps/Action;->shortcutId:Ljava/lang/String;

    .line 25
    iput-wide p3, p0, Lcom/google/android/apps/nexuslauncher/allapps/Action;->expirationTimeMillis:J

    .line 26
    iput-object p5, p0, Lcom/google/android/apps/nexuslauncher/allapps/Action;->publisherPackage:Ljava/lang/String;

    .line 27
    iput-object p6, p0, Lcom/google/android/apps/nexuslauncher/allapps/Action;->badgePackage:Ljava/lang/String;

    .line 28
    iput-object p7, p0, Lcom/google/android/apps/nexuslauncher/allapps/Action;->openingPackageDescription:Ljava/lang/CharSequence;

    .line 29
    iput-object p8, p0, Lcom/google/android/apps/nexuslauncher/allapps/Action;->shortcut:Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;

    .line 30
    iput-object p9, p0, Lcom/google/android/apps/nexuslauncher/allapps/Action;->shortcutInfo:Lcom/android/launcher3/ShortcutInfo;

    .line 31
    iput-wide p10, p0, Lcom/google/android/apps/nexuslauncher/allapps/Action;->position:J

    .line 32
    iget-object p1, p9, Lcom/android/launcher3/ShortcutInfo;->contentDescription:Ljava/lang/CharSequence;

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/allapps/Action;->contentDescription:Ljava/lang/CharSequence;

    return-void
.end method


# virtual methods
.method public toString()Ljava/lang/String;
    .locals 3

    .line 37
    new-instance v0, Ljava/lang/StringBuilder;

    const-string v1, "{"

    invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/allapps/Action;->id:Ljava/lang/String;

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, ","

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/allapps/Action;->shortcut:Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;

    invoke-virtual {v1}, Lcom/android/launcher3/shortcuts/ShortcutInfoCompat;->getShortLabel()Ljava/lang/CharSequence;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    const-string v1, ","

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-wide v1, p0, Lcom/google/android/apps/nexuslauncher/allapps/Action;->position:J

    invoke-virtual {v0, v1, v2}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    const-string v1, "}"

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method
