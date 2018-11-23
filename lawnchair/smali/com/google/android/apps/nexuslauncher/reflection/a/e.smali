.class public Lcom/google/android/apps/nexuslauncher/reflection/a/e;
.super Ljava/lang/Object;
.source "SourceFile"


# direct methods
.method public static a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;Ljava/lang/String;Ljava/lang/String;JLcom/google/research/reflection/signal/b;)Lcom/google/android/apps/nexuslauncher/reflection/signal/a;
    .locals 7

    .line 122
    invoke-static {}, Ljava/util/Calendar;->getInstance()Ljava/util/Calendar;

    move-result-object v0

    .line 123
    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J

    move-result-wide v1

    .line 124
    new-instance v3, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-direct {v3}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>()V

    .line 126
    new-instance v4, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;

    invoke-direct {v4}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;-><init>()V

    .line 128
    invoke-virtual {v0}, Ljava/util/Calendar;->getTimeInMillis()J

    move-result-wide v5

    invoke-virtual {v4, v5, v6}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->e(J)Lcom/google/research/reflection/signal/d;

    .line 129
    invoke-virtual {v0}, Ljava/util/Calendar;->getTimeZone()Ljava/util/TimeZone;

    move-result-object v5

    invoke-virtual {v5}, Ljava/util/TimeZone;->getID()Ljava/lang/String;

    move-result-object v5

    invoke-virtual {v4, v5}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->i(Ljava/lang/String;)Lcom/google/research/reflection/signal/d;

    .line 130
    invoke-virtual {v0}, Ljava/util/Calendar;->getTimeZone()Ljava/util/TimeZone;

    move-result-object v0

    iget-object v5, v4, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->dh:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    iget-wide v5, v5, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->timestamp:J

    invoke-virtual {v0, v5, v6}, Ljava/util/TimeZone;->getOffset(J)I

    move-result v0

    int-to-long v5, v0

    invoke-virtual {v4, v5, v6}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->h(J)Lcom/google/research/reflection/signal/d;

    .line 131
    invoke-virtual {v4, p3, p4}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->f(J)Lcom/google/research/reflection/signal/d;

    .line 132
    invoke-virtual {v4, v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/signal/f;->g(J)Lcom/google/research/reflection/signal/d;

    .line 133
    invoke-virtual {v3, v4}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/d;)Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 134
    invoke-virtual {v3, p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->g(Ljava/lang/String;)Lcom/google/research/reflection/signal/ReflectionEvent;

    const/4 p1, 0x2

    .line 135
    new-array p1, p1, [Ljava/lang/String;

    const-string p3, "GEL"

    const/4 p4, 0x0

    aput-object p3, p1, p4

    const/4 p3, 0x1

    aput-object p2, p1, p3

    invoke-static {p1}, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;

    move-result-object p1

    invoke-virtual {v3, p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->f(Ljava/util/List;)Lcom/google/research/reflection/signal/ReflectionEvent;

    const-string p1, "GEL"

    .line 136
    invoke-virtual {v3, p1}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->h(Ljava/lang/String;)Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    if-nez p0, :cond_0

    .line 138
    sget-object p0, Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;->fW:Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;

    invoke-virtual {v3, p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;)Lcom/google/research/reflection/signal/ReflectionEvent;

    goto :goto_0

    .line 140
    :cond_0
    invoke-virtual {v3, p0}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;)Lcom/google/research/reflection/signal/ReflectionEvent;

    :goto_0
    if-eqz p5, :cond_1

    .line 143
    invoke-virtual {v3, p5}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/b;)Lcom/google/research/reflection/signal/ReflectionEvent;

    :cond_1
    return-object v3
.end method

.method public static a(Landroid/content/ComponentName;JLandroid/content/Context;)Ljava/lang/String;
    .locals 2

    .line 157
    invoke-static/range {p0 .. p0}, Lcom/google/android/apps/nexuslauncher/reflection/f;->a(Landroid/content/ComponentName;)Ljava/lang/String;

    move-result-object p0

    .line 158
    invoke-static/range {p3 .. p3}, Lcom/android/launcher3/compat/UserManagerCompat;->getInstance(Landroid/content/Context;)Lcom/android/launcher3/compat/UserManagerCompat;

    move-result-object p3

    .line 159
    invoke-static {}, Landroid/os/Process;->myUserHandle()Landroid/os/UserHandle;

    move-result-object v0

    invoke-virtual {p3, v0}, Lcom/android/launcher3/compat/UserManagerCompat;->getSerialNumberForUser(Landroid/os/UserHandle;)J

    move-result-wide v0

    cmp-long p3, p1, v0

    if-eqz p3, :cond_0

    const-string p3, "%s#%d"

    const/4 v0, 0x2

    .line 161
    new-array v0, v0, [Ljava/lang/Object;

    const/4 v1, 0x0

    aput-object p0, v0, v1

    const/4 p0, 0x1

    invoke-static/range {p1 .. p2}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object p1

    aput-object p1, v0, p0

    invoke-static {p3, v0}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object p0

    :cond_0
    return-object p0
.end method

.method public static a(Ljava/util/Map;)[Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;
    .locals 5
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Ljava/lang/Integer;",
            ">;)[",
            "Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;"
        }
    .end annotation

    .line 87
    invoke-interface/range {p0 .. p0}, Ljava/util/Map;->size()I

    move-result v0

    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    .line 89
    invoke-interface/range {p0 .. p0}, Ljava/util/Map;->entrySet()Ljava/util/Set;

    move-result-object p0

    invoke-interface {p0}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object p0

    const/4 v1, 0x0

    :goto_0
    invoke-interface {p0}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_0

    invoke-interface {p0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/util/Map$Entry;

    .line 90
    new-instance v3, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;

    invoke-direct {v3}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;-><init>()V

    .line 91
    invoke-interface {v2}, Ljava/util/Map$Entry;->getValue()Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Ljava/lang/Integer;

    invoke-virtual {v4}, Ljava/lang/Integer;->intValue()I

    move-result v4

    iput v4, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->id:I

    .line 92
    invoke-interface {v2}, Ljava/util/Map$Entry;->getKey()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/lang/String;

    iput-object v2, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/c$f;->name:Ljava/lang/String;

    add-int/lit8 v2, v1, 0x1

    .line 93
    aput-object v3, v0, v1

    move v1, v2

    goto :goto_0

    :cond_0
    return-object v0
.end method

.method public static b(Lcom/google/research/reflection/signal/ReflectionEvent;)Z
    .locals 3

    const/4 v0, 0x0

    if-eqz p0, :cond_4

    .line 36
    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->F()Ljava/util/List;

    move-result-object v1

    if-eqz v1, :cond_4

    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->F()Ljava/util/List;

    move-result-object v1

    invoke-interface {v1}, Ljava/util/List;->size()I

    move-result v1

    const/4 v2, 0x2

    if-ge v1, v2, :cond_0

    goto :goto_1

    .line 39
    :cond_0
    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->F()Ljava/util/List;

    move-result-object v1

    invoke-interface {v1, v0}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v1

    check-cast v1, Ljava/lang/String;

    const-string v2, "GEL"

    .line 40
    invoke-virtual {v2, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_1

    return v0

    .line 44
    :cond_1
    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->F()Ljava/util/List;

    move-result-object p0

    const/4 v1, 0x1

    invoke-interface {p0, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object p0

    check-cast p0, Ljava/lang/String;

    const/4 v2, 0x4

    .line 45
    invoke-static {v2}, Ljava/lang/Integer;->toString(I)Ljava/lang/String;

    move-result-object v2

    invoke-static {p0, v2}, Ljava/util/Objects;->equals(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_3

    const/4 v2, 0x7

    .line 46
    invoke-static {v2}, Ljava/lang/Integer;->toString(I)Ljava/lang/String;

    move-result-object v2

    invoke-static {p0, v2}, Ljava/util/Objects;->equals(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_3

    const/16 v2, 0x8

    .line 47
    invoke-static {v2}, Ljava/lang/Integer;->toString(I)Ljava/lang/String;

    move-result-object v2

    invoke-static {p0, v2}, Ljava/util/Objects;->equals(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_3

    const/16 v2, 0x9

    .line 48
    invoke-static {v2}, Ljava/lang/Integer;->toString(I)Ljava/lang/String;

    move-result-object v2

    invoke-static {p0, v2}, Ljava/util/Objects;->equals(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result p0

    if-eqz p0, :cond_2

    goto :goto_0

    :cond_2
    return v0

    :cond_3
    :goto_0
    return v1

    :cond_4
    :goto_1
    return v0
.end method

.method public static b(Ljava/util/Map;)[Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;
    .locals 6
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "<T:",
            "Ljava/lang/Number;",
            ">(",
            "Ljava/util/Map<",
            "Ljava/lang/Integer;",
            "TT;>;)[",
            "Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;"
        }
    .end annotation

    .line 105
    invoke-interface/range {p0 .. p0}, Ljava/util/Map;->size()I

    move-result v0

    new-array v0, v0, [Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    .line 107
    invoke-interface/range {p0 .. p0}, Ljava/util/Map;->entrySet()Ljava/util/Set;

    move-result-object p0

    invoke-interface {p0}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    move-result-object p0

    const/4 v1, 0x0

    :goto_0
    invoke-interface {p0}, Ljava/util/Iterator;->hasNext()Z

    move-result v2

    if-eqz v2, :cond_0

    invoke-interface {p0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/util/Map$Entry;

    .line 108
    new-instance v3, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;

    invoke-direct {v3}, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;-><init>()V

    .line 109
    invoke-interface {v2}, Ljava/util/Map$Entry;->getKey()Ljava/lang/Object;

    move-result-object v4

    check-cast v4, Ljava/lang/Integer;

    invoke-virtual {v4}, Ljava/lang/Integer;->intValue()I

    move-result v4

    iput v4, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->key:I

    .line 110
    invoke-interface {v2}, Ljava/util/Map$Entry;->getValue()Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/lang/Number;

    invoke-virtual {v2}, Ljava/lang/Number;->longValue()J

    move-result-wide v4

    iput-wide v4, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/c$a;->value:J

    add-int/lit8 v2, v1, 0x1

    .line 111
    aput-object v3, v0, v1

    move v1, v2

    goto :goto_0

    :cond_0
    return-object v0
.end method
