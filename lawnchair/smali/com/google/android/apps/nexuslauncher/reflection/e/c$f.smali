.class public final Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;
.super Lcom/google/protobuf/nano/MessageNano;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/e/c;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "f"
.end annotation


# static fields
.field private static volatile cG:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;


# instance fields
.field public id:I

.field public name:Ljava/lang/String;


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 706
    invoke-direct/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;-><init>()V

    const/4 v0, 0x0

    .line 707
    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->id:I

    const-string v0, ""

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->name:Ljava/lang/String;

    const/4 v0, -0x1

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->cachedSize:I

    return-void
.end method

.method public static z()[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;
    .locals 2

    .line 689
    sget-object v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->cG:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    if-nez v0, :cond_1

    .line 690
    sget-object v0, Lcom/google/protobuf/nano/InternalNano;->LAZY_INIT_LOCK:Ljava/lang/Object;

    monitor-enter v0

    .line 692
    :try_start_0
    sget-object v1, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->cG:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    if-nez v1, :cond_0

    const/4 v1, 0x0

    .line 693
    new-array v1, v1, [Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    sput-object v1, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->cG:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    .line 695
    :cond_0
    monitor-exit v0

    goto :goto_0

    :catchall_0
    move-exception v1

    monitor-exit v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    throw v1

    .line 697
    :cond_1
    :goto_0
    sget-object v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->cG:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    return-object v0
.end method


# virtual methods
.method protected final computeSerializedSize()I
    .locals 3

    .line 731
    invoke-super/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;->computeSerializedSize()I

    move-result v0

    .line 732
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->id:I

    if-eqz v1, :cond_0

    const/4 v1, 0x1

    .line 733
    iget v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->id:I

    .line 734
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt32Size(II)I

    move-result v1

    add-int/2addr v0, v1

    .line 736
    :cond_0
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->name:Ljava/lang/String;

    const-string v2, ""

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_1

    const/4 v1, 0x2

    .line 737
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->name:Ljava/lang/String;

    .line 738
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

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

    .line 683
    :cond_0
    :goto_0
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    move-result v0

    if-eqz v0, :cond_3

    const/16 v1, 0x8

    if-eq v0, v1, :cond_2

    const/16 v1, 0x12

    if-eq v0, v1, :cond_1

    invoke-static {p1, v0}, Lcom/google/protobuf/nano/WireFormatNano;->parseUnknownField(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)Z

    move-result v0

    if-nez v0, :cond_0

    return-object p0

    :cond_1
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->name:Ljava/lang/String;

    goto :goto_0

    :cond_2
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt32()I

    move-result v0

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->id:I

    goto :goto_0

    :cond_3
    return-object p0
.end method

.method public final writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V
    .locals 2
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 720
    iget v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->id:I

    if-eqz v0, :cond_0

    const/4 v0, 0x1

    .line 721
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->id:I

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt32(II)V

    .line 723
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->name:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_1

    const/4 v0, 0x2

    .line 724
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->name:Ljava/lang/String;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 726
    :cond_1
    invoke-super/range {p0 .. p1}, Lcom/google/protobuf/nano/MessageNano;->writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V

    return-void
.end method
