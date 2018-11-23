.class public Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils$EventTimestampComparator;
    }
.end annotation


# static fields
.field static final COMPARATOR:Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils$EventTimestampComparator;


# direct methods
.method static constructor <clinit>()V
    .locals 1

    .line 45
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils$EventTimestampComparator;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils$EventTimestampComparator;-><init>()V

    sput-object v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils;->COMPARATOR:Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils$EventTimestampComparator;

    return-void
.end method

.method public static a(Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;)Lcom/google/android/apps/nexuslauncher/reflection/signal/d;
    .locals 5

    .line 246
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/d;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/d;-><init>()V

    .line 247
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;->bF:[Ljava/lang/String;

    const/4 v2, 0x0

    aget-object v1, v1, v2

    .line 248
    invoke-virtual {v1}, Ljava/lang/String;->hashCode()I

    move-result v3

    const v4, 0x2268ff

    if-eq v3, v4, :cond_1

    const v4, 0x293b31

    if-eq v3, v4, :cond_0

    goto :goto_0

    :cond_0
    const-string v3, "Work"

    invoke-virtual {v1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2

    const/4 v1, 0x1

    goto :goto_1

    :cond_1
    const-string v3, "Home"

    invoke-virtual {v1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-eqz v1, :cond_2

    const/4 v1, 0x0

    goto :goto_1

    :cond_2
    :goto_0
    const/4 v1, -0x1

    :goto_1
    packed-switch v1, :pswitch_data_0

    const/4 v1, 0x0

    goto :goto_2

    :pswitch_0
    sget-object v1, Lcom/google/research/reflection/signal/ReflectionPrivatePlace$Alias;->gh:Lcom/google/research/reflection/signal/ReflectionPrivatePlace$Alias;

    goto :goto_2

    :pswitch_1
    sget-object v1, Lcom/google/research/reflection/signal/ReflectionPrivatePlace$Alias;->gg:Lcom/google/research/reflection/signal/ReflectionPrivatePlace$Alias;

    :goto_2
    invoke-static {v1}, Ljava/util/Collections;->singletonList(Ljava/lang/Object;)Ljava/util/List;

    move-result-object v1

    if-eqz v1, :cond_3

    .line 247
    iget-object v3, v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/d;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    invoke-interface {v1}, Ljava/util/List;->size()I

    move-result v4

    new-array v4, v4, [I

    iput-object v4, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;->bD:[I

    :goto_3
    invoke-interface {v1}, Ljava/util/List;->size()I

    move-result v3

    if-ge v2, v3, :cond_3

    iget-object v3, v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/d;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    iget-object v3, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;->bD:[I

    invoke-interface {v1, v2}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Lcom/google/research/reflection/signal/ReflectionPrivatePlace$Alias;

    invoke-virtual {v4}, Lcom/google/research/reflection/signal/ReflectionPrivatePlace$Alias;->ordinal()I

    move-result v4

    aput v4, v3, v2

    add-int/lit8 v2, v2, 0x1

    goto :goto_3

    .line 249
    :cond_3
    iget-wide v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;->time:J

    invoke-static {v1, v2}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object p0

    iget-object v1, v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/d;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    invoke-virtual {p0}, Ljava/lang/Long;->longValue()J

    move-result-wide v2

    iput-wide v2, v1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;->time:J

    return-object v0

    :pswitch_data_0
    .packed-switch 0x0
        :pswitch_1
        :pswitch_0
    .end packed-switch
.end method

.method public static a(Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;)Ljava/util/List;
    .locals 7
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;",
            ")",
            "Ljava/util/List<",
            "Lcom/google/android/apps/nexuslauncher/reflection/signal/a;",
            ">;"
        }
    .end annotation

    .line 144
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    new-instance v1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-direct {v1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>()V

    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->id:Ljava/lang/String;

    invoke-virtual {v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->g(Ljava/lang/String;)Lcom/google/research/reflection/signal/ReflectionEvent;

    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->type:Ljava/lang/String;

    invoke-virtual {v2}, Ljava/lang/String;->hashCode()I

    move-result v3

    const/4 v4, 0x0

    const/4 v5, 0x1

    sparse-switch v3, :sswitch_data_0

    goto :goto_0

    :sswitch_0
    const-string v3, "app_usage"

    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_0

    const/4 v3, 0x2

    goto :goto_1

    :sswitch_1
    const-string v3, "app_launch"

    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_0

    const/4 v3, 0x1

    goto :goto_1

    :sswitch_2
    const-string v3, "deep_link_usage"

    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_0

    const/4 v3, 0x4

    goto :goto_1

    :sswitch_3
    const-string v3, "headset"

    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_0

    const/4 v3, 0x3

    goto :goto_1

    :sswitch_4
    const-string v3, "app_install"

    invoke-virtual {v2, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_0

    const/4 v3, 0x0

    goto :goto_1

    :cond_0
    :goto_0
    const/4 v3, -0x1

    :goto_1
    packed-switch v3, :pswitch_data_0

    const-string v3, "Reflection.loadBuffer"

    const-string v6, "Unknown event type %s, default to app usage."

    new-array v5, v5, [Ljava/lang/Object;

    aput-object v2, v5, v4

    invoke-static {v6, v5}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v2

    invoke-static {v3, v2}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    :pswitch_0
    sget-object v2, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fW:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    goto :goto_2

    :pswitch_1
    sget-object v2, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fZ:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    goto :goto_2

    :pswitch_2
    sget-object v2, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->ga:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    goto :goto_2

    :pswitch_3
    sget-object v2, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fX:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    :goto_2
    invoke-virtual {v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;)Lcom/google/research/reflection/signal/ReflectionEvent;

    iget-wide v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->duration:J

    iget-object v4, v1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iput-wide v2, v4, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->duration:J

    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bq:Ljava/lang/String;

    iget-object v3, v1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iput-object v2, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bq:Ljava/lang/String;

    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;

    invoke-direct {v2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;-><init>()V

    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->time:J

    invoke-virtual {v2, v3, v4}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->e(J)Lcom/google/research/reflection/signal/d;

    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->br:J

    invoke-virtual {v2, v3, v4}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->f(J)Lcom/google/research/reflection/signal/d;

    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bu:J

    invoke-virtual {v2, v3, v4}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->h(J)Lcom/google/research/reflection/signal/d;

    iget-wide v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bs:J

    invoke-virtual {v2, v3, v4}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->g(J)Lcom/google/research/reflection/signal/d;

    iget-object v3, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bt:Ljava/lang/String;

    invoke-virtual {v2, v3}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->i(Ljava/lang/String;)Lcom/google/research/reflection/signal/d;

    invoke-virtual {v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/d;)Lcom/google/research/reflection/signal/ReflectionEvent;

    invoke-static/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils;->d(Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;)Lcom/google/android/apps/nexuslauncher/reflection/signal/c;

    move-result-object v2

    invoke-virtual {v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/b;)Lcom/google/research/reflection/signal/ReflectionEvent;

    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    if-eqz v2, :cond_1

    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    invoke-static {v2}, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;

    move-result-object v2

    invoke-virtual {v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->f(Ljava/util/List;)Lcom/google/research/reflection/signal/ReflectionEvent;

    :cond_1
    invoke-interface {v0, v1}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    invoke-static/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils;->b(Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;)Ljava/util/List;

    move-result-object v1

    invoke-interface {v0, v1}, Ljava/util/List;->addAll(Ljava/util/Collection;)Z

    invoke-static/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils;->c(Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;)Ljava/util/List;

    move-result-object p0

    invoke-interface {v0, p0}, Ljava/util/List;->addAll(Ljava/util/Collection;)Z

    sget-object p0, Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils;->COMPARATOR:Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils$EventTimestampComparator;

    invoke-static {v0, p0}, Ljava/util/Collections;->sort(Ljava/util/List;Ljava/util/Comparator;)V

    return-object v0

    :sswitch_data_0
    .sparse-switch
        0x29f7957d -> :sswitch_4
        0x2f67a282 -> :sswitch_3
        0x354a7a4f -> :sswitch_2
        0x3f912f91 -> :sswitch_1
        0x6dee7e03 -> :sswitch_0
    .end sparse-switch

    :pswitch_data_0
    .packed-switch 0x0
        :pswitch_3
        :pswitch_0
        :pswitch_0
        :pswitch_2
        :pswitch_1
    .end packed-switch
.end method

.method private static b(Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;)Ljava/util/List;
    .locals 8
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;",
            ")",
            "Ljava/util/List<",
            "Lcom/google/android/apps/nexuslauncher/reflection/signal/a;",
            ">;"
        }
    .end annotation

    .line 148
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    .line 149
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    if-eqz v1, :cond_1

    .line 150
    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    array-length v1, p0

    const/4 v2, 0x0

    :goto_0
    if-ge v2, v1, :cond_1

    aget-object v3, p0, v2

    .line 151
    iget-object v4, v3, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;->type:Ljava/lang/String;

    const-string v5, "headset"

    invoke-virtual {v4, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v4

    if-eqz v4, :cond_0

    .line 152
    new-instance v4, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-direct {v4}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>()V

    .line 153
    sget-object v5, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->ga:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    invoke-virtual {v4, v5}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;)Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 154
    iget-object v5, v3, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;->id:Ljava/lang/String;

    invoke-virtual {v4, v5}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->g(Ljava/lang/String;)Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 155
    new-instance v5, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;

    invoke-direct {v5}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;-><init>()V

    iget-wide v6, v3, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;->time:J

    invoke-virtual {v5, v6, v7}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->e(J)Lcom/google/research/reflection/signal/d;

    move-result-object v3

    invoke-virtual {v4, v3}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/d;)Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 156
    invoke-interface {v0, v4}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    :cond_0
    add-int/lit8 v2, v2, 0x1

    goto :goto_0

    :cond_1
    return-object v0
.end method

.method private static c(Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;)Ljava/util/List;
    .locals 8
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;",
            ")",
            "Ljava/util/List<",
            "Lcom/google/android/apps/nexuslauncher/reflection/signal/a;",
            ">;"
        }
    .end annotation

    .line 164
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    .line 165
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    if-eqz v1, :cond_1

    .line 166
    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    array-length v1, p0

    const/4 v2, 0x0

    :goto_0
    if-ge v2, v1, :cond_1

    aget-object v3, p0, v2

    .line 167
    iget-object v4, v3, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;->type:Ljava/lang/String;

    const-string v5, "app_usage"

    invoke-virtual {v4, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v4

    if-eqz v4, :cond_0

    .line 168
    new-instance v4, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-direct {v4}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>()V

    .line 169
    sget-object v5, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fW:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    invoke-virtual {v4, v5}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;)Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 170
    iget-object v5, v3, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;->id:Ljava/lang/String;

    invoke-virtual {v4, v5}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->g(Ljava/lang/String;)Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 171
    new-instance v5, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;

    invoke-direct {v5}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;-><init>()V

    iget-wide v6, v3, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;->time:J

    invoke-virtual {v5, v6, v7}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->e(J)Lcom/google/research/reflection/signal/d;

    move-result-object v3

    invoke-virtual {v4, v3}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/d;)Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 172
    invoke-interface {v0, v4}, Ljava/util/List;->add(Ljava/lang/Object;)Z

    :cond_0
    add-int/lit8 v2, v2, 0x1

    goto :goto_0

    :cond_1
    return-object v0
.end method

.method private static d(Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;)Lcom/google/android/apps/nexuslauncher/reflection/signal/c;
    .locals 6

    .line 229
    new-instance v0, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;-><init>()V

    .line 230
    iget-object v1, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    if-eqz v1, :cond_1

    .line 231
    iget-object p0, p0, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bv:[Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    array-length v1, p0

    const/4 v2, 0x0

    :goto_0
    if-ge v2, v1, :cond_1

    aget-object v3, p0, v2

    .line 232
    iget-object v4, v3, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;->type:Ljava/lang/String;

    const-string v5, "semantic_place"

    invoke-virtual {v4, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v4

    if-eqz v4, :cond_0

    .line 233
    iget-object v4, v3, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;->bF:[Ljava/lang/String;

    array-length v4, v4

    if-lez v4, :cond_0

    .line 234
    invoke-static {v3}, Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils;->a(Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;)Lcom/google/android/apps/nexuslauncher/reflection/signal/d;

    move-result-object v4

    invoke-virtual {v0, v4}, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->a(Lcom/google/research/reflection/signal/ReflectionPrivatePlace;)Lcom/google/research/reflection/signal/b;

    .line 237
    :cond_0
    iget-object v3, v3, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;->type:Ljava/lang/String;

    const-string v4, "lat_long"

    invoke-virtual {v3, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    add-int/lit8 v2, v2, 0x1

    goto :goto_0

    :cond_1
    return-object v0
.end method

.method public static f(Ljava/lang/String;)Lcom/google/research/reflection/predictor/b;
    .locals 13
    .annotation system Ldalvik/annotation/Throws;
        value = {
            Ljava/io/IOException;
        }
    .end annotation

    .line 53
    new-instance v0, Lcom/google/research/reflection/predictor/b;

    invoke-direct {v0}, Lcom/google/research/reflection/predictor/b;-><init>()V

    if-eqz p0, :cond_6

    .line 55
    sget-object v1, Ljava/nio/charset/StandardCharsets;->ISO_8859_1:Ljava/nio/charset/Charset;

    invoke-virtual {p0, v1}, Ljava/lang/String;->getBytes(Ljava/nio/charset/Charset;)[B

    move-result-object p0

    .line 56
    new-instance v1, Ljava/io/DataInputStream;

    new-instance v2, Ljava/io/ByteArrayInputStream;

    invoke-direct {v2, p0}, Ljava/io/ByteArrayInputStream;-><init>([B)V

    invoke-direct {v1, v2}, Ljava/io/DataInputStream;-><init>(Ljava/io/InputStream;)V

    .line 57
    invoke-virtual {v1}, Ljava/io/DataInputStream;->readInt()I

    move-result p0

    const/4 v2, 0x0

    const/4 v3, 0x0

    move-object v5, v2

    const/4 v4, 0x0

    :goto_0
    if-ge v4, p0, :cond_6

    .line 61
    invoke-virtual {v1}, Ljava/io/DataInputStream;->readInt()I

    move-result v6

    if-eqz v5, :cond_0

    .line 62
    array-length v7, v5

    if-ge v7, v6, :cond_1

    .line 63
    :cond_0
    new-array v5, v6, [B

    .line 66
    :cond_1
    invoke-virtual {v1, v5, v3, v6}, Ljava/io/DataInputStream;->read([BII)I

    .line 70
    :try_start_0
    invoke-static {v5, v3, v6}, Lcom/google/protobuf/nano/CodedInputByteBufferNano;->newInstance([BII)Lcom/google/protobuf/nano/CodedInputByteBufferNano;

    move-result-object v6

    invoke-static {v6}, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->b(Lcom/google/protobuf/nano/CodedInputByteBufferNano;)Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;

    move-result-object v6
    :try_end_0
    .catch Ljava/io/IOException; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_1

    :catch_0
    const-string v6, "Reflection.loadBuffer"

    const-string v7, "deserialize event failed!"

    .line 72
    invoke-static {v6, v7}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    move-object v6, v2

    :goto_1
    const/4 v7, 0x1

    if-eqz v6, :cond_2

    .line 74
    iget-object v8, v6, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->id:Ljava/lang/String;

    if-eqz v8, :cond_2

    iget-wide v8, v6, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->time:J

    const-wide/16 v10, 0x0

    cmp-long v8, v8, v10

    if-eqz v8, :cond_2

    sget-object v8, Lcom/google/android/apps/nexuslauncher/reflection/f;->ai:Ljava/util/regex/Pattern;

    iget-object v9, v6, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->id:Ljava/lang/String;

    invoke-virtual {v8, v9}, Ljava/util/regex/Pattern;->matcher(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;

    move-result-object v8

    invoke-virtual {v8}, Ljava/util/regex/Matcher;->find()Z

    move-result v8

    if-eqz v8, :cond_2

    const/4 v8, 0x1

    goto :goto_2

    :cond_2
    const/4 v8, 0x0

    :goto_2
    if-eqz v8, :cond_5

    .line 75
    invoke-static {v6}, Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils;->a(Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;)Ljava/util/List;

    move-result-object v7

    .line 76
    invoke-interface {v7}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v8

    :goto_3
    invoke-interface {v8}, Ljava/util/Iterator;->hasNext()Z

    move-result v9

    if-eqz v9, :cond_3

    invoke-interface {v8}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v9

    check-cast v9, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    .line 77
    invoke-virtual {v0, v9}, Lcom/google/research/reflection/predictor/b;->c(Lcom/google/research/reflection/signal/ReflectionEvent;)V

    goto :goto_3

    .line 80
    :cond_3
    new-instance v8, Ljava/lang/StringBuilder;

    invoke-direct {v8}, Ljava/lang/StringBuilder;-><init>()V

    const-string v9, "Converted event "

    .line 81
    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v9, v6, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->id:Ljava/lang/String;

    .line 82
    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v9, ", "

    .line 83
    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v9, v6, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->type:Ljava/lang/String;

    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v9, ", "

    .line 84
    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    iget-object v6, v6, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    invoke-virtual {v8, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    const-string v6, " to events ("

    .line 85
    invoke-virtual {v8, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 86
    invoke-interface {v7}, Ljava/util/List;->size()I

    move-result v6

    invoke-virtual {v8, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    const-string v6, "):\n"

    .line 87
    invoke-virtual {v8, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 88
    invoke-interface {v7}, Ljava/util/List;->iterator()Ljava/util/Iterator;

    move-result-object v6

    :goto_4
    invoke-interface {v6}, Ljava/util/Iterator;->hasNext()Z

    move-result v7

    if-eqz v7, :cond_4

    invoke-interface {v6}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v7

    check-cast v7, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    .line 89
    iget-object v9, v7, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    iget-object v9, v9, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v9, ", "

    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 90
    invoke-virtual {v7}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->C()Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    move-result-object v9

    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    const-string v9, ", "

    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 91
    invoke-virtual {v7}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->F()Ljava/util/List;

    move-result-object v9

    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    const-string v9, ", "

    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 92
    invoke-virtual {v7}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v7

    invoke-interface {v7}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v9

    invoke-virtual {v8, v9, v10}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    const/16 v7, 0xa

    invoke-virtual {v8, v7}, Ljava/lang/StringBuilder;->append(C)Ljava/lang/StringBuilder;

    goto :goto_4

    :cond_4
    const-string v6, "Reflection.loadBuffer"

    .line 94
    invoke-virtual {v8}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v7

    invoke-static {v6, v7}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto :goto_5

    :cond_5
    const-string v8, "Reflection.loadBuffer"

    const-string v9, "Event not loaded correctly (id: %s, type %s, eventSrc: %s, time: %d)"

    const/4 v10, 0x4

    .line 98
    new-array v10, v10, [Ljava/lang/Object;

    iget-object v11, v6, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->id:Ljava/lang/String;

    aput-object v11, v10, v3

    iget-object v11, v6, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->type:Ljava/lang/String;

    aput-object v11, v10, v7

    const/4 v7, 0x2

    iget-object v11, v6, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->bw:[Ljava/lang/String;

    aput-object v11, v10, v7

    const/4 v7, 0x3

    iget-wide v11, v6, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->time:J

    .line 100
    invoke-static {v11, v12}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object v6

    aput-object v6, v10, v7

    .line 98
    invoke-static {v9, v10}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v6

    invoke-static {v8, v6}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    :goto_5
    add-int/lit8 v4, v4, 0x1

    goto/16 :goto_0

    :cond_6
    return-object v0
.end method
