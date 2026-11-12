package com.example.adblocker;

/**
 * Collect network-change requests while a single DNS recovery worker probes.
 * Unlike a busy flag, the latest change is replayed after the current probe,
 * including a null preference meaning "re-evaluate all underlying networks".
 */
final class ResolverRefreshQueue<T> {
    static final class Request<T> {
        final T preferred;
        private Request(T preferred) { this.preferred = preferred; }
    }

    private boolean workerActive;
    private boolean pending;
    private T preferred;

    /** Returns true only to the caller responsible for starting the worker. */
    synchronized boolean request(T nextPreferred) {
        preferred = nextPreferred;
        pending = true;
        if (workerActive) return false;
        workerActive = true;
        return true;
    }

    /** Returns null only when idle, atomically allowing a new worker to start. */
    synchronized Request<T> next() {
        if (!pending) {
            workerActive = false;
            return null;
        }
        T nextPreferred = preferred;
        preferred = null;
        pending = false;
        return new Request<>(nextPreferred);
    }

    synchronized boolean hasPending() {
        return pending;
    }

    /** Only for shutdown or failure to launch the worker. */
    synchronized void reset() {
        workerActive = false;
        pending = false;
        preferred = null;
    }
}
