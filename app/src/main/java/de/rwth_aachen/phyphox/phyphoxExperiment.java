package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

//This class holds all the information that makes up an experiment
//There are also some functions that the experiment should perform
public class phyphoxExperiment {
    boolean loaded = false; //Set to true if this instance holds a successfully loaded experiment
    boolean isLocal; //Set to true if this experiment was loaded from a local file. (if false, the experiment can be added to the library)
    String message = ""; //Holds error messages
    String title = ""; //The title of this experiment
    String category = ""; //The category of this experiment
    String icon = ""; //The icon. This is either a base64-encoded drawable (typically png) or (if its length is 3 or less characters) it is a short form which should be used in a simple generated logo (like "gyr" for gyroscope). (The experiment list will use the first three characters of the title if this is completely empty)
    String description = "There is no description available for this experiment."; //A long text, explaining details about the experiment
    public Vector<expView> experimentViews = new Vector<>(); //Instances of the experiment views (see expView.java) that define the views for this experiment
    public Vector<sensorInput> inputSensors = new Vector<>(); //Instances of sensorInputs (see sensorInput.java) which are used in this experiment
    public final Vector<dataBuffer> dataBuffers = new Vector<>(); //Instances of dataBuffers (see dataBuffer.java) that are used to store sensor data, analysis results etc.
    public final Map<String, Integer> dataMap = new HashMap<>(); //This maps key names (string) defined in the experiment-file to the index of a dataBuffer
    public Vector<Analysis.analysisModule> analysis = new Vector<>(); //Instances of analysisModules (see analysis.java) that define all the mathematical processes in this experiment

    double analysisPeriod = 0.; //Pause between analysis cycles. At 0 analysis is done as fast as possible.

    //Parameters for audio playback
    AudioTrack audioTrack = null; //Instance of AudioTrack class for playback. Not used if null
    String audioSource; //The key name of the buffer which holds the data that should be played back through the speaker.
    int audioRate = 48000; //The playback rate (in Hz)
    int audioBufferSize = 0; //The size of the audio buffer

    //Parameters for audio record
    AudioRecord audioRecord = null; //Instance of AudioRecord. Not used if null.
    String micOutput; //The key name of the buffer which receives the data from audio recording.
    int micRate = 48000; //The recording rate in Hz
    int micBufferSize = 0; //The size of the recording buffer

    public dataExport exporter; //An instance of the dataExport class for exporting functionality (see dataExport.java)

    //The constructor will just instantiate the dataExport. Everything else will be set directly by the phyphoxFile loading function (see phyphoxFile.java)
    phyphoxExperiment() {
        exporter = new dataExport(this);
    }

    //Helper function to get a dataBuffer by its key name
    public dataBuffer getBuffer(String key) {
        Integer index = this.dataMap.get(key);
        //Some sanity checks. To make this function more fail-safe
        if (index == null)
            return null;
        if (index >= dataBuffers.size())
            return null;

        return dataBuffers.get(index);
    }

    //Do the export using the dataExport class (see dataExport.java)
    public void export(Activity c) {
        exporter.export(c);
    }

    //This function gets called in the main loop and takes care of any inputElements in the current experiment view
    public void handleInputViews(int currentView) {
        for (dataBuffer buffer : dataBuffers) { //Compare each databuffer name...
            for (expView.expViewElement eve : experimentViews.elementAt(currentView).elements) { //...to each expViewElement
                try {
                    if (eve.getValueOutput() != null && eve.getValueOutput().equals(buffer.name)) { //if the buffer matches the expView's output buffer...
                        Double v = eve.getValue(); //...get the value
                        if (!Double.isNaN(v)) //Only send it to the buffer if it is valid
                            buffer.append(v);
                    }
                } catch (Exception e) {
                    Log.e("updateViews", "Unhandled exception in view module (input) " + eve.toString() + " while sending data.", e);
                }
            }
        }
    }

    //called by th main loop to initialize the analysis process
    public void processAnalysis() {
        //Get the data from the audio recording if used
        if (audioRecord != null) {
            audioRecord.stop(); //Stop the recording first
            int bytesRead = 1;
            short[] buffer = new short[1024];
            while (bytesRead > 0) {
                bytesRead = audioRecord.read(buffer, 0, 1024);
                dataBuffers.get(dataMap.get(micOutput)).append(buffer, bytesRead);
            }
        }

        //Call all the analysis modules and let them do their work.
        for (Analysis.analysisModule mod : analysis) {
            try {
                mod.updateIfNotStatic();
            } catch (Exception e) {
                Log.e("updateViews", "Unhandled exception in analysis module " + mod.toString() + ".", e);
            }
        }

        //Send the results to audio playback if used
        if (audioTrack != null) {
            if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED)
                audioTrack.stop(); //Stop the playback first
            if (!(audioTrack.getState() == AudioTrack.STATE_INITIALIZED && dataBuffers.get(dataMap.get(audioSource)).isStatic)) {
                //If the data is not static, or the audio track not yet initialized, we have to push our data to the audioTrack
                short[] data = dataBuffers.get(dataMap.get(audioSource)).getShortArray();
                int result = audioTrack.write(data, 0, audioBufferSize);
                if (result < audioBufferSize)
                    Log.d("updateViews", "Unexpected audio write result: " + result + " written / " + audioBufferSize + " buffer size");
                audioTrack.reloadStaticData();
            } else //If the data is static and already loaded, we just have to rewind...
                audioTrack.setPlaybackHeadPosition(0);
        }

        //Restart recording and playback if they are used.
        if (audioRecord != null) {
            audioRecord.startRecording();
        }
        if (audioTrack != null) {
            audioTrack.play();
        }
    }

    //called by the main loop after everything is processed. Here we have to send all the analysis results to the appropriate views
    public void updateViews(int currentView) {
        for (dataBuffer buffer : dataBuffers) { //Compare each buffer...
            for (expView.expViewElement eve : experimentViews.elementAt(currentView).elements) { //...to each view.
                try {
                    //Set single value if the input buffer of the view matches the dataBuffer
                    if (eve.getValueInput() != null && eve.getValueInput().equals(buffer.name)) {
                        eve.setValue(buffer.value);
                    }
                    //Set x array data if the input buffer of the view matches the dataBuffer
                    if (eve.getDataXInput() != null && eve.getDataXInput().equals(buffer.name)) {
                        eve.setDataX(buffer);
                    }
                    //Set y array data if the input buffer of the view matches the dataBuffer
                    if (eve.getDataYInput() != null && eve.getDataYInput().equals(buffer.name)) {
                        eve.setDataY(buffer);
                    }
                } catch (Exception e) {
                    Log.e("updateViews", "Unhandled exception in view module " + eve.toString() + " while sending data.", e);
                }
            }
        }
        //Finally call dataComplete on every view to notify them that the data has been sent
        for (expView.expViewElement eve : experimentViews.elementAt(currentView).elements) {
            try {
                eve.dataComplete();
            } catch (Exception e) {
                Log.e("updateViews", "Unhandled exception in view module " + eve.toString() + " on data completion.", e);
            }
        }
    }

    //Helper to stop all I/O of this experiment (i.e. when it should be stopped)
    public void stopAllIO() {
        //Recording
        if (audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED)
            audioRecord.stop();
        //Playback
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED)
            audioTrack.stop();
        //Sensors
        for (sensorInput sensor : inputSensors)
            sensor.stop();
    }

    //Helper to start all I/O of this experiment (i.e. when it should be started)
    public void startAllIO() {
        //We will not start audio recording and output here as it will be triggered by the analysis
        //  modules.
        long t0 = System.nanoTime();
        for (sensorInput sensor : inputSensors)
            sensor.start(t0);
    }
}
