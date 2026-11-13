package com.example.adblocker;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * User-invoked Quick Settings shortcut. The activity owns VPN consent and
 * foreground-service startup; this tile never starts the VPN in background.
 */
public final class AdBlockQuickTile extends TileService {
    public static final String ACTION_TOGGLE_FROM_TILE =
            "com.example.adblocker.TOGGLE_FROM_TILE";

    @Override public void onStartListening() {
        super.onStartListening();
        refreshTile();
    }

    @Override public void onClick() {
        super.onClick();
        if (isLocked()) {
            unlockAndRun(this::launchDashboard);
        } else {
            launchDashboard();
        }
    }

    // Required on API 24-33; the PendingIntent replacement needs API 34.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private void launchDashboard() {
        Intent launch = new Intent(this, MainActivity.class)
                .setAction(ACTION_TOGGLE_FROM_TILE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pending = PendingIntent.getActivity(this, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pending);
        } else {
            // Android 8-13 supports the Intent-based user-initiated API.
            startActivityAndCollapse(launch);
        }
    }

    private void refreshTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean enabled = AdBlockVpnService.isRunning;
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.quick_tile_label));
        if (Build.VERSION.SDK_INT >= 29) {
            tile.setSubtitle(getString(enabled
                    ? R.string.quick_tile_on : R.string.quick_tile_off));
        }
        tile.updateTile();
    }

    /** Ask Android to refresh an active tile after VPN state changes. */
    public static void requestUpdate(Context context) {
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                requestListeningState(context,
                        new android.content.ComponentName(context, AdBlockQuickTile.class));
            } catch (RuntimeException ignored) {
                // The tile may not have been added to Quick Settings yet.
            }
        }
    }
}
