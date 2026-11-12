package com.example.adblocker;

/**
 * Coalesces filter reload requests while a single background worker runs.
 * A forced refresh is never downgraded by later ordinary settings updates.
 *
 * The caller starts a worker only when request() returns true. That worker
 * drains next() until it returns null, which atomically releases ownership.
 */
final class FilterRefreshQueue {
    private boolean workerActive;
    private boolean pending;
    private boolean pendingForce;

    synchronized boolean request(boolean force) {
        pending = true;
        pendingForce |= force;
        if (workerActive) return false;
        workerActive = true;
        return true;
    }

    /** Null means there is no further work and a new worker may start. */
    synchronized Boolean next() {
        if (!pending) {
            workerActive = false;
            return null;
        }
        boolean force = pendingForce;
        pending = false;
        pendingForce = false;
        return force;
    }

    /** Reset only when a worker cannot start or the VPN is stopping. */
    synchronized void reset() {
        workerActive = false;
        pending = false;
        pendingForce = false;
    }
}
