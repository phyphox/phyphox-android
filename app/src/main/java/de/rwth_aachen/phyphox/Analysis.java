package de.rwth_aachen.phyphox;

import java.util.Iterator;
import java.util.Vector;

// The analysis class is used to to do math operations on dataBuffers

public class Analysis {

    //analysisModule is is the prototype from which each analysis module inherits its interface
    public static class analysisModule {
        protected Vector<String> inputs; //The key of input dataBuffers
        protected Vector<dataBuffer> outputs; //The keys of outputBuffers
        protected Vector<Double> values; //Fixed numeric values if inputs are null
        protected phyphoxExperiment experiment; //experiment reference to access buffers
        protected boolean isStatic = false; //If a module is defined as static, it will only be executed once. This is used to save performance if data does not change
        protected boolean executed = false; //This takes track if the module has been executed at all. Used for static modules.

        //Main contructor
        protected analysisModule(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            this.experiment = experiment;
            this.inputs = inputs;
            this.outputs = outputs;

            //Interpret the input strings. If it is not a valid identifier (not starting with a number and only consisting of [a-zA-Z0-9_] try to interpret it as a number
            this.values = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                if (!phyphoxFile.isValidIdentifier(inputs.get(i))) {
                    try {
                        //Try to interpret the input as a number
                        this.values.add(Double.valueOf(inputs.get(i)));
                    } catch (Exception e) {
                        //No number and no valid buffer - set to NaN
                        this.values.add(Double.NaN);
                    }
                    //This is interpreted as a fixed value, so set the input to null to indicate that there is no buffer behind it.
                    this.inputs.set(i, null);
                } else
                    //This is a buffer. Just fill the values list with a dummy value.
                    this.values.add(0.);
            }
        }

        //Interface to set the module to static mode. In this mode, the module will only be executed once to initialize the buffer - no updates to save performance
        protected void setStatic(boolean isStatic) {
            this.isStatic = isStatic;
            for (dataBuffer output : outputs) {
                if (output != null)
                    output.setStatic(isStatic);
            }
        }

        //Wrapper to update the module only if it is not static or has never been executed
        protected void updateIfNotStatic() {
            if (!(isStatic && executed)) {
                update();
                executed = true;
            }
        }

        //Read a single value from an input string.
        protected double getSingleValueFromUserString(String key) {
            //Try to read the buffer
            dataBuffer db = experiment.getBuffer(key);
            if (db != null)
                return db.value;
            else {
                //Buffer could not be read. Is the string numeric?
                try {
                    return Double.valueOf(key);
                } catch (Exception e) {
                    //Neither a buffer nor numeric. Return NaN
                    return Double.NaN;
                }
            }
        }

        //The main update function has to be overridden by the modules
        protected void update() {

        }
    }

    //Add input values. The output has the length of the longest input buffer or the size of the output buffer (whichever is smaller). Missing values in shorter buffers are filled from the last value.
    public static class addAM extends analysisModule {

        protected addAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>(); //Iterators for all input buffers
            Vector<Double> lastValues = new Vector<>(); //Buffer to hold last value if buffer is shorter than expected

            //Get iterators and fill history with fixed value if an input is not a buffer
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    //Not a buffer: Fixed value
                    lastValues.add(values.get(i));
                    its.add(null);
                } else {
                    //Buffer: Get iterator
                    its.add(experiment.getBuffer(inputs.get(i)).getIterator());
                    lastValues.add(0.);
                }
            }

            //Clear output
            outputs.get(0).clear();


            for (int i = 0; i < outputs.get(0).size; i++) { //For each value of output buffer
                double sum = 0;
                boolean anyInput = false; //Is there any buffer left with values?
                for (int j = 0; j < its.size(); j++) { //For each input buffer
                    if (its.get(j) != null && its.get(j).hasNext()) { //New value from this iterator
                        lastValues.set(j, (double)its.get(j).next());
                        anyInput = true;
                    }
                    sum += lastValues.get(j); //Get value even if there is no value left in the buffer
                }
                if (anyInput) //There was a new value. Append the result.
                    outputs.get(0).append(sum);
                else //No values left. Let's stop here...
                    break;
            }
        }
    }

    //Subtract input values (i1-i2-i3-i4...). The output has the length of the longest input buffer or the size of the output buffer (whichever is smaller). Missing values in shorter buffers are filled from the last value.
    public static class subtractAM extends analysisModule {

        protected subtractAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>(); //Iterators for all input buffers
            Vector<Double> lastValues = new Vector<>(); //Buffer to hold last value if buffer is shorter than expected

            //Get iterators and fill history with fixed value if an input is not a buffer
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    //Not a buffer: Fixed value
                    lastValues.add(values.get(i));
                    its.add(null);
                } else {
                    //Buffer: Get iterator
                    its.add(experiment.getBuffer(inputs.get(i)).getIterator());
                    lastValues.add(0.);
                }
            }

            //Clear output
            outputs.get(0).clear();


            for (int i = 0; i < outputs.get(0).size; i++) { //For each value of output buffer
                double sum = 0.;
                boolean anyInput = false; //Is there any buffer left with values?
                for (int j = 0; j < its.size(); j++) { //For each input buffer
                    if (its.get(j) != null && its.get(j).hasNext()) { //New value from this iterator
                        lastValues.set(j, (double)its.get(j).next());
                        anyInput = true;
                    }
                    if (j == 0)
                        sum = lastValues.get(j); //First value: Get value even if there is no value left in the buffer
                    else
                        sum -= lastValues.get(j); //Subtracted values: Get value even if there is no value left in the buffer
                }
                if (anyInput) //There was a new value. Append the result.
                    outputs.get(0).append(sum);
                else //No values left. Let's stop here...
                    break;
            }
        }
    }

    //Multiply input values. The output has the length of the longest input buffer or the size of the output buffer (whichever is smaller). Missing values in shorter buffers are filled from the last value.
    public static class multiplyAM extends analysisModule {

        protected multiplyAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>();  //Iterators for all input buffers
            Vector<Double> lastValues = new Vector<>();  //Buffer to hold last value if buffer is shorter than expected

            //Get iterators and fill history with fixed value if an input is not a buffer
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    //Not a buffer: Fixed value
                    lastValues.add(values.get(i));
                    its.add(null);
                } else {
                    //Buffer: Get iterator
                    its.add(experiment.getBuffer(inputs.get(i)).getIterator());
                    lastValues.add(0.);
                }
            }

            //Clear output
            outputs.get(0).clear();

            for (int i = 0; i < outputs.get(0).size; i++) { //For each value of output buffer
                double product = 1.;
                boolean anyInput = false; //Is there any buffer left with values?
                for (int j = 0; j < its.size(); j++) { //For each input buffer
                    if (its.get(j) != null && its.get(j).hasNext()) { //New value from this iterator
                        lastValues.set(j, (double)its.get(j).next());
                        anyInput = true;
                    }
                    product *= lastValues.get(j); //Get value even if there is no value left in the buffer
                }
                if (anyInput) //There was a new value. Append the result.
                    outputs.get(0).append(product);
                else //No values left. Let's stop here...
                    break;
            }
        }
    }


    //Divide input values (i1/i2/i3/...). The output has the length of the longest input buffer or the size of the output buffer (whichever is smaller). Missing values in shorter buffers are filled from the last value.
    public static class divideAM extends analysisModule {

        protected divideAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>(); //Iterators for all input buffers
            Vector<Double> lastValues = new Vector<>(); //Buffer to hold last value if buffer is shorter than expected

            //Get iterators and fill history with fixed value if an input is not a buffer
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    //Not a buffer: Fixed value
                    lastValues.add(values.get(i));
                    its.add(null);
                } else {
                    //Buffer: Get iterator
                    its.add(experiment.getBuffer(inputs.get(i)).getIterator());
                    lastValues.add(0.);
                }
            }

            //Clear output
            outputs.get(0).clear();

            for (int i = 0; i < outputs.get(0).size; i++) { //For each value of output buffer
                double product = 1.;
                boolean anyInput = false; //Is there any buffer left with values?
                for (int j = 0; j < its.size(); j++) { //For each input buffer
                    if (its.get(j) != null && its.get(j).hasNext()) { //New value from this iterator
                        lastValues.set(j, (double)its.get(j).next());
                        anyInput = true;
                    }
                    if (j == 0)
                        product = lastValues.get(j); //First value: Get value even if there is no value left in the buffer
                    else
                        product /= lastValues.get(j); //Divisor values: Get value even if there is no value left in the buffer
                }
                if (anyInput) //There was a new value. Append the result.
                    outputs.get(0).append(product);
                else //No values left. Let's stop here...
                    break;
            }
        }
    }

    // Calculate the power of input values. ((i1^i2)^i3)^..., but you probably only want two inputs here...
    // The output has the length of the longest input buffer or the size of the output buffer (whichever is smaller).
    // Missing values in shorter buffers are filled from the last value.
    public static class powerAM extends analysisModule {

        protected powerAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>(); //Iterators for all input buffers
            Vector<Double> lastValues = new Vector<>(); //Buffer to hold last value if buffer is shorter than expected

            //Get iterators and fill history with fixed value if an input is not a buffer
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    //Not a buffer: Fixed value
                    lastValues.add(values.get(i));
                    its.add(null);
                } else {
                    //Buffer: Get iterator
                    its.add(experiment.getBuffer(inputs.get(i)).getIterator());
                    lastValues.add(0.);
                }
            }

            //Clear output
            outputs.get(0).clear();

            for (int i = 0; i < outputs.get(0).size; i++) { //For each value of output buffer
                double power = 1.;
                boolean anyInput = false; //Is there any buffer left with values?
                for (int j = 0; j < its.size(); j++) { //For each input buffer
                    if (its.get(j) != null && its.get(j).hasNext()) { //New value from this iterator
                        lastValues.set(j, (double)its.get(j).next());
                        anyInput = true;
                    }
                    if (j == 0)
                        power = lastValues.get(j); //First value: Get value even if there is no value left in the buffer
                    else
                        power = Math.pow(power, lastValues.get(j)); //power values: Get value even if there is no value left in the buffer
                }
                if (anyInput) //There was a new value. Append the result.
                    outputs.get(0).append(power);
                else //No values left. Let's stop here...
                    break;
            }
        }
    }

    // Calculate the greatest common divisor (GCD), takes exactly two inputs.
    // The output has the length of the longest input buffer or the size of the output buffer (whichever is smaller).
    // Missing values in shorter buffers are filled from the last value.
    public static class gcdAM extends analysisModule {

        protected gcdAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>(); //Iterators for all input buffers
            Vector<Double> lastValues = new Vector<>(); //Buffer to hold last value if buffer is shorter than expected

            //Get iterators and fill history with fixed value if an input is not a buffer
            for (int i = 0; i < 2; i++) {
                if (inputs.get(i) == null) {
                    //Not a buffer: Fixed value
                    lastValues.add(values.get(i));
                    its.add(null);
                } else {
                    //Buffer: Get iterator
                    its.add(experiment.getBuffer(inputs.get(i)).getIterator());
                    lastValues.add(0.);
                }
            }

            //Clear output
            outputs.get(0).clear();

            for (int i = 0; i < outputs.get(0).size; i++) { //For each value of output buffer
                boolean anyInput = false; //Is there any buffer left with values?
                for (int j = 0; j < its.size(); j++) { //For each input buffer
                    if (its.get(j) != null && its.get(j).hasNext()) { //New value from this iterator
                        lastValues.set(j, (double) its.get(j).next());
                        anyInput = true;
                    }
                }
                long a = Math.round(lastValues.get(0)); //First input
                long b = Math.round(lastValues.get(1)); //Second input

                //Euclid's algorithm (modern iterative version
                while (b > 0) {
                    long tmp = b;
                    b = a % b;
                    a = tmp;
                }

                if (anyInput) //There was a new value. Append the result.
                    outputs.get(0).append(a);
                else //No values left. Let's stop here...
                    break;
            }
        }
    }

    // Calculate the least common multiple (GCD), takes exactly two inputs.
    // The output has the length of the longest input buffer or the size of the output buffer (whichever is smaller).
    // Missing values in shorter buffers are filled from the last value.
    public static class lcmAM extends analysisModule {

        protected lcmAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>(); //Iterators for all input buffers
            Vector<Double> lastValues = new Vector<>(); //Buffer to hold last value if buffer is shorter than expected

            //Get iterators and fill history with fixed value if an input is not a buffer
            for (int i = 0; i < 2; i++) {
                if (inputs.get(i) == null) {
                    //Not a buffer: Fixed value
                    lastValues.add(values.get(i));
                    its.add(null);
                } else {
                    //Buffer: Get iterator
                    its.add(experiment.getBuffer(inputs.get(i)).getIterator());
                    lastValues.add(0.);
                }
            }

            //Clear output
            outputs.get(0).clear();

            for (int i = 0; i < outputs.get(0).size; i++) { //For each value of output buffer
                boolean anyInput = false; //Is there any buffer left with values?
                for (int j = 0; j < its.size(); j++) { //For each input buffer
                    if (its.get(j) != null && its.get(j).hasNext()) { //New value from this iterator
                        lastValues.set(j, (double) its.get(j).next());
                        anyInput = true;
                    }
                }
                long a = Math.round(lastValues.get(0)); //First input
                long b = Math.round(lastValues.get(1)); //Second input

                long a0 = a;
                long b0 = b;

                //calculate lcm from gcd: Euclid's algorithm (modern iterative version)
                while (b > 0) {
                    long tmp = b;
                    b = a % b;
                    a = tmp;
                }

                if (anyInput) //There was a new value. Append the result.
                    outputs.get(0).append(a0*(b0/a)); //lcd from gcd: a*b = gcd(a,b) * lcm(a.b)
                else //No values left. Let's stop here...
                    break;
            }
        }
    }

    // Calculate the absolute of a single input value.
    // The output has the length of the input buffer or at 1 for a value.
    public static class absAM extends analysisModule {

        protected absAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Iterator it;
            double lastValue;

            //Get value or iterator
            if (inputs.get(0) == null) {
                //value
                lastValue = values.get(0);
                it = null;
            } else {
                //iterator
                it = experiment.getBuffer(inputs.get(0)).getIterator();
                lastValue = 0.;
            }

            //Clear output
            outputs.get(0).clear();


            while (it == null || it.hasNext()) { //For each output value or at least once for values
                if (it != null && it.hasNext()) {
                    //Update lastValue if a new value exists in input buffer
                    lastValue = (double)it.next();
                }
                outputs.get(0).append(Math.abs(lastValue));
                if (it == null)
                    break;
            }
        }
    }

    // Calculate the sine of a single input value.
    // The output has the length of the input buffer or at 1 for a value.
    public static class sinAM extends analysisModule {

        protected sinAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Iterator it;
            double lastValue;

            //Get value or iterator
            if (inputs.get(0) == null) {
                //value
                lastValue = values.get(0);
                it = null;
            } else {
                //iterator
                it = experiment.getBuffer(inputs.get(0)).getIterator();
                lastValue = 0.;
            }

            //Clear output
            outputs.get(0).clear();

            while (it == null || it.hasNext()) { //For each output value or at least once for values
                if (it != null && it.hasNext()) {
                    //Update lastValue if a new value exists in input buffer
                    lastValue = (double)it.next();
                }
                outputs.get(0).append(Math.sin(lastValue));
                if (it == null)
                    break;
            }
        }
    }

    // Calculate the cosine of a single input value.
    // The output has the length of the input buffer or at 1 for a value.
    public static class cosAM extends analysisModule {

        protected cosAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Iterator it;
            double lastValue;

            //Get value or iterator
            if (inputs.get(0) == null) {
                //value
                lastValue = values.get(0);
                it = null;
            } else {
                //iterator
                it = experiment.getBuffer(inputs.get(0)).getIterator();
                lastValue = 0.;
            }

            //Clear output
            outputs.get(0).clear();

            while (it == null || it.hasNext()) { //For each output value or at least once for values
                if (it != null && it.hasNext()) {
                    //Update lastValue if a new value exists in input buffer
                    lastValue = (double)it.next();
                }
                outputs.get(0).append(Math.cos(lastValue));
                if (it == null)
                    break;
            }
        }
    }


    // Calculate the tangens of a single input value.
    // The output has the length of the input buffer or at 1 for a value.
    public static class tanAM extends analysisModule {

        protected tanAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Iterator it;
            double lastValue;

            //Get value or iterator
            if (inputs.get(0) == null) {
                //value
                lastValue = values.get(0);
                it = null;
            } else {
                //iterator
                it = experiment.getBuffer(inputs.get(0)).getIterator();
                lastValue = 0.;
            }

            //Clear output
            outputs.get(0).clear();

            while (it == null || it.hasNext()) { //For each output value or at least once for values
                if (it != null && it.hasNext()) {
                    //Update lastValue if a new value exists in input buffer
                    lastValue = (double)it.next();
                }
                outputs.get(0).append(Math.tan(lastValue));
                if (it == null)
                    break;
            }
        }
    }

    //Get the first value of the dataset.
    public static class firstAM extends analysisModule {

        protected firstAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {

            //Get iterators, values do not make sense here.
            Vector<Iterator> its = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    its.add(null);
                } else {
                    its.add(experiment.getBuffer(inputs.get(i)).getIterator());
                }
            }

            //Just get the first value and append it to each buffer
            for (int i = 0; i < outputs.size(); i++) {
                if (its.get(i) != null)
                    outputs.get(i).append((double)its.get(i).next());
            }

        }
    }

    //Get the maximum of the whole dataset.
    //input1 is y
    //input2 is x (if ommitted, it will be filled with 1, 2, 3, ...)
    //output1 will receive a single value, the maximum y
    //output2 (if used) will receive a single value, the x of the maximum
    //input1 and output1 are mandatory, input1 and input2 are optional.
    public static class maxAM extends analysisModule {

        protected maxAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {

            //Get iterators, values do not make sense here.
            Vector<Iterator> its = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    its.add(null);
                } else {
                    its.add(experiment.getBuffer(inputs.get(i)).getIterator());
                }
            }

            double max = Double.NEGATIVE_INFINITY; //This will hold the maximum value
            double x = Double.NEGATIVE_INFINITY; //The x location of the maximum
            double currentX = -1; //Current x during iteration

            while (its.get(0).hasNext()) { //For each value of input1
                double v = (double)its.get(0).next();

                //if input2 is given set x to this value. Otherwise generate x by incrementing it by 1.
                if (its.size() > 1 && its.get(1).hasNext())
                    currentX = (double)its.get(1).next();
                else
                    currentX += 1;

                //Is the current value bigger then the previous maximum?
                if (v > max) {
                    //Set maximum and location of maximum
                    max = v;
                    x = currentX;
                }
            }

            //Done. Append result to output1 and output2 if used.
            outputs.get(0).append(max);
            if (outputs.size() > 1 && outputs.get(1) != null) {
                outputs.get(1).append(x);
            }

        }
    }

    //Find the x value where the input crosses a given threshold.
    //input1 is y
    //input2 is x (if ommitted, it will be filled with 1, 2, 3, ...)
    //output1 will receive the x value of the point the threshold is crossed
    //The threshold is set in the contructor (in the loading code it defaults to 0)
    //The constructor parameter falling select positive or negative edge triggering (loaded with rising as default)
    public static class thresholdAM extends analysisModule {
        String threshold; //Threshold as string as it might be a buffer reference
        boolean falling = false; //Falling or rising trigger?

        //Extended constructor which receives the threshold and falling as well.
        protected thresholdAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs, String threshold, boolean falling) {
            super(experiment, inputs, outputs);
            this.threshold = threshold;
            this.falling = falling;
        }

        @Override
        protected void update() {
            //Update the threshold from buffer or convert numerical string
            double vthreshold = getSingleValueFromUserString(threshold);
            if (Double.isNaN(vthreshold))
                vthreshold = 0.;

            //Get iterators
            Vector<Iterator> its = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    its.add(null);
                } else {
                    its.add(experiment.getBuffer(inputs.get(i)).getIterator());
                }
            }

            double last = Double.NaN; //Last value that did not trigger. Start with a NaN as result
            double currentX = -1; //Position of last no-trigger value.
            while (its.get(0).hasNext()) { //For each value of input1
                double v = (double)its.get(0).next();

                //if input2 exists, use this as x value, otherwise generate x by incrementing by 1
                if (inputs.size() > 1 && its.get(1).hasNext())
                    currentX = (double)its.get(1).next();
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

    //Append all inputs to the output.
    public static class appendAM extends analysisModule {

        protected appendAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {

            //Clear output buffer
            outputs.get(0).clear();

            for (int i = 0; i < inputs.size(); i++) { //For each input
                //Get iterator
                Iterator it = experiment.getBuffer(inputs.get(i)).getIterator();
                if (it == null) //non-buffer values are ignored
                    continue;
                //Append all data from input to the output buffer
                while (it.hasNext()) {
                    outputs.get(0).append((double)it.next());
                }
            }

        }
    }

    //Calculate FFT of single input
    //If the input length is not a power of two the input will be filled with zeros until it is a power of two
    public static class fftAM extends analysisModule {
        private int n, np2, logn; //input size, power-of-two filled size, log2 of input size (integer)
        private double [] cos, sin; //Lookup table

        protected fftAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);

            n = experiment.getBuffer(inputs.get(0)).size; //Actual input size
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

        @Override
        protected void update() {

            //Create arrays for random access
            double x[] = new double[np2];
            double y[] = new double[np2];

            //Iterator of first input -> Re(z)
            Iterator ix = experiment.getBuffer(inputs.get(0)).getIterator();
            int i = 0;
            while (ix.hasNext())
                x[i++] = (double)ix.next();
            if (inputs.size() > 1) { //Is there imaginary input?
                //Iterator of second input -> Im(z)
                Iterator iy = experiment.getBuffer(inputs.get(1)).getIterator();
                i = 0;
                while (iy.hasNext())
                    y[i++] = (double)iy.next();
            }// else {
                //Fill y with zeros if there is no imaginary input
                //Not explicitly needed as java initializes double arrays to zero anyway
//                for (i = 0; i < np2; i++)
//                    y[i] = 0.;
//            }


            /***************************************************************
             * fft.c
             * Douglas L. Jones
             * University of Illinois at Urbana-Champaign
             * January 19, 1992
             * http://cnx.rice.edu/content/m12016/latest/
             *
             *   fft: in-place radix-2 DIT DFT of a complex input
             *
             *   input:
             * n: length of FFT: must be a power of two
             * m: n = 2**m
             *   input/output
             * x: double array of length n with real part of data
             * y: double array of length n with imag part of data
             *
             *   Permission to copy and use this program is granted
             *   as long as this header is included.
             ****************************************************************/

            int j,k,n1,n2,a;
            double c,s,t1,t2;

            j = 0; /* bit-reverse */
            n2 = np2/2;
            for (i=1; i < np2 - 1; i++) {
                n1 = n2;
                while ( j >= n1 ) {
                    j = j - n1;
                    n1 = n1/2;
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

            for (i=0; i < logn; i++)
            {
                n1 = n2;
                n2 = n2 + n2;
                a = 0;

                for (j=0; j < n1; j++)
                {
                    c = cos[a];
                    s = sin[a];
                    a += 1 << (logn - i - 1);

                    for (k=j; k < np2; k=k+n2)
                    {
                        t1 = c*x[k+n1] - s*y[k+n1];
                        t2 = s*x[k+n1] + c*y[k+n1];
                        x[k+n1] = x[k] - t1;
                        y[k+n1] = y[k] - t2;
                        x[k] = x[k] + t1;
                        y[k] = y[k] + t2;
                    }
                }
            }

            //Append the real part of the result to output1 and the imaginary part to output2 (if used)
            for (i = 0; i < n; i++) {
                outputs.get(0).append(x[i]);
                if (outputs.size() > 1 && outputs.get(1) != null)
                    outputs.get(1).append(y[i]);
            }
        }
    }

    //Calculate the autocorrelation
    //input1 is y values
    //input2 is x values (optional, set to 0,1,2,3,4... if left out)
    //output1 is y of autocorrelation
    //output2 is relative x (displacement of autocorrelation in units of input2)
    //A min and max can be set through setMinMaxT which limit the x-range used for calculation
    public static class autocorrelationAM extends analysisModule {
        private String smint = "";
        private String smaxt = "";

        protected autocorrelationAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        //Optionally, a min and max of the used x-range can be set (same effect as a rangefilter but more efficient as it is done in one loop)
        protected void setMinMax(String mint, String maxt) {
            this.smint = mint;
            this.smaxt = maxt;
        }

        @Override
        protected void update() {
            double mint, maxt;

            //Update min and max as they might come from a dataBuffer
            if (smint == null || smint.equals(""))
                mint = Double.NEGATIVE_INFINITY; //not set by user, set to -inf so it has no effect
            else
                mint = getSingleValueFromUserString(smint);

            if (smaxt == null || smaxt.equals(""))
                maxt = Double.POSITIVE_INFINITY; //not set by user, set to +inf so it has no effect
            else
                maxt = getSingleValueFromUserString(smaxt);

            //Get arrays for random access
            Double y[] = experiment.getBuffer(inputs.get(0)).getArray();
            Double x[] = new Double[y.length]; //Relative x (the displacement in the autocorrelation). This has to be filled from input2 or manually with 1,2,3...
            if (inputs.size() > 1) {
                //There is a second input, let's use it.
                Double xraw[] = experiment.getBuffer(inputs.get(1)).getArray();
                for (int i = 0; i < x.length; i++) {
                    if (i < xraw.length)
                        x[i] = xraw[i]-xraw[0]; //There is still input left. Use it and calculate the relative x
                    else
                        x[i] = xraw[xraw.length - 1]-xraw[0]; //No input left. This probably leads to wrong results, but let's use the last value
                }
            } else {
                //There is no input2. Let's fill it with 0,1,2,3,4....
                for (int i = 0; i < x.length; i++) {
                    x[i] = (double)i;
                }
            }

            //Clear outputs
            outputs.get(0).clear();
            if (outputs.size() > 1 && outputs.get(1) != null)
                outputs.get(1).clear();

            //The actual calculation
            for (int i = 0; i < y.length; i++) { //Displacement i for each value of input1
                if (x[i] < mint || x[i] > maxt) //Skip this, if it should be filtered
                    continue;

                double sum = 0.;
                for (int j = 0; j < y.length-i; j++) { //For each value of input1 minus the current displacement
                    sum += y[j]*y[j+i]; //Product of normal and displaced data
                }
                sum /= (double)(y.length-i); //Normalize to the number of values at this displacement

                //Append y output to output1 and x to output2 (if used)
                outputs.get(0).append(sum);
                if (outputs.size() > 1 && outputs.get(1) != null)
                    outputs.get(1).append(x[i]);
            }
        }
    }

    //Simple differentiation by calculating the difference of neighboring points
    //The resulting array has exactly one element less than the input array
    public static class differentiateAM extends analysisModule {

        protected differentiateAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            double v, last = Double.NaN;
            boolean first = true;

            //Clear output
            outputs.get(0).clear();

            //The actual calculation
            Iterator it = experiment.getBuffer(inputs.get(0)).getIterator();
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
    public static class integrateAM extends analysisModule {

        protected integrateAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            double sum = 0.;

            //Clear output
            outputs.get(0).clear();

            //The actual calculation
            Iterator it = experiment.getBuffer(inputs.get(0)).getIterator();
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
    public static class crosscorrelationAM extends analysisModule {
        protected crosscorrelationAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Double a[], b[];
            //Put the larger input in a and the smaller one in b
            if (experiment.getBuffer(inputs.get(0)).getFilledSize() > experiment.getBuffer(inputs.get(1)).getFilledSize()) {
                a = experiment.getBuffer(inputs.get(0)).getArray();
                b = experiment.getBuffer(inputs.get(1)).getArray();
            } else {
                b = experiment.getBuffer(inputs.get(0)).getArray();
                a = experiment.getBuffer(inputs.get(1)).getArray();
            }

            //Clear output
            outputs.get(0).clear();

            //The actual calculation
            int compRange = a.length - b.length;
            for (int i = 0; i < compRange; i++) {
                double sum = 0.;
                for (int j = 0; j < b.length; j++) {
                    sum += a[j+i]*b[j];
                }
                sum /= (double)(compRange); //Normalize bynumber of values
                outputs.get(0).append(sum);
            }
        }
    }

    //Smooth data using a gauss distribution
    //Sigma defaults to 3 but can be changed.
    //Note that sigma cannot be set by a dataBuffer
    public static class gaussSmoothAM extends analysisModule {
        int calcWidth; //range to which the gauss is calculated
        double[] gauss; //Gauss-weight look-up-table

        protected gaussSmoothAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
            setSigma(3); //default
        }

        //Change sigma
        protected void setSigma(double sigma) {
            this.calcWidth = (int)Math.round(sigma*3); //Adapt calculation range: 3x sigma should be plenty

            gauss = new double[calcWidth*2+1];
            for (int i = -calcWidth; i <= calcWidth; i++) {
                gauss[i+calcWidth] = Math.exp(-(i/sigma*i/sigma)/2.)/(sigma*Math.sqrt(2.*Math.PI)); //Gauß!
            }
        }

        @Override
        protected void update() {
            //Get array for random access
            Double y[] = experiment.getBuffer(inputs.get(0)).getArray();

            //Clear output
            outputs.get(0).clear();

            for (int i = 0; i < y.length; i++) { //For each data-point
                double sum = 0;
                for (int j = -calcWidth; j <= calcWidth; j++) { //For each step in the look-up-table
                    int k = i+j; //index in input that corresponds to the step in the look-up-table
                    if (k >= 0 && k < y.length)
                        sum += gauss[j+calcWidth]*y[k]; //Add weighted contribution
                }
                outputs.get(0).append(sum); //Append the result to the output buffer
            }
        }
    }


    //Filter a couple of inputs with some max and min values per input
    //For each input you can set a min and max value.
    // If tha value of any input falls outside min and max, the data at this index if discarded for all inputs
    //You need exactly as many outputs as there are inputs.
    public static class rangefilterAM extends analysisModule {
        private Vector<String> min; //Hold min and max as string as it might be a dataBuffer
        private Vector<String> max;

        //Constructor also takes arrays of min and max values
        protected rangefilterAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs, Vector<String> min, Vector<String> max) {
            super(experiment, inputs, outputs);
            this.min = min;
            this.max = max;
        }

        @Override
        protected void update() {
            double[] min; //Double-valued min and max. Filled from String value / dataBuffer
            double[] max;

            min = new double[inputs.size()];
            max = new double[inputs.size()];
            for(int i = 0; i < inputs.size(); i++) {
                if (this.min.get(i) == null)
                    min[i] = Double.NEGATIVE_INFINITY; //Not set by user, set to -inf so it has no influence
                else {
                    min[i] = getSingleValueFromUserString(this.min.get(i)); //Get value from string: numeric or buffer
                }
                if (this.max.get(i) == null)
                    max[i] = Double.POSITIVE_INFINITY; //Not set by user, set to +inf so it has no influence
                else {
                    max[i] = getSingleValueFromUserString(this.max.get(i)); //Get value from string: numeric or buffer
                }

            }

            //Get iterators of all inputs (numeric string not allowed here as it makes no sense to filter static input)
            Vector<Iterator> its = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    its.add(null); //input does not exist
                } else {
                    //Valid buffer. Get iterator
                    its.add(experiment.getBuffer(inputs.get(i)).getIterator());
                }
            }

            //Clear all outputs
            for (dataBuffer output : outputs) {
                output.clear();
            }

            double []data = new double[inputs.size()]; //Will hold values of all inputs at same index
            boolean hasNext = true; //Will be set to true if ANY of the iterators has a next item (not neccessarily all of them)
            while (hasNext) {
                //Check if any input has a value left
                hasNext = false;
                for (Iterator it : its) {
                    if (it.hasNext())
                        hasNext = true;
                }

                if (hasNext) {
                    boolean filter = false; //Will be set to true if any input falls outside its min/max
                    for (int i = 0; i < inputs.size(); i++) { //For each input...
                        if (its.get(i).hasNext()) { //This input has a value left. Get it!
                            data[i] = (double) its.get(i).next();
                            if (data[i] < min[i] || data[i] > max[i]) { //Is this value outside its min/max?
                                filter = true; //Yepp, filter this index
                            }
                        } else
                            data[i] = Double.NaN; //No value left in input. Set this value to NaN and do not filter it
                    }
                    if (!filter) { //Filter not triggered? Append the values of each input to the corresponding outputs.
                        for (int i = 0; i < inputs.size(); i++) {
                            outputs.get(i).append(data[i]);
                        }
                    }

                }
            }
        }
    }

    //Create a linear ramp.
    //No input needed. The length of the output matches "length" or, if not set, the size of the output buffer
    //Defaults to a ramp from 0 to 100
    //You can set start, stop and length. The first value will match start, the last value will match stop
    //Databuffers allowed for all parameters
    public static class rampGeneratorAM extends analysisModule {
        //Defaults as string values as this might be set to a dataBuffer
        private String start = "0";
        private String stop = "100";
        private String length = "-1";

        protected rampGeneratorAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        //Set start, stop and length
        protected void setParameters(String start, String stop, String length) {
            if (start != null)
                this.start = start;
            if (stop != null)
                this.stop = stop;
            if (length != null)
                this.length = length;
        }

        @Override
        protected void update() {
            double vstart = getSingleValueFromUserString(start); //Get value from numeric string or buffer
            double vstop = getSingleValueFromUserString(stop); //Get value from numeric string or buffer
            int vlength = (int)getSingleValueFromUserString(length); //Get value from numeric string or buffer

            //Clear output
            outputs.get(0).clear();

            //If length is not set, use the size of the output buffer
            if (vlength < 0)
                vlength = outputs.get(0).size;

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
    public static class constGeneratorAM extends analysisModule {
        //Defaults as string values as this might be set to a dataBuffer
        private String value = "0";
        private String length = "-1";

        protected constGeneratorAM(phyphoxExperiment experiment, Vector<String> inputs, Vector<dataBuffer> outputs) {
            super(experiment, inputs, outputs);
        }

        //Set value and length
        protected void setParameters(String value, String length) {
            if (value != null)
                this.value = value;
            if (length != null)
                this.length = length;
        }

        @Override
        protected void update() {
            double vvalue = getSingleValueFromUserString(value); //Get value from numeric string or buffer
            int vlength = (int)getSingleValueFromUserString(length); //Get value from numeric string or buffer

            //Clear output
            outputs.get(0).clear();

            //If length is not set, use the size of the output buffer
            if (vlength < 0)
                vlength = outputs.get(0).size;

            //Write values to output
            for (int i = 0; i < vlength; i++) {
                outputs.get(0).append(vvalue);
            }
        }
    }

}
