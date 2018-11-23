.class public abstract Lcom/google/research/reflection/layers/c;
.super Ljava/lang/Object;
.source "SourceFile"


# static fields
.field static eo:D = 0.1


# instance fields
.field dF:I

.field dH:I

.field ep:Lcom/google/research/reflection/a/a;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lcom/google/research/reflection/a/a<",
            "Lcom/google/research/reflection/layers/e;",
            ">;"
        }
    .end annotation
.end field

.field public eq:Lcom/google/research/reflection/a/a;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lcom/google/research/reflection/a/a<",
            "Lcom/google/research/reflection/layers/e;",
            ">;"
        }
    .end annotation
.end field

.field public er:Lcom/google/research/reflection/a/a;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lcom/google/research/reflection/a/a<",
            "[",
            "Ljava/util/ArrayList<",
            "Lcom/google/research/reflection/a/d;",
            ">;>;"
        }
    .end annotation
.end field

.field es:Lcom/google/research/reflection/layers/e;

.field public et:Lcom/google/research/reflection/layers/e;

.field public eu:Lcom/google/research/reflection/layers/e;

.field public ev:Z

.field public ew:I

.field ex:Z


# direct methods
.method static constructor <clinit>()V
    .locals 0

    return-void
.end method

.method constructor <init>()V
    .locals 0

    .line 68
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method constructor <init>(ZIIII)V
    .locals 2

    .line 50
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 51
    new-instance v0, Lcom/google/research/reflection/a/a;

    const/4 v1, 0x1

    invoke-direct {v0, p2, v1}, Lcom/google/research/reflection/a/a;-><init>(IZ)V

    iput-object v0, p0, Lcom/google/research/reflection/layers/c;->ep:Lcom/google/research/reflection/a/a;

    .line 52
    new-instance v0, Lcom/google/research/reflection/a/a;

    invoke-direct {v0, p2, v1}, Lcom/google/research/reflection/a/a;-><init>(IZ)V

    iput-object v0, p0, Lcom/google/research/reflection/layers/c;->eq:Lcom/google/research/reflection/a/a;

    .line 53
    new-instance v0, Lcom/google/research/reflection/a/a;

    invoke-direct {v0, p2, v1}, Lcom/google/research/reflection/a/a;-><init>(IZ)V

    iput-object v0, p0, Lcom/google/research/reflection/layers/c;->er:Lcom/google/research/reflection/a/a;

    .line 55
    iput p3, p0, Lcom/google/research/reflection/layers/c;->dF:I

    .line 56
    iput p4, p0, Lcom/google/research/reflection/layers/c;->dH:I

    .line 57
    iput p5, p0, Lcom/google/research/reflection/layers/c;->ew:I

    .line 58
    iput-boolean p1, p0, Lcom/google/research/reflection/layers/c;->ex:Z

    .line 60
    new-instance p1, Lcom/google/research/reflection/layers/e;

    invoke-direct {p1, p3, p5}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object p1, p0, Lcom/google/research/reflection/layers/c;->es:Lcom/google/research/reflection/layers/e;

    .line 61
    new-instance p1, Lcom/google/research/reflection/layers/e;

    invoke-direct {p1, p3, p4}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object p1, p0, Lcom/google/research/reflection/layers/c;->et:Lcom/google/research/reflection/layers/e;

    .line 62
    new-instance p1, Lcom/google/research/reflection/layers/e;

    invoke-direct {p1, p3, p5}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object p1, p0, Lcom/google/research/reflection/layers/c;->eu:Lcom/google/research/reflection/layers/e;

    .line 63
    iget-object p1, p0, Lcom/google/research/reflection/layers/c;->et:Lcom/google/research/reflection/layers/e;

    iget-object p1, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    const-wide/16 p2, 0x0

    invoke-static {p1, p2, p3}, Ljava/util/Arrays;->fill([DD)V

    .line 64
    iget-object p1, p0, Lcom/google/research/reflection/layers/c;->eu:Lcom/google/research/reflection/layers/e;

    iget-object p1, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {p1, p2, p3}, Ljava/util/Arrays;->fill([DD)V

    .line 65
    iget-object p1, p0, Lcom/google/research/reflection/layers/c;->es:Lcom/google/research/reflection/layers/e;

    iget-object p1, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {p1, p2, p3}, Ljava/util/Arrays;->fill([DD)V

    return-void
.end method

.method public static ad()D
    .locals 2

    .line 231
    sget-wide v0, Lcom/google/research/reflection/layers/c;->eo:D

    return-wide v0
.end method


# virtual methods
.method public abstract Z()Lcom/google/research/reflection/layers/c;
.end method

.method public abstract a(ZLcom/google/research/reflection/layers/f;[Ljava/util/ArrayList;Lcom/google/research/reflection/layers/e;)Lcom/google/research/reflection/layers/e;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(Z",
            "Lcom/google/research/reflection/layers/f;",
            "[",
            "Ljava/util/ArrayList<",
            "Lcom/google/research/reflection/a/d;",
            ">;",
            "Lcom/google/research/reflection/layers/e;",
            ")",
            "Lcom/google/research/reflection/layers/e;"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation
.end method

.method public final a(Lcom/google/research/reflection/layers/c;)V
    .locals 3

    .line 109
    iget-object v0, p0, Lcom/google/research/reflection/layers/c;->ep:Lcom/google/research/reflection/a/a;

    iget-object v0, v0, Lcom/google/research/reflection/a/a;->dl:[Ljava/lang/Object;

    array-length v0, v0

    .line 110
    new-instance v1, Lcom/google/research/reflection/a/a;

    const/4 v2, 0x1

    invoke-direct {v1, v0, v2}, Lcom/google/research/reflection/a/a;-><init>(IZ)V

    iput-object v1, p1, Lcom/google/research/reflection/layers/c;->ep:Lcom/google/research/reflection/a/a;

    .line 111
    new-instance v1, Lcom/google/research/reflection/a/a;

    invoke-direct {v1, v0, v2}, Lcom/google/research/reflection/a/a;-><init>(IZ)V

    iput-object v1, p1, Lcom/google/research/reflection/layers/c;->eq:Lcom/google/research/reflection/a/a;

    .line 112
    new-instance v1, Lcom/google/research/reflection/a/a;

    invoke-direct {v1, v0, v2}, Lcom/google/research/reflection/a/a;-><init>(IZ)V

    iput-object v1, p1, Lcom/google/research/reflection/layers/c;->er:Lcom/google/research/reflection/a/a;

    .line 113
    iget v0, p0, Lcom/google/research/reflection/layers/c;->dH:I

    iput v0, p1, Lcom/google/research/reflection/layers/c;->dH:I

    .line 114
    iget v0, p0, Lcom/google/research/reflection/layers/c;->ew:I

    iput v0, p1, Lcom/google/research/reflection/layers/c;->ew:I

    .line 115
    iget v0, p0, Lcom/google/research/reflection/layers/c;->dF:I

    iput v0, p1, Lcom/google/research/reflection/layers/c;->dF:I

    .line 116
    iget-boolean v0, p0, Lcom/google/research/reflection/layers/c;->ex:Z

    iput-boolean v0, p1, Lcom/google/research/reflection/layers/c;->ex:Z

    .line 118
    new-instance v0, Lcom/google/research/reflection/layers/e;

    iget v1, p0, Lcom/google/research/reflection/layers/c;->dF:I

    iget v2, p0, Lcom/google/research/reflection/layers/c;->ew:I

    invoke-direct {v0, v1, v2}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object v0, p1, Lcom/google/research/reflection/layers/c;->es:Lcom/google/research/reflection/layers/e;

    .line 119
    new-instance v0, Lcom/google/research/reflection/layers/e;

    iget v1, p0, Lcom/google/research/reflection/layers/c;->dF:I

    iget v2, p0, Lcom/google/research/reflection/layers/c;->dH:I

    invoke-direct {v0, v1, v2}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object v0, p1, Lcom/google/research/reflection/layers/c;->et:Lcom/google/research/reflection/layers/e;

    .line 120
    new-instance v0, Lcom/google/research/reflection/layers/e;

    iget v1, p0, Lcom/google/research/reflection/layers/c;->dF:I

    iget v2, p0, Lcom/google/research/reflection/layers/c;->ew:I

    invoke-direct {v0, v1, v2}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object v0, p1, Lcom/google/research/reflection/layers/c;->eu:Lcom/google/research/reflection/layers/e;

    .line 121
    iget-object v0, p1, Lcom/google/research/reflection/layers/c;->et:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    const-wide/16 v1, 0x0

    invoke-static {v0, v1, v2}, Ljava/util/Arrays;->fill([DD)V

    .line 122
    iget-object v0, p1, Lcom/google/research/reflection/layers/c;->eu:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {v0, v1, v2}, Ljava/util/Arrays;->fill([DD)V

    .line 123
    iget-object p1, p1, Lcom/google/research/reflection/layers/c;->es:Lcom/google/research/reflection/layers/e;

    iget-object p1, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {p1, v1, v2}, Ljava/util/Arrays;->fill([DD)V

    return-void
.end method

.method public abstract a(Lcom/google/research/reflection/layers/f;ILcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;Lcom/google/research/reflection/layers/e;)V
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation
.end method

.method aa()V
    .locals 3

    .line 173
    iget-object v0, p0, Lcom/google/research/reflection/layers/c;->et:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    const-wide/16 v1, 0x0

    invoke-static {v0, v1, v2}, Ljava/util/Arrays;->fill([DD)V

    .line 174
    iget-object v0, p0, Lcom/google/research/reflection/layers/c;->eu:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {v0, v1, v2}, Ljava/util/Arrays;->fill([DD)V

    .line 175
    iget-object v0, p0, Lcom/google/research/reflection/layers/c;->es:Lcom/google/research/reflection/layers/e;

    iget-object v0, v0, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {v0, v1, v2}, Ljava/util/Arrays;->fill([DD)V

    return-void
.end method

.method public ab()V
    .locals 1

    .line 167
    iget-object v0, p0, Lcom/google/research/reflection/layers/c;->ep:Lcom/google/research/reflection/a/a;

    invoke-virtual {v0}, Lcom/google/research/reflection/a/a;->clear()V

    .line 168
    iget-object v0, p0, Lcom/google/research/reflection/layers/c;->eq:Lcom/google/research/reflection/a/a;

    invoke-virtual {v0}, Lcom/google/research/reflection/a/a;->clear()V

    .line 169
    iget-object v0, p0, Lcom/google/research/reflection/layers/c;->er:Lcom/google/research/reflection/a/a;

    invoke-virtual {v0}, Lcom/google/research/reflection/a/a;->clear()V

    return-void
.end method

.method public final ac()I
    .locals 1

    .line 155
    iget-object v0, p0, Lcom/google/research/reflection/layers/c;->ep:Lcom/google/research/reflection/a/a;

    iget-object v0, v0, Lcom/google/research/reflection/a/a;->dl:[Ljava/lang/Object;

    array-length v0, v0

    return v0
.end method

.method public b(Ljava/io/DataInputStream;)V
    .locals 2
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 80
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readInt()I

    move-result v0

    iput v0, p0, Lcom/google/research/reflection/layers/c;->dF:I

    .line 81
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readInt()I

    move-result v0

    iput v0, p0, Lcom/google/research/reflection/layers/c;->ew:I

    .line 82
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readInt()I

    move-result v0

    iput v0, p0, Lcom/google/research/reflection/layers/c;->dH:I

    .line 83
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readInt()I

    move-result v0

    .line 84
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readBoolean()Z

    move-result p1

    iput-boolean p1, p0, Lcom/google/research/reflection/layers/c;->ex:Z

    .line 86
    new-instance p1, Lcom/google/research/reflection/a/a;

    const/4 v1, 0x1

    invoke-direct {p1, v0, v1}, Lcom/google/research/reflection/a/a;-><init>(IZ)V

    iput-object p1, p0, Lcom/google/research/reflection/layers/c;->ep:Lcom/google/research/reflection/a/a;

    .line 87
    new-instance p1, Lcom/google/research/reflection/a/a;

    invoke-direct {p1, v0, v1}, Lcom/google/research/reflection/a/a;-><init>(IZ)V

    iput-object p1, p0, Lcom/google/research/reflection/layers/c;->eq:Lcom/google/research/reflection/a/a;

    .line 88
    new-instance p1, Lcom/google/research/reflection/a/a;

    invoke-direct {p1, v0, v1}, Lcom/google/research/reflection/a/a;-><init>(IZ)V

    iput-object p1, p0, Lcom/google/research/reflection/layers/c;->er:Lcom/google/research/reflection/a/a;

    .line 90
    new-instance p1, Lcom/google/research/reflection/layers/e;

    iget v0, p0, Lcom/google/research/reflection/layers/c;->dF:I

    iget v1, p0, Lcom/google/research/reflection/layers/c;->ew:I

    invoke-direct {p1, v0, v1}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object p1, p0, Lcom/google/research/reflection/layers/c;->es:Lcom/google/research/reflection/layers/e;

    .line 91
    new-instance p1, Lcom/google/research/reflection/layers/e;

    iget v0, p0, Lcom/google/research/reflection/layers/c;->dF:I

    iget v1, p0, Lcom/google/research/reflection/layers/c;->dH:I

    invoke-direct {p1, v0, v1}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object p1, p0, Lcom/google/research/reflection/layers/c;->et:Lcom/google/research/reflection/layers/e;

    .line 92
    new-instance p1, Lcom/google/research/reflection/layers/e;

    iget v0, p0, Lcom/google/research/reflection/layers/c;->dF:I

    iget v1, p0, Lcom/google/research/reflection/layers/c;->ew:I

    invoke-direct {p1, v0, v1}, Lcom/google/research/reflection/layers/e;-><init>(II)V

    iput-object p1, p0, Lcom/google/research/reflection/layers/c;->eu:Lcom/google/research/reflection/layers/e;

    .line 93
    iget-object p1, p0, Lcom/google/research/reflection/layers/c;->et:Lcom/google/research/reflection/layers/e;

    iget-object p1, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    const-wide/16 v0, 0x0

    invoke-static {p1, v0, v1}, Ljava/util/Arrays;->fill([DD)V

    .line 94
    iget-object p1, p0, Lcom/google/research/reflection/layers/c;->eu:Lcom/google/research/reflection/layers/e;

    iget-object p1, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {p1, v0, v1}, Ljava/util/Arrays;->fill([DD)V

    .line 95
    iget-object p1, p0, Lcom/google/research/reflection/layers/c;->es:Lcom/google/research/reflection/layers/e;

    iget-object p1, p1, Lcom/google/research/reflection/layers/e;->eR:[D

    invoke-static {p1, v0, v1}, Ljava/util/Arrays;->fill([DD)V

    return-void
.end method

.method public b(Ljava/io/DataOutputStream;)V
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 72
    iget v0, p0, Lcom/google/research/reflection/layers/c;->dF:I

    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeInt(I)V

    .line 73
    iget v0, p0, Lcom/google/research/reflection/layers/c;->ew:I

    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeInt(I)V

    .line 74
    iget v0, p0, Lcom/google/research/reflection/layers/c;->dH:I

    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeInt(I)V

    .line 75
    iget-object v0, p0, Lcom/google/research/reflection/layers/c;->ep:Lcom/google/research/reflection/a/a;

    iget-object v0, v0, Lcom/google/research/reflection/a/a;->dl:[Ljava/lang/Object;

    array-length v0, v0

    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeInt(I)V

    .line 76
    iget-boolean v0, p0, Lcom/google/research/reflection/layers/c;->ex:Z

    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeBoolean(Z)V

    return-void
.end method

.method final c(Ljava/io/DataInputStream;)V
    .locals 4
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 212
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readUTF()Ljava/lang/String;

    move-result-object p1

    .line 213
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/layers/c;->getName()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_0

    return-void

    .line 214
    :cond_0
    new-instance v0, Ljava/lang/RuntimeException;

    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/layers/c;->getName()Ljava/lang/String;

    move-result-object v1

    invoke-static {v1}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v2}, Ljava/lang/String;->length()I

    move-result v2

    add-int/lit8 v2, v2, 0x13

    invoke-static {p1}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/String;->length()I

    move-result v3

    add-int/2addr v2, v3

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3, v2}, Ljava/lang/StringBuilder;-><init>(I)V

    const-string v2, "Expected "

    invoke-virtual {v3, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v1, " acquired "

    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-direct {v0, p1}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    throw v0
.end method

.method final c(Ljava/io/DataOutputStream;)V
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 208
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/layers/c;->getName()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeUTF(Ljava/lang/String;)V

    return-void
.end method

.method public synthetic clone()Ljava/lang/Object;
    .locals 1
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/lang/CloneNotSupportedException;
        }
    .end annotation

    .line 14
    invoke-virtual/range {p0 .. p0}, Lcom/google/research/reflection/layers/c;->Z()Lcom/google/research/reflection/layers/c;

    move-result-object v0

    return-object v0
.end method

.method public abstract getName()Ljava/lang/String;
.end method

.method public abstract update()V
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Lcom/google/research/reflection/layers/InvalidValueException;
        }
    .end annotation
.end method
