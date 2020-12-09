package de.rwth_aachen.phyphox;

import android.os.Build;
import android.os.SystemClock;

import java.util.LinkedList;
import java.util.List;

public class ExperimentTimeReference {
    public enum TimeMappingEvent {
        EXPERIMENT_START, EXPERIMENT_PAUSE
    }

    public class TimeMapping {
        TimeMappingEvent event;
        Double experimentTime;
        long eventTime;
        long systemTime;

        TimeMapping(TimeMappingEvent event, Double experimentTime, long eventTime, long systemTime) {
            this.event = event;
            this.experimentTime = experimentTime;
            this.eventTime = eventTime;
            this.systemTime = systemTime;
        }
    }

    List<TimeMapping> timeMappings = new LinkedList<>();

    ExperimentTimeReference() {
        reset();
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
            if (event != TimeMappingEvent.EXPERIMENT_START)
                return;
            timeMappings.add(new TimeMapping(event, 0.0, eventTime, systemTime));
        } else {
            TimeMapping last = timeMappings.get(timeMappings.size()-1);
            switch (last.event) {
                case EXPERIMENT_START:
                    if (event != TimeMappingEvent.EXPERIMENT_PAUSE)
                        return;
                    timeMappings.add(new TimeMapping(event, getExperimentTimeFromEvent(eventTime), eventTime, systemTime));
                    break;
                case EXPERIMENT_PAUSE:
                    if (event != TimeMappingEvent.EXPERIMENT_START)
                        return;
                    timeMappings.add(new TimeMapping(event, last.experimentTime, eventTime, systemTime));
                    break;
            }
        }
    }

    public void reset() {
        timeMappings.clear();
    }

    public double getExperimentTimeFromEvent(long eventTime) {
        if (timeMappings.isEmpty())
            return 0.0;
        TimeMapping last = timeMappings.get(timeMappings.size()-1);
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
}
