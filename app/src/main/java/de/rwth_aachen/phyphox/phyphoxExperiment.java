package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;


public class phyphoxExperiment {
    boolean loaded = false;
    boolean isLocal;
    String message = "";
    String title = "";
    String category = "";
    String icon = "";
    String description = "There is no description available for this experiment.";
    public Vector<expView> experimentViews = new Vector<>();
    public Vector<sensorInput> inputSensors = new Vector<>();
    public final Vector<dataBuffer> dataBuffers = new Vector<>();
    public final Map<String, Integer> dataMap = new HashMap<>();
    public Vector<Analysis.analysisModule> analysis = new Vector<>();

    double analysisPeriod = 0.;

    AudioTrack audioTrack = null;
    String audioSource;
    int audioRate = 48000;
    int audioBufferSize = 0;
    AudioRecord audioRecord = null;
    String micOutput;
    int micRate = 48000;
    int micBufferSize = 0;

    public dataExport exporter;

    phyphoxExperiment() {
        exporter = new dataExport(this);
    }

    public dataBuffer getBuffer(String key) {
        Integer index = this.dataMap.get(key);
        if (index == null)
            return null;
        if (index >= dataBuffers.size())
            return null;
        return dataBuffers.get(index);
    }

    public void export(Activity c) {
        exporter.export(c);
    }

    public void handleInputViews(int currentView) {
        for (dataBuffer buffer : dataBuffers) {
            for (expView.expViewElement eve : experimentViews.elementAt(currentView).elements) {
                try {
                    if (eve.getValueOutput() != null && eve.getValueOutput().equals(buffer.name)) {
                        Double v = eve.getValue();
                        if (!Double.isNaN(v))
                            buffer.append(v);
                    }
                } catch (Exception e) {
                    Log.e("updateViews", "Unhandled exception in view module (input) " + eve.toString() + " while sending data.", e);
                }
            }
        }
    }

    public void processAnalysis() {
        if (audioRecord != null) {
            audioRecord.stop();
            int bytesRead = 1;
            short[] buffer = new short[1024];
            while (bytesRead > 0) {
                bytesRead = audioRecord.read(buffer, 0, 1024);
                dataBuffers.get(dataMap.get(micOutput)).append(buffer, bytesRead);
            }
        }
        for (Analysis.analysisModule mod : analysis) {
            try {
                mod.updateIfNotStatic();
            } catch (Exception e) {
                Log.e("updateViews", "Unhandled exception in analysis module " + mod.toString() + ".", e);
            }
        }
        if (audioTrack != null) {
            if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED)
                audioTrack.stop();
            if (!(audioTrack.getState() == AudioTrack.STATE_INITIALIZED && dataBuffers.get(dataMap.get(audioSource)).isStatic)) {
                short[] data = dataBuffers.get(dataMap.get(audioSource)).getShortArray();
                int result = audioTrack.write(data, 0, audioBufferSize);
                if (result < audioBufferSize)
                    Log.d("updateViews", "Unexpected audio write result: " + result + " written / " + audioBufferSize + " buffer size");
                audioTrack.reloadStaticData();
            } else
                audioTrack.setPlaybackHeadPosition(0);
        }
        if (audioRecord != null) {
            audioRecord.startRecording();
        }
        if (audioTrack != null) {
            audioTrack.play();
        }
    }

    public void updateViews(int currentView) {
        for (dataBuffer buffer : dataBuffers) {
            for (expView.expViewElement eve : experimentViews.elementAt(currentView).elements) {
                try {
                    if (eve.getValueInput() != null && eve.getValueInput().equals(buffer.name)) {
                        eve.setValue(buffer.value);
                    }
                    if (eve.getDataXInput() != null && eve.getDataXInput().equals(buffer.name)) {
                        eve.setDataX(buffer);
                    }
                    if (eve.getDataYInput() != null && eve.getDataYInput().equals(buffer.name)) {
                        eve.setDataY(buffer);
                    }
                } catch (Exception e) {
                    Log.e("updateViews", "Unhandled exception in view module " + eve.toString() + " while sending data.", e);
                }
            }
        }
        for (expView.expViewElement eve : experimentViews.elementAt(currentView).elements) {
            try {
                eve.dataComplete();
            } catch (Exception e) {
                Log.e("updateViews", "Unhandled exception in view module " + eve.toString() + " on data completion.", e);
            }
        }
    }
}
