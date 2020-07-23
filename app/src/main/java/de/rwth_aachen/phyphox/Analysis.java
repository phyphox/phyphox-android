package de.rwth_aachen.phyphox;

import android.util.Log;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Vector;

// The analysis class is used to to do math operations on dataBuffers

public class Analysis {

    private static boolean nativeLib = false;

    static {
        try {
            System.loadLibrary("fftw3f");
            System.loadLibrary("analysis");
            nativeLib = true;
            Log.i("cpp library", "Using native analysis library.");
        } catch (Error e) {
            Log.w("cpp library", "Could not load native analysis library. Falling back to Java implementation.");
        }
    }

    public static native void nativePower(double[] x, double[] y);
    public static native void fftw3complex(float[] xy, int n);
    public static native void fftw3crosscorrelation(float[] x, float[] y, int n);
    public static native void fftw3autocorrelation(float[] x, int n);

    public static class FFT implements Serializable {
        private int n, logn; //input size, power-of-two filled size, log2 of input size (integer)
        private double [] cos, sin; //Lookup table

        public int np2;

        FFT() {
            n = 0;
        }

        public void prepare(int n) {
            this.n = n;
            if (n < 2)
                return;

            logn = (int)(Math.log(n)/Math.log(2)); //log of input size
            if (n != (1 << logn)) {
                logn++;
                np2 = (1 << logn); //power of two after zero filling
            } else
                np2 = n; //n is already power of two

            //Create buffer of sine and cosine values
            cos = new double[np2/2];
            sin = new double[np2/2];
            for (int i = 0; i < np2 / 2; i++) {
                cos[i] = Math.cos(-2 * Math.PI * i / np2);
                sin[i] = Math.sin(-2 * Math.PI * i / np2);
            }
        }

        public void calculate(Double[] x, Double[] y) {
            if (n < 2)
                return;

            /***************************************************************
             * fft.c
             * Douglas L. Jones
             * University of Illinois at Urbana-Champaign
             * January 19, 1992
             * http://cnx.rice.edu/content/m12016/latest/
             * <p/>
             * fft: in-place radix-2 DIT DFT of a complex input
             * <p/>
             * input:
             * n: length of FFT: must be a power of two
             * m: n = 2**m
             * input/output
             * x: double array of length n with real part of data
             * y: double array of length n with imag part of data
             * <p/>
             * Permission to copy and use this program is granted
             * as long as this header is included.
             ****************************************************************/

            int j, k, n1, n2, a;
            double c, s, t1, t2;

            j = 0; /* bit-reverse */
            n2 = np2 / 2;
            for(int i = 1; i < np2 - 1; i++)

            {
                n1 = n2;
                while (j >= n1) {
                    j = j - n1;
                    n1 = n1 / 2;
                }
                j = j + n1;

                if (i < j) {
                    t1 = x[i];
                    x[i] = x[j];
                    x[j] = t1;
                    t1 = y[i];
                    y[i] = y[j];
                    y[j] = t1;
                }
            }

            n2 = 1;

            for(int i = 0; i < logn; i++)

            {
                n1 = n2;
                n2 = n2 + n2;
                a = 0;

                for (j = 0; j < n1; j++) {
                    c = cos[a];
                    s = sin[a];
                    a += 1 << (logn - i - 1);

                    for (k = j; k < np2; k = k + n2) {
                        t1 = c * x[k + n1] - s * y[k + n1];
                        t2 = s * x[k + n1] + c * y[k + n1];
                        x[k + n1] = x[k] - t1;
                        y[k + n1] = y[k] - t2;
                        x[k] = x[k] + t1;
                        y[k] = y[k] + t2;
                    }
                }
            }
        }

    }

    //analysisModule is is the prototype from which each analysis module inherits its interface
    public static class AnalysisModule implements Serializable, BufferNotification {
        private Vector<DataInput> inputsOriginal; //The key of input dataBuffers, note that this is private, so derived classes cannot access this directly, but have to use the copy
        protected Vector<DataInput> inputs = new Vector<>(); //The local copy of the input data when the analysis module starts its update
        protected Vector<Double[]> inputArrays = new Vector<>(); //The local copy of the input data when the analysis module starts its update
        protected Vector<Integer> inputArraySizes = new Vector<>(); //The local copy of the input data when the analysis module starts its update
        protected Vector<DataOutput> outputs; //The keys of outputBuffers
        protected PhyphoxExperiment experiment; //experiment reference to access buffers
        protected boolean isStatic = false; //If a module is defined as static, it will only be executed once. This is used to save performance if data does not change
        protected boolean executed = false; //This takes track if the module has been executed at all. Used for static modules.

        protected boolean useArray = false;
        protected boolean clearInModule = false;

        public static class CycleRange {
            int start = -1;
            int stop = -1;

            CycleRange(int start, int stop) {
                this.start = start;
                this.stop = stop;
            }
        }
        protected Vector<CycleRange> cycles;

        //Main contructor
        protected AnalysisModule(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            this.experiment = experiment;
            this.inputsOriginal = inputs;
            this.outputs = outputs;

            //If any output module is non-static, then this analysis module is not static
            this.isStatic = true;
            for (int i = 0; i < outputs.size(); i++) {
                if (outputs.get(i) != null && !outputs.get(i).isStatic()) {
                    this.isStatic = false;
                    break;
                }
            }

            //Subscribe to updates from input and output buffers
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) != null && inputs.get(i).isBuffer) {
                    inputs.get(i).buffer.register(this);
                }
            }
            for (int i = 0; i < outputs.size(); i++) {
                if (outputs.get(i) != null) {
                    outputs.get(i).buffer.register(this);
                }
            }

        }

        public void setCycles(Vector<CycleRange> cycles) {
            this.cycles = cycles;
        }

        //Called when one of the input buffers is updated
        public void notifyUpdate(boolean clear, boolean reset) {
            if (reset) {
                executed = false;
            }
        }

        private boolean runInCycle(int thisCycle) {
            if (cycles.size() == 0)
                return true;
            for (CycleRange cycle : cycles) {
                if (cycle.start >= 0 && thisCycle < cycle.start)
                    continue;
                if (cycle.stop >= 0 && thisCycle > cycle.stop)
                    continue;
                return true;
            }
            return false;
        }

        //Wrapper to update the module only if it is not static or has never been executed and to clear the buffer if required
        protected void updateIfNotStatic(int thisCycle) {
            if (!runInCycle(thisCycle))
                return;

            if (!(isStatic && executed)) {
//                long updateStart = System.nanoTime();

                experiment.dataLock.lock();
                try {
                    if (useArray) {
                        inputArrays.setSize(inputsOriginal.size());
                        inputArraySizes.setSize(inputsOriginal.size());
                    } else
                        inputs.setSize(inputsOriginal.size());
                    for (int i = 0; i < inputsOriginal.size(); i++) {
                        if (inputsOriginal.get(i) == null) {
                            if (useArray) {
                                inputArrays.set(i, null);
                                inputArraySizes.set(i, 0);
                            } else
                                inputs.set(i, null);
                        } else {
                            if (useArray) {
                                inputArrays.set(i, inputsOriginal.get(i).getArray());
                                inputArraySizes.set(i, inputsOriginal.get(i).getFilledSize());
                            } else
                                inputs.set(i, inputsOriginal.get(i).copy());
                            if (inputsOriginal.get(i).isBuffer && inputsOriginal.get(i).clearAfterRead && !inputsOriginal.get(i).buffer.isStatic)
                                inputsOriginal.get(i).clear(false);
                        }
                    }
                } finally {
                    experiment.dataLock.unlock();
                }

                if (!clearInModule) {
                    for (DataOutput output : outputs)
                        if (output != null && output.clearBeforeWrite)
                            output.buffer.clear(false);
                }


                update();

//                long time = System.nanoTime() - updateStart;
//                if (time > 1e6)
//                    Log.d("AnalysisDebug", this.toString() + " update: " + (time*1e-6) + "ms");



    // Uncomment to print the last value of inputs and outputs for debugging...
/*
                Log.d("AnalysisDebug", "[" + this.getClass().toString() + "]");
                if (!useArray) {
                    for (dataInput input : inputs)
                        if (input != null)
                            Log.d("AnalysisDebug", "in: " + input.getValue() + " (length " + input.getFilledSize() + ")");
                } else {
                    for (int i = 0; i < inputArrays.size() && i < inputArraySizes.size(); i++)
                        if (inputArrays.get(i) != null)
                            Log.d("AnalysisDebug", "in: " + (inputArraySizes.get(i) > 0 ? inputArrays.get(i)[inputArraySizes.get(i)-1] : "[]") + " (length " + inputArraySizes.get(i) + ")");
                }
                for (dataOutput output : outputs)
                    if (output != null)
                        Log.d("AnalysisDebug", output.buffer.name + " => " + output.getValue() + " (length " + output.getFilledSize() + ")");

*/
                for (int i = 0; i < outputs.size(); i++) {
                    if (outputs.get(i) != null) {
                        outputs.get(i).markSet();
                    }
                }
                executed = true;

            }
            return;
        }

        //The main update function has to be overridden by the modules
        protected void update() {

        }
    }

    //Get the seconds since the experiment started
    public static class timerAM extends AnalysisModule implements Serializable {

        protected timerAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            outputs.get(0).append((experiment.analysisTime - experiment.firstAnalysisTime)/1000.);
        }
    }

    //Describe multiple analysis steps as a formula
    public static class formulaAM extends AnalysisModule implements Serializable {
        FormulaParser formula;

        protected formulaAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, String formula) throws FormulaParser.FormulaException {
            super(experiment, inputs, outputs);
            useArray = true;
            this.formula = new FormulaParser(formula);
        }

        @Override
        protected void update() {
            if (outputs.size() > 0)
            formula.execute(inputArrays, outputs.get(0));
        }
    }

    // Get the number of elements in the input buffer
    public static class countAM extends AnalysisModule implements Serializable {

        protected countAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            int size = inputArraySizes.get(0);
            outputs.get(0).append(size);
        }
    }

    // return buffer "true" if first input is smaller than second, otherwise return buffer "false"
    public static class ifAM extends AnalysisModule implements Serializable {
        boolean less, equal, greater;

        protected ifAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, boolean less, boolean equal, boolean greater) {
            super(experiment, inputs, outputs);
            this.less = less;
            this.equal = equal;
            this.greater = greater;
            useArray = true;
            clearInModule = true;
        }

        @Override
        protected void update() {
            if (inputArrays.size() < 2 || inputArraySizes.get(0) == 0 || inputArraySizes.get(1) == 0)
                return;
            double a = inputArrays.get(0)[inputArraySizes.get(0) - 1];
            double b = inputArrays.get(1)[inputArraySizes.get(1) - 1];
            if ((a < b && less) || (a == b && equal) || (a > b && greater)) {
                if (inputArrays.size() >= 3 && inputArrays.get(2) != null) {
                    if (outputs.get(0).clearBeforeWrite)
                        outputs.get(0).clear(false);
                    outputs.get(0).append(inputArrays.get(2), inputArraySizes.get(2));
                }
            } else {
                if (inputArrays.size() >= 4  && inputArrays.get(3) != null) {
                    if (outputs.get(0).clearBeforeWrite)
                        outputs.get(0).clear(false);
                    outputs.get(0).append(inputArrays.get(3), inputArraySizes.get(3));
                }
            }
        }
    }

    // Get the average value of this buffer (ignoring NaNs)
    public static class averageAM extends AnalysisModule implements Serializable {

        protected averageAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            Double in[] = inputArrays.get(0);
            int size = inputArraySizes.get(0);
            if (size == 0)
                return;

            double sum = 0.;
            int count = 0;
            for (int i = 0; i < size; i++) {
                if (in[i].isNaN() || in[i].isInfinite())
                    continue;;
                sum += in[i];
                count++;
            }
            if (count == 0)
                return;

            double avg = sum/count;

            if (outputs.size() > 0 && outputs.get(0) != null) {
                outputs.get(0).append(avg);
            }

            //We only calculate the standard deviation if it is actually written to a buffer
            if (outputs.size() > 1 && outputs.get(1) != null) {
                if (count < 2) {
                    outputs.get(1).append(Double.NaN);
                }
                sum = 0.;
                count = 0;
                for (int i = 0; i < size; i++) {
                    if (in[i].isNaN() || in[i].isInfinite())
                        continue;;
                    sum += (in[i]-avg)*(in[i]-avg);
                    count++;
                }
                double std = Math.sqrt(sum/(count-1));
                outputs.get(1).append(std);
            }
        }
    }

    //Add input values. The output has the length of the longest input buffer or the size of the output buffer (whichever is smaller). Missing values in shorter buffers are filled from the last value.
    public static class addAM extends AnalysisModule implements Serializable {

        protected addAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {

            boolean anyInput = true; //Is there any buffer left with values?
            int i = 0;
            while (anyInput) { //For each value of output buffer
                double result = 0;
                anyInput = false;

                for (int j = 0; j < inputArrays.size(); j++) { //For each input buffer
                    Double in[] = inputArrays.get(j);
                    int size = inputArraySizes.get(j);
                    if (in == null || size == 0) {
                        anyInput = false;
                        break;
                    }
                    if (i < size) { //New value from this iterator
                        result += in[i];
                        anyInput = true;
                    } else {
                        result += in[size-1];
                    }
                }
                if (anyInput) //There was a new value. Append the result.
                    outputs.get(0).append(result);
                else //No values left. Let's stop here...
                    break;
                i++;
            }

        }
    }

    //Subtract input values (i1-i2-i3-i4...). The output has the length of the longest input buffer or the size of the output buffer (whichever is smaller). Missing values in shorter buffers are filled from the last value.
    public static class subtractAM extends AnalysisModule implements Serializable {

        protected subtractAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            boolean anyInput = true; //Is there any buffer left with values?
            int i = 0;
            while (anyInput) { //For each value of output buffer
                double result = 0;
                anyInput = false;

                for (int j = 0; j < inputArrays.size(); j++) { //For each input buffer
                    Double in[] = inputArrays.get(j);
                    int size = inputArraySizes.get(j);
                    if (in == null || size == 0) {
                        anyInput = false;
                        break;
                    }
                    if (i < size) { //New value from this iterator
                        if (j == 0)
                            result += in[i];
                        else
                            result -= in[i];
                        anyInput = true;
                    } else {
                        if (j == 0)
                            result += in[size-1];
                        else
                            result -= in[size-1];
                    }
                }
                if (anyInput) //There was a new value. Append the result.
                    outputs.get(0).append(result);
                else //No values left. Let's stop here...
                    break;
                i++;
            }

        }
    }

    //Multiply input values. The output has the length of the longest input buffer or the size of the output buffer (whichever is smaller). Missing values in shorter buffers are filled from the last value.
    public static class multiplyAM extends AnalysisModule implements Serializable {

        protected multiplyAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            boolean anyInput = true; //Is there any buffer left with values?
            int i = 0;
            while (anyInput) { //For each value of output buffer
                double result = 1.;
                anyInput = false;

                for (int j = 0; j < inputArrays.size(); j++) { //For each input buffer
                    Double in[] = inputArrays.get(j);
                    int size = inputArraySizes.get(j);
                    if (in == null || size == 0) {
                        anyInput = false;
                        break;
                    }
                    if (i < size) { //New value from this iterator
                        result *= in[i];
                        anyInput = true;
                    } else {
                        result *= in[size-1];
                    }
                }
                if (anyInput) //There was a new value. Append the result.
                    outputs.get(0).append(result);
                else //No values left. Let's stop here...
                    break;
                i++;
            }
        }
    }


    //Divide input values (i1/i2/i3/...). The output has the length of the longest input buffer or the size of the output buffer (whichever is smaller). Missing values in shorter buffers are filled from the last value.
    public static class divideAM extends AnalysisModule implements Serializable {

        protected divideAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            boolean anyInput = true; //Is there any buffer left with values?
            int i = 0;
            while (anyInput) { //For each value of output buffer
                double result = 0.;
                anyInput = false;

                for (int j = 0; j < inputArrays.size(); j++) { //For each input buffer
                    Double in[] = inputArrays.get(j);
                    int size = inputArraySizes.get(j);
                    if (in == null || size == 0) {
                        anyInput = false;
                        break;
                    }
                    if (i < size) { //New value from this iterator
                        if (j == 0)
                            result = in[i];
                        else
                            result /= in[i];
                        anyInput = true;
                    } else {
                        if (j == 0)
                            result = in[size-1];
                        else
                            result /= in[size-1];
                    }
                }
                if (anyInput) //There was a new value. Append the result.
                    outputs.get(0).append(result);
                else //No values left. Let's stop here...
                    break;
                i++;
            }
        }
    }

    // Calculate the power of input values. ((i1^i2)^i3)^..., but you probably only want two inputs here...
    // The output has the length of the longest input buffer or the size of the output buffer (whichever is smaller).
    // Missing values in shorter buffers are filled from the last value.
    public static class powerAM extends AnalysisModule implements Serializable {

        protected powerAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            if (nativeLib) {
                if (inputArraySizes.size() < 2)
                    return;

                int sizeA = inputArraySizes.get(0);
                int sizeB = inputArraySizes.get(1);

                Double[] a = inputArrays.get(0);
                Double[] b = inputArrays.get(1);

                final double ad[] = new double[sizeA];
                final double bd[] = new double[sizeB];

                for (int i = 0; i < sizeA; i++) {
                    ad[i] = a[i];
                }
                for (int i = 0; i < sizeB; i++) {
                    bd[i] = b[i];
                }

                nativePower(ad, bd);

                if (sizeA > sizeB) {
                    for (int i = 0; i < sizeA; i++) {
                        outputs.get(0).append(ad[i]);
                    }
                } else {
                    for (int i = 0; i < sizeB; i++) {
                        outputs.get(0).append(bd[i]);
                    }
                }
            } else {

                boolean anyInput = true; //Is there any buffer left with values?
                int i = 0;
                while (anyInput) { //For each value of output buffer
                    double result = 1.;
                    anyInput = false;

                    for (int j = 0; j < inputArrays.size(); j++) { //For each input buffer
                        Double in[] = inputArrays.get(j);
                        int size = inputArraySizes.get(j);
                        if (in == null || size == 0) {
                            anyInput = false;
                            break;
                        }
                        if (i < size) { //New value from this iterator
                            if (j == 0)
                                result = in[i];
                            else
                                result = Math.pow(result, in[i]);
                            anyInput = true;
                        } else {
                            if (j == 0)
                                result = in[size-1];
                            else
                                result = Math.pow(result, in[size-1]);
                        }
                    }
                    if (anyInput) //There was a new value. Append the result.
                        outputs.get(0).append(result);
                    else //No values left. Let's stop here...
                        break;
                    i++;
                }

            }
        }
    }

    // Calculate the greatest common divisor (GCD), takes exactly two inputs.
    // The output has the length of the longest input buffer or the size of the output buffer (whichever is smaller).
    // Missing values in shorter buffers are filled from the last value.
    public static class gcdAM extends AnalysisModule implements Serializable {

        protected gcdAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            boolean anyInput = true; //Is there any buffer left with values?
            int i = 0;
            while (anyInput) { //For each value of output buffer
                anyInput = false;

                long a = 1;
                long b = 1;

                for (int j = 0; j < inputArrays.size() && j < 2; j++) { //For each input buffer
                    Double in[] = inputArrays.get(j);
                    int size = inputArraySizes.get(j);
                    if (in == null || size == 0) {
                        anyInput = false;
                        break;
                    }
                    if (i < size) { //New value from this iterator
                        if (j == 0)
                            a = Math.round(in[i]);
                        else
                            b = Math.round(in[i]);
                        anyInput = true;
                    } else {
                        if (j == 0)
                            a = Math.round(in[size-1]);
                        else
                            b = Math.round(in[size-1]);
                    }
                }
                if (anyInput) { //There was a new value. Append the result.
                    while (b > 0) {
                        long tmp = b;
                        b = a % b;
                        a = tmp;
                    }
                    outputs.get(0).append(a);
                } else //No values left. Let's stop here...
                    break;
                i++;
            }

        }
    }

    // Calculate the least common multiple (GCD), takes exactly two inputs.
    // The output has the length of the longest input buffer or the size of the output buffer (whichever is smaller).
    // Missing values in shorter buffers are filled from the last value.
    public static class lcmAM extends AnalysisModule implements Serializable {

        protected lcmAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            boolean anyInput = true; //Is there any buffer left with values?
            int i = 0;
            while (anyInput) { //For each value of output buffer
                anyInput = false;

                long a = 1;
                long b = 1;

                for (int j = 0; j < inputArrays.size() && j < 2; j++) { //For each input buffer
                    Double in[] = inputArrays.get(j);
                    int size = inputArraySizes.get(j);
                    if (in == null || size == 0) {
                        anyInput = false;
                        break;
                    }
                    if (i < size) { //New value from this iterator
                        if (j == 0)
                            a = Math.round(in[i]);
                        else
                            b = Math.round(in[i]);
                        anyInput = true;
                    } else {
                        if (j == 0)
                            a = Math.round(in[size-1]);
                        else
                            b = Math.round(in[size-1]);
                    }
                }
                if (anyInput) { //There was a new value. Append the result.
                    long a0 = a;
                    long b0 = b;
                    while (b > 0) {
                        long tmp = b;
                        b = a % b;
                        a = tmp;
                    }
                    outputs.get(0).append(a0*(b0/a));
                } else //No values left. Let's stop here...
                    break;
                i++;
            }
        }
    }

    // Calculate the absolute of a single input value.
    // The output has the length of the input buffer or at 1 for a value.
    public static class absAM extends AnalysisModule implements Serializable {

        protected absAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            Double array[] = inputArrays.get(0);
            int size = inputArraySizes.get(0);
            for (int i = 0; i < size; i++)
                outputs.get(0).append(Math.abs(array[i]));
        }
    }

    // Calculate the nearest integer (or ceil/floor if set by an attribute)
    // The output has the length of the input buffer or at 1 for a value.
    public static class roundAM extends AnalysisModule implements Serializable {
        boolean floor, ceil;

        protected roundAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, boolean floor, boolean ceil) {
            super(experiment, inputs, outputs);
            this.floor = floor;
            this.ceil = ceil;
            useArray = true;
        }

        @Override
        protected void update() {
            Double array[] = inputArrays.get(0);
            int size = inputArraySizes.get(0);
            for (int i = 0; i < size; i++) {
                if (!floor && !ceil)
                    outputs.get(0).append(Math.round(array[i]));
                else if (floor) {
                    outputs.get(0).append(Math.floor(array[i]));
                } else {
                    outputs.get(0).append(Math.ceil(array[i]));
                }
            }
        }
    }

    // Calculate the natural logarithm
    // The output has the length of the input buffer or at 1 for a value.
    public static class logAM extends AnalysisModule implements Serializable {

        protected logAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            Double array[] = inputArrays.get(0);
            int size = inputArraySizes.get(0);
            for (int i = 0; i < size; i++) {
                outputs.get(0).append(Math.log(array[i]));
            }
        }
    }

    // Calculate the sine of a single input value.
    // The output has the length of the input buffer or at 1 for a value.
    public static class sinAM extends AnalysisModule implements Serializable {
        boolean deg;

        protected sinAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, boolean deg) {
            super(experiment, inputs, outputs);
            useArray = true;
            this.deg = deg;
        }

        @Override
        protected void update() {
            Double array[] = inputArrays.get(0);
            int size = inputArraySizes.get(0);
            if (deg) {
                for (int i = 0; i < size; i++)
                    outputs.get(0).append(Math.sin(Math.PI / 180. * array[i]));
            } else {
                for (int i = 0; i < size; i++)
                    outputs.get(0).append(Math.sin(array[i]));
            }
        }
    }

    // Calculate the cosine of a single input value.
    // The output has the length of the input buffer or at 1 for a value.
    public static class cosAM extends AnalysisModule implements Serializable {
        boolean deg;

        protected cosAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, boolean deg) {
            super(experiment, inputs, outputs);
            useArray = true;
            this.deg = deg;
        }

        @Override
        protected void update() {
            Double array[] = inputArrays.get(0);
            int size = inputArraySizes.get(0);
            if (deg) {
                for (int i = 0; i < size; i++)
                    outputs.get(0).append(Math.cos(Math.PI / 180. * array[i]));
            } else {
                for (int i = 0; i < size; i++)
                    outputs.get(0).append(Math.cos(array[i]));
            }
        }
    }


    // Calculate the tangens of a single input value.
    // The output has the length of the input buffer or at 1 for a value.
    public static class tanAM extends AnalysisModule implements Serializable {
        boolean deg;

        protected tanAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, boolean deg) {
            super(experiment, inputs, outputs);
            useArray = true;
            this.deg = deg;
        }

        @Override
        protected void update() {
            Double array[] = inputArrays.get(0);
            int size = inputArraySizes.get(0);
            if (deg) {
                for (int i = 0; i < size; i++)
                    outputs.get(0).append(Math.tan(Math.PI / 180. * array[i]));
            } else {
                for (int i = 0; i < size; i++)
                    outputs.get(0).append(Math.tan(array[i]));
            }
        }
    }

    public static class sinhAM extends AnalysisModule implements Serializable {
        protected sinhAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            Double array[] = inputArrays.get(0);
            int size = inputArraySizes.get(0);
            for (int i = 0; i < size; i++)
                outputs.get(0).append(Math.sinh(array[i]));
        }
    }

    public static class coshAM extends AnalysisModule implements Serializable {

        protected coshAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            Double array[] = inputArrays.get(0);
            int size = inputArraySizes.get(0);
            for (int i = 0; i < size; i++)
                outputs.get(0).append(Math.cosh(array[i]));
        }
    }

    public static class tanhAM extends AnalysisModule implements Serializable {

        protected tanhAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            Double array[] = inputArrays.get(0);
            int size = inputArraySizes.get(0);
            for (int i = 0; i < size; i++)
                outputs.get(0).append(Math.tanh(array[i]));
        }
    }

    public static class asinAM extends AnalysisModule implements Serializable {
        boolean deg;

        protected asinAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, boolean deg) {
            super(experiment, inputs, outputs);
            useArray = true;
            this.deg = deg;
        }

        @Override
        protected void update() {
            Double array[] = inputArrays.get(0);
            int size = inputArraySizes.get(0);
            if (deg) {
                for (int i = 0; i < size; i++)
                    outputs.get(0).append(180. / Math.PI * Math.asin(array[i]));
            } else {
                for (int i = 0; i < size; i++)
                    outputs.get(0).append(Math.asin(array[i]));
            }
        }
    }

    public static class acosAM extends AnalysisModule implements Serializable {
        boolean deg;

        protected acosAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, boolean deg) {
            super(experiment, inputs, outputs);
            useArray = true;
            this.deg = deg;
        }

        @Override
        protected void update() {
            Double array[] = inputArrays.get(0);
            int size = inputArraySizes.get(0);
            if (deg) {
                for (int i = 0; i < size; i++)
                    outputs.get(0).append(180. / Math.PI * Math.acos(array[i]));
            } else {
                for (int i = 0; i < size; i++)
                    outputs.get(0).append(Math.acos(array[i]));
            }
        }
    }

    public static class atanAM extends AnalysisModule implements Serializable {
        boolean deg;

        protected atanAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, boolean deg) {
            super(experiment, inputs, outputs);
            useArray = true;
            this.deg = deg;
        }

        @Override
        protected void update() {
            Double array[] = inputArrays.get(0);
            int size = inputArraySizes.get(0);
            if (deg) {
                for (int i = 0; i < size; i++)
                    outputs.get(0).append(180. / Math.PI * Math.atan(array[i]));
            } else {
                for (int i = 0; i < size; i++)
                    outputs.get(0).append(Math.atan(array[i]));
            }
        }
    }

    public static class atan2AM extends AnalysisModule implements Serializable {
        boolean deg;

        protected atan2AM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, boolean deg) {
            super(experiment, inputs, outputs);
            useArray = true;
            this.deg = deg;
        }

        @Override
        protected void update() {
            Double array[] = inputArrays.get(0);
            Double array2[] = inputArrays.get(1);
            int size = inputArraySizes.get(0);
            if (size > inputArraySizes.get(1))
                size = inputArraySizes.get(1);
            if (deg) {
                for (int i = 0; i < size; i++)
                    outputs.get(0).append(180. / Math.PI * Math.atan2(array[i], array2[i]));
            } else {
                for (int i = 0; i < size; i++)
                    outputs.get(0).append(Math.atan2(array[i], array2[i]));
            }
        }
    }

    //Get the first value of the dataset.
    public static class firstAM extends AnalysisModule implements Serializable {

        protected firstAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            //Just get the first value and append it to each buffer
            for (int i = 0; i < outputs.size(); i++) {
                if (outputs.get(i) != null && i < inputArrays.size() && inputArrays.get(i) != null && inputArraySizes.get(i) > 0) {
                    outputs.get(i).append(inputArrays.get(i)[0]);
                }
            }

        }
    }

    //Get the maximum of the whole dataset.
    //input1 is y
    //input2 is x (if ommitted, it will be filled with 1, 2, 3, ...)
    //output1 will receive a single value, the maximum y
    //output2 (if used) will receive a single value, the x of the maximum
    //input1 and output1 are mandatory, input1 and input2 are optional.
    //If the parameter "multiple" is set, this module will output multiple local maxima (and their positions)
    //In multiple mode input3 may set a threshold: A local maximum will be searched in ranges of consecutive values above the threshold, Default: 0
    public static class maxAM extends AnalysisModule implements Serializable {
        private boolean multiple = false;

        protected maxAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, boolean multiple) {
            super(experiment, inputs, outputs);
            this.multiple = multiple;
        }

        @Override
        protected void update() {
            double threshold = 0.;

            //Get iterators, values do not make sense here.
            Vector<Iterator> its = new Vector<>();
            for (int i = 0; i < 2; i++) {
                if (inputs.get(i) != null)
                    its.add(inputs.get(i).getIterator());
                else
                    its.add(null);
            }

            if (multiple && inputs.size() > 2 && inputs.get(2) != null)
                threshold = inputs.get(2).getValue();

            double max = Double.NEGATIVE_INFINITY; //This will hold the maximum value
            double x = Double.NEGATIVE_INFINITY; //The x location of the maximum
            double currentX = -1; //Current x during iteration

            while (its.get(1).hasNext()) { //For each value of input1
                double v = (double)its.get(1).next();

                //if input2 is given set x to this value. Otherwise generate x by incrementing it by 1.
                if (its.get(0) != null && its.get(0).hasNext())
                    currentX = (double)its.get(0).next();
                else
                    currentX += 1;

                if (multiple && v < threshold) {
                    if (!Double.isInfinite(x)) {
                        if (outputs.size() > 0 && outputs.get(0) != null) {
                            outputs.get(0).append(max);
                        }
                        if (outputs.size() > 1 && outputs.get(1) != null) {
                            outputs.get(1).append(x);
                        }
                        max = Double.NEGATIVE_INFINITY;
                        x = Double.NEGATIVE_INFINITY;
                    }
                } else if (v > max) {
                    //Set maximum and location of maximum
                    max = v;
                    x = currentX;
                }
            }

            //Done. Append result to output1 and output2 if used.
            if (!Double.isInfinite(x)) {
                if (outputs.size() > 0 && outputs.get(0) != null) {
                    outputs.get(0).append(max);
                }
                if (outputs.size() > 1 && outputs.get(1) != null) {
                    outputs.get(1).append(x);
                }
            }

        }
    }

    //Get the minimum of the whole dataset.
    //input1 is y
    //input2 is x (if ommitted, it will be filled with 1, 2, 3, ...)
    //output1 will receive a single value, the minimum y
    //output2 (if used) will receive a single value, the x of the minimum
    //input1 and output1 are mandatory, input1 and input2 are optional.
    //If the parameter "multiple" is set, this module will output multiple local minima (and their positions)
    //In multiple mode input3 may set a threshold: A local minimum will be searched in ranges of consecutive values below the threshold, Default: 0
    public static class minAM extends AnalysisModule implements Serializable {
        private boolean multiple = false;

        protected minAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, boolean multiple) {
            super(experiment, inputs, outputs);
            this.multiple = multiple;
        }

        @Override
        protected void update() {
            double threshold = 0.;

            //Get iterators, values do not make sense here.
            Vector<Iterator> its = new Vector<>();
            for (int i = 0; i < 2; i++) {
                if (inputs.get(i) != null)
                    its.add(inputs.get(i).getIterator());
                else
                    its.add(null);
            }

            if (multiple && inputs.size() > 2 && inputs.get(2) != null)
                threshold = inputs.get(2).getValue();

            double min = Double.POSITIVE_INFINITY; //This will hold the minimum value
            double x = Double.NEGATIVE_INFINITY; //The x location of the minimum
            double currentX = -1; //Current x during iteration

            while (its.get(1).hasNext()) { //For each value of input1
                double v = (double)its.get(1).next();

                //if input2 is given set x to this value. Otherwise generate x by incrementing it by 1.
                if (its.get(0) != null && its.get(0).hasNext())
                    currentX = (double)its.get(0).next();
                else
                    currentX += 1;

                if (multiple && v > threshold) {
                    if (!Double.isInfinite(x)) {
                        if (outputs.size() > 0 && outputs.get(0) != null) {
                            outputs.get(0).append(min);
                        }
                        if (outputs.size() > 1 && outputs.get(1) != null) {
                            outputs.get(1).append(x);
                        }
                        min = Double.POSITIVE_INFINITY;
                        x = Double.NEGATIVE_INFINITY;
                    }
                } else if (v < min) {
                    //Set maximum and location of maximum
                    min = v;
                    x = currentX;
                }
            }

            //Done. Append result to output1 and output2 if used.
            if (!Double.isInfinite(x)) {
                if (outputs.size() > 0 && outputs.get(0) != null) {
                    outputs.get(0).append(min);
                }
                if (outputs.size() > 1 && outputs.get(1) != null) {
                    outputs.get(1).append(x);
                }
            }

        }
    }

    //Find the x value where the input crosses a given threshold.
    //input1 is y
    //input2 is x (if ommitted, it will be filled with 1, 2, 3, ...)
    //output1 will receive the x value of the point the threshold is crossed
    //The threshold is set by input3 (it defaults to 0)
    //The constructor parameter falling select positive or negative edge triggering (loaded with rising as default)
    public static class thresholdAM extends AnalysisModule implements Serializable {
        boolean falling = false; //Falling or rising trigger?

        //Extended constructor which receives the threshold and falling as well.
        protected thresholdAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, boolean falling) {
            super(experiment, inputs, outputs);
            this.falling = falling;
        }

        @Override
        protected void update() {
            //Update the threshold from buffer or convert numerical string
            double vthreshold = Double.NaN;
            if (inputs.size() > 2)
                vthreshold = inputs.get(2).getValue();
            if (Double.isNaN(vthreshold))
                vthreshold = 0.;

            //Get iterators
            Vector<Iterator> its = new Vector<>();
            for (int i = 0; i < 2; i++) {
                if (inputs.get(i) != null)
                    its.add(inputs.get(i).getIterator());
                else
                    its.add(null);
            }

            double last = Double.NaN; //Last value that did not trigger. Start with a NaN as result
            double currentX = -1; //Position of last no-trigger value.
            while (its.get(1).hasNext()) { //For each value of input1
                double v = (double)its.get(1).next();

                //if input2 exists, use this as x value, otherwise generate x by incrementing by 1
                if (its.get(0) != null && its.get(0).hasNext())
                    currentX = (double)its.get(0).next();
                else
                    currentX += 1;

                //Only trigger if the last and the current value are valid,
                if (!(Double.isNaN(last) || Double.isNaN(v))) {
                    if (falling) {
                        if (last > vthreshold && v < vthreshold) {
                            //Falling trigger and value went below threshold -> break
                            break;
                        }
                    } else {
                        if (last < vthreshold && v > vthreshold) {
                            //Rising trigger and value went above threshold -> break
                            break;
                        }
                    }
                }
                last = v;
            }
            outputs.get(0).append(currentX); //Append final x position to output1

        }
    }

    //Binning: get number of elements that fall into the intervals x0..x0+dx..x0+2dx.. (default: x0 = 0, dx = 1)
    public static class binningAM extends AnalysisModule implements Serializable {

        protected binningAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Iterator it = inputs.get(0).getIterator();
            double x0 = 0.;
            if (inputs.size() > 1 && inputs.get(1) != null)
                x0 = inputs.get(1).getValue();
            if (Double.isNaN(x0))
                x0 = 0.;

            double dx = 1.;
            if (inputs.size() > 2 && inputs.get(2) != null)
                dx = inputs.get(2).getValue();
            if (Double.isNaN(dx))
                dx = 1.;

            Vector<Double> binStarts = new Vector<>();
            Vector<Double> binCounts = new Vector<>();

            while (it.hasNext()) {
                double v = (double)it.next();
                if (Double.isNaN(v) || Double.isInfinite(v))
                    continue;
                int binIndex = (int)((v-x0)/dx);
                if (binStarts.size() == 0) {
                    binStarts.add(x0+binIndex*dx);
                    binCounts.add(1.);
                } else {
                    int firstBinIndex = (int)Math.round((binStarts.get(0)-x0)/dx);
                    while (binIndex > firstBinIndex + binStarts.size() - 1) {
                        binStarts.add(x0+(firstBinIndex+binStarts.size())*dx);
                        binCounts.add(0.);
                    }
                    while (binIndex < firstBinIndex) {
                        binStarts.insertElementAt(x0+(firstBinIndex-1)*dx,0);
                        binCounts.insertElementAt(0.,0);
                        firstBinIndex = (int)Math.round((binStarts.get(0)-x0)/dx);
                    }
                    binCounts.set(binIndex-firstBinIndex,binCounts.get(binIndex-firstBinIndex)+1);
                }
            }

            outputs.get(0).append(binStarts.toArray(new Double[0]), binStarts.size());
            outputs.get(1).append(binCounts.toArray(new Double[0]), binCounts.size());

        }
    }

    //map: Combine unsorted and redundant x, y and z data into output suitable for map graphs
    //All inputs are required except for z. But z can only be left out if zmode is set to "count".
    //input1: mapWidth - Number of values for each line (along x axis). This must be the same as the mapWidth of the graph
    //input2: xmin
    //input3: xmax
    //input4: mapHeight - Number of values for each column (along y axis).
    //input5: ymin
    //input6: ymax
    //input7: x
    //input8: y
    //input9: z
    //output1: x
    //output2: y
    //output3: z
    //Parameters:
    //zMode - can be "count", "sum" or "average". "count" counts the number of times x and y combinations fall into a bin (z is ignored here). "sum" sums up all z values that fall into the same bin. "average" averages the z values of a single bin.
    public static class mapAM extends AnalysisModule implements Serializable {

        public enum ZMode {
            count, sum, average;
        }

        ZMode zMode = ZMode.count;

        protected mapAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, ZMode zMode) {
            super(experiment, inputs, outputs);
            this.zMode = zMode;
            useArray = true;
        }

        @Override
        protected void update() {
            if ((inputArrays.size() < 9 && zMode != ZMode.count) || inputArrays.size() < 8)
                return;

            if (inputArraySizes.get(0) < 1 || inputArraySizes.get(1) < 1 || inputArraySizes.get(2) < 1
                || inputArraySizes.get(3) < 1 || inputArraySizes.get(4) < 1 || inputArraySizes.get(5) < 1)
                return;

            int mapWidth = inputArrays.get(0)[0].intValue();
            double minx = inputArrays.get(1)[0];
            double maxx = inputArrays.get(2)[0];

            int mapHeight = inputArrays.get(3)[0].intValue();
            double miny = inputArrays.get(4)[0];
            double maxy = inputArrays.get(5)[0];

            int n = Math.min(inputArraySizes.get(6), inputArraySizes.get(7));
            if (zMode != ZMode.count)
                n = Math.min(n, inputArraySizes.get(8));

            Double[] xin = inputArrays.get(6);
            Double[] yin = inputArrays.get(7);
            Double[] zin = inputArrays.get(8);

            double[] zsumout = new double[mapHeight*mapWidth];
            int[] nout = new int[mapHeight*mapWidth];

            for (int i = 0; i < n; i++) {
                int x = (int)Math.round((mapWidth-1)*(xin[i]-minx)/(maxx-minx));
                int y = (int)Math.round((mapHeight-1)*(yin[i]-miny)/(maxy-miny));
                if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight)
                    continue;
                int index = x + y*mapWidth;
                if (zMode != ZMode.count) {
                    zsumout[index] += zin[i];
                }
                nout[index]++;
            }

            DataOutput xout = null;
            if (outputs.size() > 0)
                xout = outputs.get(0);

            DataOutput yout = null;
            if (outputs.size() > 1)
                yout = outputs.get(1);

            DataOutput zout = null;
            if (outputs.size() > 2)
                zout = outputs.get(2);

            for (int y = 0; y < mapHeight; y++) {
                for (int x = 0; x < mapWidth; x++) {
                    if (xout != null)
                        xout.append(minx + x*(maxx-minx)/((double)(mapWidth-1)));
                    if (yout != null)
                        yout.append(miny + y*(maxy-miny)/((double)(mapHeight-1)));
                    if (zout != null) {
                        switch (zMode) {
                            case count:   zout.append(nout[y*mapWidth + x]);
                                          break;
                            case sum:     zout.append(zsumout[y*mapWidth + x]);
                                          break;
                            case average: zout.append(zsumout[y*mapWidth + x] / (double)nout[y*mapWidth + x]);
                                          break;
                        }
                    }
                }
            }

        }
    }

    //Append all inputs to the output.
    public static class appendAM extends AnalysisModule implements Serializable {

        protected appendAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            for (int i = 0; i < inputs.size(); i++) { //For each input
                if (inputs.get(i).isEmpty)
                    continue;
                //Get iterator
                Iterator it = inputs.get(i).getIterator();
                if (it == null) { //non-buffer value
                    double v = inputs.get(i).getValue();
                    if (!Double.isNaN(v))
                        outputs.get(0).append(v);
                    continue;
                }
                //Append all data from input to the output buffer
                while (it.hasNext()) {
                    outputs.get(0).append((double)it.next());
                }
            }

        }
    }

    //Reduce: Combine neighboring values to a smaller array by an integer factor, either skipping values inbetween or summing them (also stretch values if there are too few)
    public static class reduceAM extends AnalysisModule implements Serializable {

        boolean averageX = false;
        boolean sumY = false;
        boolean averageY = false;

        protected reduceAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, boolean averageX, boolean sumY, boolean averageY) {
            super(experiment, inputs, outputs);
            this.averageX = averageX;
            this.sumY = sumY;
            this.averageY = averageY;
        }

        @Override
        protected void update() {
            double inFactor;
            if (inputs.size() > 0 && inputs.get(0) != null)
                inFactor = inputs.get(0).getValue();
            else
                return;


            Iterator itx;
            Iterator ity = null;
            if (inputs.size() > 1 && inputs.get(1) != null)
                itx = inputs.get(1).getIterator();
            else
                return;

            if (inputs.size() > 2 && inputs.get(2) != null)
                ity = inputs.get(2).getIterator();

            if (inFactor > 1) {
                int factor = (int)Math.round(inFactor);
                while (itx.hasNext()) {
                    double newx = 0.;
                    double newy = 0.;
                    for (int i = 0; i < factor; i++) {
                        if (!itx.hasNext())
                            break;
                        double x = (double) itx.next();
                        double y;
                        if (ity != null && ity.hasNext())
                            y = (double) ity.next();
                        else
                            y = 0.;
                        if (i == 0) {
                            newx = x;
                            newy = y;
                        } else {
                            if (sumY || averageY)
                                newy += y;
                            if (averageX)
                                newx += x;
                        }
                    }
                    if (averageX)
                        newx /= (double) factor;
                    if (averageY)
                        newy /= (double) factor;

                    outputs.get(0).append(newx);
                    if (outputs.size() > 1)
                        outputs.get(1).append(newy);
                }
            } else {
                int factor = (int) Math.round(1. / inFactor);
                while (itx.hasNext()) {
                    double newx = (double)itx.next();
                    double newy = 0.;
                    if (ity != null && ity.hasNext())
                        newy = (double)ity.next();
                    for (int i = 0; i < factor; i++) {
                        outputs.get(0).append(newx);
                        if (outputs.size() > 1)
                            outputs.get(1).append(newy);
                    }
                }
            }
        }
    }

    //Calculate FFT of single input
    //If the input length is not a power of two the input will be filled with zeros until it is a power of two
    public static class fftAM extends AnalysisModule implements Serializable {
        private FFT fft;

        protected fftAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);

            useArray = true;
            if (!nativeLib)
                fft = new FFT();
        }

        @Override
        protected void update() {

            if (nativeLib) {

                if (inputArrays.size() == 0)
                    return;

                int size = inputArraySizes.get(0);
                if (size < 2)
                    return;

                final float xy[] = new float[2 * size];

                for (int i = 0; i < size; i++) {
                    xy[2 * i] = inputArrays.get(0)[i].floatValue();
                    xy[2 * i + 1] = (inputArrays.size() > 1 && inputArraySizes.get(1) > i ? inputArrays.get(1)[i].floatValue() : 0.f);
                }

                fftw3complex(xy, size);

                //Append the real part of the result to output1 and the imaginary part to output2 (if used)
                for (int i = 0; i < size; i++) {
                    if (outputs.size() > 0 && outputs.get(0) != null)
                        outputs.get(0).append(xy[2 * i]);
                    if (outputs.size() > 1 && outputs.get(1) != null)
                        outputs.get(1).append(xy[2 * i + 1]);
                }
            } else {

                int size = inputArrays.get(0).length;
                if (size < 2)
                    return;

                if (fft.n != size) {
                    fft.prepare(size);
                }

                Double x[] = Arrays.copyOf(inputArrays.get(0), fft.np2);
                Double y[];
                if (inputArrays.size() > 1)
                    y = Arrays.copyOf(inputArrays.get(1), fft.np2);
                else
                    y = new Double[fft.np2];

                //Fill any unused inputs with zeros
                for (int i = 0; i < fft.np2; i++) {
                    if (x[i] == null)
                        x[i] = 0.;
                    if (y[i] == null)
                        y[i] = 0.;
                }

                fft.calculate(x, y);

                //Append the real part of the result to output1 and the imaginary part to output2 (if used)
                for (int i = 0; i < size; i++) {
                    if (outputs.size() > 0 && outputs.get(0) != null)
                        outputs.get(0).append(x[i]);
                    if (outputs.size() > 1 && outputs.get(1) != null)
                        outputs.get(1).append(y[i]);
                }

            }
        }
    }

    //Calculate the autocorrelation
    //input1 is y values
    //input2 is x values (optional, set to 0,1,2,3,4... if left out)
    //output1 is y of autocorrelation
    //output2 is relative x (displacement of autocorrelation in units of input2)
    //A min and max can be set through inputs as well, which limit the x-range used for calculation
    public static class autocorrelationAM extends AnalysisModule implements Serializable {

        protected autocorrelationAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            double mint, maxt;

            //Update min and max as they might come from a dataBuffer
            if (inputArrays.size() < 3 || inputArrays.get(2) == null || inputArraySizes.get(2) == 0)
                mint = Double.NEGATIVE_INFINITY; //not set by user, set to -inf so it has no effect
            else
                mint = inputArrays.get(2)[inputArraySizes.get(2)-1];

            if (inputArrays.size() < 4 || inputArrays.get(3) == null || inputArraySizes.get(3) == 0)
                maxt = Double.POSITIVE_INFINITY; //not set by user, set to +inf so it has no effect
            else
                maxt = inputArrays.get(3)[inputArraySizes.get(3)-1];

            Double y[] = inputArrays.get(1);

            int size = inputArraySizes.get(1);
            Double x[];
            if (inputArrays.get(0) != null) {
                x = inputArrays.get(0);
                if (x.length < size)
                    size = x.length;
                for (int i = 1; i < size; i++) {
                    x[i] -= x[0];
                }
                if (size > 0)
                    x[0] = 0.;
            } else {
                x = new Double[size]; //Relative x (the displacement in the autocorrelation). This has to be filled from input2 or manually with 1,2,3...
                //There is no input2. Let's fill it with 0,1,2,3,4....
                for (int i = 0; i < size; i++) {
                    x[i] = (double)i;
                }
            }

            //The actual calculation
            for (int i = 0; i < size; i++) { //Displacement i for each value of input1
                if (x[i] < mint || x[i] > maxt) //Skip this, if it should be filtered
                    continue;

                double sum = 0.;
                for (int j = 0; j < size-i; j++) { //For each value of input1 minus the current displacement
                    sum += y[j]*y[j+i]; //Product of normal and displaced data
                }
                sum /= (double)(size-i); //Normalize to the number of values at this displacement

                //Append y output to output1 and x to output2 (if used)
                if (outputs.size() > 0 && outputs.get(0) != null)
                    outputs.get(0).append(sum);
                if (outputs.size() > 1 && outputs.get(1) != null)
                    outputs.get(1).append(x[i]);
            }
        }
    }

    //Calculate the periodicity over time by doing autocorrelations on a series of subsets of the input data
    //input1 is x values
    //input2 is y values
    //input3 is step distance dx in samples
    //input4 is step overlap in samples (optional, default: 0)
    //input5 is the minimum period in samples (optional, default: 0)
    //input6 is the maximum period in samples (optional, default: +Inf)
    //input6 is the precision in samples (optional, default: 1)
    //output1 is the periodicity in units of input1
    public static class periodicityAM extends AnalysisModule implements Serializable {

        protected periodicityAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            //Get arrays for random access
            Double x[] = inputs.get(0).buffer.getArray();
            Double y[] = inputs.get(1).buffer.getArray();

            int n = inputs.get(1).buffer.getFilledSize();

            //Get dx and overlap
            int dx = (int)inputs.get(2).getValue();
            if (dx <= 0)
                return;

            //Overlap is optional...
            int overlap = 0;
            if (inputs.size() >= 4 && inputs.get(3) != null)
                overlap = (int)inputs.get(3).getValue();

            boolean userSelectedRange = false;

            //min period is optional...
            int minPeriod = 0;
            if (inputs.size() >= 5 && inputs.get(4) != null) {
                minPeriod = (int) Math.floor(inputs.get(4).getValue());
                userSelectedRange = true;
            }

            //max period is optional...
            int maxPeriod = Integer.MAX_VALUE;
            if (inputs.size() >= 6 && inputs.get(5) != null) {
                maxPeriod = (int) Math.ceil(inputs.get(5).getValue());
                userSelectedRange = true;
            }

            //The actual calculation
            for (int stepX = 0; stepX <= n - dx; stepX += dx) {
                //Calculate actual autocorrelation range as it might be cut off at the edges
                int x1 = stepX - overlap;
                if (x1 < 0)
                    x1 = 0;

                int x2 = stepX + dx + overlap;
                if (x2 > n)
                    x2 = n;

                if (maxPeriod > x2-x1)
                    maxPeriod = x2-x1;

                int firstNegative = -1;
                int maxPosition = -1;
                double maxValue = Double.NEGATIVE_INFINITY;
                double maxValueLeft = Double.NEGATIVE_INFINITY;
                double maxValueRight = Double.NEGATIVE_INFINITY;
                double lastSum = Double.NEGATIVE_INFINITY;

                double step = 1;
                if (!userSelectedRange)
                    step = 2; //Until we find the first negative value, we can go faster...

                for (int i = minPeriod; i < maxPeriod; i += step) { //Displacement i for each value of input1
                    double sum = 0.;
                    for (int j = x1; j < x2 - i; j++) { //For each value of input1 minus the current displacement
                        sum += y[j] * y[j + i]; //Product of normal and displaced data
                    }
                    sum /= (double) (x2-x1-i); //Normalize to the number of values at this displacement

                    if (!userSelectedRange && firstNegative < 0) {
                        if (sum < 0) { //So, this is the first negative one... We can now skip ahead to 3 times this position and work more precisely from there.
                            firstNegative = i;
                            i = 3*firstNegative+1;
                            step = 1;
                        }
                    } else if (!userSelectedRange && i > 5 * firstNegative) { //We have passed the first period. Further maxima can only be found on the next period and we are not interested in this...
                        break;
                    } else if (userSelectedRange || i > 3 * firstNegative) {
                        if (sum > maxValue) {
                            maxValue = sum;
                            maxPosition = i;
                            maxValueLeft = lastSum;
                            maxValueRight = Double.NEGATIVE_INFINITY;
                        } else if (i == maxPosition + 1) {
                            maxValueRight = sum;
                        }
                    }
                    lastSum = sum;
                }

                double xMax = Double.NaN;
                if (maxPosition > 0 && maxValue > 0 && maxValueLeft > 0 && maxValueRight > 0) {
                    double dy = 0.5 * (maxValueRight - maxValueLeft);
                    double d2y = 2*maxValue - maxValueLeft - maxValueRight;
                    double m = dy / d2y;
                    xMax = x[x1+maxPosition] + 0.5*m*(x[x1+maxPosition+1] - x[x1+maxPosition-1]) - x[x1];
                }
//Log.d("test", "min: " + minPeriod + ", max: " + maxPeriod + ", x1: " + x1 + ", pos: " + maxPosition + ", period: " + xMax);
                if (outputs.size() > 0 && outputs.get(0) != null)
                    outputs.get(0).append(x[x1]);
                if (outputs.size() > 1 && outputs.get(1) != null)
                    outputs.get(1).append(xMax);
            }
        }
    }

    //Simple differentiation by calculating the difference of neighboring points
    //The resulting array has exactly one element less than the input array
    public static class differentiateAM extends AnalysisModule implements Serializable {

        protected differentiateAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            double v, last = Double.NaN;
            boolean first = true;

            //The actual calculation
            Iterator it = inputs.get(0).getIterator();
            if (it == null) //non-buffer values are ignored
                return;
            //Calculate difference of neighbors
            while (it.hasNext()) {
                if (first) { //The first value is just stored
                    last = (double)it.next();
                    first = false;
                    continue;
                }
                v = (double)it.next();
                outputs.get(0).append(v-last);
                last = v;
            }
        }
    }

    //Simple integration by calculating the sum of all values up to the output index
    //So, first value will be v0, second will be v0+v1, third v0+v1+v2 etc.
    //The resulting array has exactly as many elements as the input array
    public static class integrateAM extends AnalysisModule implements Serializable {

        protected integrateAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            double sum = 0.;

            //The actual calculation
            Iterator it = inputs.get(0).getIterator();
            if (it == null) //non-buffer values are ignored
                return;
            //Calculate the sum
            while (it.hasNext()) {
                sum += (double)it.next();
                outputs.get(0).append(sum);
            }
        }
    }

    //Calculate the cross correlation of two inputs
    //input1 and input2 represent the y values, there is no x input and only one output
    //The indices of the output match the offset between the inputs
    //There is no zero-padding. The smaller input is moved "along" the larger input.
    //This does not work if both have the same size. Pad one input to match the target total size first.
    //The size of the output is the difference of both input sizes.
    public static class crosscorrelationAM extends AnalysisModule implements Serializable {
        protected crosscorrelationAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            if (nativeLib) {
                int sizeA = inputArraySizes.get(0);
                int sizeB = inputArraySizes.get(1);
                int size = 2 * (sizeA + sizeB);

                if (sizeA == 0 || sizeB == 0)
                    return;

                Double[] a = inputArrays.get(0);
                Double[] b = inputArrays.get(1);

                final float af[] = new float[size];
                final float bf[] = new float[size];

                if (sizeA > sizeB) {
                    for (int i = 0; i < sizeA; i++) {
                        af[i] = a[i].floatValue();
                    }
                    for (int i = 0; i < sizeB; i++) {
                        bf[i] = b[i].floatValue();
                    }
                } else {
                    for (int i = 0; i < sizeA; i++) {
                        bf[i] = a[i].floatValue();
                    }
                    for (int i = 0; i < sizeB; i++) {
                        af[i] = b[i].floatValue();
                    }
                }

                fftw3crosscorrelation(af, bf, size);

                //Append the real part of the result to output1 and the imaginary part to output2 (if used)
                for (int i = 0; i < Math.abs(sizeA - sizeB); i++) {
                    if (outputs.size() > 0 && outputs.get(0) != null)
                        outputs.get(0).append(af[i]);
                }
            } else {

                Double a[], b[];
                int asize, bsize;
                //Put the larger input in a and the smaller one in b
                if (inputArraySizes.get(0) > inputArraySizes.get(1)) {
                    a = inputArrays.get(0);
                    asize = inputArraySizes.get(0);
                    b = inputArrays.get(1);
                    bsize = inputArraySizes.get(1);
                } else {
                    a = inputArrays.get(1);
                    asize = inputArraySizes.get(1);
                    b = inputArrays.get(0);
                    bsize = inputArraySizes.get(0);
                }

                if (asize == 0 || bsize == 0)
                    return;

                //The actual calculation
                int compRange = asize - bsize;
                for (int i = 0; i < compRange; i++) {
                    double sum = 0.;
                    for (int j = 0; j < bsize; j++) {
                        sum += a[j+i]*b[j];
                    }
                    sum /= (double)(compRange); //Normalize bynumber of values
                    outputs.get(0).append(sum);
                }
            }
        }
    }

    //Smooth data using a gauss distribution
    //Sigma defaults to 3 but can be changed.
    //Note that sigma cannot be set by a dataBuffer
    public static class gaussSmoothAM extends AnalysisModule implements Serializable {
        int calcWidth; //range to which the gauss is calculated
        double[] gauss; //Gauss-weight look-up-table

        protected gaussSmoothAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            setSigma(3); //default
        }

        //Change sigma
        protected void setSigma(double sigma) {
            this.calcWidth = (int)Math.round(sigma*3); //Adapt calculation range: 3x sigma should be plenty

            gauss = new double[calcWidth*2+1];
            double sum = 0.;
            for (int i = -calcWidth; i <= calcWidth; i++) {
                gauss[i+calcWidth] = Math.exp(-(i/sigma*i/sigma)/2.); //Gauß! We normalize numerically to make up for imprecisions due to discretization.
                sum += gauss[i+calcWidth];
            }
            for (int i = -calcWidth; i <= calcWidth; i++) {
                gauss[i+calcWidth] /= sum;
            }
        }

        @Override
        protected void update() {
            //Get array for random access
            Double y[] = inputs.get(0).getArray();

            for (int i = 0; i < y.length; i++) { //For each data-point
                double sum = 0;
                double norm = 0;
                for (int j = -calcWidth; j <= calcWidth; j++) { //For each step in the look-up-table
                    int k = i+j; //index in input that corresponds to the step in the look-up-table
                    if (k >= 0 && k < y.length) {
                        sum += gauss[j + calcWidth] * y[k]; //Add weighted contribution
                        if (i < calcWidth || i > y.length - calcWidth - 1)
                            norm += gauss[j + calcWidth];
                    }
                }
                if (i < calcWidth || i > y.length-calcWidth)
                    sum /= norm;
                outputs.get(0).append(sum); //Append the result to the output buffer
            }
        }
    }

    //Smooth data using locally estimated scatterplot smoothing (LOESS) aka local regression
    //x is the x coordinates of the input data. Has to be monotonic!
    //y is the y coordinates of the input data
    //d has to be given as the width of the tri-cubic weighting function and can be set dynamically via an input
    //xi is the x coordinates at which the loess function should be evaluated. Has to be monotonic!
    public static class loessAM extends AnalysisModule implements Serializable {
        double d;

        protected loessAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        protected double weight(double dx) {
            if (dx > d)
                return 0.0;
            double v = dx / d;
            v = 1.0 - v*v*v;
            return v*v*v;
        }

        @Override
        protected void update() {
            //Get arrays for random access
            Double x[] = inputArrays.get(0);
            Double y[] = inputArrays.get(1);
            if (inputArraySizes.get(2) == 0)
                return;
            d = inputArrays.get(2)[0];
            if (d <= 0.0 || Double.isNaN(d))
                return;
            Double xout[] = inputArrays.get(3);
            int incount = Math.min(inputArraySizes.get(0), inputArraySizes.get(1));
            int outcount = inputArraySizes.get(3);

            int minj = 0;

            for (int i = 0; i < outcount; i++) {
                double xi = xout[i];

                double w;
                double sw = 0.;
                double swx = 0.;
                double swxx = 0.;
                double swxxx = 0.;
                double swxxxx = 0.;
                double swy = 0.;
                double swxy = 0.;
                double swxxy = 0.;
                double dx, xj, yj, wx, wxx, wxxx;

                for (int j = minj; j < incount; j++) {
                    xj = x[j];
                    yj = y[j];
                    if (Double.isNaN(xj) || Double.isNaN(yj)) {
                        continue;
                    }
                    dx = xj-xi;
                    if (Math.abs(dx) > d) {
                        if (dx < 0) {
                            minj = j+1;
                            continue;
                        } else {
                            break;
                        }
                    }

                    w = weight(Math.abs(dx));

                    sw += w;
                    wx = w*dx;
                    swx += wx;
                    wxx = wx*dx;
                    swxx += wxx;
                    wxxx = wxx*dx;
                    swxxx += wxxx;
                    swxxxx += wxxx*dx;
                    swy += w*yj;
                    swxy += wx*yj;
                    swxxy += wxx*yj;
                }

                double a = swxx*swxxxx-swxxx*swxxx;
                double b = swxx*swxxx-swx*swxxxx;
                double c = swx*swxxx-swxx*swxx;

                double det = sw*swxx*swxxxx+2*swx*swxx*swxxx-swxx*swxx*swxx-swx*swx*swxxxx-sw*swxxx*swxxx;

                double yi0 = (a*swy + b*swxy + c*swxxy)/det;

                outputs.get(0).append(yi0); //Append the result to the output buffer

                if (outputs.size() > 1) {
                    double d = sw*swxxxx-swxx*swxx;
                    double e = swx*swxx-sw*swxxx;
                    double f = sw*swxx-swx*swx;

                    if (outputs.get(1) != null) {
                        double yi1 = (b * swy + d * swxy + e * swxxy) / det;
                        outputs.get(1).append(yi1);
                    }
                    if (outputs.size() > 2 && outputs.get(2) != null) {
                        double yi2 = (c * swy + e * swxy + f * swxxy) / det;
                        outputs.get(2).append(yi2);
                    }
                }
            }
        }
    }

    //Interpolate data
    //x is the x coordinates of the input data. Has to be monotonic!
    //y is the y coordinates of the input data
    //xi is the x coordinates at which to interpolate. Has to be monotonic!
    //Additional parameter "method" can be "previous", "next", "nearest", "linear"
    public static class interpolateAM extends AnalysisModule implements Serializable {
        public enum InterpolationMethod {
            previous, next, nearest, linear
        }

        InterpolationMethod method = InterpolationMethod.linear;

        protected interpolateAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, InterpolationMethod method) {
            super(experiment, inputs, outputs);
            this.method = method;
            useArray = true;
        }

        @Override
        protected void update() {
            Double x[] = inputArrays.get(0);
            Double y[] = inputArrays.get(1);
            Double xout[] = inputArrays.get(2);
            int incount = Math.min(inputArraySizes.get(0), inputArraySizes.get(1));
            int outcount = inputArraySizes.get(2);

            int j = 0;
            for (int i = 0; i < outcount; i++) {
                if (incount == 0) {
                    outputs.get(0).append(Double.NaN);
                    continue;
                } else if (incount == 1) {
                    outputs.get(0).append(y[0]);
                    continue;
                }
                double xi = xout[i];

                while (j < incount && x[j] < xi)
                    j++;

                if (j == 0) {
                    outputs.get(0).append(y[j]);
                    continue;
                } else if (j == incount) {
                    outputs.get(0).append(y[incount-1]);
                    continue;
                } else if (x[j] == xi) {
                    outputs.get(0).append(y[j]);
                    continue;
                }

                double yi;
                switch (method) {
                    case previous:
                        yi = y[j-1];
                        break;
                    case next:
                        yi = y[j];
                        break;
                    case nearest:
                        yi = (xi - x[j-1] < x[j] - xi) ? y[j-1] : y[j];
                        break;
                    case linear:
                        yi = y[j-1] + (y[j]-y[j-1])*(xi-x[j-1])/(x[j]-x[j-1]);
                        break;
                    default:
                        yi = Double.NaN;
                        break;
                }
                outputs.get(0).append(yi); //Append the result to the output buffer
            }
        }
    }

    //Match a couple of inputs and only return those that have a valid value in every input
    //You need exactly as many outputs as there are inputs.
    public static class matchAM extends AnalysisModule implements Serializable {

        protected matchAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            //Get iterators of all inputs (numeric string not allowed here as it makes no sense to filter static input)
            Vector<Iterator> its = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                its.add(inputs.get(i).getIterator());
            }

            double []data = new double[inputs.size()]; //Will hold values of all inputs at same index
            boolean hasNext = true; //Will be set to false if ANY of the iterators has no next item
            while (hasNext) {
                //Check if any input has a value left
                hasNext = true;
                for (Iterator it : its) {
                    if (!it.hasNext()) {
                        hasNext = false;
                        break;
                    }
                }
                if (hasNext) {
                    boolean filter = false; //Will be set to true if any input is invalid
                    for (int i = 0; i < inputs.size(); i++) { //For each input...
                        data[i] = (double) its.get(i).next();
                        if (Double.isInfinite(data[i]) || Double.isNaN(data[i])) { //Is this value valid?
                            filter = true; //Yepp, filter this index
                        }
                    }
                    if (!filter) { //Filter not triggered? Append the values of each input to the corresponding outputs.
                        for (int i = 0; i < inputs.size(); i++) {
                            if (i < outputs.size() && outputs.get(i) != null)
                                outputs.get(i).append(data[i]);
                        }
                    }
                }
            }
        }
    }


    //Filter a couple of inputs with some max and min values per input
    //For each input you can set a min and max value.
    // If tha value of any input falls outside min and max, the data at this index if discarded for all inputs
    //You need exactly as many outputs as there are inputs.
    public static class rangefilterAM extends AnalysisModule implements Serializable {

        //Constructor also takes arrays of min and max values
        protected rangefilterAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
            useArray = true;
        }

        @Override
        protected void update() {
            double[] min; //Double-valued min and max. Filled from String value / dataBuffer
            double[] max;

            int n = (inputArrays.size()-1)/3+1;
            min = new double[n];
            max = new double[n];
            inputArrays.setSize(n * 3);

            for(int i = 0; i < n; i++) {
                if (inputArrays.get(3*i+1) == null || inputArraySizes.get(3*i+1) == 0)
                    min[i] = Double.NEGATIVE_INFINITY; //Not set by user, set to -inf so it has no influence
                else {
                    min[i] = inputArrays.get(3*i+1)[inputArraySizes.get(3*i+1)-1]; //Get value from string: numeric or buffer
                }
                if (inputArrays.get(3*i+2) == null || inputArraySizes.get(3*i+2) == 0)
                    max[i] = Double.POSITIVE_INFINITY; //Not set by user, set to +inf so it has no influence
                else {
                    max[i] = inputArrays.get(3*i+2)[inputArraySizes.get(3*i+2)-1]; //Get value from string: numeric or buffer
                }
            }

            //Get iterators of all inputs (numeric string not allowed here as it makes no sense to filter static input)
            Double[][] ins = new Double[n][];
            int[] sizes = new int[n];
            for (int i = 0; i < n; i++) {
                ins[i] = inputArrays.get(3*i);
                sizes[i] = inputArraySizes.get(3*i);
            }

            double []data = new double[n]; //Will hold values of all inputs at same index
            boolean hasNext = true; //Will be set to true if ANY of the iterators has a next item (not neccessarily all of them)
            int index = 0;
            while (hasNext) {
                //Check if any input has a value left
                hasNext = false;
                for (int i = 0; i < n; i++) {
                    if (index < sizes[i])
                        hasNext = true;
                }

                if (hasNext) {
                    boolean filter = false; //Will be set to true if any input falls outside its min/max
                    for (int i = 0; i < n; i++) { //For each input...
                        if (index < sizes[i]) { //This input has a value left. Get it!
                            data[i] = ins[i][index];
                            if (data[i] < min[i] || data[i] > max[i]) { //Is this value outside its min/max?
                                filter = true; //Yepp, filter this index
                            }
                        } else
                            data[i] = Double.NaN; //No value left in input. Set this value to NaN and do not filter it
                    }
                    if (!filter) { //Filter not triggered? Append the values of each input to the corresponding outputs.
                        for (int i = 0; i < n; i++) {
                            if (i < outputs.size() && outputs.get(i) != null) {
                                outputs.get(i).append(data[i]);
                            }
                        }
                    }

                }
                index++;
            }
        }
    }

    //Create a linear ramp.
    //No input needed. The length of the output matches "length" or, if not set, the size of the output buffer
    //Defaults to a ramp from 0 to 100
    //You can set start, stop and length. The first value will match start, the last value will match stop
    //Databuffers allowed for all parameters
    public static class rampGeneratorAM extends AnalysisModule implements Serializable {
        //Defaults as string values as this might be set to a dataBuffer
        double vstart = 0.;
        double vstop = 100.;
        int vlength = -1;

        protected rampGeneratorAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            if (inputs.size() > 0 && inputs.get(0) != null)
                vstart = inputs.get(0).getValue();
            if (inputs.size() > 1 && inputs.get(1) != null)
                vstop = inputs.get(1).getValue();
            if (inputs.size() > 2 && inputs.get(2) != null)
                vlength = (int)inputs.get(2).getValue();

            //If length is not set, use the size of the output buffer
            if (vlength < 0)
                vlength = outputs.get(0).size();

            //Write ramp to output buffer
            for (int i = 0; i < vlength; i++) {
                outputs.get(0).append(vstart+(vstop-vstart)/(vlength-1)*i);
            }
        }
    }

    //Generate a constant function (you could also say: Initialize buffer to a fixed value)
    //No input needed. The length of the output matches "length" or, if not set, the size of the output buffer
    //Defaults to a value of zero
    //You can set the value and length.
    //Databuffers allowed for all parameters
    public static class constGeneratorAM extends AnalysisModule implements Serializable {
        //Defaults as string values as this might be set to a dataBuffer
        double vvalue = 0.;
        int vlength = -1;

        protected constGeneratorAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            if (inputs.size() > 0 && inputs.get(0) != null)
                vvalue = inputs.get(0).getValue();
            if (inputs.size() > 1 && inputs.get(1) != null)
                vlength = (int)inputs.get(1).getValue();

            //If length is not set, use the size of the output buffer
            if (vlength < 0)
                vlength = outputs.get(0).size();

            //Write values to output
            for (int i = 0; i < vlength; i++) {
                outputs.get(0).append(vvalue);
            }
        }
    }

    //Return a subrange of multiple inputs, starting at index start and returning a total of length
    //or starting at index start and stopping at index end-1
    public static class subrangeAM extends AnalysisModule implements Serializable {

        protected subrangeAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {

            int start = 0;
            int end = -1;
            if (inputs.size() > 0 && inputs.get(0) != null)
                start = (int)inputs.get(0).getValue();
            if (inputs.size() > 1 && inputs.get(1) != null)
                end = (int)inputs.get(1).getValue();
            if (inputs.size() > 2 && inputs.get(2) != null)
                end = start + (int)inputs.get(2).getValue();

            if (start < 0) {
                start = 0;
            }
            if (end < 0) {
                end = 0;
                for (int i = 3; i < inputs.size(); i++)
                    if (inputs.get(i) != null && inputs.get(i).getFilledSize() > end)
                        end = inputs.get(i).getFilledSize();
            }

            for (int i = 3; i < inputs.size(); i++) {
                if (outputs.size() > i-3 && outputs.get(i-3) != null && inputs.get(i) != null) {
                    int thisEnd = Math.min(end,inputs.get(i).getFilledSize());
                    if (start < thisEnd)
                        outputs.get(i-3).append(Arrays.copyOfRange(inputs.get(i).getArray(), start, thisEnd), thisEnd-start);
                }
            }
        }
    }

    //Sort the input buffers such that the content of the first supplied buffer is ascending
    //Setting descending to true reverses the output
    //The output length will correspond to the shortest of the input buffers
    public static class sortAM extends AnalysisModule implements Serializable {
        boolean descending = false;

        protected sortAM(PhyphoxExperiment experiment, Vector<DataInput> inputs, Vector<DataOutput> outputs, boolean descending) {
            super(experiment, inputs, outputs);
            this.descending = descending;
            useArray = true;
        }

        @Override
        protected void update() {
            Double[] mainArray = inputArrays.get(0);
            int n = inputArraySizes.get(0);
            for (int i = 1; i < inputArraySizes.size(); i++)
                if (inputArraySizes.get(i) < n)
                    n = inputArraySizes.get(i);

            Integer[] indexArray = new Integer[n];
            for (int i = 0; i < n; i++)
                indexArray[i] = i;

            class IndexArrayLookupComparator implements Comparator<Integer> {
                @Override
                public int compare(Integer i1, Integer i2) {
                    if (descending)
                        return mainArray[i2].compareTo(mainArray[i1]);
                    else
                        return mainArray[i1].compareTo(mainArray[i2]);
                }
            }

            Arrays.sort(indexArray, new IndexArrayLookupComparator());

            for (int i = 0; i < n; i++) {
                for (int out = 0; out < outputs.size() && out < inputArrays.size(); out++) {
                    outputs.get(out).append(inputArrays.get(out)[indexArray[i]]);
                }
            }

        }
    }

}
