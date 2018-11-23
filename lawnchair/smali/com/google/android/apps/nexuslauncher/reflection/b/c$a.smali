.class public Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/b/c;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x2
    name = "a"
.end annotation


# instance fields
.field public bl:Ljava/lang/String;

.field public bm:I

.field final synthetic bn:Lcom/google/android/apps/nexuslauncher/reflection/b/c;

.field public time:J


# direct methods
.method public constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/b/c;Landroid/content/ComponentName;JJ)V
    .locals 0

    .line 111
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;->bn:Lcom/google/android/apps/nexuslauncher/reflection/b/c;

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 112
    invoke-static/range {p1 .. p4}, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->a(Lcom/google/android/apps/nexuslauncher/reflection/b/c;Landroid/content/ComponentName;J)Ljava/lang/String;

    move-result-object p1

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;->bl:Ljava/lang/String;

    .line 113
    iput-wide p5, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;->time:J

    const/4 p1, 0x0

    .line 114
    iput p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;->bm:I

    return-void
.end method
