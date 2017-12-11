package ch.deletescape.lawnchair.lawnfeed;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public class PermissionActivity extends Activity {
    public static final int REQUEST_CODE = 1337;

    private ResultReceiver resultReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Don't continue if the activity doesn't have an intent
        if (getIntent() == null) {
            finish();
            return;
        }

        // Get permissions array which we want to request
        resultReceiver = getIntent().getParcelableExtra("resultReceiver");
        String[] permissionsArray = getIntent().getStringArrayExtra("permissions");
        int requestCode = getIntent().getIntExtra("requestCode", REQUEST_CODE);

        // Check if those permissions are already granted
        if (PermissionResponse.hasPermissions(this, permissionsArray)) {
            // Proceed like those permissions have been now granted
            onComplete(requestCode, permissionsArray, new int[]{ PackageManager.PERMISSION_GRANTED });
        } else {
            // Otherwise request those permissions and wait for users response
            ActivityCompat.requestPermissions(this, permissionsArray, requestCode);
        }
    }

    private void onComplete(int requestCode, String[] permissions, int[] grantResult) {
        Bundle bundle = new Bundle();
        bundle.putStringArray("permissions", permissions);
        bundle.putIntArray("grantResult", grantResult);
        bundle.putInt("requestCode", requestCode);

        // Send our callback to the result receiver
        resultReceiver.send(requestCode, bundle);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResult) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResult);
        onComplete(requestCode, permissions, grantResult);
    }

    public static void callAsync(Context context, String[] permissions, int requestCode, final PermissionResultCallback callback) {
        // Proceed to the callback if permissions were already granted
        if (PermissionResponse.hasPermissions(context, permissions)) {
            callback.onComplete(new PermissionResponse(permissions, new int[]{PackageManager.PERMISSION_GRANTED}, requestCode));
            return;
        }

        // Our result receiver to get the response asynchronously
        ResultReceiver receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                super.onReceiveResult(resultCode, resultData);
                int[] grantResult = resultData.getIntArray("grantResult");
                String[] permissions = resultData.getStringArray("permissions");

                // Call the callback with the result
                callback.onComplete(new PermissionResponse(permissions, grantResult, resultCode));
            }
        };

        // Build our intent to launch
        Intent intent = new Intent(context, PermissionActivity.class);
        intent.putExtra("requestCode", requestCode);
        intent.putExtra("permissions", permissions);
        intent.putExtra("resultReceiver", receiver);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        // Start our activity and wait
        context.startActivity(intent);
    }

    // Contains results from requesting the permissions
    public static class PermissionResponse {
        private String[] permissions;
        private int [] grantResult;
        private int requestCode;

        public PermissionResponse(String[] permissions, int[] grantResult, int requestCode) {
            this.permissions = permissions;
            this.grantResult = grantResult;
            this.requestCode = requestCode;
        }

        public boolean isGranted() {
            return (grantResult != null && grantResult.length > 0 && grantResult[0] == PackageManager.PERMISSION_GRANTED);
        }

        public String[] getPermissions() {
            return permissions;
        }

        public int[] getGrantResult() {
            return grantResult;
        }

        public int getRequestCode() {
            return requestCode;
        }

        public static boolean hasPermissions(Context context, String[] permissionsArray) {
            // If a permission isn't granted => return false
            for (String permission : permissionsArray) {
                if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED){
                    return false;
                }
            }

            // Return true only when all permissions are granted
            return true;
        }
    }

    // Simple interface to handle our async callbacks
    public interface PermissionResultCallback {
        void onComplete(PermissionResponse response);
    }
}
