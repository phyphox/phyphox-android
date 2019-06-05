package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

//This class holds all the information that makes up an experiment
//There are also some functions that the experiment should perform
public class phyphoxExperiment implements Serializable {
    boolean loaded = false; //Set to true if this instance holds a successfully loaded experiment
    boolean isLocal; //Set to true if this experiment was loaded from a local file. (if false, the experiment can be added to the library)
    byte[] source = null; //This holds the original source file
    String message = ""; //Holds error messages
    String title = ""; //The title of this experiment
    String stateTitle = ""; //The title of this experiment
    String category = ""; //The category of this experiment
    String icon = ""; //The icon. This is either a base64-encoded drawable (typically png) or (if its length is 3 or less characters) it is a short form which should be used in a simple generated logo (like "gyr" for gyroscope). (The experiment list will use the first three characters of the title if this is completely empty)
    String description = "There is no description available for this experiment."; //A long text, explaining details about the experiment
    public Map<String, String> links = new LinkedHashMap<>(); //This contains links to external documentation or similar stuff
    public Map<String, String> highlightedLinks = new LinkedHashMap<>(); //This contains highlighted (= showing up in the menu) links to external documentation or similar stuff
    public Vector<expView> experimentViews = new Vector<>(); //Instances of the experiment views (see expView.java) that define the views for this experiment
    public Vector<sensorInput> inputSensors = new Vector<>(); //Instances of sensorInputs (see sensorInput.java) which are used in this experiment
    public gpsInput gpsIn = null;
    public Vector<BluetoothInput> bluetoothInputs = new Vector<>(); //Instances of bluetoothInputs (see sensorInput.java) which are used in this experiment
    public Vector<BluetoothOutput> bluetoothOutputs = new Vector<>(); //Instances of bluetoothOutputs (see sensorInput.java) which are used in this experiment
    public final Vector<dataBuffer> dataBuffers = new Vector<>(); //Instances of dataBuffers (see dataBuffer.java) that are used to store sensor data, analysis results etc.
    public final Map<String, Integer> dataMap = new HashMap<>(); //This maps key names (string) defined in the experiment-file to the index of a dataBuffer
    public Vector<Analysis.analysisModule> analysis = new Vector<>(); //Instances of analysisModules (see analysis.java) that define all the mathematical processes in this experiment
    public Lock dataLock = new ReentrantLock();

    double analysisSleep = 0.; //Pause between analysis cycles. At 0 analysis is done as fast as possible.
    dataBuffer analysisDynamicSleep = null;
    long lastAnalysis = 0; //This variable holds the system time of the moment the last analysis process finished. This is necessary for experiments, which do analysis after given intervals
    long analysisTime; //This variable holds the system time of the moment the current analysis process started.
    long firstAnalysisTime = 0; //This variable holds the system time of the moment the first analysis process started.
    boolean analysisOnUserInput = false; //Do the data analysis only if there is fresh input from the user.
    boolean optimization = false; //Modules are only executed if inputs or outputs have changed. Default to false because in some cases this breaks the logic of the experiment. (Example: If the currect data of sources at different acquisition rates should be appended periodically (for example with a timer module), not alle append modules will be executed for slow changing values as both input and output my be unchanged.)
    boolean newUserInput = true; //Will be set to true if the user changed any values
    boolean newData = true; //Will be set to true if we have fresh data to present
    boolean recordingUsed = true; //This keeps track, whether the recorded data has been used, so the next call reading from the mic can clear the old data first

    //Parameters for audio playback
    transient AudioTrack audioTrack = null; //Instance of AudioTrack class for playback. Not used if null
    String audioSource; //The key name of the buffer which holds the data that should be played back through the speaker.
    int audioRate = 48000; //The playback rate (in Hz)
    int audioBufferSize = 0; //The size of the audio buffer
    boolean audioLoop = false; //Loop the track?

    //Parameters for audio record
    transient AudioRecord audioRecord = null; //Instance of AudioRecord. Not used if null.
    String micOutput; //The key name of the buffer which receives the data from audio recording.
    String micRateOutput; //The key name of the buffer which receives the sample rate of audio recording.
    int micRate = 48000; //The recording rate in Hz
    int micBufferSize = 0; //The size of the recording buffer
    int minBufferSize = 0; //The minimum buffer size requested by the device

    public DataExport exporter; //An instance of the DataExport class for exporting functionality (see DataExport.java)

    //The constructor will just instantiate the DataExport. Everything else will be set directly by the phyphoxFile loading function (see phyphoxFile.java)
    phyphoxExperiment() {
        exporter = new DataExport(this);
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

    //Do the export using the DataExport class (see DataExport.java)
    public void export(Activity c) {
        exporter.export(c, false);
    }

    //This function gets called in the main loop and takes care of any inputElements in the current experiment view
    public void handleInputViews(int currentView, boolean measuring) {
        if (!loaded)
            return;
        if (dataLock.tryLock()) {
            try {
                for (dataBuffer buffer : dataBuffers) { //Compare each databuffer name...
                    for (expView ev : experimentViews) {
                            for (expView.expViewElement eve : ev.elements) { //...to each expViewElement
                                try {
                                    if (eve.onMayWriteToBuffers()) //The element may now write to its buffers if it wants to do it on its own...
                                        newUserInput = true;
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
                }
            } finally {
                dataLock.unlock();
            }
        }
        //else: Lock not aquired, but this is not urgent. Try another time instead of blocking the UI thread
        if (newUserInput && !measuring)
            processAnalysis(false);
    }

    //called by th main loop to initialize the analysis process
    public void processAnalysis(boolean measuring) {
        if (!loaded)
            return;

        //Get the data from the audio recording if used
        if (audioRecord != null && measuring) {
            dataBuffer sampleRateBuffer = null;
            if (!micRateOutput.isEmpty())
                sampleRateBuffer = getBuffer(micRateOutput);
            dataBuffer recording = getBuffer(micOutput);
            final int readBufferSize = Math.max(Math.min(recording.size, 4800),minBufferSize); //The dataBuffer for the recording
            short[] buffer = new short[readBufferSize]; //The temporary buffer to read to
            int bytesRead = audioRecord.read(buffer, 0, readBufferSize);
            if (lastAnalysis != 0) { //The first recording data does not make sense, but we had to read it to clear the recording buffer...
                dataLock.lock();
                try {
                    if (recordingUsed) {
                        recording.clear(false); //We only want fresh data
                        if (sampleRateBuffer != null)
                            sampleRateBuffer.append(audioRecord.getSampleRate());
                        recordingUsed = false;
                    }
                    recording.append(buffer, bytesRead);
                } finally {
                    dataLock.unlock();
                }
            } else {
                //Even if we do not use the first recording, we write the audio rate so it is available early.
                dataLock.lock();
                try {
                    if (sampleRateBuffer != null)
                        sampleRateBuffer.append(audioRecord.getSampleRate());
                } finally {
                    dataLock.unlock();
                }
            }
        }

        Double sleep = analysisSleep;
        if (analysisDynamicSleep != null && !Double.isNaN(analysisDynamicSleep.value) && !Double.isInfinite(analysisDynamicSleep.value)) {
            sleep = analysisDynamicSleep.value;
        }

        //Check if the actual math should be done
        if (analysisOnUserInput) {
            //If set by the experiment, the analysis is only done when there is new input from the user
            if (!newUserInput) {
                return; //No new input. Nothing to do.
            }
        } else if (System.currentTimeMillis() - lastAnalysis <= sleep * 1000) {
            //This is the default: The analysis is done periodically. Either as fast as possible or after a period defined by the experiment
            return; //Too soon. Nothing to do
        }
        newUserInput = false;

        dataLock.lock();
        analysisTime = System.currentTimeMillis();
        if (firstAnalysisTime == 0)
            firstAnalysisTime = analysisTime;
        //Call all the analysis modules and let them do their work.
        try {
            for (Analysis.analysisModule mod : analysis) {
                try {
                    Thread.yield();
                    if (!mod.updateIfNotStatic()) { //This only returns false if the module found its inputs to be uninitialized, in which case we abort the rest... (Typical example: no audio recorded yet)
//                        break; //Nope, let's not break... This should not be an issue anywhere but may prevent resets from a button to show up when experiment is stopped.
                    }
                } catch (Exception e) {
                    Log.e("processAnalysis", "Unhandled exception in analysis module " + mod.toString() + ".", e);
                }
            }
        } finally {
            dataLock.unlock();
        }

        //Send the results to audio playback if used
        if (audioTrack != null && measuring) {
            if (!(audioTrack.getState() == AudioTrack.STATE_INITIALIZED && getBuffer(audioSource).isStatic)) {
                //If the data is not static, or the audio track not yet initialized, we have to push our data to the audioTrack

                if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED)
                    audioTrack.pause(); //Stop the playback first


                //Get the data to output
                short[] data = getBuffer(audioSource).getShortArray();
                if (data.length > 0) {
                    int result; //Will hold the write result to log errors
                    int loopedBufferSize = 0;

                    if (audioLoop) {
                        //In case of loops we want to repeat the data buffer. However, some
                        //  implementations do not allow short loops. So as a workaround we increase
                        //  the size of the audio buffer to at least 1 second and make it a multiple
                        //  size of the samples that we have to loop. The samples will be written
                        //  to this buffer multiple times. This way the device will accept the loop.
                        loopedBufferSize = data.length;
                        if (loopedBufferSize < audioRate) {
                            loopedBufferSize = (audioRate / data.length + 1) * data.length;
                        }
                        short[] filledBuffer = new short[loopedBufferSize];
                        for (int i = 0; i < loopedBufferSize; i++)
                            filledBuffer[i] = data[i % data.length];
                        result = audioTrack.write(filledBuffer, 0, loopedBufferSize);
                    } else //Usually, just write the small buffer...
                        result = audioTrack.write(data, 0, data.length);

                    if (result <= 0)
                        Log.e("processAnalysis", "Unexpected audio write result: " + result + " written.");

                    audioTrack.reloadStaticData();
                    audioTrack.setPlaybackHeadPosition(0);
                    if (audioLoop) //If looping is enabled, loop from the end of the data
                        audioTrack.setLoopPoints(0, loopedBufferSize, -1);
                }
            } else { //If the data is static and already loaded, we just have to rewind...
                if (!audioLoop) { //We only want to play again since we are not looping
                    audioTrack.stop();
                    audioTrack.reloadStaticData();
                }
            }
        }

        //Restart if used.
        if (measuring && audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            audioTrack.play();
        }

        if (measuring && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            //Send the results to the bluetooth outputs (if used)
            for (BluetoothOutput btOut : bluetoothOutputs) {
                btOut.sendData();
            }
        }

        recordingUsed = true;
        newData = true; //We have fresh data to present.
        lastAnalysis = System.currentTimeMillis(); //Remember when we were done this time
    }

    //called by the main loop after everything is processed. Here we have to send all the analysis results to the appropriate views
    public boolean updateViews(int currentView, boolean force) {
        if (!loaded)
            return true;
        if (!(newData || force)) //New data to present? If not: Nothing to do, unless an update is forced
            return true;

        try {
            if (dataLock.tryLock(10, TimeUnit.MILLISECONDS)) {
                try {
                    for (expView experimentView : experimentViews) {
                        for (expView.expViewElement eve : experimentView.elements) {
                            eve.onMayReadFromBuffers(this); //Notify each view, that it should update from the buffers
                        }
                    }
                } finally {
                    dataLock.unlock();
                }
            } else
                return false; //This is not urgent. Try another time instead of blocking the UI thread!
        } catch (Exception e) {
            return false;
        }

        newData = false;
        //Finally call dataComplete on every view to notify them that the data has been sent - heavy operation can now be done by the views while the buffers have been unlocked again
        for (expView.expViewElement eve : experimentViews.elementAt(currentView).elements) {
            try {
                eve.dataComplete();
            } catch (Exception e) {
                Log.e("updateViews", "Unhandled exception in view module " + eve.toString() + " on data completion.", e);
            }
        }
        return true;
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

        if (gpsIn != null)
            gpsIn.stop();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            //Bluetooth
            for (BluetoothInput bti : bluetoothInputs)
                bti.stop();
            for (BluetoothOutput bto : bluetoothOutputs)
                bto.stop();
        }
    }


    //Helper to start all I/O of this experiment (i.e. when it should be started)
    public void startAllIO() throws Bluetooth.BluetoothException {

        if (!loaded)
            return;

        //If we start IO and the data has not been reset, we want to set the first analysis time to now, offset by the duration of the last measurement period
        if (firstAnalysisTime != 0)
            firstAnalysisTime = System.currentTimeMillis() - (analysisTime-firstAnalysisTime);

        newUserInput = true; //Set this to true to execute analysis at least ones with default values.
        for (sensorInput sensor : inputSensors)
            sensor.start();

        if (gpsIn != null)
            gpsIn.start();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            for (BluetoothInput bti : bluetoothInputs) {
                bti.start();
            }
            for (BluetoothOutput btO : bluetoothOutputs) {
                btO.start();
            }
        }

        //Audio Recording
        if (audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED)
            audioRecord.startRecording();

        //We will not start audio output here as it will be triggered by the analysis modules.
    }

    public void init(SensorManager sensorManager, LocationManager locationManager) throws Exception {
        //Update all the views
        for (int i = 0; i < experimentViews.size(); i++) {
            updateViews(i, true);
        }

        //Create the audioTrack instance
        if (audioSource != null) {
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, audioRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, 2 * audioBufferSize, AudioTrack.MODE_STATIC);
            if (audioTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
                throw new Exception("Could not initialize audio. (" + audioTrack.getState() + ")");
            }
        }

        //Create audioTrack instance
        if (micBufferSize > 0)
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, micRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, micBufferSize * 2);

        //Reconnect sensors
        for (sensorInput si : inputSensors) {
            si.attachSensorManager(sensorManager);
        }

        if (gpsIn != null)
            gpsIn.attachLocationManager(locationManager);

    }

    public String writeStateFile(String customTitle, OutputStream os) {
        if (source == null)
            return "Source is null.";
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db;
        try {
            db = dbf.newDocumentBuilder();
        } catch (Exception e) {
            return "Could not create DocumentBuilder: " + e.getMessage();
        }

        Document doc;
        InputStream is = new ByteArrayInputStream(source);
        try {
            doc = db.parse(is);
        } catch (Exception e) {
            return "Could not parse source: " + e.getMessage();
        }

        Element root = doc.getDocumentElement();
        if (root == null)
            return "Source has no root.";

        root.normalize();

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
            if (children.item(i).getNodeName().equals("state-title") || children.item(i).getNodeName().equals("color"))
                root.removeChild(children.item(i));


        Element customTitleEl = doc.createElement("state-title");
        customTitleEl.setTextContent(customTitle);
        root.appendChild(customTitleEl);

        Element colorEl = doc.createElement("color");
        colorEl.setTextContent("blue");
        root.appendChild(colorEl);


        NodeList containers = root.getElementsByTagName("data-containers");
        if (containers.getLength() != 1)
            return "Source needs exactly one data-container block.";

        NodeList buffers = containers.item(0).getChildNodes();

        DecimalFormat format = (DecimalFormat) NumberFormat.getInstance(Locale.ENGLISH);
        format.applyPattern("0.#########E0");
        DecimalFormatSymbols dfs = format.getDecimalFormatSymbols();
        dfs.setDecimalSeparator('.');
        format.setDecimalFormatSymbols(dfs);
        format.setGroupingUsed(false);

        for (int i = 0; i < buffers.getLength(); i++) {
            if (!buffers.item(i).getNodeName().equals("container"))
                continue;

            dataBuffer buffer = getBuffer(buffers.item(i).getTextContent());
            if (buffer == null)
                continue;

            Attr attr = doc.createAttribute("init");

            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (double v : buffer.getArray()) {
                if (first)
                    first = false;
                else
                    sb.append(",");
                sb.append(format.format(v));
            }

            attr.setValue(sb.toString());

            buffers.item(i).getAttributes().setNamedItem(attr);
        }

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t;
        try {
            t = tf.newTransformer();
        } catch (Exception e) {
            return "Could not create transformer: " + e.getMessage();
        }

        DOMSource domSource = new DOMSource(doc);


        StreamResult streamResult = new StreamResult(os);
        try {
            t.transform(domSource, streamResult);
        } catch (Exception e) {
            return "Transform failed: " + e.getMessage();
        }

        return null;
    }
}
