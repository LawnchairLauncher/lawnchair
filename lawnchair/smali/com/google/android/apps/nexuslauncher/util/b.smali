.class public Lcom/google/android/apps/nexuslauncher/util/b;
.super Ljava/lang/Object;
.source "SourceFile"


# direct methods
.method public static varargs a(Ljava/lang/String;[Ljava/lang/String;)Landroid/content/IntentFilter;
    .locals 5

    .line 18
    new-instance v0, Landroid/content/IntentFilter;

    invoke-direct {v0}, Landroid/content/IntentFilter;-><init>()V

    .line 19
    array-length v1, p1

    const/4 v2, 0x0

    const/4 v3, 0x0

    :goto_0
    if-ge v3, v1, :cond_0

    aget-object v4, p1, v3

    .line 20
    invoke-virtual {v0, v4}, Landroid/content/IntentFilter;->addAction(Ljava/lang/String;)V

    add-int/lit8 v3, v3, 0x1

    goto :goto_0

    :cond_0
    const-string p1, "package"

    .line 22
    invoke-virtual {v0, p1}, Landroid/content/IntentFilter;->addDataScheme(Ljava/lang/String;)V

    .line 23
    invoke-virtual {v0, p0, v2}, Landroid/content/IntentFilter;->addDataSchemeSpecificPart(Ljava/lang/String;I)V

    return-object v0
.end method
