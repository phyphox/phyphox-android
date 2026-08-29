package de.rwth_aachen.phyphox;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

//All access to the time mappings is serialized: they are written on the UI thread (start, pause,
//reset) while several background threads read them - the analysis thread, the webserver, the
//exporter and the network connections. The list itself is private, so no caller can iterate it
//while it is being modified; getTimeMappings() hands out an immutable snapshot instead.
public class ExperimentTimeReference implements Serializable {
    interface Listener {
        void onExperimentTimeReferenceUpdated(ExperimentTimeReference experimentTimeReference);
    }
    private Listener listener;

    public enum TimeMappingEvent {
        START, PAUSE, CLEAR //Note about clear: Since the timemapping list is also cleared in that case, clear is immediately removed after being added to the list. It is however transmitted to clients via the event BLE characteristic
    }

    public static class TimeMapping {
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

    private final List<TimeMapping> timeMappings = new LinkedList<>();

    ExperimentTimeReference(Listener listener) {
        this.listener = listener;
        reset();
    }

    //A snapshot for callers that need to look at all events. The TimeMapping objects are never
    //modified after they have been created, so the copy stays valid for the caller.
    public List<TimeMapping> getTimeMappings() {
        synchronized (this) {
            return Collections.unmodifiableList(new ArrayList<>(timeMappings));
        }
    }

    public synchronized boolean hasMappings() {
        return !timeMappings.isEmpty();
    }

    public synchronized TimeMapping getLastMapping() {
        if (timeMappings.isEmpty())
            return null;
        return timeMappings.get(timeMappings.size() - 1);
    }

    //Restores an event of an experiment state saved earlier (see the events block of a state file).
    public synchronized void addRestoredMapping(TimeMappingEvent event, Double experimentTime, long systemTime) {
        timeMappings.add(new TimeMapping(event, experimentTime, 0, systemTime));
    }

    public void logToDebug() {
        for (TimeMapping mapping : getTimeMappings()) {
            Log.d("TimeReference", mapping.event.name() + ": experiment time = " + mapping.experimentTime + ", event time = " + mapping.eventTime + ", system time = " + mapping.systemTime);
        }
        Log.d("TimeReference", "...");
    }

    public void registerEvent(TimeMappingEvent event) {
        long eventTime;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            eventTime = SystemClock.elapsedRealtimeNanos();
        } else {
            eventTime = SystemClock.elapsedRealtime() * 1000000L;
        }
        long systemTime = System.currentTimeMillis();

        //The listener is notified outside the lock: it walks the view elements, which read this
        //object back and take locks of their own.
        synchronized (this) {
            if (timeMappings.isEmpty()) {
                if (event != TimeMappingEvent.START)
                    return;
                timeMappings.add(new TimeMapping(event, 0.0, eventTime, systemTime));
            } else {
                TimeMapping last = timeMappings.get(timeMappings.size() - 1);
                switch (last.event) {
                    case START:
                        if (event == TimeMappingEvent.START)
                            return;
                        timeMappings.add(new TimeMapping(event, getExperimentTimeFromEvent(eventTime), eventTime, systemTime));
                        break;
                    case PAUSE:
                        if (event == TimeMappingEvent.PAUSE)
                            return;
                        timeMappings.add(new TimeMapping(event, last.experimentTime, eventTime, systemTime));
                        break;
                }
            }
        }
        if (listener != null)
            listener.onExperimentTimeReferenceUpdated(this);
    }

    public void reset() {
        synchronized (this) {
            timeMappings.clear();
        }
        if (listener != null)
            listener.onExperimentTimeReferenceUpdated(this);
    }

    public synchronized double getExperimentTimeFromEvent(long eventTime) {
        if (timeMappings.isEmpty())
            return 0.0;
        TimeMapping last = timeMappings.get(timeMappings.size()-1);
        if ((last.event == TimeMappingEvent.PAUSE) || (eventTime < last.eventTime))
            return last.experimentTime;
        return last.experimentTime + (eventTime - last.eventTime)*1e-9;
    }

    public double getExperimentTime() {
        long eventTime;
        eventTime = SystemClock.elapsedRealtimeNanos();
        return getExperimentTimeFromEvent(eventTime);
    }

    public synchronized double getLinearTime() {
        if (timeMappings.isEmpty())
            return 0.0;
        return (System.currentTimeMillis() - timeMappings.get(0).systemTime) * 0.001;
    }

    public synchronized int getReferenceIndexFromExperimentTime(double t) {
        int i = 0;
        while (timeMappings.size() > i+1 && timeMappings.get(i+1).experimentTime <= t)
            i++;
        return i;
    }

    public synchronized int getReferenceIndexFromLinearTime(double t) {
        int i = 0;
        while (timeMappings.size() > i+1 && (timeMappings.get(i+1).systemTime - timeMappings.get(0).systemTime) * 0.001 <= t) {
            i++;
        }
        return i;
    }

    //An index is usually retrieved from one of the methods above and used in a separate call, so
    //the list may have been reset in between. Out-of-range indices give the same answer as an
    //empty list instead of throwing.
    public synchronized long getSystemTimeReferenceByIndex(int i) {
        if (i < 0 || i >= timeMappings.size())
            return 0;
        return timeMappings.get(i).systemTime;
    }

    //The same lookup for callers that need a usable timestamp rather than a stored one: before
    //the first start there is no mapping yet and the experiment time is exactly zero, so the
    //current time is the only answer that keeps "experiment time plus offset" a real timestamp.
    //This is what the timer module's offset1970 output promises (timer-offset1970-prestart,
    //decided 2026-08-24; iOS falls back to Date() for the same reason). Callers that read a
    //stored reference and take 0 as "nothing recorded yet" - the absolute time axis of a graph,
    //the export file name - keep using getSystemTimeReferenceByIndex.
    public synchronized long getSystemTimeReferenceByIndexOrNow(int i) {
        if (timeMappings.isEmpty())
            return System.currentTimeMillis();
        return getSystemTimeReferenceByIndex(i);
    }

    public synchronized boolean getPausedByIndex(int i) {
        if (i < 0 || i >= timeMappings.size())
            return true;
        return timeMappings.get(i).event == TimeMappingEvent.PAUSE;
    }

    public synchronized double getExperimentTimeReferenceByIndex(int i) {
        if (i < 0 || i >= timeMappings.size())
            return 0.0;
        return timeMappings.get(i).experimentTime;
    }
}
