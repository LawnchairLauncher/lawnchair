// Add a fallback implementation for ViewTreeLifecycleOwner in older Android versions
public class AnimationHandler {
    // ...

    public static ViewTreeLifecycleOwner getViewTreeLifecycleOwner(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getApplicationContext().getSystemService(ViewTreeLifecycleOwner.class);
        } else {
            // Fallback implementation for older Android versions
            return new ViewTreeLifecycleOwner() {
                @Override
                public void setLifecycleOwner(LifecycleOwner lifecycleOwner) {
                    // No-op implementation
                }

                @Override
                public LifecycleOwner getLifecycleOwner() {
                    return null;
                }
            };
        }
    }

    // ...
}