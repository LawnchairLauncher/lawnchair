.class public final Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;
.super Lcom/google/protobuf/nano/MessageNano;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/e/c;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "d"
.end annotation


# instance fields
.field public cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

.field public cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

.field public cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

.field public cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

.field public cw:Ljava/lang/String;

.field public cx:[B

.field public cy:I

.field public cz:I


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 204
    invoke-direct/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;-><init>()V

    const-string v0, ""

    .line 205
    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cw:Ljava/lang/String;

    sget-object v0, Lcom/google/protobuf/nano/WireFormatNano;->EMPTY_BYTES:[B

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cx:[B

    const/4 v0, 0x0

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cy:I

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cz:I

    invoke-static {}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->z()[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    invoke-static {}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->w()[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    invoke-static {}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->w()[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    invoke-static {}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->w()[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    const/4 v0, -0x1

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cachedSize:I

    return-void
.end method


# virtual methods
.method protected final computeSerializedSize()I
    .locals 5

    .line 273
    invoke-super/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;->computeSerializedSize()I

    move-result v0

    .line 274
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cw:Ljava/lang/String;

    const-string v2, ""

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_0

    .line 275
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cw:Ljava/lang/String;

    const/4 v2, 0x1

    .line 276
    invoke-static {v2, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    .line 278
    :cond_0
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cx:[B

    sget-object v2, Lcom/google/protobuf/nano/WireFormatNano;->EMPTY_BYTES:[B

    invoke-static {v1, v2}, Ljava/util/Arrays;->equals([B[B)Z

    move-result v1

    if-nez v1, :cond_1

    const/4 v1, 0x2

    .line 279
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cx:[B

    .line 280
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeBytesSize(I[B)I

    move-result v1

    add-int/2addr v0, v1

    .line 282
    :cond_1
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cy:I

    if-eqz v1, :cond_2

    const/4 v1, 0x3

    .line 283
    iget v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cy:I

    .line 284
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt32Size(II)I

    move-result v1

    add-int/2addr v0, v1

    .line 286
    :cond_2
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cz:I

    if-eqz v1, :cond_3

    const/4 v1, 0x4

    .line 287
    iget v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cz:I

    .line 288
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt32Size(II)I

    move-result v1

    add-int/2addr v0, v1

    .line 290
    :cond_3
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    const/4 v2, 0x0

    if-eqz v1, :cond_6

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    array-length v1, v1

    if-lez v1, :cond_6

    move v1, v0

    const/4 v0, 0x0

    .line 291
    :goto_0
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    array-length v3, v3

    if-ge v0, v3, :cond_5

    .line 292
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    aget-object v3, v3, v0

    if-eqz v3, :cond_4

    const/4 v4, 0x5

    .line 295
    invoke-static {v4, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v3

    add-int/2addr v1, v3

    :cond_4
    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    :cond_5
    move v0, v1

    .line 299
    :cond_6
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    if-eqz v1, :cond_9

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v1, v1

    if-lez v1, :cond_9

    move v1, v0

    const/4 v0, 0x0

    .line 300
    :goto_1
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v3, v3

    if-ge v0, v3, :cond_8

    .line 301
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    aget-object v3, v3, v0

    if-eqz v3, :cond_7

    const/4 v4, 0x6

    .line 304
    invoke-static {v4, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v3

    add-int/2addr v1, v3

    :cond_7
    add-int/lit8 v0, v0, 0x1

    goto :goto_1

    :cond_8
    move v0, v1

    .line 308
    :cond_9
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    if-eqz v1, :cond_c

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v1, v1

    if-lez v1, :cond_c

    move v1, v0

    const/4 v0, 0x0

    .line 309
    :goto_2
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v3, v3

    if-ge v0, v3, :cond_b

    .line 310
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    aget-object v3, v3, v0

    if-eqz v3, :cond_a

    const/4 v4, 0x7

    .line 313
    invoke-static {v4, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v3

    add-int/2addr v1, v3

    :cond_a
    add-int/lit8 v0, v0, 0x1

    goto :goto_2

    :cond_b
    move v0, v1

    .line 317
    :cond_c
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    if-eqz v1, :cond_e

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v1, v1

    if-lez v1, :cond_e

    .line 318
    :goto_3
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v1, v1

    if-ge v2, v1, :cond_e

    .line 319
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    aget-object v1, v1, v2

    if-eqz v1, :cond_d

    const/16 v3, 0x8

    .line 322
    invoke-static {v3, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v1

    add-int/2addr v0, v1

    :cond_d
    add-int/lit8 v2, v2, 0x1

    goto :goto_3

    :cond_e
    return v0
.end method

.method public final synthetic mergeFrom(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/protobuf/nano/MessageNano;
    .locals 4
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 163
    :cond_0
    :goto_0
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    move-result v0

    if-eqz v0, :cond_15

    const/16 v1, 0xa

    if-eq v0, v1, :cond_14

    const/16 v1, 0x12

    if-eq v0, v1, :cond_13

    const/16 v1, 0x18

    if-eq v0, v1, :cond_12

    const/16 v1, 0x20

    if-eq v0, v1, :cond_11

    const/16 v1, 0x2a

    const/4 v2, 0x0

    if-eq v0, v1, :cond_d

    const/16 v1, 0x32

    if-eq v0, v1, :cond_9

    const/16 v1, 0x3a

    if-eq v0, v1, :cond_5

    const/16 v1, 0x42

    if-eq v0, v1, :cond_1

    invoke-static {p1, v0}, Lcom/google/protobuf/nano/WireFormatNano;->parseUnknownField(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)Z

    move-result v0

    if-nez v0, :cond_0

    return-object p0

    :cond_1
    invoke-static {p1, v1}, Lcom/google/protobuf/nano/WireFormatNano;->getRepeatedFieldArrayLength(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)I

    move-result v0

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    if-nez v1, :cond_2

    const/4 v1, 0x0

    goto :goto_1

    :cond_2
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v1, v1

    :goto_1
    add-int/2addr v0, v1

    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    if-eqz v1, :cond_3

    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    invoke-static {v3, v2, v0, v2, v1}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    :cond_3
    :goto_2
    array-length v2, v0

    add-int/lit8 v2, v2, -0x1

    if-ge v1, v2, :cond_4

    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;-><init>()V

    aput-object v2, v0, v1

    aget-object v2, v0, v1

    invoke-virtual {p1, v2}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    add-int/lit8 v1, v1, 0x1

    goto :goto_2

    :cond_4
    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;-><init>()V

    aput-object v2, v0, v1

    aget-object v1, v0, v1

    invoke-virtual {p1, v1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    goto :goto_0

    :cond_5
    invoke-static {p1, v1}, Lcom/google/protobuf/nano/WireFormatNano;->getRepeatedFieldArrayLength(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)I

    move-result v0

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    if-nez v1, :cond_6

    const/4 v1, 0x0

    goto :goto_3

    :cond_6
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v1, v1

    :goto_3
    add-int/2addr v0, v1

    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    if-eqz v1, :cond_7

    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    invoke-static {v3, v2, v0, v2, v1}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    :cond_7
    :goto_4
    array-length v2, v0

    add-int/lit8 v2, v2, -0x1

    if-ge v1, v2, :cond_8

    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;-><init>()V

    aput-object v2, v0, v1

    aget-object v2, v0, v1

    invoke-virtual {p1, v2}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    add-int/lit8 v1, v1, 0x1

    goto :goto_4

    :cond_8
    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;-><init>()V

    aput-object v2, v0, v1

    aget-object v1, v0, v1

    invoke-virtual {p1, v1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    goto/16 :goto_0

    :cond_9
    invoke-static {p1, v1}, Lcom/google/protobuf/nano/WireFormatNano;->getRepeatedFieldArrayLength(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)I

    move-result v0

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    if-nez v1, :cond_a

    const/4 v1, 0x0

    goto :goto_5

    :cond_a
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v1, v1

    :goto_5
    add-int/2addr v0, v1

    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    if-eqz v1, :cond_b

    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    invoke-static {v3, v2, v0, v2, v1}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    :cond_b
    :goto_6
    array-length v2, v0

    add-int/lit8 v2, v2, -0x1

    if-ge v1, v2, :cond_c

    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;-><init>()V

    aput-object v2, v0, v1

    aget-object v2, v0, v1

    invoke-virtual {p1, v2}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    add-int/lit8 v1, v1, 0x1

    goto :goto_6

    :cond_c
    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;-><init>()V

    aput-object v2, v0, v1

    aget-object v1, v0, v1

    invoke-virtual {p1, v1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    goto/16 :goto_0

    :cond_d
    invoke-static {p1, v1}, Lcom/google/protobuf/nano/WireFormatNano;->getRepeatedFieldArrayLength(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)I

    move-result v0

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    if-nez v1, :cond_e

    const/4 v1, 0x0

    goto :goto_7

    :cond_e
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    array-length v1, v1

    :goto_7
    add-int/2addr v0, v1

    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    if-eqz v1, :cond_f

    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    invoke-static {v3, v2, v0, v2, v1}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    :cond_f
    :goto_8
    array-length v2, v0

    add-int/lit8 v2, v2, -0x1

    if-ge v1, v2, :cond_10

    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;-><init>()V

    aput-object v2, v0, v1

    aget-object v2, v0, v1

    invoke-virtual {p1, v2}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    add-int/lit8 v1, v1, 0x1

    goto :goto_8

    :cond_10
    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;-><init>()V

    aput-object v2, v0, v1

    aget-object v1, v0, v1

    invoke-virtual {p1, v1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    goto/16 :goto_0

    :cond_11
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt32()I

    move-result v0

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cz:I

    goto/16 :goto_0

    :cond_12
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt32()I

    move-result v0

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cy:I

    goto/16 :goto_0

    :cond_13
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readBytes()[B

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cx:[B

    goto/16 :goto_0

    :cond_14
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cw:Ljava/lang/String;

    goto/16 :goto_0

    :cond_15
    return-object p0
.end method

.method public final writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V
    .locals 4
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 224
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cw:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_0

    .line 225
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cw:Ljava/lang/String;

    const/4 v1, 0x1

    invoke-virtual {p1, v1, v0}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 227
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cx:[B

    sget-object v1, Lcom/google/protobuf/nano/WireFormatNano;->EMPTY_BYTES:[B

    invoke-static {v0, v1}, Ljava/util/Arrays;->equals([B[B)Z

    move-result v0

    if-nez v0, :cond_1

    const/4 v0, 0x2

    .line 228
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cx:[B

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeBytes(I[B)V

    .line 230
    :cond_1
    iget v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cy:I

    if-eqz v0, :cond_2

    const/4 v0, 0x3

    .line 231
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cy:I

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt32(II)V

    .line 233
    :cond_2
    iget v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cz:I

    if-eqz v0, :cond_3

    const/4 v0, 0x4

    .line 234
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cz:I

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt32(II)V

    .line 236
    :cond_3
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    const/4 v1, 0x0

    if-eqz v0, :cond_5

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    array-length v0, v0

    if-lez v0, :cond_5

    const/4 v0, 0x0

    .line 237
    :goto_0
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    array-length v2, v2

    if-ge v0, v2, :cond_5

    .line 238
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cA:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    aget-object v2, v2, v0

    if-eqz v2, :cond_4

    const/4 v3, 0x5

    .line 240
    invoke-virtual {p1, v3, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    :cond_4
    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    .line 244
    :cond_5
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    if-eqz v0, :cond_7

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v0, v0

    if-lez v0, :cond_7

    const/4 v0, 0x0

    .line 245
    :goto_1
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v2, v2

    if-ge v0, v2, :cond_7

    .line 246
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cB:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    aget-object v2, v2, v0

    if-eqz v2, :cond_6

    const/4 v3, 0x6

    .line 248
    invoke-virtual {p1, v3, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    :cond_6
    add-int/lit8 v0, v0, 0x1

    goto :goto_1

    .line 252
    :cond_7
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    if-eqz v0, :cond_9

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v0, v0

    if-lez v0, :cond_9

    const/4 v0, 0x0

    .line 253
    :goto_2
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v2, v2

    if-ge v0, v2, :cond_9

    .line 254
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cC:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    aget-object v2, v2, v0

    if-eqz v2, :cond_8

    const/4 v3, 0x7

    .line 256
    invoke-virtual {p1, v3, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    :cond_8
    add-int/lit8 v0, v0, 0x1

    goto :goto_2

    .line 260
    :cond_9
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    if-eqz v0, :cond_b

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v0, v0

    if-lez v0, :cond_b

    .line 261
    :goto_3
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    array-length v0, v0

    if-ge v1, v0, :cond_b

    .line 262
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$d;->cD:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    aget-object v0, v0, v1

    if-eqz v0, :cond_a

    const/16 v2, 0x8

    .line 264
    invoke-virtual {p1, v2, v0}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    :cond_a
    add-int/lit8 v1, v1, 0x1

    goto :goto_3

    .line 268
    :cond_b
    invoke-super/range {p0 .. p1}, Lcom/google/protobuf/nano/MessageNano;->writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V

    return-void
.end method
