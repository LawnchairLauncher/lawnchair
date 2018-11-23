.class Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils$EventTimestampComparator;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Ljava/util/Comparator;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x8
    name = "EventTimestampComparator"
.end annotation

.annotation system Ldalvik/annotation/Signature;
    value = {
        "Ljava/lang/Object;",
        "Ljava/util/Comparator<",
        "Lcom/google/android/apps/nexuslauncher/reflection/signal/a;",
        ">;"
    }
.end annotation


# direct methods
.method constructor <init>()V
    .locals 0

    .line 114
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public synthetic compare(Ljava/lang/Object;Ljava/lang/Object;)I
    .locals 4

    .line 113
    check-cast p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    check-cast p2, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-virtual {p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v0

    invoke-interface {v0}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v0

    invoke-virtual {p2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v2

    invoke-interface {v2}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v2

    cmp-long v0, v0, v2

    if-nez v0, :cond_0

    const/4 p1, 0x0

    return p1

    :cond_0
    invoke-virtual {p2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object p2

    invoke-interface {p2}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v0

    invoke-virtual {p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object p1

    invoke-interface {p1}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide p1

    cmp-long p1, v0, p1

    if-lez p1, :cond_1

    const/4 p1, -0x1

    return p1

    :cond_1
    const/4 p1, 0x1

    return p1
.end method
