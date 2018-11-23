.class public final Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;
.super Lcom/google/protobuf/nano/MessageNano;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/e/a;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "c"
.end annotation


# static fields
.field private static volatile bY:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;


# instance fields
.field public bZ:Ljava/lang/String;

.field public ca:F


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 310
    invoke-direct/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;-><init>()V

    const-string v0, ""

    .line 311
    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->bZ:Ljava/lang/String;

    const/4 v0, 0x0

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->ca:F

    const/4 v0, -0x1

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->cachedSize:I

    return-void
.end method

.method public static v()[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;
    .locals 2

    .line 293
    sget-object v0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->bY:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-nez v0, :cond_1

    .line 294
    sget-object v0, Lcom/google/protobuf/nano/InternalNano;->LAZY_INIT_LOCK:Ljava/lang/Object;

    monitor-enter v0

    .line 296
    :try_start_0
    sget-object v1, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->bY:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    if-nez v1, :cond_0

    const/4 v1, 0x0

    .line 297
    new-array v1, v1, [Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    sput-object v1, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->bY:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    .line 299
    :cond_0
    monitor-exit v0

    goto :goto_0

    :catchall_0
    move-exception v1

    monitor-exit v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    throw v1

    .line 301
    :cond_1
    :goto_0
    sget-object v0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->bY:[Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;

    return-object v0
.end method


# virtual methods
.method protected final computeSerializedSize()I
    .locals 3

    .line 336
    invoke-super/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;->computeSerializedSize()I

    move-result v0

    .line 337
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->bZ:Ljava/lang/String;

    const-string v2, ""

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_0

    const/4 v1, 0x1

    .line 338
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->bZ:Ljava/lang/String;

    .line 339
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    .line 341
    :cond_0
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->ca:F

    invoke-static {v1}, Ljava/lang/Float;->floatToIntBits(F)I

    move-result v1

    const/4 v2, 0x0

    .line 342
    invoke-static {v2}, Ljava/lang/Float;->floatToIntBits(F)I

    move-result v2

    if-eq v1, v2, :cond_1

    const/4 v1, 0x2

    .line 343
    iget v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->ca:F

    .line 344
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeFloatSize(IF)I

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

    .line 287
    :cond_0
    :goto_0
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    move-result v0

    if-eqz v0, :cond_3

    const/16 v1, 0xa

    if-eq v0, v1, :cond_2

    const/16 v1, 0x15

    if-eq v0, v1, :cond_1

    invoke-static {p1, v0}, Lcom/google/protobuf/nano/WireFormatNano;->parseUnknownField(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)Z

    move-result v0

    if-nez v0, :cond_0

    return-object p0

    :cond_1
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readFloat()F

    move-result v0

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->ca:F

    goto :goto_0

    :cond_2
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->bZ:Ljava/lang/String;

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

    .line 324
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->bZ:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_0

    const/4 v0, 0x1

    .line 325
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->bZ:Ljava/lang/String;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 327
    :cond_0
    iget v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->ca:F

    invoke-static {v0}, Ljava/lang/Float;->floatToIntBits(F)I

    move-result v0

    const/4 v1, 0x0

    .line 328
    invoke-static {v1}, Ljava/lang/Float;->floatToIntBits(F)I

    move-result v1

    if-eq v0, v1, :cond_1

    const/4 v0, 0x2

    .line 329
    iget v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/a$c;->ca:F

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeFloat(IF)V

    .line 331
    :cond_1
    invoke-super/range {p0 .. p1}, Lcom/google/protobuf/nano/MessageNano;->writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V

    return-void
.end method
