.class public Lcom/google/android/apps/nexuslauncher/reflection/d/c;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/a/c;


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/android/apps/nexuslauncher/reflection/d/c$a;
    }
.end annotation


# static fields
.field private static final bK:J


# instance fields
.field private final ad:Lcom/google/android/apps/nexuslauncher/reflection/a/b;

.field private final bI:Ljava/util/Map;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Ljava/lang/Long;",
            ">;"
        }
    .end annotation
.end field

.field private final bJ:Ljava/util/Map;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation
.end field

.field private final bL:Lcom/google/android/apps/nexuslauncher/reflection/d/a;


# direct methods
.method static constructor <clinit>()V
    .locals 3

    .line 80
    sget-object v0, Ljava/util/concurrent/TimeUnit;->DAYS:Ljava/util/concurrent/TimeUnit;

    const-wide/16 v1, 0x1

    invoke-virtual {v0, v1, v2}, Ljava/util/concurrent/TimeUnit;->toMillis(J)J

    move-result-wide v0

    sput-wide v0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bK:J

    return-void
.end method

.method public constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/d/a;Lcom/google/android/apps/nexuslauncher/reflection/a/b;)V
    .locals 1

    .line 85
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 73
    new-instance v0, Ljava/util/HashMap;

    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bI:Ljava/util/Map;

    .line 74
    new-instance v0, Ljava/util/HashMap;

    invoke-direct {v0}, Ljava/util/HashMap;-><init>()V

    iput-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bJ:Ljava/util/Map;

    .line 86
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bL:Lcom/google/android/apps/nexuslauncher/reflection/d/a;

    .line 87
    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->ad:Lcom/google/android/apps/nexuslauncher/reflection/a/b;

    return-void
.end method

.method private a(Ljava/lang/String;J[Ljava/lang/String;)Ljava/lang/String;
    .locals 8

    .line 307
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v0

    .line 308
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bI:Ljava/util/Map;

    invoke-interface {v2, p1}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v2

    if-nez v2, :cond_0

    const-wide/16 v2, 0x0

    goto :goto_0

    :cond_0
    iget-object v2, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bI:Ljava/util/Map;

    invoke-interface {v2, p1}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Ljava/lang/Long;

    invoke-virtual {v2}, Ljava/lang/Long;->longValue()J

    move-result-wide v2

    :goto_0
    sub-long v2, v0, v2

    cmp-long p2, v2, p2

    if-gez p2, :cond_2

    .line 309
    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bJ:Ljava/util/Map;

    invoke-interface {p2, p1}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p2

    if-nez p2, :cond_1

    goto :goto_1

    .line 316
    :cond_1
    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bJ:Ljava/util/Map;

    invoke-interface {p2, p1}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Ljava/lang/String;

    return-object p1

    :cond_2
    :goto_1
    const-wide/16 p2, 0xa

    const/4 v2, 0x0

    const/4 v3, 0x0

    move-object v4, v3

    .line 310
    :goto_2
    array-length v5, p4

    if-ge v2, v5, :cond_4

    aget-object v5, p4, v2

    invoke-direct {p0, v5}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->e(Ljava/lang/String;)J

    move-result-wide v5

    cmp-long v7, v5, p2

    if-ltz v7, :cond_3

    aget-object p2, p4, v2

    move-object v4, p2

    move-wide p2, v5

    :cond_3
    add-int/lit8 v2, v2, 0x1

    goto :goto_2

    .line 311
    :cond_4
    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bI:Ljava/util/Map;

    invoke-static {v0, v1}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object p3

    invoke-interface {p2, p1, p3}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    .line 312
    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->ad:Lcom/google/android/apps/nexuslauncher/reflection/a/b;

    if-eqz v4, :cond_6

    iget-object p3, p2, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aV:Ljava/util/Map;

    invoke-interface {p3, v4}, Ljava/util/Map;->containsKey(Ljava/lang/Object;)Z

    move-result p3

    if-nez p3, :cond_5

    goto :goto_3

    :cond_5
    iget-object p2, p2, Lcom/google/android/apps/nexuslauncher/reflection/a/b;->aV:Ljava/util/Map;

    invoke-interface {p2, v4}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p2

    check-cast p2, Lcom/google/android/apps/nexuslauncher/reflection/a/a;

    new-instance p3, Landroid/content/ComponentName;

    iget-object p4, p2, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->packageName:Ljava/lang/String;

    iget-object p2, p2, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->className:Ljava/lang/String;

    invoke-direct {p3, p4, p2}, Landroid/content/ComponentName;-><init>(Ljava/lang/String;Ljava/lang/String;)V

    invoke-static {p3}, Lcom/google/android/apps/nexuslauncher/reflection/f;->a(Landroid/content/ComponentName;)Ljava/lang/String;

    move-result-object v3

    .line 313
    :cond_6
    :goto_3
    iget-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bJ:Ljava/util/Map;

    invoke-interface {p2, p1, v3}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    return-object v3
.end method

.method private static synthetic a(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V
    .locals 0

    if-eqz p0, :cond_0

    .line 198
    :try_start_0
    invoke-interface/range {p1 .. p1}, Ljava/lang/AutoCloseable;->close()V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    return-void

    :catch_0
    move-exception p1

    invoke-virtual {p0, p1}, Ljava/lang/Throwable;->addSuppressed(Ljava/lang/Throwable;)V

    return-void

    :cond_0
    invoke-interface/range {p1 .. p1}, Ljava/lang/AutoCloseable;->close()V

    return-void
.end method

.method private declared-synchronized e(Ljava/lang/String;)J
    .locals 3

    monitor-enter p0

    .line 341
    :try_start_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bL:Lcom/google/android/apps/nexuslauncher/reflection/d/a;

    invoke-virtual {v0}, Lcom/google/android/apps/nexuslauncher/reflection/d/a;->getReadableDatabase()Landroid/database/sqlite/SQLiteDatabase;

    move-result-object v0

    const-string v1, "select count(*) from reflection_event where id like ?"

    .line 342
    invoke-virtual {v0, v1}, Landroid/database/sqlite/SQLiteDatabase;->compileStatement(Ljava/lang/String;)Landroid/database/sqlite/SQLiteStatement;

    move-result-object v0

    const/4 v1, 0x1

    .line 349
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v2, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string p1, "/%"

    invoke-virtual {v2, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {v0, v1, p1}, Landroid/database/sqlite/SQLiteStatement;->bindString(ILjava/lang/String;)V

    .line 350
    invoke-virtual {v0}, Landroid/database/sqlite/SQLiteStatement;->simpleQueryForLong()J

    move-result-wide v0
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    monitor-exit p0

    return-wide v0

    :catchall_0
    move-exception p1

    .line 340
    monitor-exit p0

    throw p1
.end method


# virtual methods
.method public final declared-synchronized a(JI)Lcom/google/android/apps/nexuslauncher/reflection/d/c$a;
    .locals 20

    move-object/from16 v1, p0

    monitor-enter p0

    .line 206
    :try_start_0
    iget-object v0, v1, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bL:Lcom/google/android/apps/nexuslauncher/reflection/d/a;

    invoke-virtual {v0}, Lcom/google/android/apps/nexuslauncher/reflection/d/a;->getReadableDatabase()Landroid/database/sqlite/SQLiteDatabase;

    move-result-object v2

    .line 208
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    const-wide/16 v11, -0x1

    const-string v3, "reflection_event"

    const/4 v4, 0x0

    .line 210
    sget-object v5, Ljava/util/Locale;->US:Ljava/util/Locale;

    const-string v6, "%s > ?"

    const/4 v13, 0x1

    new-array v7, v13, [Ljava/lang/Object;

    const-string v8, "_id"

    const/4 v14, 0x0

    aput-object v8, v7, v14

    .line 212
    invoke-static {v5, v6, v7}, Ljava/lang/String;->format(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v5

    new-array v6, v13, [Ljava/lang/String;

    .line 213
    invoke-static/range {p1 .. p2}, Ljava/lang/Long;->toString(J)Ljava/lang/String;

    move-result-object v7

    aput-object v7, v6, v14

    const/4 v7, 0x0

    const/4 v8, 0x0

    const-string v9, "_id ASC"

    .line 216
    invoke-static/range {p3 .. p3}, Ljava/lang/Integer;->toString(I)Ljava/lang/String;

    move-result-object v10

    .line 211
    invoke-virtual/range {v2 .. v10}, Landroid/database/sqlite/SQLiteDatabase;->query(Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;

    move-result-object v2
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_2

    :try_start_1
    const-string v4, "_id"

    .line 218
    invoke-interface {v2, v4}, Landroid/database/Cursor;->getColumnIndex(Ljava/lang/String;)I

    move-result v4

    const-string v5, "proto"

    .line 219
    invoke-interface {v2, v5}, Landroid/database/Cursor;->getColumnIndex(Ljava/lang/String;)I

    move-result v5

    const-string v6, "id"

    .line 220
    invoke-interface {v2, v6}, Landroid/database/Cursor;->getColumnIndex(Ljava/lang/String;)I

    move-result v6

    const-string v7, "public_place"

    .line 221
    invoke-interface {v2, v7}, Landroid/database/Cursor;->getColumnIndex(Ljava/lang/String;)I

    move-result v7

    const-string v8, "semanticPlace"

    .line 222
    invoke-interface {v2, v8}, Landroid/database/Cursor;->getColumnIndex(Ljava/lang/String;)I

    move-result v8

    const-string v9, "latLong"

    .line 223
    invoke-interface {v2, v9}, Landroid/database/Cursor;->getColumnIndex(Ljava/lang/String;)I

    move-result v9

    .line 225
    :goto_0
    invoke-interface {v2}, Landroid/database/Cursor;->moveToNext()Z

    move-result v10
    :try_end_1
    .catch Ljava/lang/Throwable; {:try_start_1 .. :try_end_1} :catch_3
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    if-eqz v10, :cond_7

    .line 230
    :try_start_2
    new-instance v10, Ljava/util/ArrayList;

    invoke-direct {v10}, Ljava/util/ArrayList;-><init>()V
    :try_end_2
    .catch Lcom/google/protobuf/nano/InvalidProtocolBufferNanoException; {:try_start_2 .. :try_end_2} :catch_2
    .catch Ljava/lang/Throwable; {:try_start_2 .. :try_end_2} :catch_3
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    .line 232
    :try_start_3
    new-instance v15, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    .line 233
    invoke-interface {v2, v5}, Landroid/database/Cursor;->getBlob(I)[B

    move-result-object v3

    invoke-static {v3}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->c([B)Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    move-result-object v3

    invoke-direct {v15, v3}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;)V

    .line 232
    invoke-interface {v10, v15}, Ljava/util/List;->add(Ljava/lang/Object;)Z
    :try_end_3
    .catch Lcom/google/protobuf/nano/InvalidProtocolBufferNanoException; {:try_start_3 .. :try_end_3} :catch_0
    .catch Ljava/lang/Throwable; {:try_start_3 .. :try_end_3} :catch_3
    .catchall {:try_start_3 .. :try_end_3} :catchall_0

    .line 243
    :catch_0
    :try_start_4
    invoke-interface {v10}, Ljava/util/List;->isEmpty()Z

    move-result v3

    if-nez v3, :cond_1

    invoke-interface {v10, v14}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-virtual {v3}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->D()Lcom/google/research/reflection/signal/d;

    move-result-object v3

    invoke-interface {v3}, Lcom/google/research/reflection/signal/d;->getTimestamp()J

    move-result-wide v16

    const-wide/16 v18, 0x0

    cmp-long v3, v16, v18

    if-nez v3, :cond_0

    goto :goto_1

    :cond_0
    const/4 v3, 0x0

    goto :goto_2

    .line 244
    :cond_1
    :goto_1
    invoke-interface {v10}, Ljava/util/List;->clear()V

    .line 246
    invoke-interface {v2, v5}, Landroid/database/Cursor;->getBlob(I)[B

    move-result-object v3

    invoke-static {v3}, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;->a([B)Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;

    move-result-object v3

    .line 245
    invoke-static {v3}, Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils;->a(Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$a;)Ljava/util/List;

    move-result-object v3

    invoke-interface {v10, v3}, Ljava/util/List;->addAll(Ljava/util/Collection;)Z

    const/4 v3, 0x1

    .line 251
    :goto_2
    invoke-interface {v10}, Ljava/util/List;->isEmpty()Z

    move-result v15

    if-nez v15, :cond_6

    .line 252
    invoke-interface {v10}, Ljava/util/List;->size()I

    move-result v15

    sub-int/2addr v15, v13

    invoke-interface {v10, v15}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v15

    check-cast v15, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-interface {v2, v6}, Landroid/database/Cursor;->getString(I)Ljava/lang/String;

    move-result-object v14

    invoke-virtual {v15, v14}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->g(Ljava/lang/String;)Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 253
    new-instance v14, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;

    invoke-direct {v14}, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;-><init>()V

    .line 254
    invoke-interface {v2, v7}, Landroid/database/Cursor;->isNull(I)Z

    move-result v15

    if-nez v15, :cond_2

    .line 256
    invoke-interface {v2, v7}, Landroid/database/Cursor;->getBlob(I)[B

    move-result-object v15

    invoke-static {v15}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;->f([B)Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    move-result-object v15
    :try_end_4
    .catch Lcom/google/protobuf/nano/InvalidProtocolBufferNanoException; {:try_start_4 .. :try_end_4} :catch_2
    .catch Ljava/lang/Throwable; {:try_start_4 .. :try_end_4} :catch_3
    .catchall {:try_start_4 .. :try_end_4} :catchall_0

    .line 257
    :try_start_5
    new-instance v13, Lcom/google/android/apps/nexuslauncher/reflection/signal/e;

    invoke-direct {v13, v15}, Lcom/google/android/apps/nexuslauncher/reflection/signal/e;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;)V

    invoke-virtual {v14, v13}, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->a(Lcom/google/research/reflection/signal/c;)Lcom/google/research/reflection/signal/b;

    .line 259
    :cond_2
    invoke-interface {v2, v8}, Landroid/database/Cursor;->isNull(I)Z

    move-result v13

    if-nez v13, :cond_4

    if-eqz v3, :cond_3

    .line 262
    invoke-interface {v2, v8}, Landroid/database/Cursor;->getBlob(I)[B

    move-result-object v3

    invoke-static {v3}, Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;->b([B)Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;

    move-result-object v3

    .line 264
    invoke-static {v3}, Lcom/google/android/apps/nexuslauncher/reflection/signal/LegacyEventProtoUtils;->a(Lcom/google/android/apps/nexuslauncher/reflection/c/a/a$e;)Lcom/google/android/apps/nexuslauncher/reflection/signal/d;

    move-result-object v3

    .line 263
    invoke-virtual {v14, v3}, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->a(Lcom/google/research/reflection/signal/ReflectionPrivatePlace;)Lcom/google/research/reflection/signal/b;

    goto :goto_3

    .line 267
    :cond_3
    invoke-interface {v2, v8}, Landroid/database/Cursor;->getBlob(I)[B

    move-result-object v3

    invoke-static {v3}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;->e([B)Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    move-result-object v3

    .line 268
    new-instance v13, Lcom/google/android/apps/nexuslauncher/reflection/signal/d;

    invoke-direct {v13, v3}, Lcom/google/android/apps/nexuslauncher/reflection/signal/d;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;)V

    invoke-virtual {v14, v13}, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->a(Lcom/google/research/reflection/signal/ReflectionPrivatePlace;)Lcom/google/research/reflection/signal/b;

    .line 271
    :cond_4
    :goto_3
    invoke-interface {v2, v9}, Landroid/database/Cursor;->isNull(I)Z

    move-result v3

    if-nez v3, :cond_5

    .line 272
    invoke-interface {v2, v9}, Landroid/database/Cursor;->getBlob(I)[B

    move-result-object v3

    invoke-static {v3}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;->d([B)Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    move-result-object v3

    .line 273
    new-instance v13, Lcom/google/android/apps/nexuslauncher/reflection/signal/b;

    invoke-direct {v13, v3}, Lcom/google/android/apps/nexuslauncher/reflection/signal/b;-><init>(Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;)V

    invoke-virtual {v14, v13}, Lcom/google/android/apps/nexuslauncher/reflection/signal/c;->a(Lcom/google/research/reflection/signal/a;)Lcom/google/research/reflection/signal/b;

    .line 275
    :cond_5
    invoke-interface {v10}, Ljava/util/List;->size()I

    move-result v3
    :try_end_5
    .catch Lcom/google/protobuf/nano/InvalidProtocolBufferNanoException; {:try_start_5 .. :try_end_5} :catch_1
    .catch Ljava/lang/Throwable; {:try_start_5 .. :try_end_5} :catch_3
    .catchall {:try_start_5 .. :try_end_5} :catchall_0

    const/4 v13, 0x1

    sub-int/2addr v3, v13

    :try_start_6
    invoke-interface {v10, v3}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    invoke-virtual {v3, v14}, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->a(Lcom/google/research/reflection/signal/b;)Lcom/google/research/reflection/signal/ReflectionEvent;

    .line 277
    invoke-virtual {v0, v10}, Ljava/util/ArrayList;->addAll(Ljava/util/Collection;)Z

    goto :goto_4

    :catch_1
    const/4 v13, 0x1

    goto :goto_5

    .line 279
    :cond_6
    :goto_4
    invoke-interface {v2, v4}, Landroid/database/Cursor;->getLong(I)J

    move-result-wide v14
    :try_end_6
    .catch Lcom/google/protobuf/nano/InvalidProtocolBufferNanoException; {:try_start_6 .. :try_end_6} :catch_2
    .catch Ljava/lang/Throwable; {:try_start_6 .. :try_end_6} :catch_3
    .catchall {:try_start_6 .. :try_end_6} :catchall_0

    move-wide v11, v14

    :catch_2
    :goto_5
    const/4 v14, 0x0

    goto/16 :goto_0

    :cond_7
    if-eqz v2, :cond_8

    const/4 v3, 0x0

    .line 287
    :try_start_7
    invoke-static {v3, v2}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->a(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V

    .line 288
    :cond_8
    new-instance v2, Lcom/google/android/apps/nexuslauncher/reflection/d/c$a;

    invoke-direct {v2, v11, v12, v0}, Lcom/google/android/apps/nexuslauncher/reflection/d/c$a;-><init>(JLjava/util/List;)V
    :try_end_7
    .catchall {:try_start_7 .. :try_end_7} :catchall_2

    monitor-exit p0

    return-object v2

    :catchall_0
    move-exception v0

    const/4 v3, 0x0

    goto :goto_6

    :catch_3
    move-exception v0

    move-object v3, v0

    .line 210
    :try_start_8
    throw v3
    :try_end_8
    .catchall {:try_start_8 .. :try_end_8} :catchall_1

    :catchall_1
    move-exception v0

    :goto_6
    if-eqz v2, :cond_9

    .line 287
    :try_start_9
    invoke-static {v3, v2}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->a(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V

    :cond_9
    throw v0
    :try_end_9
    .catchall {:try_start_9 .. :try_end_9} :catchall_2

    :catchall_2
    move-exception v0

    .line 205
    monitor-exit p0

    throw v0
.end method

.method public final declared-synchronized a(J)V
    .locals 5

    monitor-enter p0

    .line 143
    :try_start_0
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    const-wide v0, 0xb43e9400L

    sub-long/2addr p1, v0

    .line 145
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bL:Lcom/google/android/apps/nexuslauncher/reflection/d/a;

    invoke-virtual {v0}, Lcom/google/android/apps/nexuslauncher/reflection/d/a;->getWritableDatabase()Landroid/database/sqlite/SQLiteDatabase;

    move-result-object v0

    const-string v1, "reflection_event"

    const-string v2, "timestamp <= ?"

    const/4 v3, 0x1

    .line 146
    new-array v3, v3, [Ljava/lang/String;

    const/4 v4, 0x0

    .line 147
    invoke-static {p1, p2}, Ljava/lang/Long;->toString(J)Ljava/lang/String;

    move-result-object p1

    aput-object p1, v3, v4

    .line 146
    invoke-virtual {v0, v1, v2, v3}, Landroid/database/sqlite/SQLiteDatabase;->delete(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)I
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 148
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 142
    monitor-exit p0

    throw p1
.end method

.method public final declared-synchronized a(Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;)V
    .locals 5

    monitor-enter p0

    .line 97
    :try_start_0
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 101
    :try_start_1
    invoke-static/range {p1 .. p1}, Lcom/google/protobuf/nano/MessageNano;->toByteArray(Lcom/google/protobuf/nano/MessageNano;)[B

    move-result-object p1

    invoke-static {p1}, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->c([B)Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    move-result-object p1
    :try_end_1
    .catch Lcom/google/protobuf/nano/InvalidProtocolBufferNanoException; {:try_start_1 .. :try_end_1} :catch_0
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    .line 109
    :try_start_2
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bL:Lcom/google/android/apps/nexuslauncher/reflection/d/a;

    invoke-virtual {v0}, Lcom/google/android/apps/nexuslauncher/reflection/d/a;->getWritableDatabase()Landroid/database/sqlite/SQLiteDatabase;

    move-result-object v0

    .line 110
    new-instance v1, Landroid/content/ContentValues;

    invoke-direct {v1}, Landroid/content/ContentValues;-><init>()V

    const-string v2, "timestamp"

    .line 111
    iget-object v3, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->ci:Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;

    iget-wide v3, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/b$f;->timestamp:J

    invoke-static {v3, v4}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    move-result-object v3

    invoke-virtual {v1, v2, v3}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/Long;)V

    const-string v2, "client"

    const-string v3, ""

    .line 112
    invoke-virtual {v1, v2, v3}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/String;)V

    const-string v2, "type"

    .line 113
    iget v3, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->type:I

    invoke-static {v3}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v3

    invoke-virtual {v1, v2, v3}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/Integer;)V

    const-string v2, "id"

    .line 114
    iget-object v3, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->id:Ljava/lang/String;

    invoke-virtual {v1, v2, v3}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/String;)V

    const-string v2, "generated_from"

    .line 115
    iget-object v3, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cl:Ljava/lang/String;

    invoke-virtual {v1, v2, v3}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/String;)V

    const-string v2, "eventSource"

    .line 117
    iget-object v3, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    array-length v3, v3

    const/4 v4, 0x1

    if-le v3, v4, :cond_0

    iget-object v3, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->bw:[Ljava/lang/String;

    aget-object v3, v3, v4

    goto :goto_0

    :cond_0
    const-string v3, ""

    :goto_0
    invoke-virtual {v1, v2, v3}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/String;)V

    .line 119
    iget-object v2, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    if-eqz v2, :cond_3

    .line 120
    iget-object v2, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    iget-object v2, v2, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    if-eqz v2, :cond_1

    const-string v2, "semanticPlace"

    .line 121
    iget-object v3, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    iget-object v3, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cm:Lcom/google/android/apps/nexuslauncher/reflection/e/b$d;

    .line 122
    invoke-static {v3}, Lcom/google/protobuf/nano/MessageNano;->toByteArray(Lcom/google/protobuf/nano/MessageNano;)[B

    move-result-object v3

    .line 121
    invoke-virtual {v1, v2, v3}, Landroid/content/ContentValues;->put(Ljava/lang/String;[B)V

    .line 124
    :cond_1
    iget-object v2, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    iget-object v2, v2, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    if-eqz v2, :cond_2

    const-string v2, "public_place"

    .line 125
    iget-object v3, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    iget-object v3, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->cn:Lcom/google/android/apps/nexuslauncher/reflection/e/b$e;

    .line 126
    invoke-static {v3}, Lcom/google/protobuf/nano/MessageNano;->toByteArray(Lcom/google/protobuf/nano/MessageNano;)[B

    move-result-object v3

    .line 125
    invoke-virtual {v1, v2, v3}, Landroid/content/ContentValues;->put(Ljava/lang/String;[B)V

    .line 128
    :cond_2
    iget-object v2, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    iget-object v2, v2, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->co:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    if-eqz v2, :cond_3

    const-string v2, "latLong"

    .line 129
    iget-object v3, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    iget-object v3, v3, Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;->co:Lcom/google/android/apps/nexuslauncher/reflection/e/b$b;

    .line 130
    invoke-static {v3}, Lcom/google/protobuf/nano/MessageNano;->toByteArray(Lcom/google/protobuf/nano/MessageNano;)[B

    move-result-object v3

    .line 129
    invoke-virtual {v1, v2, v3}, Landroid/content/ContentValues;->put(Ljava/lang/String;[B)V

    :cond_3
    const/4 v2, 0x0

    .line 133
    iput-object v2, p1, Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;->cj:Lcom/google/android/apps/nexuslauncher/reflection/e/b$c;

    const-string v3, "proto"

    .line 135
    invoke-static {p1}, Lcom/google/protobuf/nano/MessageNano;->toByteArray(Lcom/google/protobuf/nano/MessageNano;)[B

    move-result-object p1

    invoke-virtual {v1, v3, p1}, Landroid/content/ContentValues;->put(Ljava/lang/String;[B)V

    const-string p1, "reflection_event"

    .line 136
    invoke-virtual {v0, p1, v2, v1}, Landroid/database/sqlite/SQLiteDatabase;->insert(Ljava/lang/String;Ljava/lang/String;Landroid/content/ContentValues;)J
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_0

    .line 137
    monitor-exit p0

    return-void

    .line 106
    :catch_0
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 96
    monitor-exit p0

    throw p1
.end method

.method public final declared-synchronized a(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;)V
    .locals 12
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "Ljava/util/Map<",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            ">;)V"
        }
    .end annotation

    monitor-enter p0

    .line 168
    :try_start_0
    invoke-static {}, Lcom/android/launcher3/util/Preconditions;->assertNonUiThread()V

    .line 169
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bL:Lcom/google/android/apps/nexuslauncher/reflection/d/a;

    invoke-virtual {v0}, Lcom/google/android/apps/nexuslauncher/reflection/d/a;->getWritableDatabase()Landroid/database/sqlite/SQLiteDatabase;

    move-result-object v0

    .line 170
    invoke-virtual {v0}, Landroid/database/sqlite/SQLiteDatabase;->beginTransaction()V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_4

    :try_start_1
    const-string v2, "reflection_event"

    const-string v1, "_id"

    const-string v3, "id"

    .line 172
    filled-new-array {v1, v3}, [Ljava/lang/String;

    move-result-object v3

    const-string v4, "id like ?"

    const/4 v10, 0x1

    new-array v5, v10, [Ljava/lang/String;

    const/4 v1, 0x0

    new-instance v6, Ljava/lang/StringBuilder;

    invoke-direct {v6}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v6, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string p1, "%"

    invoke-virtual {v6, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v6}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    aput-object p1, v5, v1

    const/4 v6, 0x0

    const/4 v7, 0x0

    const/4 v8, 0x0

    const/4 v9, 0x0

    move-object v1, v0

    invoke-virtual/range {v1 .. v9}, Landroid/database/sqlite/SQLiteDatabase;->query(Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;

    move-result-object p1
    :try_end_1
    .catch Landroid/database/SQLException; {:try_start_1 .. :try_end_1} :catch_2
    .catchall {:try_start_1 .. :try_end_1} :catchall_3

    const/4 v1, 0x0

    :try_start_2
    const-string v2, "UPDATE reflection_event SET id = ? WHERE _id = ?"

    .line 178
    invoke-virtual {v0, v2}, Landroid/database/sqlite/SQLiteDatabase;->compileStatement(Ljava/lang/String;)Landroid/database/sqlite/SQLiteStatement;

    move-result-object v2
    :try_end_2
    .catch Ljava/lang/Throwable; {:try_start_2 .. :try_end_2} :catch_1
    .catchall {:try_start_2 .. :try_end_2} :catchall_2

    :try_start_3
    const-string v3, "_id"

    .line 181
    invoke-interface {p1, v3}, Landroid/database/Cursor;->getColumnIndex(Ljava/lang/String;)I

    move-result v3

    const-string v4, "id"

    .line 182
    invoke-interface {p1, v4}, Landroid/database/Cursor;->getColumnIndexOrThrow(Ljava/lang/String;)I

    move-result v4

    .line 184
    :goto_0
    invoke-interface {p1}, Landroid/database/Cursor;->moveToNext()Z

    move-result v5

    if-eqz v5, :cond_1

    .line 185
    invoke-interface {p1, v3}, Landroid/database/Cursor;->getLong(I)J

    move-result-wide v5

    .line 186
    invoke-interface {p1, v4}, Landroid/database/Cursor;->getString(I)Ljava/lang/String;

    move-result-object v7

    .line 188
    invoke-interface {p3, v7}, Ljava/util/Map;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v8

    check-cast v8, Ljava/lang/String;

    if-nez v8, :cond_0

    .line 190
    new-instance v8, Ljava/lang/StringBuilder;

    invoke-direct {v8}, Ljava/lang/StringBuilder;-><init>()V

    invoke-virtual {v8, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v9, "_"

    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-interface/range {p3 .. p3}, Ljava/util/Map;->size()I

    move-result v9

    invoke-virtual {v8, v9}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    invoke-virtual {v8}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v8

    .line 191
    invoke-interface {p3, v7, v8}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    .line 193
    :cond_0
    invoke-virtual {v2, v10, v8}, Landroid/database/sqlite/SQLiteStatement;->bindString(ILjava/lang/String;)V

    const/4 v7, 0x2

    .line 194
    invoke-virtual {v2, v7, v5, v6}, Landroid/database/sqlite/SQLiteStatement;->bindLong(IJ)V

    .line 195
    invoke-virtual {v2}, Landroid/database/sqlite/SQLiteStatement;->executeUpdateDelete()I

    goto :goto_0

    .line 197
    :cond_1
    invoke-virtual {v0}, Landroid/database/sqlite/SQLiteDatabase;->setTransactionSuccessful()V
    :try_end_3
    .catch Ljava/lang/Throwable; {:try_start_3 .. :try_end_3} :catch_0
    .catchall {:try_start_3 .. :try_end_3} :catchall_0

    if-eqz v2, :cond_2

    .line 198
    :try_start_4
    invoke-static {v1, v2}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->a(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V
    :try_end_4
    .catch Ljava/lang/Throwable; {:try_start_4 .. :try_end_4} :catch_1
    .catchall {:try_start_4 .. :try_end_4} :catchall_2

    :cond_2
    if-eqz p1, :cond_3

    :try_start_5
    invoke-static {v1, p1}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->a(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V
    :try_end_5
    .catch Landroid/database/SQLException; {:try_start_5 .. :try_end_5} :catch_2
    .catchall {:try_start_5 .. :try_end_5} :catchall_3

    .line 201
    :cond_3
    :try_start_6
    invoke-virtual {v0}, Landroid/database/sqlite/SQLiteDatabase;->endTransaction()V
    :try_end_6
    .catchall {:try_start_6 .. :try_end_6} :catchall_4

    .line 202
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p2

    move-object p3, v1

    goto :goto_1

    :catch_0
    move-exception p2

    .line 172
    :try_start_7
    throw p2
    :try_end_7
    .catchall {:try_start_7 .. :try_end_7} :catchall_1

    :catchall_1
    move-exception p3

    move-object v11, p3

    move-object p3, p2

    move-object p2, v11

    :goto_1
    if-eqz v2, :cond_4

    .line 198
    :try_start_8
    invoke-static {p3, v2}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->a(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V

    :cond_4
    throw p2
    :try_end_8
    .catch Ljava/lang/Throwable; {:try_start_8 .. :try_end_8} :catch_1
    .catchall {:try_start_8 .. :try_end_8} :catchall_2

    :catchall_2
    move-exception p2

    goto :goto_2

    :catch_1
    move-exception p2

    move-object v1, p2

    .line 172
    :try_start_9
    throw v1
    :try_end_9
    .catchall {:try_start_9 .. :try_end_9} :catchall_2

    :goto_2
    if-eqz p1, :cond_5

    .line 198
    :try_start_a
    invoke-static {v1, p1}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->a(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V

    :cond_5
    throw p2
    :try_end_a
    .catch Landroid/database/SQLException; {:try_start_a .. :try_end_a} :catch_2
    .catchall {:try_start_a .. :try_end_a} :catchall_3

    :catchall_3
    move-exception p1

    goto :goto_3

    :catch_2
    move-exception p1

    :try_start_b
    const-string p2, "Reflection.EvtDbLogger"

    const-string p3, "Error renaming EventIds"

    .line 199
    invoke-static {p2, p3, p1}, Lcom/android/launcher3/logging/FileLog;->d(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Exception;)V
    :try_end_b
    .catchall {:try_start_b .. :try_end_b} :catchall_3

    .line 201
    :try_start_c
    invoke-virtual {v0}, Landroid/database/sqlite/SQLiteDatabase;->endTransaction()V
    :try_end_c
    .catchall {:try_start_c .. :try_end_c} :catchall_4

    .line 202
    monitor-exit p0

    return-void

    .line 201
    :goto_3
    :try_start_d
    invoke-virtual {v0}, Landroid/database/sqlite/SQLiteDatabase;->endTransaction()V

    throw p1
    :try_end_d
    .catchall {:try_start_d .. :try_end_d} :catchall_4

    :catchall_4
    move-exception p1

    .line 167
    monitor-exit p0

    throw p1
.end method

.method public final declared-synchronized d(Lcom/google/research/reflection/signal/ReflectionEvent;)V
    .locals 0

    monitor-enter p0

    .line 91
    :try_start_0
    check-cast p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;

    iget-object p1, p1, Lcom/google/android/apps/nexuslauncher/reflection/signal/a;->df:Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;

    invoke-virtual {p0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->a(Lcom/google/android/apps/nexuslauncher/reflection/e/b$a;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 92
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 90
    monitor-exit p0

    throw p1
.end method

.method public final q()Ljava/lang/String;
    .locals 4

    const-string v0, "music_app"

    .line 293
    sget-wide v1, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bK:J

    sget-object v3, Lcom/google/research/reflection/a/c;->do:[Ljava/lang/String;

    invoke-direct {p0, v0, v1, v2, v3}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->a(Ljava/lang/String;J[Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method

.method public final r()Ljava/lang/String;
    .locals 4

    const-string v0, "taxi_app"

    .line 298
    sget-wide v1, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bK:J

    sget-object v3, Lcom/google/research/reflection/a/c;->dp:[Ljava/lang/String;

    invoke-direct {p0, v0, v1, v2, v3}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->a(Ljava/lang/String;J[Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method

.method public final s()Ljava/lang/String;
    .locals 4

    const-string v0, "cafe_app"

    .line 303
    sget-wide v1, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->bK:J

    sget-object v3, Lcom/google/research/reflection/a/c;->dq:[Ljava/lang/String;

    invoke-direct {p0, v0, v1, v2, v3}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->a(Ljava/lang/String;J[Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method
