.class public final Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;
.super Lcom/google/protobuf/nano/MessageNano;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/e/c;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "a"
.end annotation


# static fields
.field private static volatile cq:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;


# instance fields
.field public key:I

.field public value:J


# direct methods
.method public constructor <init>()V
    .locals 2

    .line 805
    invoke-direct/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;-><init>()V

    const/4 v0, 0x0

    .line 806
    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->key:I

    const-wide/16 v0, 0x0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->value:J

    const/4 v0, -0x1

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->cachedSize:I

    return-void
.end method

.method public static w()[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;
    .locals 2

    .line 788
    sget-object v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->cq:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    if-nez v0, :cond_1

    .line 789
    sget-object v0, Lcom/google/protobuf/nano/InternalNano;->LAZY_INIT_LOCK:Ljava/lang/Object;

    monitor-enter v0

    .line 791
    :try_start_0
    sget-object v1, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->cq:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    if-nez v1, :cond_0

    const/4 v1, 0x0

    .line 792
    new-array v1, v1, [Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    sput-object v1, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->cq:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    .line 794
    :cond_0
    monitor-exit v0

    goto :goto_0

    :catchall_0
    move-exception v1

    monitor-exit v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    throw v1

    .line 796
    :cond_1
    :goto_0
    sget-object v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->cq:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    return-object v0
.end method


# virtual methods
.method protected final computeSerializedSize()I
    .locals 5

    .line 830
    invoke-super/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;->computeSerializedSize()I

    move-result v0

    .line 831
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->key:I

    if-eqz v1, :cond_0

    const/4 v1, 0x1

    .line 832
    iget v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->key:I

    .line 833
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt32Size(II)I

    move-result v1

    add-int/2addr v0, v1

    .line 835
    :cond_0
    iget-wide v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->value:J

    const-wide/16 v3, 0x0

    cmp-long v1, v1, v3

    if-eqz v1, :cond_1

    const/4 v1, 0x2

    .line 836
    iget-wide v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->value:J

    .line 837
    invoke-static {v1, v2, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt64Size(IJ)I

    move-result v1

    add-int/2addr v0, v1

    :cond_1
    return v0
.end method

.method public final synthetic mergeFrom(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/protobuf/nano/MessageNano;
    .locals 2
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 782
    :cond_0
    :goto_0
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    move-result v0

    if-eqz v0, :cond_3

    const/16 v1, 0x8

    if-eq v0, v1, :cond_2

    const/16 v1, 0x10

    if-eq v0, v1, :cond_1

    invoke-static {p1, v0}, Lcom/google/protobuf/nano/WireFormatNano;->parseUnknownField(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)Z

    move-result v0

    if-nez v0, :cond_0

    return-object p0

    :cond_1
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt64()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->value:J

    goto :goto_0

    :cond_2
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt32()I

    move-result v0

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->key:I

    goto :goto_0

    :cond_3
    return-object p0
.end method

.method public final writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V
    .locals 4
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 819
    iget v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->key:I

    if-eqz v0, :cond_0

    const/4 v0, 0x1

    .line 820
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->key:I

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt32(II)V

    .line 822
    :cond_0
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->value:J

    const-wide/16 v2, 0x0

    cmp-long v0, v0, v2

    if-eqz v0, :cond_1

    const/4 v0, 0x2

    .line 823
    iget-wide v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->value:J

    invoke-virtual {p1, v0, v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt64(IJ)V

    .line 825
    :cond_1
    invoke-super/range {p0 .. p1}, Lcom/google/protobuf/nano/MessageNano;->writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V

    return-void
.end method
