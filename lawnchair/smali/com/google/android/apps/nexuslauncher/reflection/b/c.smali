.class public Lcom/google/android/apps/nexuslauncher/reflection/b/c;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;
    }
.end annotation


# static fields
.field private static bg:I = 0x1

.field private static bh:J = 0x1499700L

.field private static bi:I = 0xa


# instance fields
.field public final bj:Ljava/util/LinkedList;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/LinkedList<",
            "Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;",
            ">;"
        }
    .end annotation
.end field

.field private final bk:Ljava/util/HashSet;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/HashSet<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation
.end field

.field private final mContext:Landroid/content/Context;


# direct methods
.method static constructor <clinit>()V
    .locals 0

    return-void
.end method

.method public constructor <init>(Landroid/content/Context;)V
    .locals 1

    .line 27
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 24
    new-instance v0, Ljava/util/LinkedList;

    invoke-direct {v0}, Ljava/util/LinkedList;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bj:Ljava/util/LinkedList;

    .line 25
    new-instance v0, Ljava/util/HashSet;

    invoke-direct {v0}, Ljava/util/HashSet;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bk:Ljava/util/HashSet;

    .line 28
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->mContext:Landroid/content/Context;

    return-void
.end method

.method static synthetic a(Lcom/google/android/apps/nexuslauncher/reflection/b/c;Landroid/content/ComponentName;J)Ljava/lang/String;
    .locals 0

    .line 16
    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->mContext:Landroid/content/Context;

    invoke-static {p1, p2, p3, p0}, Lcom/google/android/apps/nexuslauncher/reflection/a/e;->a(Landroid/content/ComponentName;JLandroid/content/Context;)Ljava/lang/String;

    move-result-object p0

    return-object p0
.end method


# virtual methods
.method public final a(Ljava/util/List;Ljava/util/List;)V
    .locals 9
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/List<",
            "Lcom/google/research/reflection/predictor/k$a;",
            ">;",
            "Ljava/util/List<",
            "Lcom/google/research/reflection/predictor/k$a;",
            ">;)V"
        }
    .end annotation

    .line 48
    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->o()V

    .line 51
    invoke-interface/range {p1 .. p1}, Ljava/util/List;->size()I

    move-result v0

    const/high16 v1, 0x3f800000    # 1.0f

    const/4 v2, 0x0

    if-lez v0, :cond_0

    .line 52
    invoke-interface {p1, v2}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/google/research/reflection/predictor/k$a;

    iget v0, v0, Lcom/google/research/reflection/predictor/k$a;->ca:F

    add-float/2addr v1, v0

    .line 58
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bk:Ljava/util/HashSet;

    invoke-virtual {v0}, Ljava/util/HashSet;->clear()V

    .line 61
    new-instance v0, Ljava/util/LinkedList;

    invoke-direct {v0}, Ljava/util/LinkedList;-><init>()V

    .line 62
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bj:Ljava/util/LinkedList;

    invoke-virtual {v3}, Ljava/util/LinkedList;->size()I

    move-result v3

    sget v4, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bg:I

    sub-int/2addr v3, v4

    invoke-static {v3, v2}, Ljava/lang/Math;->max(II)I

    move-result v3

    const/4 v4, 0x0

    .line 63
    :goto_0
    iget-object v5, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bj:Ljava/util/LinkedList;

    invoke-virtual {v5}, Ljava/util/LinkedList;->size()I

    move-result v5

    if-ge v3, v5, :cond_2

    .line 64
    iget-object v5, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bk:Ljava/util/HashSet;

    iget-object v6, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bj:Ljava/util/LinkedList;

    invoke-virtual {v6, v3}, Ljava/util/LinkedList;->get(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;

    iget-object v6, v6, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;->bl:Ljava/lang/String;

    invoke-virtual {v5, v6}, Ljava/util/HashSet;->add(Ljava/lang/Object;)Z

    .line 65
    new-instance v5, Lcom/google/research/reflection/predictor/k$a;

    iget-object v6, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bj:Ljava/util/LinkedList;

    .line 66
    invoke-virtual {v6, v3}, Ljava/util/LinkedList;->get(I)Ljava/lang/Object;

    move-result-object v6

    check-cast v6, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;

    iget-object v6, v6, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;->bl:Ljava/lang/String;

    int-to-float v7, v4

    add-float/2addr v7, v1

    const-string v8, "Reflection.NewInstFilt"

    invoke-direct {v5, v6, v7, v8}, Lcom/google/research/reflection/predictor/k$a;-><init>(Ljava/lang/String;FLjava/lang/String;)V

    .line 69
    invoke-interface {v0, v2, v5}, Ljava/util/List;->add(ILjava/lang/Object;)V

    if-eqz p2, :cond_1

    .line 71
    invoke-interface {p2, v2, v5}, Ljava/util/List;->add(ILjava/lang/Object;)V

    :cond_1
    add-int/lit8 v4, v4, 0x1

    add-int/lit8 v3, v3, 0x1

    goto :goto_0

    .line 77
    :cond_2
    invoke-interface/range {p1 .. p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p2

    :cond_3
    :goto_1
    invoke-interface {p2}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_4

    invoke-interface {p2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/predictor/k$a;

    .line 78
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bk:Ljava/util/HashSet;

    iget-object v3, v1, Lcom/google/research/reflection/predictor/k$a;->id:Ljava/lang/String;

    invoke-virtual {v2, v3}, Ljava/util/HashSet;->contains(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_3

    .line 79
    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    goto :goto_1

    .line 82
    :cond_4
    invoke-interface/range {p1 .. p1}, Ljava/util/List;->clear()V

    .line 83
    invoke-interface {p1, v0}, Ljava/util/List;->addAll(Ljava/util/Collection;)Z

    return-void
.end method

.method public final o()V
    .locals 6

    .line 92
    invoke-static {}, Ljava/util/Calendar;->getInstance()Ljava/util/Calendar;

    move-result-object v0

    invoke-virtual {v0}, Ljava/util/Calendar;->getTimeInMillis()J

    move-result-wide v0

    .line 93
    :goto_0
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bj:Ljava/util/LinkedList;

    invoke-virtual {v2}, Ljava/util/LinkedList;->size()I

    move-result v2

    if-lez v2, :cond_1

    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bj:Ljava/util/LinkedList;

    .line 95
    invoke-virtual {v2}, Ljava/util/LinkedList;->peek()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;

    iget-wide v2, v2, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;->time:J

    sget-wide v4, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bh:J

    add-long/2addr v2, v4

    cmp-long v2, v0, v2

    if-gtz v2, :cond_0

    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bj:Ljava/util/LinkedList;

    .line 96
    invoke-virtual {v2}, Ljava/util/LinkedList;->peek()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;

    iget v2, v2, Lcom/google/android/apps/nexuslauncher/reflection/b/c$a;->bm:I

    sget v3, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bi:I

    if-le v2, v3, :cond_1

    .line 98
    :cond_0
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bj:Ljava/util/LinkedList;

    invoke-virtual {v2}, Ljava/util/LinkedList;->removeFirst()Ljava/lang/Object;

    goto :goto_0

    :cond_1
    return-void
.end method

.method setMaxNumPromotion(I)V
    .locals 0

    .line 120
    sput p1, Lcom/google/android/apps/nexuslauncher/reflection/b/c;->bg:I

    return-void
.end method
