.class public Lcom/google/android/apps/nexuslauncher/reflection/d/b;
.super Ljava/lang/Object;
.source "SourceFile"

# interfaces
.implements Lcom/google/research/reflection/c/c;


# instance fields
.field private final ap:Lcom/google/android/apps/nexuslauncher/reflection/d/c;


# direct methods
.method public constructor <init>(Lcom/google/android/apps/nexuslauncher/reflection/d/c;)V
    .locals 0

    .line 10
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 11
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/b;->ap:Lcom/google/android/apps/nexuslauncher/reflection/d/c;

    return-void
.end method


# virtual methods
.method public final a(Lcom/google/research/reflection/signal/ReflectionEvent;)V
    .locals 0

    .line 20
    invoke-virtual/range {p0 .. p1}, Lcom/google/android/apps/nexuslauncher/reflection/d/b;->c(Lcom/google/research/reflection/signal/ReflectionEvent;)V

    return-void
.end method

.method public final declared-synchronized c(Lcom/google/research/reflection/signal/ReflectionEvent;)V
    .locals 1

    monitor-enter p0

    .line 15
    :try_start_0
    iget-object v0, p0, Lcom/google/android/apps/nexuslauncher/reflection/d/b;->ap:Lcom/google/android/apps/nexuslauncher/reflection/d/c;

    invoke-virtual {v0, p1}, Lcom/google/android/apps/nexuslauncher/reflection/d/c;->d(Lcom/google/research/reflection/signal/ReflectionEvent;)V
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 16
    monitor-exit p0

    return-void

    :catchall_0
    move-exception p1

    .line 14
    monitor-exit p0

    throw p1
.end method
