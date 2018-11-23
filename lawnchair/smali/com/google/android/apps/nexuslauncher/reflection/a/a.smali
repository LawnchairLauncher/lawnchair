.class public final Lcom/google/android/apps/nexuslauncher/reflection/a/a;
.super Ljava/lang/Object;
.source "SourceFile"


# instance fields
.field public className:Ljava/lang/String;

.field public final packageName:Ljava/lang/String;

.field public state:I


# direct methods
.method constructor <init>(Ljava/lang/String;Ljava/lang/String;I)V
    .locals 0

    .line 20
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 21
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->packageName:Ljava/lang/String;

    .line 22
    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->className:Ljava/lang/String;

    .line 23
    iput p3, p0, Lcom/google/android/apps/nexuslauncher/reflection/a/a;->state:I

    return-void
.end method
