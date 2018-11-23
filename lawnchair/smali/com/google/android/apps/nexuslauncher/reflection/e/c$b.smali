.class public final Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;
.super Lcom/google/protobuf/nano/MessageNano;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/e/c;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "b"
.end annotation


# static fields
.field private static volatile cr:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;


# instance fields
.field public cs:Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;

.field public ct:I


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 604
    invoke-direct/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;-><init>()V

    const/4 v0, 0x0

    .line 605
    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->cs:Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;

    const/4 v0, 0x0

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->ct:I

    const/4 v0, -0x1

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->cachedSize:I

    return-void
.end method

.method public static x()[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;
    .locals 2

    .line 587
    sget-object v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->cr:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    if-nez v0, :cond_1

    .line 588
    sget-object v0, Lcom/google/protobuf/nano/InternalNano;->LAZY_INIT_LOCK:Ljava/lang/Object;

    monitor-enter v0

    .line 590
    :try_start_0
    sget-object v1, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->cr:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    if-nez v1, :cond_0

    const/4 v1, 0x0

    .line 591
    new-array v1, v1, [Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    sput-object v1, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->cr:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    .line 593
    :cond_0
    monitor-exit v0

    goto :goto_0

    :catchall_0
    move-exception v1

    monitor-exit v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    throw v1

    .line 595
    :cond_1
    :goto_0
    sget-object v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->cr:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    return-object v0
.end method


# virtual methods
.method protected final computeSerializedSize()I
    .locals 3

    .line 629
    invoke-super/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;->computeSerializedSize()I

    move-result v0

    .line 630
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->cs:Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;

    if-eqz v1, :cond_0

    const/4 v1, 0x1

    .line 631
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->cs:Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;

    .line 632
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v1

    add-int/2addr v0, v1

    .line 634
    :cond_0
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->ct:I

    if-eqz v1, :cond_1

    const/4 v1, 0x2

    .line 635
    iget v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->ct:I

    .line 636
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt32Size(II)I

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

    .line 581
    :cond_0
    :goto_0
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    move-result v0

    if-eqz v0, :cond_4

    const/16 v1, 0xa

    if-eq v0, v1, :cond_2

    const/16 v1, 0x10

    if-eq v0, v1, :cond_1

    invoke-static {p1, v0}, Lcom/google/protobuf/nano/WireFormatNano;->parseUnknownField(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)Z

    move-result v0

    if-nez v0, :cond_0

    return-object p0

    :cond_1
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt32()I

    move-result v0

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->ct:I

    goto :goto_0

    :cond_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->cs:Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;

    if-nez v0, :cond_3

    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->cs:Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;

    :cond_3
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->cs:Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;

    invoke-virtual {p1, v0}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    goto :goto_0

    :cond_4
    return-object p0
.end method

.method public final writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V
    .locals 2
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 618
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->cs:Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;

    if-eqz v0, :cond_0

    const/4 v0, 0x1

    .line 619
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->cs:Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    .line 621
    :cond_0
    iget v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->ct:I

    if-eqz v0, :cond_1

    const/4 v0, 0x2

    .line 622
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->ct:I

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt32(II)V

    .line 624
    :cond_1
    invoke-super/range {p0 .. p1}, Lcom/google/protobuf/nano/MessageNano;->writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V

    return-void
.end method
