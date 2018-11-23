.class public final Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;
.super Lcom/google/protobuf/nano/MessageNano;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/e/c;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "e"
.end annotation


# static fields
.field private static volatile cE:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;


# instance fields
.field public bp:Ljava/lang/String;

.field public cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 479
    invoke-direct/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;-><init>()V

    const-string v0, ""

    .line 480
    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->bp:Ljava/lang/String;

    invoke-static {}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;->x()[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    const/4 v0, -0x1

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cachedSize:I

    return-void
.end method

.method public static y()[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;
    .locals 2

    .line 462
    sget-object v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cE:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    if-nez v0, :cond_1

    .line 463
    sget-object v0, Lcom/google/protobuf/nano/InternalNano;->LAZY_INIT_LOCK:Ljava/lang/Object;

    monitor-enter v0

    .line 465
    :try_start_0
    sget-object v1, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cE:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    if-nez v1, :cond_0

    const/4 v1, 0x0

    .line 466
    new-array v1, v1, [Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    sput-object v1, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cE:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    .line 468
    :cond_0
    monitor-exit v0

    goto :goto_0

    :catchall_0
    move-exception v1

    monitor-exit v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    throw v1

    .line 470
    :cond_1
    :goto_0
    sget-object v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cE:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    return-object v0
.end method


# virtual methods
.method protected final computeSerializedSize()I
    .locals 4

    .line 509
    invoke-super/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;->computeSerializedSize()I

    move-result v0

    .line 510
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->bp:Ljava/lang/String;

    const-string v2, ""

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_0

    .line 511
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->bp:Ljava/lang/String;

    const/4 v2, 0x1

    .line 512
    invoke-static {v2, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    .line 514
    :cond_0
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    if-eqz v1, :cond_2

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    array-length v1, v1

    if-lez v1, :cond_2

    const/4 v1, 0x0

    .line 515
    :goto_0
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    array-length v2, v2

    if-ge v1, v2, :cond_2

    .line 516
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    aget-object v2, v2, v1

    if-eqz v2, :cond_1

    const/4 v3, 0x2

    .line 519
    invoke-static {v3, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v2

    add-int/2addr v0, v2

    :cond_1
    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    :cond_2
    return v0
.end method

.method public final synthetic mergeFrom(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/protobuf/nano/MessageNano;
    .locals 4
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 456
    :cond_0
    :goto_0
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    move-result v0

    if-eqz v0, :cond_6

    const/16 v1, 0xa

    if-eq v0, v1, :cond_5

    const/16 v1, 0x12

    if-eq v0, v1, :cond_1

    invoke-static {p1, v0}, Lcom/google/protobuf/nano/WireFormatNano;->parseUnknownField(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)Z

    move-result v0

    if-nez v0, :cond_0

    return-object p0

    :cond_1
    invoke-static {p1, v1}, Lcom/google/protobuf/nano/WireFormatNano;->getRepeatedFieldArrayLength(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)I

    move-result v0

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    const/4 v2, 0x0

    if-nez v1, :cond_2

    const/4 v1, 0x0

    goto :goto_1

    :cond_2
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    array-length v1, v1

    :goto_1
    add-int/2addr v0, v1

    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    if-eqz v1, :cond_3

    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    invoke-static {v3, v2, v0, v2, v1}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    :cond_3
    :goto_2
    array-length v2, v0

    add-int/lit8 v2, v2, -0x1

    if-ge v1, v2, :cond_4

    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;-><init>()V

    aput-object v2, v0, v1

    aget-object v2, v0, v1

    invoke-virtual {p1, v2}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    add-int/lit8 v1, v1, 0x1

    goto :goto_2

    :cond_4
    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;-><init>()V

    aput-object v2, v0, v1

    aget-object v1, v0, v1

    invoke-virtual {p1, v1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    goto :goto_0

    :cond_5
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->bp:Ljava/lang/String;

    goto :goto_0

    :cond_6
    return-object p0
.end method

.method public final writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V
    .locals 3
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 493
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->bp:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_0

    .line 494
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->bp:Ljava/lang/String;

    const/4 v1, 0x1

    invoke-virtual {p1, v1, v0}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 496
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    if-eqz v0, :cond_2

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    array-length v0, v0

    if-lez v0, :cond_2

    const/4 v0, 0x0

    .line 497
    :goto_0
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    array-length v1, v1

    if-ge v0, v1, :cond_2

    .line 498
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->cF:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$b;

    aget-object v1, v1, v0

    if-eqz v1, :cond_1

    const/4 v2, 0x2

    .line 500
    invoke-virtual {p1, v2, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    :cond_1
    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    .line 504
    :cond_2
    invoke-super/range {p0 .. p1}, Lcom/google/protobuf/nano/MessageNano;->writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V

    return-void
.end method
