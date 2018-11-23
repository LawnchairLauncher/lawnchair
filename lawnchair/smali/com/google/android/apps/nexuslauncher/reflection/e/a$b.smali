.class public final Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;
.super Lcom/google/protobuf/nano/MessageNano;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/e/a;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "b"
.end annotation


# instance fields
.field public bA:Ljava/lang/String;

.field public bW:Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;

.field public bX:Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;

.field public packageName:Ljava/lang/String;

.field public timestamp:J


# direct methods
.method public constructor <init>()V
    .locals 2

    .line 40
    invoke-direct/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;-><init>()V

    const-string v0, ""

    .line 41
    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bA:Ljava/lang/String;

    const-wide/16 v0, 0x0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->timestamp:J

    const/4 v0, 0x0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bW:Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bX:Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;

    const-string v0, ""

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->packageName:Ljava/lang/String;

    const/4 v0, -0x1

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->cachedSize:I

    return-void
.end method

.method public static d(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 154
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;-><init>()V

    invoke-virtual {v0, p0}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->c(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;

    move-result-object p0

    return-object p0
.end method


# virtual methods
.method public final c(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;
    .locals 2
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 106
    :cond_0
    :goto_0
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    move-result v0

    if-eqz v0, :cond_8

    const/16 v1, 0xa

    if-eq v0, v1, :cond_7

    const/16 v1, 0x10

    if-eq v0, v1, :cond_6

    const/16 v1, 0x1a

    if-eq v0, v1, :cond_4

    const/16 v1, 0x22

    if-eq v0, v1, :cond_2

    const/16 v1, 0x2a

    if-eq v0, v1, :cond_1

    .line 111
    invoke-static {p1, v0}, Lcom/google/protobuf/nano/WireFormatNano;->parseUnknownField(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)Z

    move-result v0

    if-nez v0, :cond_0

    return-object p0

    .line 139
    :cond_1
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->packageName:Ljava/lang/String;

    goto :goto_0

    .line 132
    :cond_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bX:Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;

    if-nez v0, :cond_3

    .line 133
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bX:Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;

    .line 135
    :cond_3
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bX:Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;

    invoke-virtual {p1, v0}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    goto :goto_0

    .line 125
    :cond_4
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bW:Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;

    if-nez v0, :cond_5

    .line 126
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bW:Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;

    .line 128
    :cond_5
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bW:Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;

    invoke-virtual {p1, v0}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    goto :goto_0

    .line 121
    :cond_6
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt64()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->timestamp:J

    goto :goto_0

    .line 117
    :cond_7
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bA:Ljava/lang/String;

    goto :goto_0

    :cond_8
    return-object p0
.end method

.method protected final computeSerializedSize()I
    .locals 5

    .line 77
    invoke-super/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;->computeSerializedSize()I

    move-result v0

    .line 78
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bA:Ljava/lang/String;

    const-string v2, ""

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_0

    const/4 v1, 0x1

    .line 79
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bA:Ljava/lang/String;

    .line 80
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    .line 82
    :cond_0
    iget-wide v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->timestamp:J

    const-wide/16 v3, 0x0

    cmp-long v1, v1, v3

    if-eqz v1, :cond_1

    const/4 v1, 0x2

    .line 83
    iget-wide v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->timestamp:J

    .line 84
    invoke-static {v1, v2, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt64Size(IJ)I

    move-result v1

    add-int/2addr v0, v1

    .line 86
    :cond_1
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bW:Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;

    if-eqz v1, :cond_2

    const/4 v1, 0x3

    .line 87
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bW:Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;

    .line 88
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v1

    add-int/2addr v0, v1

    .line 90
    :cond_2
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bX:Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;

    if-eqz v1, :cond_3

    const/4 v1, 0x4

    .line 91
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bX:Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;

    .line 92
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v1

    add-int/2addr v0, v1

    .line 94
    :cond_3
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->packageName:Ljava/lang/String;

    const-string v2, ""

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_4

    const/4 v1, 0x5

    .line 95
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->packageName:Ljava/lang/String;

    .line 96
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    :cond_4
    return v0
.end method

.method public final synthetic mergeFrom(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/protobuf/nano/MessageNano;
    .locals 0
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 8
    invoke-virtual/range {p0 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->c(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;

    move-result-object p1

    return-object p1
.end method

.method public final writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V
    .locals 4
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 57
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bA:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_0

    const/4 v0, 0x1

    .line 58
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bA:Ljava/lang/String;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 60
    :cond_0
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->timestamp:J

    const-wide/16 v2, 0x0

    cmp-long v0, v0, v2

    if-eqz v0, :cond_1

    const/4 v0, 0x2

    .line 61
    iget-wide v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->timestamp:J

    invoke-virtual {p1, v0, v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt64(IJ)V

    .line 63
    :cond_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bW:Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;

    if-eqz v0, :cond_2

    const/4 v0, 0x3

    .line 64
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bW:Lcom/google/android/apps/nexuslauncher/reflection/e/a$a;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    .line 66
    :cond_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bX:Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;

    if-eqz v0, :cond_3

    const/4 v0, 0x4

    .line 67
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->bX:Lcom/google/android/apps/nexuslauncher/reflection/e/a$e;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    .line 69
    :cond_3
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->packageName:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_4

    const/4 v0, 0x5

    .line 70
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$b;->packageName:Ljava/lang/String;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 72
    :cond_4
    invoke-super/range {p0 .. p1}, Lcom/google/protobuf/nano/MessageNano;->writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V

    return-void
.end method
