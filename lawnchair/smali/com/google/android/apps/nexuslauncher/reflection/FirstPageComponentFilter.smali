.class public Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;
.super Ljava/lang/Object;
.source "SourceFile"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter$IntentParser;
    }
.end annotation


# instance fields
.field private final X:Landroid/content/ContentResolver;

.field private final Y:Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter$IntentParser;

.field private final Z:Landroid/content/SharedPreferences;

.field private final aa:Lcom/google/android/apps/nexuslauncher/reflection/d/d;

.field private ab:Ljava/util/Set;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Set<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation
.end field

.field private final mContext:Landroid/content/Context;


# direct methods
.method public constructor <init>(Landroid/content/ContentResolver;Landroid/content/SharedPreferences;Lcom/google/android/apps/nexuslauncher/reflection/d/d;Landroid/content/Context;)V
    .locals 0

    .line 65
    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    .line 66
    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->X:Landroid/content/ContentResolver;

    .line 67
    iput-object p2, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->Z:Landroid/content/SharedPreferences;

    .line 68
    iput-object p3, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->aa:Lcom/google/android/apps/nexuslauncher/reflection/d/d;

    .line 69
    iput-object p4, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->mContext:Landroid/content/Context;

    .line 71
    new-instance p1, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter$IntentParser;

    invoke-direct {p1}, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter$IntentParser;-><init>()V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->Y:Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter$IntentParser;

    .line 72
    new-instance p1, Ljava/util/HashSet;

    invoke-direct {p1}, Ljava/util/HashSet;-><init>()V

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->ab:Ljava/util/Set;

    .line 73
    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->Z:Landroid/content/SharedPreferences;

    if-eqz p1, :cond_0

    iget-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->Z:Landroid/content/SharedPreferences;

    const-string p2, "model_subtracted_events"

    const/4 p3, 0x0

    invoke-interface {p1, p2, p3}, Landroid/content/SharedPreferences;->getStringSet(Ljava/lang/String;Ljava/util/Set;)Ljava/util/Set;

    move-result-object p1

    if-eqz p1, :cond_0

    iput-object p1, p0, Lcom/google/android/apps/nexuslauncher/reflection/FirstPageComponentFilter;->ab:Ljava/util/Set;

    :cond_0
    return-void
.end method


# virtual methods
.method public final declared-synchronized update()V
    .locals 13

    return-void
.end method
