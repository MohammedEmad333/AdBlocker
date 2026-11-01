package com.example.adblocker; // change to match your package

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public final class NotificationHelper {

    public static final String CHANNEL_ID      = "adblock_vpn";
    public static final int    NOTIFICATION_ID = 1;

    private NotificationHelper() {}

    /** Call once on service create -- safe to call repeatedly, it's idempotent. */
    public static void createChannel(Context ctx) {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW); // silent -- no sound, no badge
        ch.setDescription(ctx.getString(R.string.notification_description));
        ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    /**
     * @param loading true while the blocklist is still downloading -- shows
     *                "Loading blocklist…" instead of the blocked count.
     */
    public static Notification build(Context ctx, int blocked, boolean loading) {
        // Tap the notification -> open the app.
        PendingIntent openApp = PendingIntent.getActivity(
                ctx, 0,
                new Intent(ctx, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // "Stop" action -- works without opening the app.
        PendingIntent stopAction = PendingIntent.getService(
                ctx, 1,
                new Intent(ctx, AdBlockVpnService.class)
                        .setAction(AdBlockVpnService.ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String body = loading
                ? ctx.getString(R.string.notification_loading)
                : ctx.getString(R.string.notification_blocked_count, blocked);

        return new Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_shield)
                .setContentTitle(ctx.getString(R.string.notification_title))
                .setContentText(body)
                .setContentIntent(openApp)
                .addAction(0, ctx.getString(R.string.notification_stop), stopAction)
                .setOngoing(true) // user can't swipe it away
                .build();
    }

    /** Push an updated count to the existing notification without rebuilding it. */
    public static void update(Context ctx, int blocked) {
        // Foreground VPN can run when notification permission is declined,
        // but explicit notify() calls require this permission on API 33+.
        if (Build.VERSION.SDK_INT >= 33
                && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) return;
        try {
            ctx.getSystemService(NotificationManager.class)
                    .notify(NOTIFICATION_ID, build(ctx, blocked, false));
        } catch (SecurityException ignored) {
            // Permission can be revoked while protection is running.
        }
    }
}
