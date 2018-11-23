.class public Lcom/google/research/reflection/predictor/b;
.super Ljava/lang/Object;
.source "SourceFile"


# static fields
.field private static final fv:J


# instance fields
.field private fw:J

.field private fx:I

.field protected fy:Lcom/google/research/reflection/a/a;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lcom/google/research/reflection/a/a<",
            "Lcom/google/research/reflection/signal/ReflectionEvent;",
            ">;"
        }
    .end annotation
.end field

.field protected fz:Z


# direct methods
.method static constructor <clinit>()V
    .locals 3

    .line 19
    sget-object v0, Ljava/util/concurrent/TimeUnit;->DAYS:Ljava/util/concurrent/TimeUnit;

    const-wide/16 v1, 0x1

    invoke-virtual {v0, v1, v2}, Ljava/util/concurrent/TimeUnit;->toMillis(J)J

    move-result-wide v0

    sput-wide v0, Lcom/google/research/reflection/predictor/b;->fv:J

    return-void
.end method

.method public constructor <init>()V
    .locals 3

    .line 29
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 21
    sget-wide v0, Lcom/google/research/reflection/predictor/b;->fv:J

    iput-wide v0, p0, Lcom/google/research/reflection/predictor/b;->fw:J

    const/16 v0, 0x3e8

    .line 23
    iput v0, p0, Lcom/google/research/reflection/predictor/b;->fx:I

    .line 30
    new-instance v0, Lcom/google/research/reflection/a/a;

    iget v1, p0, Lcom/google/research/reflection/predictor/b;->fx:I

    const/4 v2, 0x0

    invoke-direct {v0, v1, v2}, Lcom/google/research/reflection/a/a;-><init>(IZ)V

    iput-object v0, p0, Lcom/google/research/reflection/predictor/b;->fy:Lcom/google/research/reflection/a/a;

    return-void
.end method

.method public static a(Ljava/lang/String;Lcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/b;
    .locals 1
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "<T::",
            "Lcom/google/research/reflection/signal/ReflectionEvent;",
            ">(",
            "Ljava/lang/String;",
            "TT;)",
            "Lcom/google/research/reflection/predictor/b;"
        }
    .end annotation

    .line 124
    new-instance v0, Lcom/google/research/reflection/predictor/b;

    invoke-direct {v0}, Lcom/google/research/reflection/predictor/b;-><init>()V

    if-eqz p0, :cond_0

    .line 126
    sget-object v0, Ljava/nio/charset/StandardCharsets;->ISO_8859_1:Ljava/nio/charset/Charset;

    invoke-virtual {p0, v0}, Ljava/lang/String;->getBytes(Ljava/nio/charset/Charset;)[B

    move-result-object p0

    .line 127
    invoke-static {p0, p1}, Lcom/google/research/reflection/predictor/b;->a([BLcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/b;

    move-result-object v0

    :cond_0
    return-object v0
.end method

.method public static a([BLcom/google/research/reflection/signal/ReflectionEvent;)Lcom/google/research/reflection/predictor/b;
    .locals 3
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "<T::",
            "Lcom/google/research/reflection/signal/ReflectionEvent;",
            ">([BTT;)",
            "Lcom/google/research/reflection/predictor/b;"
        }
    .end annotation

    .line 98
    new-instance v0, Lcom/google/research/reflection/predictor/b;

    invoke-direct {v0}, Lcom/google/research/reflection/predictor/b;-><init>()V

    .line 100
    :try_start_0
    new-instance v1, Ljava/io/DataInputStream;

    new-instance v2, Ljava/io/ByteArrayInputStream;

    invoke-direct {v2, p0}, Ljava/io/ByteArrayInputStream;-><init>([B)V

    invoke-direct {v1, v2}, Ljava/io/DataInputStream;-><init>(Ljava/io/InputStream;)V

    .line 101
    invoke-virtual {v0, v1, p1}, Lcom/google/research/reflection/predictor/b;->a(Ljava/io/DataInputStream;Lcom/google/research/reflection/signal/ReflectionEvent;)V

    .line 102
    invoke-virtual {v1}, Ljava/io/DataInputStream;->close()V
    :try_end_0
    .catch Ljava/io/IOException; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception p0

    .line 103
    invoke-virtual {p0}, Ljava/io/IOException;->printStackTrace()V

    :goto_0
    return-object v0
.end method

.method public static a(Lcom/google/research/reflection/predictor/b;)[B
    .locals 2

    .line 111
    :try_start_0
    new-instance v0, Ljava/io/ByteArrayOutputStream;

    invoke-direct {v0}, Ljava/io/ByteArrayOutputStream;-><init>()V

    .line 112
    new-instance v1, Ljava/io/DataOutputStream;

    invoke-direct {v1, v0}, Ljava/io/DataOutputStream;-><init>(Ljava/io/OutputStream;)V

    .line 113
    invoke-virtual {p0, v1}, Lcom/google/research/reflection/predictor/b;->d(Ljava/io/DataOutputStream;)V

    .line 114
    invoke-virtual {v1}, Ljava/io/DataOutputStream;->close()V

    .line 115
    invoke-virtual {v0}, Ljava/io/ByteArrayOutputStream;->toByteArray()[B

    move-result-object p0
    :try_end_0
    .catch Ljava/io/IOException; {:try_start_0 .. :try_end_0} :catch_0

    return-object p0

    :catch_0
    move-exception p0

    .line 116
    invoke-virtual {p0}, Ljava/io/IOException;->printStackTrace()V

    const/4 p0, 0x0

    return-object p0
.end method

.method public static b(Lcom/google/research/reflection/predictor/b;)Ljava/lang/String;
    .locals 2

    if-eqz p0, :cond_0

    .line 134
    invoke-static/range {p0 .. p0}, Lcom/google/research/reflection/predictor/b;->a(Lcom/google/research/reflection/predictor/b;)[B

    move-result-object p0

    .line 135
    new-instance v0, Ljava/lang/String;

    sget-object v1, Ljava/nio/charset/StandardCharsets;->ISO_8859_1:Ljava/nio/charset/Charset;

    invoke-direct {v0, p0, v1}, Ljava/lang/String;-><init>([BLjava/nio/charset/Charset;)V

    return-object v0

    :cond_0
    const/4 p0, 0x0

    return-object p0
.end method


# virtual methods
.method public final a(Ljava/io/DataInputStream;Lcom/google/research/reflection/signal/ReflectionEvent;)V
    .locals 6
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "<T::",
            "Lcom/google/research/reflection/signal/ReflectionEvent;",
            ">(",
            "Ljava/io/DataInputStream;",
            "TT;)V"
        }
    .end annotation

    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 82
    iget-object v0, p0, Lcom/google/research/reflection/predictor/b;->fy:Lcom/google/research/reflection/a/a;

    invoke-virtual {v0}, Lcom/google/research/reflection/a/a;->clear()V

    .line 83
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readInt()I

    move-result v0

    const/4 v1, 0x0

    const/4 v2, 0x0

    move-object v3, v2

    const/4 v2, 0x0

    :goto_0
    if-ge v2, v0, :cond_2

    .line 86
    invoke-virtual/range {p1 .. p1}, Ljava/io/DataInputStream;->readInt()I

    move-result v4

    if-eqz v3, :cond_0

    .line 87
    array-length v5, v3

    if-ge v5, v4, :cond_1

    .line 88
    :cond_0
    new-array v3, v4, [B

    .line 90
    :cond_1
    invoke-virtual {p1, v3, v1, v4}, Ljava/io/DataInputStream;->read([BII)I

    .line 91
    invoke-interface {p2, v3, v4}, Lcom/google/research/reflection/signal/ReflectionEvent;->a([BI)Lcom/google/research/reflection/signal/ReflectionEvent;

    move-result-object v4

    .line 92
    invoke-virtual {p0, v4}, Lcom/google/research/reflection/predictor/b;->c(Lcom/google/research/reflection/signal/ReflectionEvent;)V

    add-int/lit8 v2, v2, 0x1

    goto :goto_0

    :cond_2
    return-void
.end method

.method public final ar()Lcom/google/research/reflection/a/a;
    .locals 1
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Lcom/google/research/reflection/a/a<",
            "Lcom/google/research/reflection/signal/ReflectionEvent;",
            ">;"
        }
    .end annotation

    .line 53
    iget-object v0, p0, Lcom/google/research/reflection/predictor/b;->fy:Lcom/google/research/reflection/a/a;

    return-object v0
.end method

.method public final c(Lcom/google/research/reflection/signal/ReflectionEvent;)V
    .locals 6

    .line 40
    :cond_0
    :goto_0
    iget-object v0, p0, Lcom/google/research/reflection/predictor/b;->fy:Lcom/google/research/reflection/a/a;

    iget v0, v0, Lcom/google/research/reflection/a/a;->dk:I

    const/4 v1, 0x1

    if-lez v0, :cond_3

    .line 41
    iget-object v0, p0, Lcom/google/research/reflection/predictor/b;->fy:Lcom/google/research/reflection/a/a;

    const/4 v2, 0x0

    invoke-virtual {v0, v2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/google/research/reflection/signal/ReflectionEvent;

    invoke-static {v0, p1}, Lcom/google/research/reflection/a/e;->a(Lcom/google/research/reflection/signal/ReflectionEvent;Lcom/google/research/reflection/signal/ReflectionEvent;)J

    move-result-wide v2

    .line 42
    iget-wide v4, p0, Lcom/google/research/reflection/predictor/b;->fw:J

    cmp-long v0, v2, v4

    if-lez v0, :cond_3

    .line 43
    iget-object v0, p0, Lcom/google/research/reflection/predictor/b;->fy:Lcom/google/research/reflection/a/a;

    iget v2, v0, Lcom/google/research/reflection/a/a;->dk:I

    if-eqz v2, :cond_0

    iget v2, v0, Lcom/google/research/reflection/a/a;->dk:I

    sub-int/2addr v2, v1

    iget v3, v0, Lcom/google/research/reflection/a/a;->dj:I

    sub-int/2addr v3, v2

    if-gez v3, :cond_1

    iget-object v2, v0, Lcom/google/research/reflection/a/a;->dl:[Ljava/lang/Object;

    array-length v2, v2

    add-int/2addr v3, v2

    :cond_1
    invoke-virtual {v0}, Lcom/google/research/reflection/a/a;->R()Z

    move-result v2

    if-eqz v2, :cond_2

    iget-object v2, v0, Lcom/google/research/reflection/a/a;->dm:Ljava/util/LinkedList;

    iget-object v4, v0, Lcom/google/research/reflection/a/a;->dl:[Ljava/lang/Object;

    aget-object v4, v4, v3

    invoke-virtual {v2, v4}, Ljava/util/LinkedList;->add(Ljava/lang/Object;)Z

    :cond_2
    iget-object v2, v0, Lcom/google/research/reflection/a/a;->dl:[Ljava/lang/Object;

    const/4 v4, 0x0

    aput-object v4, v2, v3

    iget v2, v0, Lcom/google/research/reflection/a/a;->dk:I

    sub-int/2addr v2, v1

    iput v2, v0, Lcom/google/research/reflection/a/a;->dk:I

    iget v2, v0, Lcom/google/research/reflection/a/a;->dn:I

    sub-int/2addr v2, v1

    iput v2, v0, Lcom/google/research/reflection/a/a;->dn:I

    goto :goto_0

    .line 48
    :cond_3
    iget-object v0, p0, Lcom/google/research/reflection/predictor/b;->fy:Lcom/google/research/reflection/a/a;

    invoke-virtual {v0, p1}, Lcom/google/research/reflection/a/a;->a(Ljava/lang/Object;)Ljava/lang/Object;

    .line 49
    iput-boolean v1, p0, Lcom/google/research/reflection/predictor/b;->fz:Z

    return-void
.end method

.method public final clear()V
    .locals 1

    .line 61
    iget-object v0, p0, Lcom/google/research/reflection/predictor/b;->fy:Lcom/google/research/reflection/a/a;

    invoke-virtual {v0}, Lcom/google/research/reflection/a/a;->clear()V

    return-void
.end method

.method public final d(Ljava/io/DataOutputStream;)V
    .locals 5
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 69
    iget-object v0, p0, Lcom/google/research/reflection/predictor/b;->fy:Lcom/google/research/reflection/a/a;

    iget v0, v0, Lcom/google/research/reflection/a/a;->dk:I

    .line 70
    invoke-virtual {p1, v0}, Ljava/io/DataOutputStream;->writeInt(I)V

    const/4 v1, 0x0

    const/4 v2, 0x0

    :goto_0
    if-ge v2, v0, :cond_0

    .line 72
    iget-object v3, p0, Lcom/google/research/reflection/predictor/b;->fy:Lcom/google/research/reflection/a/a;

    invoke-virtual {v3, v2}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 73
    invoke-interface {v3}, Lcom/google/research/reflection/signal/ReflectionEvent;->G()[B

    move-result-object v3

    .line 74
    array-length v4, v3

    invoke-virtual {p1, v4}, Ljava/io/DataOutputStream;->writeInt(I)V

    .line 75
    array-length v4, v3

    invoke-virtual {p1, v3, v1, v4}, Ljava/io/DataOutputStream;->write([BII)V

    add-int/lit8 v2, v2, 0x1

    goto :goto_0

    .line 77
    :cond_0
    iput-boolean v1, p0, Lcom/google/research/reflection/predictor/b;->fz:Z

    return-void
.end method

.method public final size()I
    .locals 1

    .line 65
    iget-object v0, p0, Lcom/google/research/reflection/predictor/b;->fy:Lcom/google/research/reflection/a/a;

    iget v0, v0, Lcom/google/research/reflection/a/a;->dk:I

    return v0
.end method

.method public toString()Ljava/lang/String;
    .locals 3

    .line 143
    new-instance v0, Ljava/lang/StringBuilder;

    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V

    const-string v1, "(size "

    .line 144
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 145
    iget-object v1, p0, Lcom/google/research/reflection/predictor/b;->fy:Lcom/google/research/reflection/a/a;

    iget v1, v1, Lcom/google/research/reflection/a/a;->dk:I

    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v1, "): "

    .line 146
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const/4 v1, 0x0

    .line 147
    :goto_0
    iget-object v2, p0, Lcom/google/research/reflection/predictor/b;->fy:Lcom/google/research/reflection/a/a;

    iget v2, v2, Lcom/google/research/reflection/a/a;->dk:I

    if-ge v1, v2, :cond_0

    .line 148
    iget-object v2, p0, Lcom/google/research/reflection/predictor/b;->fy:Lcom/google/research/reflection/a/a;

    invoke-virtual {v2, v1}, Lcom/google/research/reflection/a/a;->a(I)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Lcom/google/research/reflection/signal/ReflectionEvent;

    invoke-interface {v2}, Lcom/google/research/reflection/signal/ReflectionEvent;->getId()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v2, " "

    .line 149
    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    :cond_0
    const-string v1, "\n"

    .line 151
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 152
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method
