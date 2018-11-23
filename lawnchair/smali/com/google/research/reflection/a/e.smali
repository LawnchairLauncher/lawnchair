.class public Lcom/google/research/reflection/a/e;
.super Ljava/lang/Object;
.source "SourceFile"


# direct methods
.method public static a(Lcom/google/research/reflection/signal/ReflectionEvent;Lcom/google/research/reflection/signal/ReflectionEvent;)J
    .locals 12

    .line 62
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v0

    invoke-interface {v0}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v0

    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v2

    invoke-interface {v2}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v2

    sub-long/2addr v0, v2

    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->getDuration()J

    move-result-wide v2

    sub-long/2addr v0, v2

    .line 65
    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v2

    invoke-interface {v2}, Lcom/google/research/reflection/signal/d;->g()J

    move-result-wide v2

    const-wide/16 v4, 0x0

    cmp-long v2, v2, v4

    const-wide v6, 0x7fffffffffffffffL

    if-lez v2, :cond_4

    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v2

    invoke-interface {v2}, Lcom/google/research/reflection/signal/d;->g()J

    move-result-wide v2

    cmp-long v2, v2, v4

    if-lez v2, :cond_4

    .line 66
    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v2

    invoke-interface {v2}, Lcom/google/research/reflection/signal/d;->g()J

    move-result-wide v2

    invoke-static {v2, v3}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object v2

    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v3

    invoke-interface {v3}, Lcom/google/research/reflection/signal/d;->g()J

    move-result-wide v8

    invoke-static {v8, v9}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object v3

    invoke-static {v2, v3}, Ljava/util/Objects;->equals(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_3

    .line 68
    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v2

    invoke-interface {v2}, Lcom/google/research/reflection/signal/d;->g()J

    move-result-wide v2

    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v8

    invoke-interface {v8}, Lcom/google/research/reflection/signal/d;->O()J

    move-result-wide v8

    add-long/2addr v2, v8

    .line 69
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v8

    invoke-interface {v8}, Lcom/google/research/reflection/signal/d;->g()J

    move-result-wide v8

    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object p1

    invoke-interface {p1}, Lcom/google/research/reflection/signal/d;->O()J

    move-result-wide v10

    add-long/2addr v8, v10

    sub-long/2addr v8, v2

    .line 70
    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->getDuration()J

    move-result-wide p0

    sub-long/2addr v8, p0

    cmp-long p0, v0, v4

    if-ltz p0, :cond_0

    cmp-long p1, v8, v4

    if-ltz p1, :cond_0

    .line 74
    invoke-static {v0, v1, v8, v9}, Ljava/lang/Math;->min(JJ)J

    move-result-wide p0

    return-wide p0

    :cond_0
    if-gez p0, :cond_1

    cmp-long p1, v8, v4

    if-ltz p1, :cond_1

    return-wide v8

    :cond_1
    if-ltz p0, :cond_2

    cmp-long p0, v8, v4

    if-gez p0, :cond_2

    return-wide v0

    :cond_2
    return-wide v6

    .line 84
    :cond_3
    invoke-interface/range {p1 .. p1}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object p1

    invoke-interface {p1}, Lcom/google/research/reflection/signal/d;->O()J

    move-result-wide v0

    .line 85
    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object p1

    invoke-interface {p1}, Lcom/google/research/reflection/signal/d;->O()J

    move-result-wide v2

    sub-long/2addr v0, v2

    .line 86
    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->getDuration()J

    move-result-wide p0

    sub-long/2addr v0, p0

    return-wide v0

    :cond_4
    cmp-long p0, v0, v4

    if-gez p0, :cond_5

    return-wide v6

    :cond_5
    return-wide v0
.end method

.method public static e(Lcom/google/research/reflection/signal/ReflectionEvent;)Ljava/util/Calendar;
    .locals 5

    .line 13
    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v0

    invoke-interface {v0}, Lcom/google/research/reflection/signal/d;->getTimeZone()Ljava/lang/String;

    move-result-object v0

    if-eqz v0, :cond_0

    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v0

    invoke-interface {v0}, Lcom/google/research/reflection/signal/d;->getTimeZone()Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/String;->isEmpty()Z

    move-result v0

    if-nez v0, :cond_0

    .line 14
    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v0

    invoke-interface {v0}, Lcom/google/research/reflection/signal/d;->getTimeZone()Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Ljava/util/TimeZone;->getTimeZone(Ljava/lang/String;)Ljava/util/TimeZone;

    move-result-object v0

    invoke-static {v0}, Ljava/util/Calendar;->getInstance(Ljava/util/TimeZone;)Ljava/util/Calendar;

    move-result-object v0

    .line 15
    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object p0

    invoke-interface {p0}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v1

    invoke-virtual {v0, v1, v2}, Ljava/util/Calendar;->setTimeInMillis(J)V

    return-object v0

    :cond_0
    const-string v0, "UTC"

    .line 18
    invoke-static {v0}, Ljava/util/TimeZone;->getTimeZone(Ljava/lang/String;)Ljava/util/TimeZone;

    move-result-object v0

    invoke-static {v0}, Ljava/util/Calendar;->getInstance(Ljava/util/TimeZone;)Ljava/util/Calendar;

    move-result-object v0

    .line 19
    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v1

    invoke-interface {v1}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v1

    invoke-interface/range {p0 .. p0}, Lcom/google/research/reflection/signal/ReflectionEvent;->D()Lcom/google/research/reflection/signal/d;

    move-result-object p0

    invoke-interface {p0}, Lcom/google/research/reflection/signal/d;->P()J

    move-result-wide v3

    add-long/2addr v1, v3

    invoke-virtual {v0, v1, v2}, Ljava/util/Calendar;->setTimeInMillis(J)V

    return-object v0
.end method
