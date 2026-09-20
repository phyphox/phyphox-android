package de.rwth_aachen.phyphox;

public class ExperimentTimeReferenceSet {
    public int index;
    public int count;
    public int referenceIndex;
    public double experimentTime;
    public long systemTime;
    public boolean isPaused;

    public ExperimentTimeReferenceSet(int index, int count, double experimentTime, long systemTime, int referenceIndex, boolean isPaused) {
        this.index = index;
        this.count = count;
        this.experimentTime = experimentTime;
        this.systemTime = systemTime;
        this.referenceIndex = referenceIndex;
        this.isPaused = isPaused;
    }
}
