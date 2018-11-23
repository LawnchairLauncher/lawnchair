.class public final enum Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;
.super Ljava/lang/Enum;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/google/research/reflection/signal/ReflectionEvent;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x4019
    name = "ReflectionEventType"
.end annotation

.annotation system Ldalvik/annotation/Signature;
    value = {
        "Ljava/lang/Enum<",
        "Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;",
        ">;"
    }
.end annotation


# static fields
.field public static final enum fW:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

.field public static final enum fX:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

.field public static final enum fY:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

.field public static final enum fZ:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

.field public static final enum ga:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

.field public static final enum gb:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

.field public static final enum gc:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

.field public static final enum gd:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

.field private static final synthetic ge:[Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;


# direct methods
.method static constructor <clinit>()V
    .locals 10

    .line 17
    new-instance v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    const-string v1, "APP_USAGE"

    const/4 v2, 0x0

    invoke-direct {v0, v1, v2}, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;-><init>(Ljava/lang/String;I)V

    sput-object v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fW:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    .line 18
    new-instance v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    const-string v1, "APP_INSTALL"

    const/4 v3, 0x1

    invoke-direct {v0, v1, v3}, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;-><init>(Ljava/lang/String;I)V

    sput-object v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fX:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    .line 19
    new-instance v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    const-string v1, "INSTANT_APP_USAGE"

    const/4 v4, 0x2

    invoke-direct {v0, v1, v4}, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;-><init>(Ljava/lang/String;I)V

    sput-object v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fY:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    .line 20
    new-instance v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    const-string v1, "SHORTCUTS"

    const/4 v5, 0x3

    invoke-direct {v0, v1, v5}, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;-><init>(Ljava/lang/String;I)V

    sput-object v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fZ:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    .line 21
    new-instance v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    const-string v1, "HEADSET"

    const/4 v6, 0x4

    invoke-direct {v0, v1, v6}, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;-><init>(Ljava/lang/String;I)V

    sput-object v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->ga:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    .line 22
    new-instance v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    const-string v1, "CLICK"

    const/4 v7, 0x5

    invoke-direct {v0, v1, v7}, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;-><init>(Ljava/lang/String;I)V

    sput-object v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->gb:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    .line 23
    new-instance v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    const-string v1, "SPECIAL_SCREEN"

    const/4 v8, 0x6

    invoke-direct {v0, v1, v8}, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;-><init>(Ljava/lang/String;I)V

    sput-object v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->gc:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    .line 24
    new-instance v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    const-string v1, "NOTIFICATION"

    const/4 v9, 0x7

    invoke-direct {v0, v1, v9}, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;-><init>(Ljava/lang/String;I)V

    sput-object v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->gd:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    const/16 v0, 0x8

    .line 16
    new-array v0, v0, [Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    sget-object v1, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fW:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    aput-object v1, v0, v2

    sget-object v1, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fX:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    aput-object v1, v0, v3

    sget-object v1, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fY:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    aput-object v1, v0, v4

    sget-object v1, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fZ:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    aput-object v1, v0, v5

    sget-object v1, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->ga:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    aput-object v1, v0, v6

    sget-object v1, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->gb:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    aput-object v1, v0, v7

    sget-object v1, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->gc:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    aput-object v1, v0, v8

    sget-object v1, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->gd:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    aput-object v1, v0, v9

    sput-object v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->ge:[Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    return-void
.end method

.method private constructor <init>(Ljava/lang/String;I)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()V"
        }
    .end annotation

    .line 16
    invoke-direct/range {p0 .. p2}, Ljava/lang/Enum;-><init>(Ljava/lang/String;I)V

    return-void
.end method

.method public static valueOf(Ljava/lang/String;)Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;
    .locals 1

    .line 16
    const-class v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    invoke-static {v0, p0}, Ljava/lang/Enum;->valueOf(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;

    move-result-object p0

    check-cast p0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    return-object p0
.end method

.method public static values()[Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;
    .locals 1

    .line 16
    sget-object v0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->ge:[Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    invoke-virtual {v0}, [Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->clone()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, [Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    return-object v0
.end method
