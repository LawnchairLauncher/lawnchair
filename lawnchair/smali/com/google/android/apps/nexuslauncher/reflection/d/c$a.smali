.class public Lcom/google/android/apps/nexuslauncher/reflection/d/c$a;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/d/c;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x9
    name = "a"
.end annotation


# instance fields
.field public final bM:J

.field public final bN:Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/List<",
            "Lcom/google/android/apps/nexuslauncher/reflection/signal/a;",
            ">;"
        }
    .end annotation
.end field


# direct methods
.method public constructor <init>(JLjava/util/List;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(J",
            "Ljava/util/List<",
            "Lcom/google/android/apps/nexuslauncher/reflection/signal/a;",
            ">;)V"
        }
    .end annotation

    .line 357
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 358
    iput-wide p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c$a;->bM:J

    .line 359
    iput-object p3, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c$a;->bN:Ljava/util/List;

    return-void
.end method
