.class Lcom/google/android/apps/nexuslauncher/a/a$a;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/a/a;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x2
    name = "a"
.end annotation


# instance fields
.field public f:Lcom/android/launcher3/shortcuts/ShortcutKey;

.field public g:Lcom/android/launcher3/ShortcutInfo;

.field final synthetic h:Lcom/google/android/apps/nexuslauncher/a/a;


# direct methods
.method public constructor <init>(Lcom/google/android/apps/nexuslauncher/a/a;Lcom/android/launcher3/shortcuts/ShortcutKey;Lcom/android/launcher3/ShortcutInfo;)V
    .locals 0

    .line 97
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/a/a$a;->h:Lcom/google/android/apps/nexuslauncher/a/a;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 98
    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/a/a$a;->f:Lcom/android/launcher3/shortcuts/ShortcutKey;

    .line 99
    iput-object p3, p0, Lcom/google/android/apps/nexuslauncher/a/a$a;->g:Lcom/android/launcher3/ShortcutInfo;

    return-void
.end method
