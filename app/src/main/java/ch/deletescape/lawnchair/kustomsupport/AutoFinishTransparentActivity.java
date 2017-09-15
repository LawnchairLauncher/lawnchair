package ch.deletescape.lawnchair.kustomsupport;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;

public class AutoFinishTransparentActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }

    public static void start(Context context) {
        context.startActivity(new Intent(context, AutoFinishTransparentActivity.class));
    }
}
