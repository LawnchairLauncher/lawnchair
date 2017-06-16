package com.android.launcher3.reflection.layers;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class e
{
    static e ML;
    public static boolean MM;
    private int MI;
    private ExecutorService MJ;
    private boolean MK;
    private int MN;

    static {
        e.MM = false;
    }

    private e() {
        this.MK = false;
        this.MI = Runtime.getRuntime().availableProcessors() / 2;
        this.MJ = Executors.newFixedThreadPool(this.MI);
    }

    private void TJ(final int n, final c c) {
        int i = 1;
        // monitorenter(this)
        final boolean mk = true;
        try {
            this.MK = mk;
            final ExecutorCompletionService executorCompletionService = new ExecutorCompletionService(this.MJ);
            int n2;
            if (n >= this.MI) {
                i = (n2 = (int)Math.ceil(n / this.MI));
            }
            else {
                n2 = i;
            }
            i = this.MI;
            i = Math.min(i, n);
            this.MN = i;
            for (i = 0; i < this.MN; ++i) {
                executorCompletionService.submit(new n(i, n2, n, c));
            }
            i = 0;
            while (i < this.MN) {
                try {
                    final Future take = executorCompletionService.take();
                    try {
                        take.get();
                        ++i;
                    }
                    catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                    catch (ExecutionException ex2) {
                        System.err.println(new StringBuilder(48).append("threadCount: ").append(this.MN).append(" for length: ").append(n).toString());
                        ex2.printStackTrace();
                    }
                    finally {
                    }
                    // monitorexit(this)
                }
                catch (InterruptedException ex3) {}
            }
            this.MK = false;
        }
        finally {}
    }

    public static e getInstance() {
        if (e.ML == null) {
            e.ML = new e();
        }
        return e.ML;
    }

    public void TI(final int n, final c c) {
        int i = 0;
        if (e.MM && !this.MK && n != 1) {
            this.TJ(n, c);
        }
        else {
            while (i < n) {
                try {
                    c.TH(i);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
                ++i;
            }
        }
    }
}