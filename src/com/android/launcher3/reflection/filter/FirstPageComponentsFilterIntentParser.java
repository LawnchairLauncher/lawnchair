package com.android.launcher3.reflection.filter;

import java.net.URISyntaxException;
import android.util.Log;
import android.content.Intent;

class FirstPageComponentsFilterIntentParser
{
    public Intent i(final String s) {
        try {
            return Intent.parseUri(s, 0);
        }
        catch (URISyntaxException ex) {
            Log.e("Reflection.1stPFilter", String.format("Invalid intent URI %s", s), (Throwable)ex);
            return null;
        }
    }
}
