.class public final synthetic Lcom/google/android/apps/nexuslauncher/allapps/-$$Lambda$ActionsController$l_tTKsBoaYeLbkSChO-IgfkLKcU;
.super Ljava/lang/Object;
.source "lambda"

# interfaces
.implements Ljava/util/Comparator;


# static fields
.field public static final synthetic INSTANCE:Lcom/google/android/apps/nexuslauncher/allapps/-$$Lambda$ActionsController$l_tTKsBoaYeLbkSChO-IgfkLKcU;


# direct methods
.method static synthetic constructor <clinit>()V
    .locals 1

    new-instance v0, Lcom/google/android/apps/nexuslauncher/allapps/-$$Lambda$ActionsController$l_tTKsBoaYeLbkSChO-IgfkLKcU;

    invoke-direct {v0}, Lcom/google/android/apps/nexuslauncher/allapps/-$$Lambda$ActionsController$l_tTKsBoaYeLbkSChO-IgfkLKcU;-><init>()V

    sput-object v0, Lcom/google/android/apps/nexuslauncher/allapps/-$$Lambda$ActionsController$l_tTKsBoaYeLbkSChO-IgfkLKcU;->INSTANCE:Lcom/google/android/apps/nexuslauncher/allapps/-$$Lambda$ActionsController$l_tTKsBoaYeLbkSChO-IgfkLKcU;

    return-void
.end method

.method private synthetic constructor <init>()V
    .locals 0

    invoke-direct/range {p0 .. p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public final compare(Ljava/lang/Object;Ljava/lang/Object;)I
    .locals 0

    check-cast p1, Lcom/google/android/apps/nexuslauncher/allapps/Action;

    check-cast p2, Lcom/google/android/apps/nexuslauncher/allapps/Action;

    invoke-static {p1, p2}, Lcom/google/android/apps/nexuslauncher/allapps/ActionsController;->lambda$l_tTKsBoaYeLbkSChO-IgfkLKcU(Lcom/google/android/apps/nexuslauncher/allapps/Action;Lcom/google/android/apps/nexuslauncher/allapps/Action;)I

    move-result p1

    return p1
.end method
