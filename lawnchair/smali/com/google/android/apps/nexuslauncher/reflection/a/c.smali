.class public Lcom/google/android/apps/nexuslauncher/reflection/a/c;
.super Ljava/lang/Object;
.source "SourceFile"


# direct methods
.method public static a(Lcom/google/research/reflection/predictor/g;)Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;
    .locals 3
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 77
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;-><init>()V

    .line 78
    new-instance v1, Ljava/io/ByteArrayOutputStream;

    invoke-direct {v1}, Ljava/io/ByteArrayOutputStream;-><init>()V

    .line 79
    new-instance v2, Ljava/io/DataOutputStream;

    invoke-direct {v2, v1}, Ljava/io/DataOutputStream;-><init>(Ljava/io/OutputStream;)V

    .line 80
    invoke-virtual {p0, v2}, Lcom/google/research/reflection/predictor/g;->b(Ljava/io/DataOutputStream;)V

    .line 81
    invoke-virtual {v2}, Ljava/io/DataOutputStream;->flush()V

    .line 82
    invoke-virtual {v1}, Ljava/io/ByteArrayOutputStream;->toByteArray()[B

    move-result-object v1

    iput-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cx:[B

    .line 83
    invoke-virtual {v2}, Ljava/io/DataOutputStream;->close()V

    .line 84
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/predictor/g;->getName()Ljava/lang/String;

    move-result-object v1

    iput-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cw:Ljava/lang/String;

    .line 86
    instance-of v1, p0, Lcom/google/research/reflection/predictor/AbstractEventEstimator;

    if-nez v1, :cond_0

    return-object v0

    .line 90
    :cond_0
    check-cast p0, Lcom/google/research/reflection/predictor/AbstractEventEstimator;

    .line 92
    invoke-virtual {p0}, Lcom/google/research/reflection/predictor/AbstractEventEstimator;->ap()I

    move-result v1

    iput v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cz:I

    .line 93
    invoke-virtual {p0}, Lcom/google/research/reflection/predictor/AbstractEventEstimator;->ao()I

    move-result v1

    iput v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cy:I

    .line 95
    invoke-virtual {p0}, Lcom/google/research/reflection/predictor/AbstractEventEstimator;->aq()Ljava/util/HashMap;

    move-result-object v1

    invoke-static {v1}, Lcom/google/android/apps/nexuslauncher/reflection/a/e;->a(Ljava/util/Map;)[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    move-result-object v1

    iput-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    .line 96
    invoke-virtual {p0}, Lcom/google/research/reflection/predictor/AbstractEventEstimator;->am()Ljava/util/HashMap;

    move-result-object v1

    invoke-static {v1}, Lcom/google/android/apps/nexuslauncher/reflection/a/e;->b(Ljava/util/Map;)[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    move-result-object v1

    iput-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    .line 97
    invoke-virtual {p0}, Lcom/google/research/reflection/predictor/AbstractEventEstimator;->al()Ljava/util/HashMap;

    move-result-object v1

    invoke-static {v1}, Lcom/google/android/apps/nexuslauncher/reflection/a/e;->b(Ljava/util/Map;)[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    move-result-object v1

    iput-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    .line 98
    invoke-virtual {p0}, Lcom/google/research/reflection/predictor/AbstractEventEstimator;->an()Ljava/util/HashMap;

    move-result-object p0

    invoke-static {p0}, Lcom/google/android/apps/nexuslauncher/reflection/a/e;->b(Ljava/util/Map;)[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    move-result-object p0

    iput-object p0, v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    return-object v0
.end method
