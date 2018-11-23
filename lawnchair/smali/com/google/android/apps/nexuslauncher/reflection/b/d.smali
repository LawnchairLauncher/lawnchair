.class public Lcom/google/android/apps/nexuslauncher/reflection/b/d;
.super Ljava/lang/Object;
.source "SourceFile"


# instance fields
.field private final ad:Lcom/google/android/apps/nexuslauncher/reflection/a/b;

.field private final bk:Ljava/util/HashSet;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/HashSet<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation
.end field


# direct methods
.method public constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/a/b;)V
    .locals 1

    .line 15
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 12
    new-instance v0, Ljava/util/HashSet;

    invoke-direct {v0}, Ljava/util/HashSet;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/d;->bk:Ljava/util/HashSet;

    .line 16
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/d;->ad:Lcom/google/android/apps/nexuslauncher/reflection/a/b;

    return-void
.end method


# virtual methods
.method public final a(Ljava/util/List;Ljava/util/List;)V
    .locals 10
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

    .line 28
    invoke-interface/range {p1 .. p1}, Ljava/util/List;->size()I

    move-result v0

    const/high16 v1, 0x3f800000    # 1.0f

    const/4 v2, 0x1

    if-lez v0, :cond_0

    .line 29
    invoke-interface/range {p1 .. p1}, Ljava/util/List;->size()I

    move-result v0

    sub-int/2addr v0, v2

    invoke-interface {p1, v0}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/google/research/reflection/predictor/k$a;

    iget v0, v0, Lcom/google/research/reflection/predictor/k$a;->ca:F

    sub-float v1, v0, v1

    .line 31
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/d;->ad:Lcom/google/android/apps/nexuslauncher/reflection/a/b;

    .line 32
    new-instance v3, Ljava/util/ArrayList;

    sget-object v4, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aT:[Ljava/lang/String;

    array-length v4, v4

    invoke-direct {v3, v4}, Ljava/util/ArrayList;-><init>(I)V

    const/4 v4, 0x0

    const/4 v5, 0x0

    :goto_0
    sget-object v6, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aT:[Ljava/lang/String;

    array-length v6, v6

    if-ge v5, v6, :cond_6

    iget-object v6, v0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aU:[Lcom/google/android/apps/nexuslauncher/reflection/a/a;

    aget-object v6, v6, v5

    iget v6, v6, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->state:I

    const/4 v7, -0x1

    if-ne v6, v7, :cond_4

    sget-object v6, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aT:[Ljava/lang/String;

    aget-object v6, v6, v5

    const/4 v7, 0x0

    iget-object v8, v0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->mPackageManager:Landroid/content/pm/PackageManager;

    invoke-virtual {v8, v6}, Landroid/content/pm/PackageManager;->getLaunchIntentForPackage(Ljava/lang/String;)Landroid/content/Intent;

    move-result-object v6

    if-eqz v6, :cond_1

    iget-object v8, v0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->mPackageManager:Landroid/content/pm/PackageManager;

    invoke-virtual {v8, v6, v4}, Landroid/content/pm/PackageManager;->resolveActivity(Landroid/content/Intent;I)Landroid/content/pm/ResolveInfo;

    move-result-object v6

    if-eqz v6, :cond_1

    iget-object v7, v6, Landroid/content/pm/ResolveInfo;->activityInfo:Landroid/content/pm/ActivityInfo;

    :cond_1
    if-eqz v7, :cond_3

    iget-object v6, v7, Landroid/content/pm/ActivityInfo;->name:Ljava/lang/String;

    const-string v8, "."

    invoke-virtual {v6, v8}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v8

    if-eqz v8, :cond_2

    new-instance v8, Ljava/lang/StringBuilder;

    invoke-direct {v8}, Ljava/lang/StringBuilder;-><init>()V

    iget-object v7, v7, Landroid/content/pm/ActivityInfo;->packageName:Ljava/lang/String;

    invoke-virtual {v8, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v8, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v8}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v6

    :cond_2
    iget-object v7, v0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aU:[Lcom/google/android/apps/nexuslauncher/reflection/a/a;

    aget-object v7, v7, v5

    iput v2, v7, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->state:I

    iget-object v7, v0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aU:[Lcom/google/android/apps/nexuslauncher/reflection/a/a;

    aget-object v7, v7, v5

    iput-object v6, v7, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->className:Ljava/lang/String;

    goto :goto_1

    :cond_3
    iget-object v6, v0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aU:[Lcom/google/android/apps/nexuslauncher/reflection/a/a;

    aget-object v6, v6, v5

    iput v4, v6, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->state:I

    iget-object v6, v0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aU:[Lcom/google/android/apps/nexuslauncher/reflection/a/a;

    aget-object v6, v6, v5

    const-string v7, ""

    iput-object v7, v6, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->className:Ljava/lang/String;

    :cond_4
    :goto_1
    iget-object v6, v0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aU:[Lcom/google/android/apps/nexuslauncher/reflection/a/a;

    aget-object v6, v6, v5

    iget v6, v6, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->state:I

    if-ne v6, v2, :cond_5

    new-instance v6, Lcom/google/research/reflection/predictor/k$a;

    new-instance v7, Landroid/content/ComponentName;

    iget-object v8, v0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aU:[Lcom/google/android/apps/nexuslauncher/reflection/a/a;

    aget-object v8, v8, v5

    iget-object v8, v8, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->packageName:Ljava/lang/String;

    iget-object v9, v0, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aU:[Lcom/google/android/apps/nexuslauncher/reflection/a/a;

    aget-object v9, v9, v5

    iget-object v9, v9, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->className:Ljava/lang/String;

    invoke-direct {v7, v8, v9}, Landroid/content/ComponentName;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    invoke-static {v7}, Lcom/google/android/apps/nexuslauncher/reflection/f;->a(Landroid/content/ComponentName;)Ljava/lang/String;

    move-result-object v7

    invoke-virtual {v3}, Ljava/util/ArrayList;->size()I

    move-result v8

    int-to-float v8, v8

    sub-float v8, v1, v8

    const-string v9, "Reflection.AppInfoHelper"

    invoke-direct {v6, v7, v8, v9}, Lcom/google/research/reflection/predictor/k$a;-><init>(Ljava/lang/String;FLjava/lang/String;)V

    invoke-virtual {v3, v6}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    :cond_5
    add-int/lit8 v5, v5, 0x1

    goto/16 :goto_0

    .line 34
    :cond_6
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/d;->bk:Ljava/util/HashSet;

    invoke-virtual {v0}, Ljava/util/HashSet;->clear()V

    .line 35
    invoke-interface/range {p1 .. p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :goto_2
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_7

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/predictor/k$a;

    .line 36
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/d;->bk:Ljava/util/HashSet;

    iget-object v1, v1, Lcom/google/research/reflection/predictor/k$a;->id:Ljava/lang/String;

    invoke-virtual {v2, v1}, Ljava/util/HashSet;->add(Ljava/lang/Object;)Z

    goto :goto_2

    .line 38
    :cond_7
    invoke-virtual {v3}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    move-result-object v0

    :cond_8
    :goto_3
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    move-result v1

    if-eqz v1, :cond_9

    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Lcom/google/research/reflection/predictor/k$a;

    .line 39
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/b/d;->bk:Ljava/util/HashSet;

    iget-object v3, v1, Lcom/google/research/reflection/predictor/k$a;->id:Ljava/lang/String;

    invoke-virtual {v2, v3}, Ljava/util/HashSet;->contains(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_8

    .line 40
    invoke-interface {p1, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    if-eqz p2, :cond_8

    .line 42
    invoke-interface {p2, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    goto :goto_3

    :cond_9
    return-void
.end method
