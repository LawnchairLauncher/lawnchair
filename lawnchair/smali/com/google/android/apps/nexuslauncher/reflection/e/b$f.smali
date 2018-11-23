.class public final Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;
.super Lcom/google/protobuf/nano/MessageNano;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/android/apps/nexuslauncher/reflection/e/b;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "f"
.end annotation


# instance fields
.field public br:J

.field public bs:J

.field public bt:Ljava/lang/String;

.field public bu:J

.field public timestamp:J


# direct methods
.method public constructor <init>()V
    .locals 3

    .line 641
    invoke-direct/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;-><init>()V

    const-wide/16 v0, 0x0

    .line 642
    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->timestamp:J

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->br:J

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bs:J

    const-string v2, ""

    iput-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bt:Ljava/lang/String;

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bu:J

    const/4 v0, -0x1

    iput v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->cachedSize:I

    return-void
.end method


# virtual methods
.method protected final computeSerializedSize()I
    .locals 7

    .line 678
    invoke-super/range {p0 .. p0}, Lcom/google/protobuf/nano/MessageNano;->computeSerializedSize()I

    move-result v0

    .line 679
    iget-wide v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->timestamp:J

    const-wide/16 v3, 0x0

    cmp-long v1, v1, v3

    if-eqz v1, :cond_0

    const/4 v1, 0x1

    .line 680
    iget-wide v5, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->timestamp:J

    .line 681
    invoke-static {v1, v5, v6}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt64Size(IJ)I

    move-result v1

    add-int/2addr v0, v1

    .line 683
    :cond_0
    iget-wide v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->br:J

    cmp-long v1, v1, v3

    if-eqz v1, :cond_1

    const/4 v1, 0x2

    .line 684
    iget-wide v5, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->br:J

    .line 685
    invoke-static {v1, v5, v6}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt64Size(IJ)I

    move-result v1

    add-int/2addr v0, v1

    .line 687
    :cond_1
    iget-wide v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bs:J

    cmp-long v1, v1, v3

    if-eqz v1, :cond_2

    const/4 v1, 0x3

    .line 688
    iget-wide v5, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bs:J

    .line 689
    invoke-static {v1, v5, v6}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt64Size(IJ)I

    move-result v1

    add-int/2addr v0, v1

    .line 691
    :cond_2
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bt:Ljava/lang/String;

    const-string v2, ""

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_3

    const/4 v1, 0x4

    .line 692
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bt:Ljava/lang/String;

    .line 693
    invoke-static {v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeStringSize(ILjava/lang/String;)I

    move-result v1

    add-int/2addr v0, v1

    .line 695
    :cond_3
    iget-wide v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bu:J

    cmp-long v1, v1, v3

    if-eqz v1, :cond_4

    const/4 v1, 0x5

    .line 696
    iget-wide v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bu:J

    .line 697
    invoke-static {v1, v2, v3}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->computeInt64Size(IJ)I

    move-result v1

    add-int/2addr v0, v1

    :cond_4
    return v0
.end method

.method public final synthetic mergeFrom(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/protobuf/nano/MessageNano;
    .locals 2
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 609
    :cond_0
    :goto_0
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readTag()I

    move-result v0

    if-eqz v0, :cond_6

    const/16 v1, 0x8

    if-eq v0, v1, :cond_5

    const/16 v1, 0x10

    if-eq v0, v1, :cond_4

    const/16 v1, 0x18

    if-eq v0, v1, :cond_3

    const/16 v1, 0x22

    if-eq v0, v1, :cond_2

    const/16 v1, 0x28

    if-eq v0, v1, :cond_1

    invoke-static {p1, v0}, Lcom/google/protobuf/nano/WireFormatNano;->parseUnknownField(Lcom/google/protobuf/nano/CodedInputByteBufferNano;I)Z

    move-result v0

    if-nez v0, :cond_0

    return-object p0

    :cond_1
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt64()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bu:J

    goto :goto_0

    :cond_2
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readString()Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bt:Ljava/lang/String;

    goto :goto_0

    :cond_3
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt64()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bs:J

    goto :goto_0

    :cond_4
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt64()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->br:J

    goto :goto_0

    :cond_5
    invoke-virtual/range {p1 .. p1}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->readInt64()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->timestamp:J

    goto :goto_0

    :cond_6
    return-object p0
.end method

.method public final writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V
    .locals 6
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 658
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->timestamp:J

    const-wide/16 v2, 0x0

    cmp-long v0, v0, v2

    if-eqz v0, :cond_0

    const/4 v0, 0x1

    .line 659
    iget-wide v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->timestamp:J

    invoke-virtual {p1, v0, v4, v5}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt64(IJ)V

    .line 661
    :cond_0
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->br:J

    cmp-long v0, v0, v2

    if-eqz v0, :cond_1

    const/4 v0, 0x2

    .line 662
    iget-wide v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->br:J

    invoke-virtual {p1, v0, v4, v5}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt64(IJ)V

    .line 664
    :cond_1
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bs:J

    cmp-long v0, v0, v2

    if-eqz v0, :cond_2

    const/4 v0, 0x3

    .line 665
    iget-wide v4, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bs:J

    invoke-virtual {p1, v0, v4, v5}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt64(IJ)V

    .line 667
    :cond_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bt:Ljava/lang/String;

    const-string v1, ""

    invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_3

    const/4 v0, 0x4

    .line 668
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bt:Ljava/lang/String;

    invoke-virtual {p1, v0, v1}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeString(ILjava/lang/String;)V

    .line 670
    :cond_3
    iget-wide v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bu:J

    cmp-long v0, v0, v2

    if-eqz v0, :cond_4

    const/4 v0, 0x5

    .line 671
    iget-wide v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->bu:J

    invoke-virtual {p1, v0, v1, v2}, Lcom/google/protobuf/nano/CodedOutputByteBufferNano;->writeInt64(IJ)V

    .line 673
    :cond_4
    invoke-super/range {p0 .. p1}, Lcom/google/protobuf/nano/MessageNano;->writeTo(Lcom/google/protobuf/nano/CodedOutputByteBufferNano;)V

    return-void
.end method
