.class public final Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;
.super Lcom/google/protobuf/nano/MessageNano;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/e/b;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "c"
.end annotation


# instance fields
.field public cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

.field public cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

.field public co:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 296
    invoke-direct/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;-><init>()V

    const/4 v0, 0x0

    .line 297
    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->co:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    const/4 v0, -0x1

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cachedSize:I

    return-void
.end method


# virtual methods
.method protected final computeSerializedSize()I
    .locals 3

    .line 325
    invoke-super/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;->computeSerializedSize()I

    move-result v0

    .line 326
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    if-eqz v1, :cond_0

    const/4 v1, 0x1

    .line 327
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    .line 328
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v1

    add-int/2addr v0, v1

    .line 330
    :cond_0
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    if-eqz v1, :cond_1

    const/4 v1, 0x2

    .line 331
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    .line 332
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v1

    add-int/2addr v0, v1

    .line 334
    :cond_1
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->co:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    if-eqz v1, :cond_2

    const/4 v1, 0x3

    .line 335
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->co:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    .line 336
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeMessageSize(ILcom/google/protobuf/nano/MessageNano;)I

    move-result v1

    add-int/2addr v0, v1

    :cond_2
    return v0
.end method

.method public final synthetic mergeFrom(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/protobuf/nano/MessageNano;
    .locals 2
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 270
    :cond_0
    :goto_0
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    move-result v0

    if-eqz v0, :cond_7

    const/16 v1, 0xa

    if-eq v0, v1, :cond_5

    const/16 v1, 0x12

    if-eq v0, v1, :cond_3

    const/16 v1, 0x1a

    if-eq v0, v1, :cond_1

    invoke-static {p1, v0}, Lcom/google/protobuf/nano/WireFormatNano;->parseUnknownField(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)Z

    move-result v0

    if-nez v0, :cond_0

    return-object p0

    :cond_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->co:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    if-nez v0, :cond_2

    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->co:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    :cond_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->co:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    goto :goto_1

    :cond_3
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    if-nez v0, :cond_4

    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    :cond_4
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    goto :goto_1

    :cond_5
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    if-nez v0, :cond_6

    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    :cond_6
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    :goto_1
    invoke-virtual {p1, v0}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readMessage(Lcom/google/protobuf/nano/MessageNano;)V

    goto :goto_0

    :cond_7
    return-object p0
.end method

.method public final writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V
    .locals 2
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 311
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    if-eqz v0, :cond_0

    const/4 v0, 0x1

    .line 312
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    .line 314
    :cond_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    if-eqz v0, :cond_1

    const/4 v0, 0x2

    .line 315
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    .line 317
    :cond_1
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->co:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    if-eqz v0, :cond_2

    const/4 v0, 0x3

    .line 318
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->co:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeMessage(ILcom/google/protobuf/nano/MessageNano;)V

    .line 320
    :cond_2
    invoke-super/range {p0 .. p1}, Lcom/google/protobuf/nano/MessageNano;->writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V

    return-void
.end method
