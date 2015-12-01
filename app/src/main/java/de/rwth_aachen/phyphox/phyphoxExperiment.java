package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
    public Lock dataLock = new ReentrantLock();

    double analysisPeriod = 0.; //Pause between analysis cycles. At 0 analysis is done as fast as possible.
    long lastAnalysis = 0; //This variable holds the system time of the moment the last analysis process finished. This is necessary for experiments, which do analysis after given intervals
    boolean analysisOnUserInput = false; //Do the data analysis only if there is fresh input from the user.
    boolean newUserInput = false; //Will be set to true if the user changed any values
    boolean newData = true; //Will be set to true if we have fresh data to present

    //Parameters for audio playback
    AudioTrack audioTrack = null; //Instance of AudioTrack class for playback. Not used if null
    String audioSource; //The key name of the buffer which holds the data that should be played back through the speaker.
    int audioRate = 48000; //The playback rate (in Hz)
    int audioBufferSize = 0; //The size of the audio buffer
    boolean audioLoop = false; //Loop the track?

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

    //Create a new buffer
    public dataBuffer createBuffer(String key, int size) {
        if (key == null)
            return null;

        dataBuffer output = new dataBuffer(key, size);
        dataBuffers.add(output);
        dataMap.put(key, dataBuffers.size() - 1);
        return output;
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
        if (!loaded)
            return;
        if (dataLock.tryLock()) {
            try {
                for (dataBuffer buffer : dataBuffers) { //Compare each databuffer name...
                    for (expView.expViewElement eve : experimentViews.elementAt(currentView).elements) { //...to each expViewElement
                        try {
                            if (eve.getValueOutput() != null && eve.getValueOutput().equals(buffer.name)) { //if the buffer matches the expView's output buffer...
                                Double v = eve.getValue(); //...get the value
                                if (!Double.isNaN(v) && buffer.value != v) { //Only send it to the buffer if it is valid and a new value
                                    buffer.append(v);
                                    newUserInput = true;
                                }
                            }
                        } catch (Exception e) {
                            Log.e("handleInputViews", "Unhandled exception in view module (input) " + eve.toString() + " while sending data.", e);
                        }
                    }
                }
            } finally {
                dataLock.unlock();
            }
        } else
            return; //This is not urgent. Try another time instead of blocking the UI thread
    }

    //called by th main loop to initialize the analysis process
    public void processAnalysis() {
        if (!loaded)
            return;

        //Get the data from the audio recording if used
        if (audioRecord != null) {
            dataBuffer recording = getBuffer(micOutput);
            final int readBufferSize = recording.size; //The dataBuffer for the recording
            dataLock.lock();
            try {
                short[] buffer = new short[readBufferSize]; //The temporary buffer to read to
                recording.clear(); //We only want fresh data
                int bytesRead = audioRecord.read(buffer, 0, recording.size);
                recording.append(buffer, bytesRead);
            } finally {
                dataLock.unlock();
            }
        }

        //Check if the actual math should be done
        if (analysisOnUserInput) {
            //If set by the experiment, the analysis is only done when there is new input from the user
            if (!newUserInput) {
                return; //No new input. Nothing to do.
            }
        } else if (System.currentTimeMillis() - lastAnalysis <= analysisPeriod * 1000) {
            //This is the default: The analysis is done periodically. Either as fast as possible or after a period defined by the experiment
            return; //Too soon. Nothing to do
        }

        newUserInput = false;

        dataLock.lock();
        //Call all the analysis modules and let them do their work.
        try {
            for (Analysis.analysisModule mod : analysis) {
                try {
                    Thread.yield();
                    mod.updateIfNotStatic();
                } catch (Exception e) {
                    Log.e("processAnalysis", "Unhandled exception in analysis module " + mod.toString() + ".", e);
                }
            }
        } finally {
            dataLock.unlock();
        }

        //Send the results to audio playback if used
        if (audioTrack != null) {
            if (!(audioTrack.getState() == AudioTrack.STATE_INITIALIZED && getBuffer(audioSource).isStatic)) {
                //If the data is not static, or the audio track not yet initialized, we have to push our data to the audioTrack

                if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED)
                    audioTrack.pause(); //Stop the playback first
                audioTrack.flush(); //Empty the buffer


                //Get the data to output
                short[] data = getBuffer(audioSource).getShortArray();
                int result = 0; //Will hold the write result to log errors
                if (audioLoop) {
                    //In case of loops we want to repeat the data buffer. However, some
                    //  implementations do not allow short loops. So as a workaround we fill the
                    //  whole audio buffer with the repeated data buffer, so if the device does not
                    //  accept the loop period, we still have some looping. (Unfortunately, there
                    //  will be a discontinuity as the full buffer most likely will not have a
                    //  length that is a multiple of the data buffer.)
                    short[] filledBuffer = new short[audioBufferSize];
                    for (int i = 0; i < audioBufferSize; i++)
                        filledBuffer[i] = data[i % data.length];
                    result = audioTrack.write(filledBuffer, 0, audioBufferSize);
                } else //Usually, just write the small buffer...
                    result = audioTrack.write(data, 0, data.length);

                if (result <= 0)
                    Log.e("processAnalysis", "Unexpected audio write result: " + result + " written / " + audioBufferSize + " buffer size");

                audioTrack.reloadStaticData();
                if (audioLoop) //If looping is enabled, loop from the end of the data
                    audioTrack.setLoopPoints(0, data.length-1, -1);
            } else { //If the data is static and already loaded, we just have to rewind...
                if (!audioLoop) { //We only want to play again since we are not looping
                    audioTrack.stop();
                    audioTrack.reloadStaticData();
                }
            }
        }

        //Restart if used.
        if (audioTrack != null) {
            audioTrack.play();
        }

        newData = true; //We have fresh data to present.
        lastAnalysis = System.currentTimeMillis(); //Remember when we were done this time
    }

    //called by the main loop after everything is processed. Here we have to send all the analysis results to the appropriate views
    public void updateViews(int currentView, boolean force) {
        if (!loaded)
            return;
        if (!(newData || force)) //New data to present? If not: Nothing to do, unless an update is forced
            return;
        if (dataLock.tryLock()) {
            try {
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
            } finally {
                dataLock.unlock();
            }
        } else
            return; //This is not urgent. Try another time instead of blocking the UI thread!
        newData = false;
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
        if (!loaded)
            return;
        //Recording
        if (audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED)
            audioRecord.stop();
        //Playback
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            audioTrack.pause();
        }
        //Sensors
        for (sensorInput sensor : inputSensors)
            sensor.stop();
    }

    //Helper to start all I/O of this experiment (i.e. when it should be started)
    public void startAllIO() {
        if (!loaded)
            return;

        newUserInput = true; //Set this to true to execute analysis at least ones with default values.
        for (sensorInput sensor : inputSensors)
            sensor.start();

        //Audio Recording
        if (audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED)
            audioRecord.startRecording();

        //We will not start audio output here as it will be triggered by the analysis modules.
    }
}
