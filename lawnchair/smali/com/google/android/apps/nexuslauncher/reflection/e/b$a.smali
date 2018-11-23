.class public final Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;
.super Lcom/google/protobuf/nano/MessageNano;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/e/b;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "a"
.end annotation


# instance fields
.field public bq:Ljava/lang/String;

.field public bw:[Ljava/lang/String;

.field public ci:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

.field public cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

.field public ck:Ljava/lang/String;

.field public cl:Ljava/lang/String;

.field public duration:J

.field public id:Ljava/lang/String;

.field public type:I


# direct methods
.method public constructor <init>()V
    .locals 2

    .line 64
    invoke-direct/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;-><init>()V

    const-string v0, ""

    .line 65
    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    const/4 v0, 0x0

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->type:I

    const-wide/16 v0, 0x0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->duration:J

    const-string v0, ""

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bq:Ljava/lang/String;

    const/4 v0, 0x0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ci:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    const-string v0, ""

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ck:Ljava/lang/String;

    sget-object v0, Lcom/google/protobuf/nano/WireFormatNano;->EMPTY_STRING_ARRAY:[Ljava/lang/String;

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    const-string v0, ""

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cl:Ljava/lang/String;

    const/4 v0, -0x1

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cachedSize:I

    return-void
.end method

.method public static c([B)Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/protobuf/nano/InvalidProtocolBufferNanoException;
        }
    .end annotation

    .line 260
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;-><init>()V

    invoke-static {v0, p0}, Lcom/google/protobuf/nano/MessageNano;->mergeFrom(Lcom/google/protobuf/nano/MessageNano;[B)Lcom/google/protobuf/nano/MessageNano;

    move-result-object p0

    check-cast p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    return-object p0
.end method

.method public static f(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 266
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;-><init>()V

    invoke-virtual {v0, p0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->e(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    move-result-object p0

    return-object p0
.end method


# virtual methods
.method protected final computeSerializedSize()I
    .locals 7

    .line 122
    invoke-super/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;->computeSerializedSize()I

    move-result v0

    .line 123
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    const-string v2, ""

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    const/4 v2, 0x1

    if-nez v1, :cond_0

    .line 124
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    .line 125
    invoke-static {v2, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    .line 127
    :cond_0
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->type:I

    if-eqz v1, :cond_1

    const/4 v1, 0x2

    .line 128
    iget v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->type:I

    .line 129
    invoke-static {v1, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt32Size(II)I

    move-result v1

    add-int/2addr v0, v1

    .line 131
    :cond_1
    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->duration:J

    const-wide/16 v5, 0x0

    cmp-long v1, v3, v5

    if-eqz v1, :cond_2

    const/4 v1, 0x3

    .line 132
    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->duration:J

    .line 133
    invoke-static {v1, v3, v4}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt64Size(IJ)I

    move-result v1

    add-int/2addr v0, v1

    .line 135
    :cond_2
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bq:Ljava/lang/String;

    const-string v3, ""

    invoke-virtual {v1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_3

    const/4 v1, 0x4

    .line 136
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bq:Ljava/lang/String;

    .line 137
    invoke-static {v1, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    .line 139
    :cond_3
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ci:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    if-eqz v1, :cond_4

    const/4 v1, 0x5

    .line 140
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ci:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    .line 141
    invoke-static {v1, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v1

    add-int/2addr v0, v1

    .line 143
    :cond_4
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    if-eqz v1, :cond_5

    const/4 v1, 0x6

    .line 144
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    .line 145
    invoke-static {v1, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v1

    add-int/2addr v0, v1

    .line 147
    :cond_5
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ck:Ljava/lang/String;

    const-string v3, ""

    invoke-virtual {v1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_6

    const/4 v1, 0x7

    .line 148
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ck:Ljava/lang/String;

    .line 149
    invoke-static {v1, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    .line 151
    :cond_6
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    if-eqz v1, :cond_9

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    array-length v1, v1

    if-lez v1, :cond_9

    const/4 v1, 0x0

    const/4 v3, 0x0

    const/4 v4, 0x0

    .line 154
    :goto_0
    iget-object v5, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    array-length v5, v5

    if-ge v1, v5, :cond_8

    .line 155
    iget-object v5, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    aget-object v5, v5, v1

    if-eqz v5, :cond_7

    add-int/lit8 v4, v4, 0x1

    .line 159
    invoke-static {v5}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSizeNoTag(Ljava/lang/String;)I

    move-result v5

    add-int/2addr v3, v5

    :cond_7
    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    :cond_8
    add-int/2addr v0, v3

    mul-int/lit8 v4, v4, 0x1

    add-int/2addr v0, v4

    .line 165
    :cond_9
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cl:Ljava/lang/String;

    const-string v2, ""

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_a

    const/16 v1, 0x9

    .line 166
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cl:Ljava/lang/String;

    .line 167
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    :cond_a
    return v0
.end method

.method public final e(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;
    .locals 4
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 177
    :cond_0
    :goto_0
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    move-result v0

    if-eqz v0, :cond_f

    const/16 v1, 0xa

    if-eq v0, v1, :cond_e

    const/16 v1, 0x10

    if-eq v0, v1, :cond_d

    const/16 v1, 0x18

    if-eq v0, v1, :cond_c

    const/16 v1, 0x22

    if-eq v0, v1, :cond_b

    const/16 v1, 0x2a

    if-eq v0, v1, :cond_9

    const/16 v1, 0x32

    if-eq v0, v1, :cond_7

    const/16 v1, 0x3a

    if-eq v0, v1, :cond_6

    const/16 v1, 0x42

    if-eq v0, v1, :cond_2

    const/16 v1, 0x4a

    if-eq v0, v1, :cond_1

    .line 182
    invoke-static {p1, v0}, Lcom/google/protobuf/nano/WireFormatNano;->parseUnknownField(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)Z

    move-result v0

    if-nez v0, :cond_0

    return-object p0

    .line 251
    :cond_1
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cl:Ljava/lang/String;

    goto :goto_0

    .line 235
    :cond_2
    invoke-static {p1, v1}, Lcom/google/protobuf/nano/WireFormatNano;->getRepeatedFieldArrayLength(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)I

    move-result v0

    .line 236
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    const/4 v2, 0x0

    if-nez v1, :cond_3

    const/4 v1, 0x0

    goto :goto_1

    :cond_3
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    array-length v1, v1

    :goto_1
    add-int/2addr v0, v1

    .line 237
    new-array v0, v0, [Ljava/lang/String;

    if-eqz v1, :cond_4

    .line 239
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    invoke-static {v3, v2, v0, v2, v1}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    .line 241
    :cond_4
    :goto_2
    array-length v2, v0

    add-int/lit8 v2, v2, -0x1

    if-ge v1, v2, :cond_5

    .line 242
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v2

    aput-object v2, v0, v1

    .line 243
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    add-int/lit8 v1, v1, 0x1

    goto :goto_2

    .line 246
    :cond_5
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v2

    aput-object v2, v0, v1

    .line 247
    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    goto :goto_0

    .line 230
    :cond_6
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ck:Ljava/lang/String;

    goto :goto_0

    .line 223
    :cond_7
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    if-nez v0, :cond_8

    .line 224
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    .line 226
    :cond_8
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    invoke-virtual {p1, v0}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    goto/16 :goto_0

    .line 216
    :cond_9
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ci:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    if-nez v0, :cond_a

    .line 217
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ci:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    .line 219
    :cond_a
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ci:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    invoke-virtual {p1, v0}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    goto/16 :goto_0

    .line 212
    :cond_b
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bq:Ljava/lang/String;

    goto/16 :goto_0

    .line 208
    :cond_c
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt64()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->duration:J

    goto/16 :goto_0

    .line 192
    :cond_d
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt32()I

    move-result v0

    packed-switch v0, :pswitch_data_0

    goto/16 :goto_0

    .line 202
    :pswitch_0
    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->type:I

    goto/16 :goto_0

    .line 188
    :cond_e
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    goto/16 :goto_0

    :cond_f
    return-object p0

    nop

    :pswitch_data_0
    .packed-switch 0x0
        :pswitch_0
        :pswitch_0
        :pswitch_0
        :pswitch_0
        :pswitch_0
        :pswitch_0
        :pswitch_0
        :pswitch_0
    .end packed-switch
.end method

.method public final synthetic mergeFrom(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/protobuf/nano/MessageNano;
    .locals 0
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 8
    invoke-virtual/range {p0 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->e(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

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

    .line 85
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_0

    .line 86
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    const/4 v1, 0x1

    invoke-virtual {p1, v1, v0}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 88
    :cond_0
    iget v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->type:I

    if-eqz v0, :cond_1

    const/4 v0, 0x2

    .line 89
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->type:I

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt32(II)V

    .line 91
    :cond_1
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->duration:J

    const-wide/16 v2, 0x0

    cmp-long v0, v0, v2

    if-eqz v0, :cond_2

    const/4 v0, 0x3

    .line 92
    iget-wide v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->duration:J

    invoke-virtual {p1, v0, v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt64(IJ)V

    .line 94
    :cond_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bq:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_3

    const/4 v0, 0x4

    .line 95
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bq:Ljava/lang/String;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 97
    :cond_3
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ci:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    if-eqz v0, :cond_4

    const/4 v0, 0x5

    .line 98
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ci:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    .line 100
    :cond_4
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    if-eqz v0, :cond_5

    const/4 v0, 0x6

    .line 101
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    .line 103
    :cond_5
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ck:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_6

    const/4 v0, 0x7

    .line 104
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ck:Ljava/lang/String;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 106
    :cond_6
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    if-eqz v0, :cond_8

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    array-length v0, v0

    if-lez v0, :cond_8

    const/4 v0, 0x0

    .line 107
    :goto_0
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    array-length v1, v1

    if-ge v0, v1, :cond_8

    .line 108
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    aget-object v1, v1, v0

    if-eqz v1, :cond_7

    const/16 v2, 0x8

    .line 110
    invoke-virtual {p1, v2, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    :cond_7
    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    .line 114
    :cond_8
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cl:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_9

    const/16 v0, 0x9

    .line 115
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cl:Ljava/lang/String;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 117
    :cond_9
    invoke-super/range {p0 .. p1}, Lcom/google/protobuf/nano/MessageNano;->writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V

    return-void
.end method
