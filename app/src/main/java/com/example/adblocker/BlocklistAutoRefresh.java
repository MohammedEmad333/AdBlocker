package com.example.adblocker;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

/** Opt-in, inexact and network-constrained background list refresh scheduling. */
final class BlocklistAutoRefresh {
    private static final String TAG = "AdBlockAutoRefresh";
    static final int JOB_ID = 180001;

    private BlocklistAutoRefresh() {}

    /**
     * Reconfigure after user settings change; otherwise keep an existing job
     * to avoid resetting its countdown each time the activity opens.
     */
    static boolean sync(Context context, boolean changed) {
        Context app = context.getApplicationContext();
        JobScheduler scheduler =
                (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return false;
        FilterSettings settings = new FilterSettings(app);
        try {
            if (!settings.autoRefreshEnabled()) {
                scheduler.cancel(JOB_ID);
                return true;
            }
            if (!changed && scheduler.getPendingJob(JOB_ID) != null) return true;
            long interval = AutoRefreshPolicy.intervalMs(settings.autoRefreshFrequency());
            JobInfo info = new JobInfo.Builder(JOB_ID,
                    new ComponentName(app, BlocklistRefreshJobService.class))
                    .setPersisted(true)
                    .setPeriodic(interval)
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(settings.autoRefreshUnmeteredOnly()
                            ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY)
                    .build();
            return scheduler.schedule(info) == JobScheduler.RESULT_SUCCESS;
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not schedule background list refresh", error);
            return false;
        }
    }
}
