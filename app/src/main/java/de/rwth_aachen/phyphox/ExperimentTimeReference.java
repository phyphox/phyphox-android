package de.rwth_aachen.phyphox;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;

public class ExperimentTimeReference {
    interface Listener {
        void onExperimentTimeReferenceUpdated(ExperimentTimeReference experimentTimeReference);
    }
    private Listener listener;

    public enum TimeMappingEvent {
        START, PAUSE
    }

    public class TimeMapping {
        public TimeMappingEvent event;
        public Double experimentTime;
        public long eventTime;
        public long systemTime;

        TimeMapping(TimeMappingEvent event, Double experimentTime, long eventTime, long systemTime) {
            this.event = event;
            this.experimentTime = experimentTime;
            this.eventTime = eventTime;
            this.systemTime = systemTime;
        }
    }

    public List<TimeMapping> timeMappings = new LinkedList<>();

    ExperimentTimeReference(Listener listener) {
        this.listener = listener;
        reset();
    }

    public void logToDebug() {
        for (TimeMapping mapping : timeMappings) {
            Log.d("TimeReference", mapping.event.name() + ": experiment time = " + mapping.experimentTime + ", event time = " + mapping.eventTime + ", system time = " + mapping.systemTime);
        }
    }

    public void registerEvent(TimeMappingEvent event) {
        long eventTime;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            eventTime = SystemClock.elapsedRealtimeNanos();
        } else {
            eventTime = SystemClock.elapsedRealtime() * 1000000L;
        }
        long systemTime = System.currentTimeMillis();

        if (timeMappings.isEmpty()) {
            if (event != TimeMappingEvent.START)
                return;
            timeMappings.add(new TimeMapping(event, 0.0, eventTime, systemTime));
        } else {
            TimeMapping last = timeMappings.get(timeMappings.size()-1);
            switch (last.event) {
                case START:
                    if (event != TimeMappingEvent.PAUSE)
                        return;
                    timeMappings.add(new TimeMapping(event, getExperimentTimeFromEvent(eventTime), eventTime, systemTime));
                    break;
                case PAUSE:
                    if (event != TimeMappingEvent.START)
                        return;
                    timeMappings.add(new TimeMapping(event, last.experimentTime, eventTime, systemTime));
                    break;
            }
        }
        if (listener != null)
            listener.onExperimentTimeReferenceUpdated(this);
    }

    public void reset() {
        timeMappings.clear();
        if (listener != null)
            listener.onExperimentTimeReferenceUpdated(this);
    }

    public double getExperimentTimeFromEvent(long eventTime) {
        if (timeMappings.isEmpty())
            return 0.0;
        TimeMapping last = timeMappings.get(timeMappings.size()-1);
        if ((last.event == TimeMappingEvent.PAUSE) || (eventTime < last.eventTime))
            return last.experimentTime;
        return last.experimentTime + (eventTime - last.eventTime)*1e-9;
    }

    public double getExperimentTime() {
        long eventTime;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            eventTime = SystemClock.elapsedRealtimeNanos();
        } else {
            eventTime = SystemClock.elapsedRealtime() * 1000000L;
        }
        return getExperimentTimeFromEvent(eventTime);
    }

    public double getLinearTime() {
        if (timeMappings.isEmpty())
            return 0.0;
        return (System.currentTimeMillis() - timeMappings.get(0).systemTime) * 0.001;
    }

    public int getReferenceIndexFromExperimentTime(double t) {
        int i = 0;
        while (timeMappings.size() > i+1 && timeMappings.get(i+1).experimentTime <= t)
            i++;
        return i;
    }

    public int getReferenceIndexFromLinearTime(double t) {
        int i = 0;
        while (timeMappings.size() > i+1 && (timeMappings.get(i+1).systemTime - timeMappings.get(0).systemTime) * 0.001 <= t) {
            i++;
        }
        return i;
    }

    public long getSystemTimeReferenceByIndex(int i) {
        if (timeMappings.isEmpty())
            return 0;
        return timeMappings.get(i).systemTime;
    }

    public boolean getPausedByIndex(int i) {
        if (timeMappings.isEmpty())
            return true;
        return timeMappings.get(i).event == TimeMappingEvent.PAUSE;
    }

    public double getExperimentTimeReferenceByIndex(int i) {
        if (timeMappings.isEmpty())
            return 0.0;
        return timeMappings.get(i).experimentTime;
    }
}
