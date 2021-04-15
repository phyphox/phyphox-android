package de.rwth_aachen.phyphox;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

public class AudioOutput {
    private final static int sineLookupSize = 4096;
    private static float[] sineLookup = new float[sineLookupSize];

    public abstract class AudioOutputPlugin {
        public abstract boolean setParameter(String parameter, DataInput input);
        public abstract float getAmplitude();
        public abstract void generate(float[] buffer, int samples, int rate, int index, boolean loop);
    }

    public class AudioOutputPluginDirect extends AudioOutputPlugin {
        DataInput input;
        AudioOutputPluginDirect(DataInput input) {
            this.input = input;
        }

        @Override
        public boolean setParameter(String parameter, DataInput input) {
            return false;
        }

        @Override
        public float getAmplitude() {
            return 1.0f;
        }

        @Override
        public void generate(float[] buffer, int samples, int rate, int index, boolean loop) {
            if (input == null)
                return;
            Double[] data = input.getArray();
            if (data == null || data.length == 0)
                return;

            if (loop) {
                for (int i = 0; i < samples; i++) {
                    buffer[i] += data[(index + i) % data.length];
                }
            } else {
                for (int i = 0; i < samples && i + index < data.length; i++) {
                    buffer[i] += data[index + i];
                }
            }
        }
    }

    public class AudioOutputPluginTone extends AudioOutputPlugin {
        private DataInput amplitude = new DataInput(1.0f);
        private DataInput duration = new DataInput(1.0f);
        private DataInput frequency = new DataInput(440f);
        private double phase = 0.f;

        @Override
        public boolean setParameter(String parameter, DataInput input) {
            switch (parameter) {
                case "amplitude": amplitude = input;
                    return true;
                case "duration": duration = input;
                    return true;
                case "frequency": frequency = input;
                    return true;
            }
            return false;
        }

        @Override
        public float getAmplitude() {
            return (float)amplitude.getValue();
        }

        public void generate(float[] buffer, int samples, int rate, int index, boolean loop) {
            float d = (float)duration.getValue();
            float a = (float)amplitude.getValue();
            float f = (float)frequency.getValue();

            int end = samples;
            if (!loop) {
                int durationEnd = (int)(d * rate) - index;
                if (durationEnd < end)
                    end = durationEnd;
            }

            double phaseStep = (double)f / (double)rate;

            for (int i = 0; i < end; i++) {
                buffer[i] += a * sineLookup[(int)(phase * sineLookupSize) % sineLookupSize];
                phase += phaseStep;
            }
            while (phase > 100000.f)
                phase -= 100000.f; //Once in a while we need to dial back the phase to avoid an overflow, especially when converting this to an integer to calculate the index for the lookup table. The 100000 is rather arbitrary. We do not want it to be too small to avoid unnecessary calculations in the tone generator and it should not be too large to stay well away from the overflow.
        }
    }

    public class AudioOutputPluginNoise extends AudioOutputPlugin {
        private DataInput amplitude = new DataInput(1.0f);
        private DataInput duration = new DataInput(1.0f);

        @Override
        public boolean setParameter(String parameter, DataInput input) {
            switch (parameter) {
                case "amplitude": amplitude = input;
                    return true;
                case "duration": duration = input;
                    return true;
            }
            return false;
        }

        @Override
        public float getAmplitude() {
            return (float)amplitude.getValue();
        }

        public void generate(float[] buffer, int samples, int rate, int index, boolean loop) {
            float d = (float)duration.getValue();
            float a = (float)amplitude.getValue();

            int end = samples;
            if (!loop) {
                int durationEnd = (int)(d * rate) - index;
                if (durationEnd < end)
                    end = durationEnd;
            }

            for (int i = 0; i < end; i++) {
                buffer[i] += a * (2.0f * Math.random() - 1.0f);
            }
        }
    }

    private boolean loop;
    private int rate;
    private boolean normalize;
    private ArrayList<AudioOutputPlugin> plugins = new ArrayList<>();

    private final int bufferBaseSize = 2048; //This is actually a fourth of the total buffer, similar to the four buffers used on iOS
    private int bufferSize = 0; //Total buffer size, can be different if minBufferSize is larger than 4x2048
    private int index = 0;
    private boolean playing = false;
    private boolean active = false;
    private boolean beepOnly = false;

    Beeper beeper = null;

    private AudioTrack audioTrack;

    AudioOutput(boolean loop, int rate, boolean normalize) {
        this.loop = loop;
        this.rate = rate;
        this.normalize = normalize;
    }

    public void attachPlugin(AudioOutputPlugin plugin) {
        plugins.add(plugin);
    }

    public void init() throws Exception {
        int minBuffer = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        bufferSize = Math.max(minBuffer, bufferBaseSize*2*4); //2048 frames with 2 bytes each (16bit short int), grouped in four buffers (on iOS at least)
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
        if (audioTrack.getState() == AudioTrack.STATE_UNINITIALIZED) {
            throw new Exception("Could not initialize audio. (" + audioTrack.getState() + ")");
        }


        for (int i = 0; i < sineLookupSize; i++)
            sineLookup[i] = (float)Math.sin(2.0*Math.PI*(double)i / (double)sineLookupSize);
    }

    private Runnable fillBuffer = new Runnable() {
        @Override
        public void run() {
            float[] floatData = new float[bufferBaseSize];
            short[] shortData = new short[bufferBaseSize];
            float amplitude;

            while (playing && active) {
                Arrays.fill(floatData, 0);
                amplitude = 0.0f;

                if (beeper != null) {
                    beeper.generate(floatData, bufferBaseSize, rate, index);
                    amplitude += beeper.a;
                }

                if (!beepOnly) {
                    for (AudioOutputPlugin plugin : plugins) {
                        plugin.generate(floatData, bufferBaseSize, rate, index, loop);
                        amplitude += plugin.getAmplitude();
                    }
                }

                index += bufferBaseSize;

                if (normalize && amplitude > 0)
                    amplitude = 1.0f/amplitude;
                else
                    amplitude = 1.0f;
                float x;
                for (int i = 0; i < bufferBaseSize; i++) {
                    x = amplitude * floatData[i] * Short.MAX_VALUE;
                    if (x > Short.MAX_VALUE)
                        shortData[i] = Short.MAX_VALUE;
                    else if (x < Short.MIN_VALUE)
                        shortData[i] = Short.MIN_VALUE;
                    else
                        shortData[i] = (short)x;
                }
                audioTrack.write(shortData, 0, bufferBaseSize);
            }
        }
    };

    public void start(boolean beepOnly) {
        this.beepOnly = beepOnly;
        active = true;
    }

    public void play() {
        if (beeper != null)
            beeper.start -= index;
        index = 0;
        if (playing || !active)
            return;

        playing = true;
        try {
            audioTrack.play();
        } catch (Exception e) {

        }

        ///Start writing to the buffer
        new Thread(fillBuffer).start();
    }

    private class Beeper {
        double a = 0.5;
        double f;
        double d;
        double phase = 0;
        int start;
        boolean done = false;

        Beeper(double f, double d, double delay) {
            this.f = f;
            this.d = d;
            this.start = index + (int)(delay*audioTrack.getPlaybackRate()) - bufferSize/2;
        }

        public void generate(float[] buffer, int samples, int rate, int index) {
            if (done)
                return;

            if (start >= index + samples)
                return;

            int end = samples;
            int durationEnd = start + (int)(d * rate) - index;
            if (durationEnd <= 0) {
                done = true;
                return;
            } else if (durationEnd < samples) {
                done = true;
                end = durationEnd;
            }

            double phaseStep = (double)f / (double)rate;

            for (int i = Math.max(0, start - index); i < end; i++) {
                buffer[i] += a * sineLookup[(int)(phase * sineLookupSize) % sineLookupSize];
                phase += phaseStep;
            }

        }
    }

    public void beep(double f, double d, double delay) {
        beeper = new Beeper(f, d, delay);
    }

    public void beepRelative(double f, double d, double after) {
        if (beeper == null)
            return;
        beeper.start += after * audioTrack.getPlaybackRate();
        beeper.f = f;
        beeper.d = d;
        beeper.done = false;
    }

    public void stop() {
        if (beeper != null) {
            int maxRemainingSamples;
            if (beeper.start >= 0)
                maxRemainingSamples = beeper.start + (int)beeper.d * rate - index + bufferSize/2;
            else
                maxRemainingSamples = (int)beeper.d * rate;
            try {
                Thread.sleep(1000*maxRemainingSamples / rate);
            } catch (Exception e) {
                //Nothing to do. It's not the end of the world if we cannot wait for the final beep to finish.
            }
        }
        active = false;
        playing = false;
        audioTrack.stop();
    }

}
