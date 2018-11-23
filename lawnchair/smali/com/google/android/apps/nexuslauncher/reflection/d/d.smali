.class public Lcom/google/android/apps/nexuslauncher/reflection/d/d;
.super Ljava/lang/Object;
.source "SourceFile"


# instance fields
.field private final bO:Ljava/io/File;

.field private final bP:J


# direct methods
.method public constructor <init>(Ljava/io/File;J)V
    .locals 0

    .line 42
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 43
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/d;->bO:Ljava/io/File;

    .line 44
    iput-wide p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/d;->bP:J

    return-void
.end method


# virtual methods
.method public final declared-synchronized a(Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;)V
    .locals 4

    monitor-enter p0

    const/4 v0, 0x1

    .line 52
    :try_start_0
    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;

    const/4 v1, 0x0

    aput-object p1, v0, v1

    invoke-static {v0}, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;

    move-result-object p1

    invoke-virtual {p0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/d/d;->log(Ljava/util/List;)V

    .line 53
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/d;->bO:Ljava/io/File;

    invoke-virtual {p1}, Ljava/io/File;->exists()Z

    move-result p1

    if-eqz p1, :cond_0

    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/d;->bO:Ljava/io/File;

    invoke-virtual {p1}, Ljava/io/File;->length()J

    move-result-wide v0

    iget-wide v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/d;->bP:J

    cmp-long p1, v0, v2

    if-lez p1, :cond_0

    invoke-virtual/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/d/d;->t()Ljava/util/List;

    move-result-object p1

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/d;->bO:Ljava/io/File;

    invoke-virtual {v0}, Ljava/io/File;->delete()Z

    move-result v0

    if-eqz v0, :cond_0

    invoke-interface {p1}, Ljava/util/List;->size()I

    move-result v0

    div-int/lit8 v0, v0, 0x2

    invoke-interface {p1}, Ljava/util/List;->size()I

    move-result v1

    invoke-interface {p1, v0, v1}, Ljava/util/List;->subList(II)Ljava/util/List;

    move-result-object p1

    invoke-virtual {p0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/d/d;->log(Ljava/util/List;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 54
    :cond_0
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 51
    monitor-exit p0

    throw p1
.end method

.method log(Ljava/util/List;)V
    .locals 6
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/List<",
            "Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;",
            ">;)V"
        }
    .end annotation

    .line 58
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    const/4 v0, 0x0

    .line 62
    :try_start_0
    new-instance v1, Ljava/io/DataOutputStream;

    new-instance v2, Ljava/io/BufferedOutputStream;

    new-instance v3, Ljava/io/FileOutputStream;

    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/d;->bO:Ljava/io/File;

    const/4 v5, 0x1

    invoke-direct {v3, v4, v5}, Ljava/io/FileOutputStream;-><init>(Ljava/io/File;Z)V

    invoke-direct {v2, v3}, Ljava/io/BufferedOutputStream;-><init>(Ljava/io/OutputStream;)V

    invoke-direct {v1, v2}, Ljava/io/DataOutputStream;-><init>(Ljava/io/OutputStream;)V
    :try_end_0
    .catch Ljava/io/IOException; {:try_start_0 .. :try_end_0} :catch_1
    .catchall {:try_start_0 .. :try_end_0} :catchall_1

    .line 64
    :try_start_1
    invoke-interface/range {p1 .. p1}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object p1

    :goto_0
    invoke-interface {p1}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_2

    invoke-interface {p1}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;

    .line 65
    invoke-virtual {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->getSerializedSize()I

    move-result v3

    if-eqz v0, :cond_0

    .line 66
    array-length v4, v0

    if-ge v4, v3, :cond_1

    .line 67
    :cond_0
    new-array v0, v3, [B

    :cond_1
    const/4 v4, 0x0

    .line 69
    invoke-static {v2, v0, v4, v3}, Lcom/google/protobuf/nano/MessageNano;->toByteArray(Lcom/google/protobuf/nano/MessageNano;[BII)V

    .line 70
    invoke-virtual {v1, v3}, Ljava/io/DataOutputStream;->writeInt(I)V

    .line 71
    invoke-virtual {v1, v0, v4, v3}, Ljava/io/DataOutputStream;->write([BII)V
    :try_end_1
    .catch Ljava/io/IOException; {:try_start_1 .. :try_end_1} :catch_0
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    goto :goto_0

    .line 76
    :cond_2
    invoke-static {v1}, Lcom/android/launcher3/Utilities;->closeSilently(Ljava/io/Closeable;)V

    return-void

    :catchall_0
    move-exception p1

    move-object v0, v1

    goto :goto_2

    :catch_0
    move-exception p1

    move-object v0, v1

    goto :goto_1

    :catchall_1
    move-exception p1

    goto :goto_2

    :catch_1
    move-exception p1

    :goto_1
    :try_start_2
    const-string v1, "Reflection.ClientActLog"

    const-string v2, "Failed to write the client action log file"

    .line 74
    invoke-static {v1, v2, p1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_1

    .line 76
    invoke-static {v0}, Lcom/android/launcher3/Utilities;->closeSilently(Ljava/io/Closeable;)V

    return-void

    :goto_2
    invoke-static {v0}, Lcom/android/launcher3/Utilities;->closeSilently(Ljava/io/Closeable;)V

    throw p1
.end method

.method public final declared-synchronized t()Ljava/util/List;
    .locals 7
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/List<",
            "Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;",
            ">;"
        }
    .end annotation

    monitor-enter p0

    .line 97
    :try_start_0
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    .line 98
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_2

    const/4 v1, 0x0

    .line 102
    :try_start_1
    new-instance v2, Ljava/io/DataInputStream;

    new-instance v3, Ljava/io/BufferedInputStream;

    new-instance v4, Ljava/io/FileInputStream;

    iget-object v5, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/d;->bO:Ljava/io/File;

    invoke-direct {v4, v5}, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V

    invoke-direct {v3, v4}, Ljava/io/BufferedInputStream;-><init>(Ljava/io/InputStream;)V

    invoke-direct {v2, v3}, Ljava/io/DataInputStream;-><init>(Ljava/io/InputStream;)V
    :try_end_1
    .catch Ljava/io/EOFException; {:try_start_1 .. :try_end_1} :catch_3
    .catch Ljava/io/IOException; {:try_start_1 .. :try_end_1} :catch_2
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 104
    :cond_0
    :goto_0
    :try_start_2
    invoke-virtual {v2}, Ljava/io/DataInputStream;->readInt()I

    move-result v3

    if-eqz v1, :cond_1

    .line 105
    array-length v4, v1

    if-ge v4, v3, :cond_2

    .line 106
    :cond_1
    new-array v1, v3, [B

    :cond_2
    const/4 v4, 0x0

    .line 108
    invoke-virtual {v2, v1, v4, v3}, Ljava/io/DataInputStream;->read([BII)I

    .line 110
    invoke-static {v1, v4, v3}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->newInstance([BII)Lcom/google/protobuf/nano/CodedInputByteBufferNano;

    move-result-object v3

    invoke-static {v3}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->d(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;

    move-result-object v3

    if-eqz v3, :cond_0

    .line 112
    invoke-virtual {v0, v3}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    :try_end_2
    .catch Ljava/io/EOFException; {:try_start_2 .. :try_end_2} :catch_1
    .catch Ljava/io/IOException; {:try_start_2 .. :try_end_2} :catch_0
    .catchall {:try_start_2 .. :try_end_2} :catchall_1

    goto :goto_0

    :catch_0
    move-exception v1

    goto :goto_1

    :catch_1
    move-object v1, v2

    goto :goto_3

    :catchall_0
    move-exception v0

    move-object v2, v1

    goto :goto_2

    :catch_2
    move-exception v2

    move-object v6, v2

    move-object v2, v1

    move-object v1, v6

    :goto_1
    :try_start_3
    const-string v3, "Reflection.ClientActLog"

    const-string v4, "Failed in loading the log file"

    .line 118
    invoke-static {v3, v4, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)I
    :try_end_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_1

    .line 120
    :try_start_4
    invoke-static {v2}, Lcom/android/launcher3/Utilities;->closeSilently(Ljava/io/Closeable;)V

    goto :goto_4

    :catchall_1
    move-exception v0

    :goto_2
    invoke-static {v2}, Lcom/android/launcher3/Utilities;->closeSilently(Ljava/io/Closeable;)V

    throw v0

    :catch_3
    :goto_3
    invoke-static {v1}, Lcom/android/launcher3/Utilities;->closeSilently(Ljava/io/Closeable;)V
    :try_end_4
    .catchall {:try_start_4 .. :try_end_4} :catchall_2

    .line 122
    :goto_4
    monitor-exit p0

    return-object v0

    :catchall_2
    move-exception v0

    .line 96
    monitor-exit p0

    throw v0
.end method
