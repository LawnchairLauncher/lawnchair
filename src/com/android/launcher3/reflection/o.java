package com.android.launcher3.reflection;

import android.os.Bundle;
import android.content.Intent;
import android.app.PendingIntent;
import java.util.concurrent.CountDownLatch;
import android.util.MutableLong;

final class o implements PendingIntent.OnFinished
{
    final /* synthetic */ c bi;
    final /* synthetic */ MutableLong bj;
    final /* synthetic */ CountDownLatch bk;

    o(final c bi, final MutableLong bj, final CountDownLatch bk) {
        this.bi = bi;
        this.bj = bj;
        this.bk = bk;
    }

    public void onSendFinished(final PendingIntent pendingIntent, final Intent intent, final int n, final String s, final Bundle bundle) {
        this.bj.value = intent.getLongExtra("time", this.bj.value);
        this.bk.countDown();
    }
}
