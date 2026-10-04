package de.rwth_aachen.phyphox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

// phyphox-test: audio-output-retrigger
//Playback is triggered after every analysis cycle. A trigger starts a one-shot output over from its beginning, while a
//looped output that is already playing continues undisturbed (phyphox-docs spec/output.yml). Blocks are generated with
//nextBlock() directly, as Robolectric's AudioTrack does not take the short writes of the fill thread.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class AudioOutputRetriggerTest {

    private static final int BLOCK = 2048;

    private final float[] floatData = new float[2*BLOCK];
    private final short[] shortData = new short[2*BLOCK];

    //A waveform whose samples are all different, so the position within it can be read back
    private AudioOutput outputWithWaveform(boolean loop, int length) {
        AudioOutput output = new AudioOutput(loop, 48000, false);
        DataBuffer waveform = new DataBuffer("waveform", "", length, null);
        for (int i = 0; i < length; i++)
            waveform.append(sample(i, length));
        output.attachPlugin(output.new AudioOutputPluginDirect(new DataInput(waveform, true)));
        return output;
    }

    private static double sample(int i, int length) {
        return 0.9 * (2.0 * i / length - 1.0);
    }

    private static void set(AudioOutput output, String field, Object value) throws Exception {
        Field f = AudioOutput.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(output, value);
    }

    //Marks the output as playing without starting the fill thread
    private static void playing(AudioOutput output) throws Exception {
        set(output, "playing", true);
        set(output, "active", true);
    }

    private void assertBlockStartsAt(String message, long position, int length) {
        for (int i = 0; i < BLOCK; i += 97) {
            double expected = sample((int)((position + i) % length), length) * Short.MAX_VALUE;
            assertEquals(message + " (sample " + i + ")", expected, shortData[2*i], 1.0);
            assertEquals(message + " (right channel, sample " + i + ")", expected, shortData[2*i+1], 1.0);
        }
    }

    @Test
    public void aLoopedOutputIsNotRestartedWhilePlaying() throws Exception {
        int length = 3000; //does not divide the block size, so a restart would be a jump
        AudioOutput output = outputWithWaveform(true, length);
        playing(output);

        output.nextBlock(floatData, shortData);
        output.nextBlock(floatData, shortData);
        output.play();
        output.nextBlock(floatData, shortData);
        assertBlockStartsAt("a looped output must continue where it was after a trigger", 2*BLOCK, length);
    }

    @Test
    public void aOneShotOutputStartsOverOnATrigger() throws Exception {
        int length = 10000;
        AudioOutput output = outputWithWaveform(false, length);
        playing(output);

        output.nextBlock(floatData, shortData);
        output.nextBlock(floatData, shortData);
        output.play();
        output.nextBlock(floatData, shortData);
        assertBlockStartsAt("a one-shot output must start over from its first sample after a trigger", 0, length);
    }

    @Test
    public void aOneShotToneStartsOverOnATrigger() throws Exception {
        AudioOutput output = new AudioOutput(false, 48000, false);
        output.init();
        AudioOutput.AudioOutputPluginTone tone = output.new AudioOutputPluginTone(AudioOutput.Waveform.SQUARE);
        tone.setParameter("duration", new DataInput(0.01)); //480 samples, less than a block
        tone.setParameter("frequency", new DataInput(1000));
        output.attachPlugin(tone);
        playing(output);

        output.nextBlock(floatData, shortData);
        assertNotEquals("the tone must sound in the first block", 0, shortData[2*10]);
        output.nextBlock(floatData, shortData);
        assertEquals("the tone must have ended after its duration", 0, shortData[2*10]);
        output.play();
        output.nextBlock(floatData, shortData);
        assertNotEquals("a trigger must start the tone's duration over", 0, shortData[2*10]);
    }

    @Test
    public void aLoopedOutputSurvivesTheIntRange() throws Exception {
        int length = 3000;
        AudioOutput output = outputWithWaveform(true, length);
        playing(output);
        long position = Integer.MAX_VALUE - 1000L; //the block crosses the int range
        set(output, "index", position);

        output.nextBlock(floatData, shortData);
        assertBlockStartsAt("a looped output must keep its position beyond 2^31 samples (12 hours at 48 kHz)", position, length);
    }
}
