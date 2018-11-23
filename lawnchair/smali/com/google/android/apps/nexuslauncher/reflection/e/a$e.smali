.class public final Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;
.super Lcom/google/protobuf/nano/MessageNano;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/e/a;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "e"
.end annotation


# instance fields
.field public cb:I

.field public cc:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

.field public cd:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

.field public ce:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

.field public cf:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

.field public cg:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

.field public ch:Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 426
    invoke-direct/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;-><init>()V

    const/4 v0, 0x0

    .line 427
    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cb:I

    invoke-static {}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->v()[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cc:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-static {}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->v()[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cd:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-static {}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->v()[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ce:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-static {}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->v()[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cf:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-static {}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->v()[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cg:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    const/4 v0, 0x0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ch:Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;

    const/4 v0, -0x1

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cachedSize:I

    return-void
.end method


# virtual methods
.method protected final computeSerializedSize()I
    .locals 5

    .line 496
    invoke-super/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;->computeSerializedSize()I

    move-result v0

    .line 497
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cb:I

    if-eqz v1, :cond_0

    .line 498
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cb:I

    const/4 v2, 0x1

    .line 499
    invoke-static {v2, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt32Size(II)I

    move-result v1

    add-int/2addr v0, v1

    .line 501
    :cond_0
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cc:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    const/4 v2, 0x0

    if-eqz v1, :cond_3

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cc:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v1, v1

    if-lez v1, :cond_3

    move v1, v0

    const/4 v0, 0x0

    .line 502
    :goto_0
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cc:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v3, v3

    if-ge v0, v3, :cond_2

    .line 503
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cc:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    aget-object v3, v3, v0

    if-eqz v3, :cond_1

    const/4 v4, 0x2

    .line 506
    invoke-static {v4, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v3

    add-int/2addr v1, v3

    :cond_1
    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    :cond_2
    move v0, v1

    .line 510
    :cond_3
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cd:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-eqz v1, :cond_6

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cd:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v1, v1

    if-lez v1, :cond_6

    move v1, v0

    const/4 v0, 0x0

    .line 511
    :goto_1
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cd:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v3, v3

    if-ge v0, v3, :cond_5

    .line 512
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cd:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    aget-object v3, v3, v0

    if-eqz v3, :cond_4

    const/4 v4, 0x3

    .line 515
    invoke-static {v4, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v3

    add-int/2addr v1, v3

    :cond_4
    add-int/lit8 v0, v0, 0x1

    goto :goto_1

    :cond_5
    move v0, v1

    .line 519
    :cond_6
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ce:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-eqz v1, :cond_9

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ce:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v1, v1

    if-lez v1, :cond_9

    move v1, v0

    const/4 v0, 0x0

    .line 520
    :goto_2
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ce:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v3, v3

    if-ge v0, v3, :cond_8

    .line 521
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ce:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    aget-object v3, v3, v0

    if-eqz v3, :cond_7

    const/4 v4, 0x4

    .line 524
    invoke-static {v4, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v3

    add-int/2addr v1, v3

    :cond_7
    add-int/lit8 v0, v0, 0x1

    goto :goto_2

    :cond_8
    move v0, v1

    .line 528
    :cond_9
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cf:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-eqz v1, :cond_c

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cf:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v1, v1

    if-lez v1, :cond_c

    move v1, v0

    const/4 v0, 0x0

    .line 529
    :goto_3
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cf:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v3, v3

    if-ge v0, v3, :cond_b

    .line 530
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cf:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    aget-object v3, v3, v0

    if-eqz v3, :cond_a

    const/4 v4, 0x5

    .line 533
    invoke-static {v4, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v3

    add-int/2addr v1, v3

    :cond_a
    add-int/lit8 v0, v0, 0x1

    goto :goto_3

    :cond_b
    move v0, v1

    .line 537
    :cond_c
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cg:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-eqz v1, :cond_e

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cg:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v1, v1

    if-lez v1, :cond_e

    .line 538
    :goto_4
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cg:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v1, v1

    if-ge v2, v1, :cond_e

    .line 539
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cg:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    aget-object v1, v1, v2

    if-eqz v1, :cond_d

    const/4 v3, 0x6

    .line 542
    invoke-static {v3, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v1

    add-int/2addr v0, v1

    :cond_d
    add-int/lit8 v2, v2, 0x1

    goto :goto_4

    .line 546
    :cond_e
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ch:Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;

    if-eqz v1, :cond_f

    const/4 v1, 0x7

    .line 547
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ch:Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;

    .line 548
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v1

    add-int/2addr v0, v1

    :cond_f
    return v0
.end method

.method public final synthetic mergeFrom(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/protobuf/nano/MessageNano;
    .locals 4
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 388
    :cond_0
    :goto_0
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    move-result v0

    if-eqz v0, :cond_18

    const/16 v1, 0x8

    if-eq v0, v1, :cond_17

    const/16 v1, 0x12

    const/4 v2, 0x0

    if-eq v0, v1, :cond_13

    const/16 v1, 0x1a

    if-eq v0, v1, :cond_f

    const/16 v1, 0x22

    if-eq v0, v1, :cond_b

    const/16 v1, 0x2a

    if-eq v0, v1, :cond_7

    const/16 v1, 0x32

    if-eq v0, v1, :cond_3

    const/16 v1, 0x3a

    if-eq v0, v1, :cond_1

    invoke-static {p1, v0}, Lcom/google/protobuf/nano/WireFormatNano;->parseUnknownField(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)Z

    move-result v0

    if-nez v0, :cond_0

    return-object p0

    :cond_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ch:Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;

    if-nez v0, :cond_2

    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ch:Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;

    :cond_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ch:Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;

    invoke-virtual {p1, v0}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    goto :goto_0

    :cond_3
    invoke-static {p1, v1}, Lcom/google/protobuf/nano/WireFormatNano;->getRepeatedFieldArrayLength(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)I

    move-result v0

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cg:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-nez v1, :cond_4

    const/4 v1, 0x0

    goto :goto_1

    :cond_4
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cg:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v1, v1

    :goto_1
    add-int/2addr v0, v1

    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-eqz v1, :cond_5

    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cg:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-static {v3, v2, v0, v2, v1}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    :cond_5
    :goto_2
    array-length v2, v0

    add-int/lit8 v2, v2, -0x1

    if-ge v1, v2, :cond_6

    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;-><init>()V

    aput-object v2, v0, v1

    aget-object v2, v0, v1

    invoke-virtual {p1, v2}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    add-int/lit8 v1, v1, 0x1

    goto :goto_2

    :cond_6
    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;-><init>()V

    aput-object v2, v0, v1

    aget-object v1, v0, v1

    invoke-virtual {p1, v1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cg:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    goto :goto_0

    :cond_7
    invoke-static {p1, v1}, Lcom/google/protobuf/nano/WireFormatNano;->getRepeatedFieldArrayLength(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)I

    move-result v0

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cf:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-nez v1, :cond_8

    const/4 v1, 0x0

    goto :goto_3

    :cond_8
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cf:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v1, v1

    :goto_3
    add-int/2addr v0, v1

    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-eqz v1, :cond_9

    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cf:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-static {v3, v2, v0, v2, v1}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    :cond_9
    :goto_4
    array-length v2, v0

    add-int/lit8 v2, v2, -0x1

    if-ge v1, v2, :cond_a

    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;-><init>()V

    aput-object v2, v0, v1

    aget-object v2, v0, v1

    invoke-virtual {p1, v2}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    add-int/lit8 v1, v1, 0x1

    goto :goto_4

    :cond_a
    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;-><init>()V

    aput-object v2, v0, v1

    aget-object v1, v0, v1

    invoke-virtual {p1, v1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cf:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    goto/16 :goto_0

    :cond_b
    invoke-static {p1, v1}, Lcom/google/protobuf/nano/WireFormatNano;->getRepeatedFieldArrayLength(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)I

    move-result v0

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ce:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-nez v1, :cond_c

    const/4 v1, 0x0

    goto :goto_5

    :cond_c
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ce:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v1, v1

    :goto_5
    add-int/2addr v0, v1

    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-eqz v1, :cond_d

    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ce:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-static {v3, v2, v0, v2, v1}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    :cond_d
    :goto_6
    array-length v2, v0

    add-int/lit8 v2, v2, -0x1

    if-ge v1, v2, :cond_e

    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;-><init>()V

    aput-object v2, v0, v1

    aget-object v2, v0, v1

    invoke-virtual {p1, v2}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    add-int/lit8 v1, v1, 0x1

    goto :goto_6

    :cond_e
    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;-><init>()V

    aput-object v2, v0, v1

    aget-object v1, v0, v1

    invoke-virtual {p1, v1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ce:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    goto/16 :goto_0

    :cond_f
    invoke-static {p1, v1}, Lcom/google/protobuf/nano/WireFormatNano;->getRepeatedFieldArrayLength(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)I

    move-result v0

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cd:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-nez v1, :cond_10

    const/4 v1, 0x0

    goto :goto_7

    :cond_10
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cd:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v1, v1

    :goto_7
    add-int/2addr v0, v1

    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-eqz v1, :cond_11

    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cd:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-static {v3, v2, v0, v2, v1}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    :cond_11
    :goto_8
    array-length v2, v0

    add-int/lit8 v2, v2, -0x1

    if-ge v1, v2, :cond_12

    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;-><init>()V

    aput-object v2, v0, v1

    aget-object v2, v0, v1

    invoke-virtual {p1, v2}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    add-int/lit8 v1, v1, 0x1

    goto :goto_8

    :cond_12
    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;-><init>()V

    aput-object v2, v0, v1

    aget-object v1, v0, v1

    invoke-virtual {p1, v1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cd:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    goto/16 :goto_0

    :cond_13
    invoke-static {p1, v1}, Lcom/google/protobuf/nano/WireFormatNano;->getRepeatedFieldArrayLength(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)I

    move-result v0

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cc:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-nez v1, :cond_14

    const/4 v1, 0x0

    goto :goto_9

    :cond_14
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cc:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v1, v1

    :goto_9
    add-int/2addr v0, v1

    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-eqz v1, :cond_15

    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cc:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-static {v3, v2, v0, v2, v1}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    :cond_15
    :goto_a
    array-length v2, v0

    add-int/lit8 v2, v2, -0x1

    if-ge v1, v2, :cond_16

    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;-><init>()V

    aput-object v2, v0, v1

    aget-object v2, v0, v1

    invoke-virtual {p1, v2}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    add-int/lit8 v1, v1, 0x1

    goto :goto_a

    :cond_16
    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;-><init>()V

    aput-object v2, v0, v1

    aget-object v1, v0, v1

    invoke-virtual {p1, v1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cc:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    goto/16 :goto_0

    :cond_17
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt32()I

    move-result v0

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cb:I

    goto/16 :goto_0

    :cond_18
    return-object p0
.end method

.method public final writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V
    .locals 4
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 445
    iget v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cb:I

    if-eqz v0, :cond_0

    .line 446
    iget v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cb:I

    const/4 v1, 0x1

    invoke-virtual {p1, v1, v0}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt32(II)V

    .line 448
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cc:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    const/4 v1, 0x0

    if-eqz v0, :cond_2

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cc:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v0, v0

    if-lez v0, :cond_2

    const/4 v0, 0x0

    .line 449
    :goto_0
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cc:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v2, v2

    if-ge v0, v2, :cond_2

    .line 450
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cc:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    aget-object v2, v2, v0

    if-eqz v2, :cond_1

    const/4 v3, 0x2

    .line 452
    invoke-virtual {p1, v3, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    :cond_1
    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    .line 456
    :cond_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cd:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-eqz v0, :cond_4

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cd:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v0, v0

    if-lez v0, :cond_4

    const/4 v0, 0x0

    .line 457
    :goto_1
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cd:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v2, v2

    if-ge v0, v2, :cond_4

    .line 458
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cd:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    aget-object v2, v2, v0

    if-eqz v2, :cond_3

    const/4 v3, 0x3

    .line 460
    invoke-virtual {p1, v3, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    :cond_3
    add-int/lit8 v0, v0, 0x1

    goto :goto_1

    .line 464
    :cond_4
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ce:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-eqz v0, :cond_6

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ce:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v0, v0

    if-lez v0, :cond_6

    const/4 v0, 0x0

    .line 465
    :goto_2
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ce:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v2, v2

    if-ge v0, v2, :cond_6

    .line 466
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ce:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    aget-object v2, v2, v0

    if-eqz v2, :cond_5

    const/4 v3, 0x4

    .line 468
    invoke-virtual {p1, v3, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    :cond_5
    add-int/lit8 v0, v0, 0x1

    goto :goto_2

    .line 472
    :cond_6
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cf:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-eqz v0, :cond_8

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cf:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v0, v0

    if-lez v0, :cond_8

    const/4 v0, 0x0

    .line 473
    :goto_3
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cf:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v2, v2

    if-ge v0, v2, :cond_8

    .line 474
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cf:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    aget-object v2, v2, v0

    if-eqz v2, :cond_7

    const/4 v3, 0x5

    .line 476
    invoke-virtual {p1, v3, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    :cond_7
    add-int/lit8 v0, v0, 0x1

    goto :goto_3

    .line 480
    :cond_8
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cg:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-eqz v0, :cond_a

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cg:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v0, v0

    if-lez v0, :cond_a

    .line 481
    :goto_4
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cg:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    array-length v0, v0

    if-ge v1, v0, :cond_a

    .line 482
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->cg:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    aget-object v0, v0, v1

    if-eqz v0, :cond_9

    const/4 v2, 0x6

    .line 484
    invoke-virtual {p1, v2, v0}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    :cond_9
    add-int/lit8 v1, v1, 0x1

    goto :goto_4

    .line 488
    :cond_a
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ch:Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;

    if-eqz v0, :cond_b

    const/4 v0, 0x7

    .line 489
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;->ch:Lcom/google/android/apps/nexuslauncher/reflection/e/a$d;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    .line 491
    :cond_b
    invoke-super/range {p0 .. p1}, Lcom/google/protobuf/nano/MessageNano;->writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V

    return-void
.end method
