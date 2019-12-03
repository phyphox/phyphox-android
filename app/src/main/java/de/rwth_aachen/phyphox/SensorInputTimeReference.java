package de.rwth_aachen.phyphox;

public class SensorInputTimeReference {
    private long t0 = 0;
    private boolean valid = true;

    public void set(long t0) {
        this.t0 = t0;
        valid = true;
    }

    public void reset() {
        valid = false;
    }

    public long get() {
        return t0;
    }

    public boolean isValid() {
        return valid;
    }
}
