# Android Quick Settings protection tile

The Quick Settings tile is user initiated: expand the device's Quick Settings,
tap edit, and add **DNS protection** from the available tile list.

Tapping the tile opens MainActivity, which handles VPN consent, notification
permission prompting, and foreground service start/stop. On Android 14+ the
tile uses a PendingIntent, as required for startActivityAndCollapse. The VPN
does **not** start in the background merely from a tile service callback.

The tile is marked ACTIVE_TILE and is refreshed on protection state changes.
If protection cannot establish a DNS resolver, the tile returns to Off.

Required physical-device tests: add/remove tile, locked/unlocked tap, cancel
VPN consent, start, stop, repeated rapid taps, rotation, process death,
Android 8/9, 13, 14/15, and 16. The existing emulator launch check doesn't
assert the tile panel UI or VPN permissions.
