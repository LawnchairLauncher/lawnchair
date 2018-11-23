.class public Lcom/google/android/apps/nexuslauncher/reflection/c;
.super Ljava/lang/Object;
.source "SourceFile"


# direct methods
.method public constructor <init>()V
    .locals 0

    .line 18
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public final a(Ljava/io/File;Lcom/google/android/apps/nexuslauncher/reflection/g;Lcom/google/android/apps/nexuslauncher/reflection/j;)V
    .locals 1

    .line 31
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    .line 32
    invoke-virtual {p2, p1}, Lcom/google/android/apps/nexuslauncher/reflection/g;->a(Ljava/io/File;)V

    .line 39
    :try_start_0
    invoke-static/range {p1 .. p1}, Lcom/android/launcher3/util/IOUtils;->toByteArray(Ljava/io/File;)[B

    move-result-object p1

    invoke-static {p1}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->g([B)Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;

    move-result-object p1

    .line 40
    iget p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->version:I
    :try_end_0
    .catch Ljava/io/IOException; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    const/4 p1, -0x1

    :goto_0
    const/16 v0, 0x2a

    if-ge p1, v0, :cond_1

    .line 47
    invoke-virtual/range {p3 .. p3}, Lcom/google/android/apps/nexuslauncher/reflection/j;->isInProgress()Z

    move-result p1

    if-eqz p1, :cond_0

    .line 48
    invoke-virtual/range {p3 .. p3}, Lcom/google/android/apps/nexuslauncher/reflection/j;->k()I

    move-result p1

    if-ne p1, v0, :cond_0

    const/4 p1, 0x0

    .line 49
    invoke-virtual {p3, p1}, Lcom/google/android/apps/nexuslauncher/reflection/j;->b(Z)V

    return-void

    :cond_0
    const/4 p1, 0x1

    .line 51
    invoke-virtual {p3, p1}, Lcom/google/android/apps/nexuslauncher/reflection/j;->b(Z)V

    return-void

    .line 54
    :cond_1
    invoke-virtual/range {p2 .. p2}, Lcom/google/android/apps/nexuslauncher/reflection/g;->i()Z

    return-void
.end method
