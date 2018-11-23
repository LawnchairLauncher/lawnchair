.class public interface abstract Lcom/google/research/reflection/a/c;
.super Ljava/lang/Object;
.source "SourceFile"


# static fields
.field public static final do:[Ljava/lang/String;

.field public static final dp:[Ljava/lang/String;

.field public static final dq:[Ljava/lang/String;


# direct methods
.method static constructor <clinit>()V
    .locals 4

    const-string v0, "com.spotify.music"

    const-string v1, "com.google.android.music"

    const-string v2, "com.pandora.android"

    const-string v3, "com.amazon.mp3"

    .line 9
    filled-new-array {v0, v1, v2, v3}, [Ljava/lang/String;

    move-result-object v0

    sput-object v0, Lcom/google/research/reflection/a/c;->do:[Ljava/lang/String;

    const-string v0, "com.ubercab"

    const-string v1, "me.lyft.android"

    const-string v2, "com.sdu.didi.psnger"

    const-string v3, "com.olacabs.customer"

    .line 13
    filled-new-array {v0, v1, v2, v3}, [Ljava/lang/String;

    move-result-object v0

    sput-object v0, Lcom/google/research/reflection/a/c;->dp:[Ljava/lang/String;

    const-string v0, "com.yelp.android"

    .line 17
    filled-new-array {v0}, [Ljava/lang/String;

    move-result-object v0

    sput-object v0, Lcom/google/research/reflection/a/c;->dq:[Ljava/lang/String;

    return-void
.end method


# virtual methods
.method public abstract q()Ljava/lang/String;
.end method

.method public abstract r()Ljava/lang/String;
.end method

.method public abstract s()Ljava/lang/String;
.end method
