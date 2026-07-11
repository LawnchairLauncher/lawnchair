// Update QuickstepCompatFactoryVBaklava to use the fallback implementation for ViewTreeLifecycleOwner
public class QuickstepCompatFactoryVBaklava {
    // ...

    public static ViewTreeLifecycleOwner getViewTreeLifecycleOwner(Context context) {
        return AnimationHandler.getViewTreeLifecycleOwner(context);
    }

    // ...
}