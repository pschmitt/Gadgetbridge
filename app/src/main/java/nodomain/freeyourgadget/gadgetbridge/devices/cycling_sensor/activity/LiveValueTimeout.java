package nodomain.freeyourgadget.gadgetbridge.devices.cycling_sensor.activity;

/**
 * Remembers when a live value last arrived. A sensor that stops notifying, for example one that
 * sleeps while the bike stands still, sends nothing more, so without this its last speed or
 * cadence would stay on screen.
 */
class LiveValueTimeout {
    static final long STALE_MILLIS = 5_000;

    private long lastUpdateMillis = 0;

    void onUpdate(long nowMillis) {
        lastUpdateMillis = nowMillis;
    }

    /**
     * Returns true once when no update arrived for {@link #STALE_MILLIS}, then false until the
     * next update, so the caller resets the value a single time.
     */
    boolean expire(long nowMillis) {
        if (lastUpdateMillis == 0 || nowMillis - lastUpdateMillis <= STALE_MILLIS) {
            return false;
        }
        lastUpdateMillis = 0;
        return true;
    }
}
