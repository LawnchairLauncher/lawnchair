.class public Lcom/google/research/reflection/layers/g;
.super Lcom/google/research/reflection/layers/d;
.source "SourceFile"


# instance fields
.field private fc:I


# direct methods
.method public constructor <init>()V
    .locals 1

    .line 28
    invoke-direct/range {p0 .. p0}, Lcom/google/research/reflection/layers/d;-><init>()V

    const/4 v0, 0x0

    .line 18
    iput v0, p0, Lcom/google/research/reflection/layers/g;->fc:I

    return-void
.end method

.method public constructor <init>(IIIIIIIIZ)V
    .locals 13

    move-object v12, p0

    const/4 v1, 0x0

    const/4 v10, 0x0

    const/4 v11, 0x0

    move-object v0, p0

    move v2, p2

    move/from16 v3, p3

    move/from16 v4, p4

    move/from16 v5, p5

    move/from16 v6, p6

    move/from16 v7, p7

    move/from16 v8, p8

    move/from16 v9, p9

    .line 23
    invoke-direct/range {v0 .. v11}, Lcom/google/research/reflection/layers/d;-><init>(ZIIIIIIIZZF)V

    const/4 v0, 0x0

    .line 18
    iput v0, v12, Lcom/google/research/reflection/layers/g;->fc:I

    move v0, p1

    .line 25
    iput v0, v12, Lcom/google/research/reflection/layers/g;->fc:I

    return-void
.end method

.method static synthetic a(Lcom/google/research/reflection/layers/g;)I
    .locals 0

    .line 12
    iget p0, p0, Lcom/google/research/reflection/layers/g;->fc:I

    return p0
.end method


# virtual methods
.method public final synthetic Z()Lcom/google/research/reflection/layers/c;
    .locals 1

    .line 12
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/layers/g;->aj()Lcom/google/research/reflection/layers/g;

    move-result-object v0

    return-object v0
.end method

.method final a(ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
    .locals 9
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation

    .line 38
    invoke-static {}, Lcom/google/research/reflection/layers/i;->ak()Lcom/google/research/reflection/layers/i;

    move-result-object v0

    iget-object v1, p2, Lcom/google/research/reflection/layers/e;->eR:[D

    array-length v1, v1

    new-instance v8, Lcom/google/research/reflection/layers/g$1;

    move-object v2, v8

    move-object v3, p0

    move v4, p1

    move-object v5, p2

    move-object v6, p3

    move-object v7, p4

    invoke-direct/range {v2 .. v7}, Lcom/google/research/reflection/layers/g$1;-><init>(Lcom/google/research/reflection/layers/g;ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V

    invoke-virtual {v0, v1, v8}, Lcom/google/research/reflection/layers/i;->a(ILcom/google/research/reflection/layers/h;)V

    return-void
.end method

.method public final synthetic ae()Lcom/google/research/reflection/layers/d;
    .locals 1

    .line 12
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/layers/g;->aj()Lcom/google/research/reflection/layers/g;

    move-result-object v0

    return-object v0
.end method

.method public final aj()Lcom/google/research/reflection/layers/g;
    .locals 2

    .line 64
    new-instance v0, Lcom/google/research/reflection/layers/g;

    invoke-direct {v0}, Lcom/google/research/reflection/layers/g;-><init>()V

    .line 65
    invoke-super {p0, v0}, Lcom/google/research/reflection/layers/d;->a(Lcom/google/research/reflection/layers/d;)V

    .line 66
    iget v1, p0, Lcom/google/research/reflection/layers/g;->fc:I

    iput v1, v0, Lcom/google/research/reflection/layers/g;->fc:I

    return-object v0
.end method

.method public final b(Ljava/io/DataInputStream;)V
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 80
    invoke-super/range {p0 .. p1}, Lcom/google/research/reflection/layers/d;->b(Ljava/io/DataInputStream;)V

    .line 81
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readInt()I

    move-result v0

    iput v0, p0, Lcom/google/research/reflection/layers/g;->fc:I

    .line 82
    invoke-virtual/range {p0 .. p1}, Lcom/google/research/reflection/layers/g;->c(Ljava/io/DataInputStream;)V

    return-void
.end method

.method public final b(Ljava/io/DataOutputStream;)V
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 73
    invoke-super/range {p0 .. p1}, Lcom/google/research/reflection/layers/d;->b(Ljava/io/DataOutputStream;)V

    .line 74
    iget v0, p0, Lcom/google/research/reflection/layers/g;->fc:I

    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeInt(I)V

    .line 75
    invoke-virtual/range {p0 .. p1}, Lcom/google/research/reflection/layers/g;->c(Ljava/io/DataOutputStream;)V

    return-void
.end method

.method public synthetic clone()Ljava/lang/Object;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/CloneNotSupportedException;
        }
    .end annotation

    .line 12
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/layers/g;->aj()Lcom/google/research/reflection/layers/g;

    move-result-object v0

    return-object v0
.end method

.method public final getName()Ljava/lang/String;
    .locals 1

    const-string v0, "OutputLayer"

    return-object v0
.end method
