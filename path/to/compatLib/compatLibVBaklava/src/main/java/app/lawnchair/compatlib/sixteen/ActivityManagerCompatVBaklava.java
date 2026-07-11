// Update ActivityManagerCompatVBaklava to use the fallback implementation for ViewTreeLifecycleOwner
public class ActivityManagerCompatVBaklava {
    // ...

    public static ViewTreeLifecycleOwner getViewTreeLifecycleOwner(Context context) {
        return AnimationHandler.getViewTreeLifecycleOwner(context);
    }

    // ...
}