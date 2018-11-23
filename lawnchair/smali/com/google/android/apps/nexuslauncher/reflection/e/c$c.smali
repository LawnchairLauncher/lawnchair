.class public final Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;
.super Lcom/google/protobuf/nano/MessageNano;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/e/c;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "c"
.end annotation


# instance fields
.field public cu:Ljava/lang/String;

.field public cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

.field public timestamp:J

.field public version:I


# direct methods
.method public constructor <init>()V
    .locals 2

    .line 37
    invoke-direct/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;-><init>()V

    const-wide/16 v0, 0x0

    .line 38
    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->timestamp:J

    const/4 v0, 0x0

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->version:I

    const-string v0, ""

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cu:Ljava/lang/String;

    invoke-static {}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;->y()[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    const/4 v0, -0x1

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cachedSize:I

    return-void
.end method

.method public static g([B)Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/protobuf/nano/InvalidProtocolBufferNanoException;
        }
    .end annotation

    .line 153
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;-><init>()V

    invoke-static {v0, p0}, Lcom/google/protobuf/nano/MessageNano;->mergeFrom(Lcom/google/protobuf/nano/MessageNano;[B)Lcom/google/protobuf/nano/MessageNano;

    move-result-object p0

    check-cast p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;

    return-object p0
.end method


# virtual methods
.method protected final computeSerializedSize()I
    .locals 5

    .line 75
    invoke-super/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;->computeSerializedSize()I

    move-result v0

    .line 76
    iget-wide v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->timestamp:J

    const-wide/16 v3, 0x0

    cmp-long v1, v1, v3

    if-eqz v1, :cond_0

    .line 77
    iget-wide v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->timestamp:J

    const/4 v3, 0x1

    .line 78
    invoke-static {v3, v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt64Size(IJ)I

    move-result v1

    add-int/2addr v0, v1

    .line 80
    :cond_0
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->version:I

    if-eqz v1, :cond_1

    const/4 v1, 0x2

    .line 81
    iget v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->version:I

    .line 82
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt32Size(II)I

    move-result v1

    add-int/2addr v0, v1

    .line 84
    :cond_1
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cu:Ljava/lang/String;

    const-string v2, ""

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_2

    const/4 v1, 0x3

    .line 85
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cu:Ljava/lang/String;

    .line 86
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    .line 88
    :cond_2
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    if-eqz v1, :cond_4

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    array-length v1, v1

    if-lez v1, :cond_4

    const/4 v1, 0x0

    .line 89
    :goto_0
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    array-length v2, v2

    if-ge v1, v2, :cond_4

    .line 90
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    aget-object v2, v2, v1

    if-eqz v2, :cond_3

    const/4 v3, 0x4

    .line 93
    invoke-static {v3, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v2

    add-int/2addr v0, v2

    :cond_3
    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    :cond_4
    return v0
.end method

.method public final synthetic mergeFrom(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/protobuf/nano/MessageNano;
    .locals 4
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 8
    :cond_0
    :goto_0
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    move-result v0

    if-eqz v0, :cond_8

    const/16 v1, 0x8

    if-eq v0, v1, :cond_7

    const/16 v1, 0x10

    if-eq v0, v1, :cond_6

    const/16 v1, 0x1a

    if-eq v0, v1, :cond_5

    const/16 v1, 0x22

    if-eq v0, v1, :cond_1

    invoke-static {p1, v0}, Lcom/google/protobuf/nano/WireFormatNano;->parseUnknownField(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)Z

    move-result v0

    if-nez v0, :cond_0

    return-object p0

    :cond_1
    invoke-static {p1, v1}, Lcom/google/protobuf/nano/WireFormatNano;->getRepeatedFieldArrayLength(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)I

    move-result v0

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    const/4 v2, 0x0

    if-nez v1, :cond_2

    const/4 v1, 0x0

    goto :goto_1

    :cond_2
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    array-length v1, v1

    :goto_1
    add-int/2addr v0, v1

    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    if-eqz v1, :cond_3

    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    invoke-static {v3, v2, v0, v2, v1}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    :cond_3
    :goto_2
    array-length v2, v0

    add-int/lit8 v2, v2, -0x1

    if-ge v1, v2, :cond_4

    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;-><init>()V

    aput-object v2, v0, v1

    aget-object v2, v0, v1

    invoke-virtual {p1, v2}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    add-int/lit8 v1, v1, 0x1

    goto :goto_2

    :cond_4
    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;-><init>()V

    aput-object v2, v0, v1

    aget-object v1, v0, v1

    invoke-virtual {p1, v1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    goto :goto_0

    :cond_5
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cu:Ljava/lang/String;

    goto :goto_0

    :cond_6
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt32()I

    move-result v0

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->version:I

    goto :goto_0

    :cond_7
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt64()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->timestamp:J

    goto :goto_0

    :cond_8
    return-object p0
.end method

.method public final writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V
    .locals 4
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 53
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->timestamp:J

    const-wide/16 v2, 0x0

    cmp-long v0, v0, v2

    if-eqz v0, :cond_0

    .line 54
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->timestamp:J

    const/4 v2, 0x1

    invoke-virtual {p1, v2, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt64(IJ)V

    .line 56
    :cond_0
    iget v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->version:I

    if-eqz v0, :cond_1

    const/4 v0, 0x2

    .line 57
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->version:I

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt32(II)V

    .line 59
    :cond_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cu:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_2

    const/4 v0, 0x3

    .line 60
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cu:Ljava/lang/String;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 62
    :cond_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    if-eqz v0, :cond_4

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    array-length v0, v0

    if-lez v0, :cond_4

    const/4 v0, 0x0

    .line 63
    :goto_0
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    array-length v1, v1

    if-ge v0, v1, :cond_4

    .line 64
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/c$c;->cv:[Lcom/google/android/apps/nexuslauncher/reflection/e/c$e;

    aget-object v1, v1, v0

    if-eqz v1, :cond_3

    const/4 v2, 0x4

    .line 66
    invoke-virtual {p1, v2, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    :cond_3
    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    .line 70
    :cond_4
    invoke-super/range {p0 .. p1}, Lcom/google/protobuf/nano/MessageNano;->writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V

    return-void
.end method
