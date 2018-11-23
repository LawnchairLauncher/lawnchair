.class public final Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;
.super Lcom/google/protobuf/nano/MessageNano;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/c/a/a;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "a"
.end annotation


# instance fields
.field public bp:Ljava/lang/String;

.field public bq:Ljava/lang/String;

.field public br:J

.field public bs:J

.field public bt:Ljava/lang/String;

.field public bu:J

.field public bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

.field public bw:[Ljava/lang/String;

.field public bx:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$d;

.field public by:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$b;

.field public bz:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$c;

.field public duration:J

.field public id:Ljava/lang/String;

.field public time:J

.field public type:Ljava/lang/String;


# direct methods
.method public constructor <init>()V
    .locals 3

    .line 70
    invoke-direct/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;-><init>()V

    const-string v0, ""

    .line 71
    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->id:Ljava/lang/String;

    const-string v0, ""

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->type:Ljava/lang/String;

    const-string v0, ""

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bp:Ljava/lang/String;

    const-string v0, ""

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bq:Ljava/lang/String;

    const-wide/16 v0, 0x0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->time:J

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->br:J

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bs:J

    const-string v2, ""

    iput-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bt:Ljava/lang/String;

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bu:J

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->duration:J

    invoke-static {}, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;->p()[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    sget-object v0, Lcom/google/protobuf/nano/WireFormatNano;->EMPTY_STRING_ARRAY:[Ljava/lang/String;

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    const/4 v0, 0x0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bx:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$d;

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->by:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$b;

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bz:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$c;

    const/4 v0, -0x1

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->cachedSize:I

    return-void
.end method

.method public static a([B)Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/protobuf/nano/InvalidProtocolBufferNanoException;
        }
    .end annotation

    .line 355
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;-><init>()V

    invoke-static {v0, p0}, Lcom/google/protobuf/nano/MessageNano;->mergeFrom(Lcom/google/protobuf/nano/MessageNano;[B)Lcom/google/protobuf/nano/MessageNano;

    move-result-object p0

    check-cast p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;

    return-object p0
.end method

.method public static b(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 361
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;-><init>()V

    invoke-virtual {v0, p0}, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->a(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;

    move-result-object p0

    return-object p0
.end method


# virtual methods
.method public final a(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;
    .locals 4
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 241
    :cond_0
    :goto_0
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    move-result v0

    const/4 v1, 0x0

    sparse-switch v0, :sswitch_data_0

    .line 246
    invoke-static {p1, v0}, Lcom/google/protobuf/nano/WireFormatNano;->parseUnknownField(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)Z

    move-result v0

    if-nez v0, :cond_0

    return-object p0

    .line 343
    :sswitch_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bz:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$c;

    if-nez v0, :cond_1

    .line 344
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$c;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$c;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bz:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$c;

    .line 346
    :cond_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bz:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$c;

    invoke-virtual {p1, v0}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    goto :goto_0

    .line 336
    :sswitch_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->by:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$b;

    if-nez v0, :cond_2

    .line 337
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$b;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$b;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->by:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$b;

    .line 339
    :cond_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->by:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$b;

    invoke-virtual {p1, v0}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    goto :goto_0

    .line 329
    :sswitch_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bx:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$d;

    if-nez v0, :cond_3

    .line 330
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$d;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$d;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bx:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$d;

    .line 332
    :cond_3
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bx:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$d;

    invoke-virtual {p1, v0}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    goto :goto_0

    :sswitch_3
    const/16 v0, 0x62

    .line 313
    invoke-static {p1, v0}, Lcom/google/protobuf/nano/WireFormatNano;->getRepeatedFieldArrayLength(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)I

    move-result v0

    .line 314
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    if-nez v2, :cond_4

    const/4 v2, 0x0

    goto :goto_1

    :cond_4
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    array-length v2, v2

    :goto_1
    add-int/2addr v0, v2

    .line 315
    new-array v0, v0, [Ljava/lang/String;

    if-eqz v2, :cond_5

    .line 317
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    invoke-static {v3, v1, v0, v1, v2}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    .line 319
    :cond_5
    :goto_2
    array-length v1, v0

    add-int/lit8 v1, v1, -0x1

    if-ge v2, v1, :cond_6

    .line 320
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v1

    aput-object v1, v0, v2

    .line 321
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    add-int/lit8 v2, v2, 0x1

    goto :goto_2

    .line 324
    :cond_6
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v1

    aput-object v1, v0, v2

    .line 325
    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    goto :goto_0

    :sswitch_4
    const/16 v0, 0x5a

    .line 293
    invoke-static {p1, v0}, Lcom/google/protobuf/nano/WireFormatNano;->getRepeatedFieldArrayLength(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)I

    move-result v0

    .line 294
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    if-nez v2, :cond_7

    const/4 v2, 0x0

    goto :goto_3

    :cond_7
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    array-length v2, v2

    :goto_3
    add-int/2addr v0, v2

    .line 295
    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    if-eqz v2, :cond_8

    .line 298
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    invoke-static {v3, v1, v0, v1, v2}, Ljava/lang/System;->arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V

    .line 300
    :cond_8
    :goto_4
    array-length v1, v0

    add-int/lit8 v1, v1, -0x1

    if-ge v2, v1, :cond_9

    .line 301
    new-instance v1, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    invoke-direct {v1}, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;-><init>()V

    aput-object v1, v0, v2

    .line 302
    aget-object v1, v0, v2

    invoke-virtual {p1, v1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    .line 303
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    add-int/lit8 v2, v2, 0x1

    goto :goto_4

    .line 306
    :cond_9
    new-instance v1, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    invoke-direct {v1}, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;-><init>()V

    aput-object v1, v0, v2

    .line 307
    aget-object v1, v0, v2

    invoke-virtual {p1, v1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    .line 308
    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    goto/16 :goto_0

    .line 288
    :sswitch_5
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt64()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->duration:J

    goto/16 :goto_0

    .line 284
    :sswitch_6
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt64()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bu:J

    goto/16 :goto_0

    .line 280
    :sswitch_7
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bt:Ljava/lang/String;

    goto/16 :goto_0

    .line 276
    :sswitch_8
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt64()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bs:J

    goto/16 :goto_0

    .line 272
    :sswitch_9
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt64()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->br:J

    goto/16 :goto_0

    .line 268
    :sswitch_a
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt64()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->time:J

    goto/16 :goto_0

    .line 264
    :sswitch_b
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bq:Ljava/lang/String;

    goto/16 :goto_0

    .line 260
    :sswitch_c
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bp:Ljava/lang/String;

    goto/16 :goto_0

    .line 256
    :sswitch_d
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->type:Ljava/lang/String;

    goto/16 :goto_0

    .line 252
    :sswitch_e
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->id:Ljava/lang/String;

    goto/16 :goto_0

    :sswitch_f
    return-object p0

    :sswitch_data_0
    .sparse-switch
        0x0 -> :sswitch_f
        0xa -> :sswitch_e
        0x12 -> :sswitch_d
        0x1a -> :sswitch_c
        0x22 -> :sswitch_b
        0x28 -> :sswitch_a
        0x30 -> :sswitch_9
        0x38 -> :sswitch_8
        0x42 -> :sswitch_7
        0x48 -> :sswitch_6
        0x50 -> :sswitch_5
        0x5a -> :sswitch_4
        0x62 -> :sswitch_3
        0x6a -> :sswitch_2
        0x72 -> :sswitch_1
        0x7a -> :sswitch_0
    .end sparse-switch
.end method

.method protected final computeSerializedSize()I
    .locals 7

    .line 157
    invoke-super/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;->computeSerializedSize()I

    move-result v0

    .line 158
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->id:Ljava/lang/String;

    const-string v2, ""

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    const/4 v2, 0x1

    if-nez v1, :cond_0

    .line 159
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->id:Ljava/lang/String;

    .line 160
    invoke-static {v2, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    .line 162
    :cond_0
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->type:Ljava/lang/String;

    const-string v3, ""

    invoke-virtual {v1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_1

    const/4 v1, 0x2

    .line 163
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->type:Ljava/lang/String;

    .line 164
    invoke-static {v1, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    .line 166
    :cond_1
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bp:Ljava/lang/String;

    const-string v3, ""

    invoke-virtual {v1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_2

    const/4 v1, 0x3

    .line 167
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bp:Ljava/lang/String;

    .line 168
    invoke-static {v1, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    .line 170
    :cond_2
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bq:Ljava/lang/String;

    const-string v3, ""

    invoke-virtual {v1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_3

    const/4 v1, 0x4

    .line 171
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bq:Ljava/lang/String;

    .line 172
    invoke-static {v1, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    .line 174
    :cond_3
    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->time:J

    const-wide/16 v5, 0x0

    cmp-long v1, v3, v5

    if-eqz v1, :cond_4

    const/4 v1, 0x5

    .line 175
    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->time:J

    .line 176
    invoke-static {v1, v3, v4}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt64Size(IJ)I

    move-result v1

    add-int/2addr v0, v1

    .line 178
    :cond_4
    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->br:J

    cmp-long v1, v3, v5

    if-eqz v1, :cond_5

    const/4 v1, 0x6

    .line 179
    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->br:J

    .line 180
    invoke-static {v1, v3, v4}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt64Size(IJ)I

    move-result v1

    add-int/2addr v0, v1

    .line 182
    :cond_5
    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bs:J

    cmp-long v1, v3, v5

    if-eqz v1, :cond_6

    const/4 v1, 0x7

    .line 183
    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bs:J

    .line 184
    invoke-static {v1, v3, v4}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt64Size(IJ)I

    move-result v1

    add-int/2addr v0, v1

    .line 186
    :cond_6
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bt:Ljava/lang/String;

    const-string v3, ""

    invoke-virtual {v1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_7

    const/16 v1, 0x8

    .line 187
    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bt:Ljava/lang/String;

    .line 188
    invoke-static {v1, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    .line 190
    :cond_7
    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bu:J

    cmp-long v1, v3, v5

    if-eqz v1, :cond_8

    const/16 v1, 0x9

    .line 191
    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bu:J

    .line 192
    invoke-static {v1, v3, v4}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt64Size(IJ)I

    move-result v1

    add-int/2addr v0, v1

    .line 194
    :cond_8
    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->duration:J

    cmp-long v1, v3, v5

    if-eqz v1, :cond_9

    const/16 v1, 0xa

    .line 195
    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->duration:J

    .line 196
    invoke-static {v1, v3, v4}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt64Size(IJ)I

    move-result v1

    add-int/2addr v0, v1

    .line 198
    :cond_9
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    const/4 v3, 0x0

    if-eqz v1, :cond_c

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    array-length v1, v1

    if-lez v1, :cond_c

    move v1, v0

    const/4 v0, 0x0

    .line 199
    :goto_0
    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    array-length v4, v4

    if-ge v0, v4, :cond_b

    .line 200
    iget-object v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    aget-object v4, v4, v0

    if-eqz v4, :cond_a

    const/16 v5, 0xb

    .line 203
    invoke-static {v5, v4}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v4

    add-int/2addr v1, v4

    :cond_a
    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    :cond_b
    move v0, v1

    .line 207
    :cond_c
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    if-eqz v1, :cond_f

    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    array-length v1, v1

    if-lez v1, :cond_f

    const/4 v1, 0x0

    const/4 v4, 0x0

    .line 210
    :goto_1
    iget-object v5, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    array-length v5, v5

    if-ge v3, v5, :cond_e

    .line 211
    iget-object v5, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    aget-object v5, v5, v3

    if-eqz v5, :cond_d

    add-int/lit8 v4, v4, 0x1

    .line 215
    invoke-static {v5}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSizeNoTag(Ljava/lang/String;)I

    move-result v5

    add-int/2addr v1, v5

    :cond_d
    add-int/lit8 v3, v3, 0x1

    goto :goto_1

    :cond_e
    add-int/2addr v0, v1

    mul-int/lit8 v4, v4, 0x1

    add-int/2addr v0, v4

    .line 221
    :cond_f
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bx:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$d;

    if-eqz v1, :cond_10

    const/16 v1, 0xd

    .line 222
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bx:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$d;

    .line 223
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v1

    add-int/2addr v0, v1

    .line 225
    :cond_10
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->by:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$b;

    if-eqz v1, :cond_11

    const/16 v1, 0xe

    .line 226
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->by:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$b;

    .line 227
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v1

    add-int/2addr v0, v1

    .line 229
    :cond_11
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bz:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$c;

    if-eqz v1, :cond_12

    const/16 v1, 0xf

    .line 230
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bz:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$c;

    .line 231
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v1

    add-int/2addr v0, v1

    :cond_12
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
    invoke-virtual/range {p0 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->a(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;

    move-result-object p1

    return-object p1
.end method

.method public final writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V
    .locals 6
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 97
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->id:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_0

    .line 98
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->id:Ljava/lang/String;

    const/4 v1, 0x1

    invoke-virtual {p1, v1, v0}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 100
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->type:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_1

    const/4 v0, 0x2

    .line 101
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->type:Ljava/lang/String;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 103
    :cond_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bp:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_2

    const/4 v0, 0x3

    .line 104
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bp:Ljava/lang/String;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 106
    :cond_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bq:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_3

    const/4 v0, 0x4

    .line 107
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bq:Ljava/lang/String;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 109
    :cond_3
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->time:J

    const-wide/16 v2, 0x0

    cmp-long v0, v0, v2

    if-eqz v0, :cond_4

    const/4 v0, 0x5

    .line 110
    iget-wide v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->time:J

    invoke-virtual {p1, v0, v4, v5}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt64(IJ)V

    .line 112
    :cond_4
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->br:J

    cmp-long v0, v0, v2

    if-eqz v0, :cond_5

    const/4 v0, 0x6

    .line 113
    iget-wide v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->br:J

    invoke-virtual {p1, v0, v4, v5}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt64(IJ)V

    .line 115
    :cond_5
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bs:J

    cmp-long v0, v0, v2

    if-eqz v0, :cond_6

    const/4 v0, 0x7

    .line 116
    iget-wide v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bs:J

    invoke-virtual {p1, v0, v4, v5}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt64(IJ)V

    .line 118
    :cond_6
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bt:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_7

    const/16 v0, 0x8

    .line 119
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bt:Ljava/lang/String;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 121
    :cond_7
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bu:J

    cmp-long v0, v0, v2

    if-eqz v0, :cond_8

    const/16 v0, 0x9

    .line 122
    iget-wide v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bu:J

    invoke-virtual {p1, v0, v4, v5}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt64(IJ)V

    .line 124
    :cond_8
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->duration:J

    cmp-long v0, v0, v2

    if-eqz v0, :cond_9

    const/16 v0, 0xa

    .line 125
    iget-wide v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->duration:J

    invoke-virtual {p1, v0, v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt64(IJ)V

    .line 127
    :cond_9
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    const/4 v1, 0x0

    if-eqz v0, :cond_b

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    array-length v0, v0

    if-lez v0, :cond_b

    const/4 v0, 0x0

    .line 128
    :goto_0
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    array-length v2, v2

    if-ge v0, v2, :cond_b

    .line 129
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    aget-object v2, v2, v0

    if-eqz v2, :cond_a

    const/16 v3, 0xb

    .line 131
    invoke-virtual {p1, v3, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    :cond_a
    add-int/lit8 v0, v0, 0x1

    goto :goto_0

    .line 135
    :cond_b
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    if-eqz v0, :cond_d

    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    array-length v0, v0

    if-lez v0, :cond_d

    .line 136
    :goto_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    array-length v0, v0

    if-ge v1, v0, :cond_d

    .line 137
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    aget-object v0, v0, v1

    if-eqz v0, :cond_c

    const/16 v2, 0xc

    .line 139
    invoke-virtual {p1, v2, v0}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    :cond_c
    add-int/lit8 v1, v1, 0x1

    goto :goto_1

    .line 143
    :cond_d
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bx:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$d;

    if-eqz v0, :cond_e

    const/16 v0, 0xd

    .line 144
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bx:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$d;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    .line 146
    :cond_e
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->by:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$b;

    if-eqz v0, :cond_f

    const/16 v0, 0xe

    .line 147
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->by:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$b;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    .line 149
    :cond_f
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bz:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$c;

    if-eqz v0, :cond_10

    const/16 v0, 0xf

    .line 150
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bz:Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$c;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    .line 152
    :cond_10
    invoke-super/range {p0 .. p1}, Lcom/google/protobuf/nano/MessageNano;->writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V

    return-void
.end method
