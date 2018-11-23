.class public interface abstract Lcom/google/research/reflection/signal/ReflectionEvent;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;
    }
.end annotation


# virtual methods
.method public abstract C()Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;
.end method

.method public abstract D()Lcom/google/research/reflection/signal/d;
.end method

.method public abstract E()Lcom/google/research/reflection/signal/b;
.end method

.method public abstract F()Ljava/util/List;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Ljava/util/List<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation
.end method

.method public abstract G()[B
.end method

.method public abstract H()Ljava/lang/String;
.end method

.method public abstract a(Lcom/google/research/reflection/signal/ReflectionEvent$ReflectionEventType;)Lcom/google/research/reflection/signal/ReflectionEvent;
.end method

.method public abstract a(Lcom/google/research/reflection/signal/d;)Lcom/google/research/reflection/signal/ReflectionEvent;
.end method

.method public abstract a([BI)Lcom/google/research/reflection/signal/ReflectionEvent;
.end method

.method public abstract getDuration()J
.end method

.method public abstract getId()Ljava/lang/String;
.end method
