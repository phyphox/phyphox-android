package de.rwth_aachen.phyphox;

import android.util.Log;

import java.util.Iterator;
import java.util.Vector;

// The analysis class is used to to do math operations on dataBuffers

public class Analysis {

    //analysisModule is is the prototype from which each analysis module inherits its interface
    public static class analysisModule {
        private Vector<dataInput> inputsOriginal; //The key of input dataBuffers, note that this is private, so derived classes cannot access this directly, but have to use the copy
        protected Vector<dataInput> inputs = new Vector<>(); //The local copy of the input data when the analysis module starts its update
        protected Vector<dataOutput> outputs; //The keys of outputBuffers
        protected phyphoxExperiment experiment; //experiment reference to access buffers
        protected boolean isStatic = false; //If a module is defined as static, it will only be executed once. This is used to save performance if data does not change
        protected boolean executed = false; //This takes track if the module has been executed at all. Used for static modules.

        //Main contructor
        protected analysisModule(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            this.experiment = experiment;
            this.inputsOriginal = inputs;
            this.outputs = outputs;

            //If any output module is non-static, than this analysis module is not static
            this.isStatic = true;
            for (int i = 0; i < outputs.size(); i++) {
                if (outputs.get(i) != null && !outputs.get(i).isStatic()) {
                    this.isStatic = false;
                    break;
                }
            }
        }

        //Wrapper to update the module only if it is not static or has never been executed and to clear the buffer if required
        protected boolean updateIfNotStatic() {
            if (!(isStatic && executed)) {
                boolean inputsReady = true;
                for (int i = 0; i < inputsOriginal.size(); i++) {
                    if (inputsOriginal.get(i) != null && inputsOriginal.get(i).getFilledSize() == 0) {
                        inputsReady = false;
                        break;
                    }
                }
                if (!inputsReady)
                    return false;

                experiment.dataLock.lock();
                try {
                    inputs.setSize(inputsOriginal.size());
                    for (int i = 0; i < inputsOriginal.size(); i++) {
                        if (inputsOriginal.get(i) == null)
                            inputs.set(i, null);
                        else {
                            inputs.set(i, inputsOriginal.get(i).copy());
                            if (inputsOriginal.get(i).isBuffer && inputsOriginal.get(i).clearAfterRead && !inputsOriginal.get(i).buffer.isStatic)
                                inputsOriginal.get(i).clear();
                        }
                    }
                } finally {
                    experiment.dataLock.unlock();
                }

                for (dataOutput output : outputs)
                    if (output != null && output.clearBeforeWrite)
                        output.buffer.clear();

                update();

    // Uncomment to print the last value of inputs and outputs for debugging...
    /*
                Log.d("AnalysisDebug", "[" + this.getClass().toString() + "]");
                for (dataInput input : inputs)
                    if (input != null)
                        Log.d("AnalysisDebug", "in: " + input.getValue());
                for (dataOutput output : outputs)
                    if (output != null)
                        Log.d("AnalysisDebug", output.buffer.name + " => " + output.getValue());
    */

                executed = true;
            }
            return true;
        }

        //The main update function has to be overridden by the modules
        protected void update() {

        }
    }

    //Get the seconds since the experiment started
    public static class timerAM extends analysisModule {

        protected timerAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            outputs.get(0).append((experiment.analysisTime - experiment.firstAnalysisTime)/1000.);
        }
    }

    //Add input values. The output has the length of the longest input buffer or the size of the output buffer (whichever is smaller). Missing values in shorter buffers are filled from the last value.
    public static class addAM extends analysisModule {

        protected addAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>(); //Iterators for all input buffers
            Vector<Double> lastValues = new Vector<>(); //Buffer to hold last value if buffer is shorter than expected

            //Get iterators and fill history with fixed value if an input is not a buffer
            for (int i = 0; i < inputs.size(); i++) {
                its.add(inputs.get(i).getIterator());
                lastValues.add(inputs.get(i).value);
            }

            for (int i = 0; i < outputs.get(0).size(); i++) { //For each value of output buffer
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

        protected subtractAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>(); //Iterators for all input buffers
            Vector<Double> lastValues = new Vector<>(); //Buffer to hold last value if buffer is shorter than expected

            //Get iterators and fill history with fixed value if an input is not a buffer
            for (int i = 0; i < inputs.size(); i++) {
                its.add(inputs.get(i).getIterator());
                lastValues.add(inputs.get(i).value);
            }


            for (int i = 0; i < outputs.get(0).size(); i++) { //For each value of output buffer
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

        protected multiplyAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>();  //Iterators for all input buffers
            Vector<Double> lastValues = new Vector<>();  //Buffer to hold last value if buffer is shorter than expected

            //Get iterators and fill history with fixed value if an input is not a buffer
            for (int i = 0; i < inputs.size(); i++) {
                its.add(inputs.get(i).getIterator());
                lastValues.add(inputs.get(i).value);
            }

            for (int i = 0; i < outputs.get(0).size(); i++) { //For each value of output buffer
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

        protected divideAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>(); //Iterators for all input buffers
            Vector<Double> lastValues = new Vector<>(); //Buffer to hold last value if buffer is shorter than expected

            //Get iterators and fill history with fixed value if an input is not a buffer
            for (int i = 0; i < inputs.size(); i++) {
                its.add(inputs.get(i).getIterator());
                lastValues.add(inputs.get(i).value);
            }

            for (int i = 0; i < outputs.get(0).size(); i++) { //For each value of output buffer
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

        protected powerAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>(); //Iterators for all input buffers
            Vector<Double> lastValues = new Vector<>(); //Buffer to hold last value if buffer is shorter than expected

            //Get iterators and fill history with fixed value if an input is not a buffer
            for (int i = 0; i < inputs.size(); i++) {
                its.add(inputs.get(i).getIterator());
                lastValues.add(inputs.get(i).value);
            }

            for (int i = 0; i < outputs.get(0).size(); i++) { //For each value of output buffer
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

        protected gcdAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>(); //Iterators for all input buffers
            Vector<Double> lastValues = new Vector<>(); //Buffer to hold last value if buffer is shorter than expected

            //Get iterators and fill history with fixed value if an input is not a buffer
            for (int i = 0; i < 2; i++) {
                its.add(inputs.get(i).getIterator());
                lastValues.add(inputs.get(i).value);
            }

            for (int i = 0; i < outputs.get(0).size(); i++) { //For each value of output buffer
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

        protected lcmAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>(); //Iterators for all input buffers
            Vector<Double> lastValues = new Vector<>(); //Buffer to hold last value if buffer is shorter than expected

            //Get iterators and fill history with fixed value if an input is not a buffer
            for (int i = 0; i < 2; i++) {
                its.add(inputs.get(i).getIterator());
                lastValues.add(inputs.get(i).value);
            }

            for (int i = 0; i < outputs.get(0).size(); i++) { //For each value of output buffer
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

        protected absAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Iterator it;
            double lastValue;

            //Get value or iterator
            it = inputs.get(0).getIterator();
            lastValue = inputs.get(0).value;

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

        protected sinAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Iterator it;
            double lastValue;

            //Get value or iterator
            it = inputs.get(0).getIterator();
            lastValue = inputs.get(0).value;

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

        protected cosAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Iterator it;
            double lastValue;

            //Get value or iterator
            it = inputs.get(0).getIterator();
            lastValue = inputs.get(0).value;

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

        protected tanAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Iterator it;
            double lastValue;

            //Get value or iterator
            it = inputs.get(0).getIterator();
            lastValue = inputs.get(0).value;

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

        protected firstAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {

            //Get iterators, values do not make sense here.
            Vector<Iterator> its = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                its.add(inputs.get(i).getIterator());
            }

            //Just get the first value and append it to each buffer
            for (int i = 0; i < outputs.size(); i++) {
                if (outputs.size() > i && outputs.get(i) != null) {
                    if (its.get(i).hasNext())
                        outputs.get(i).append((double) its.get(i).next());
                    else
                        outputs.get(i).append(Double.NaN);
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
    public static class maxAM extends analysisModule {
        private boolean multiple = false;

        protected maxAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs, boolean multiple) {
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
    public static class minAM extends analysisModule {
        private boolean multiple = false;

        protected minAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs, boolean multiple) {
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
    public static class thresholdAM extends analysisModule {
        boolean falling = false; //Falling or rising trigger?

        //Extended constructor which receives the threshold and falling as well.
        protected thresholdAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs, boolean falling) {
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
                its.add(inputs.get(i).getIterator());
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

    //Append all inputs to the output.
    public static class appendAM extends analysisModule {

        protected appendAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            for (int i = 0; i < inputs.size(); i++) { //For each input
                //Get iterator
                Iterator it = inputs.get(i).getIterator();
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

        protected fftAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);

            n = inputs.get(0).buffer.size; //Actual input size
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
            Iterator ix = inputs.get(0).getIterator();
            int i = 0;
            while (ix.hasNext())
                x[i++] = (double)ix.next();
            if (inputs.size() > 1 && inputs.get(1) != null) { //Is there imaginary input?
                //Iterator of second input -> Im(z)
                Iterator iy = inputs.get(1).getIterator();
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
                if (outputs.size() > 0 && outputs.get(0) != null)
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

        protected autocorrelationAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            double mint, maxt;

            //Update min and max as they might come from a dataBuffer
            if (inputs.size() < 3 || inputs.get(2) == null)
                mint = Double.NEGATIVE_INFINITY; //not set by user, set to -inf so it has no effect
            else
                mint = inputs.get(2).getValue();

            if (inputs.size() < 4 || inputs.get(3) == null)
                maxt = Double.POSITIVE_INFINITY; //not set by user, set to +inf so it has no effect
            else
                maxt = inputs.get(3).getValue();

            //Get arrays for random access
            Double y[] = inputs.get(1).buffer.getArray();
            Double x[] = new Double[y.length]; //Relative x (the displacement in the autocorrelation). This has to be filled from input2 or manually with 1,2,3...
            if (inputs.get(0) != null) {
                //There is a second input, let's use it.
                Double xraw[] = inputs.get(0).buffer.getArray();
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
    public static class periodicityAM extends analysisModule {

        protected periodicityAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
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
    public static class differentiateAM extends analysisModule {

        protected differentiateAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
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
    public static class integrateAM extends analysisModule {

        protected integrateAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
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
    public static class crosscorrelationAM extends analysisModule {
        protected crosscorrelationAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            Double a[], b[];
            //Put the larger input in a and the smaller one in b
            if (inputs.get(0).getFilledSize() > inputs.get(1).getFilledSize()) {
                a = inputs.get(0).getArray();
                b = inputs.get(1).getArray();
            } else {
                b = inputs.get(0).getArray();
                a = inputs.get(1).getArray();
            }

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

        protected gaussSmoothAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
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
            Double y[] = inputs.get(0).getArray();

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


    //Match a couple of inputs and only return those that have a valid value in every input
    //You need exactly as many outputs as there are inputs.
    public static class matchAM extends analysisModule {

        protected matchAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
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
    public static class rangefilterAM extends analysisModule {

        //Constructor also takes arrays of min and max values
        protected rangefilterAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
            super(experiment, inputs, outputs);
        }

        @Override
        protected void update() {
            double[] min; //Double-valued min and max. Filled from String value / dataBuffer
            double[] max;

            int n = (inputs.size()-1)/3+1;
            min = new double[n];
            max = new double[n];
            inputs.setSize(n * 3);

            for(int i = 0; i < n; i++) {
                if (inputs.get(3*i+1) == null)
                    min[i] = Double.NEGATIVE_INFINITY; //Not set by user, set to -inf so it has no influence
                else {
                    min[i] = inputs.get(3*i+1).getValue(); //Get value from string: numeric or buffer
                }
                if (inputs.get(3*i+2) == null)
                    max[i] = Double.POSITIVE_INFINITY; //Not set by user, set to +inf so it has no influence
                else {
                    max[i] = inputs.get(3*i+2).getValue(); //Get value from string: numeric or buffer
                }
            }

            //Get iterators of all inputs (numeric string not allowed here as it makes no sense to filter static input)
            Vector<Iterator> its = new Vector<>();
            for (int i = 0; i < n; i++) {
                its.add(inputs.get(3*i).getIterator());
            }

            double []data = new double[n]; //Will hold values of all inputs at same index
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
                    for (int i = 0; i < n; i++) { //For each input...
                        if (its.get(i).hasNext()) { //This input has a value left. Get it!
                            data[i] = (double) its.get(i).next();
                            if (data[i] < min[i] || data[i] > max[i]) { //Is this value outside its min/max?
                                filter = true; //Yepp, filter this index
                            }
                        } else
                            data[i] = Double.NaN; //No value left in input. Set this value to NaN and do not filter it
                    }
                    if (!filter) { //Filter not triggered? Append the values of each input to the corresponding outputs.
                        for (int i = 0; i < n; i++) {
                            if (i < outputs.size() && outputs.get(i) != null)
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
        double vstart = 0.;
        double vstop = 100.;
        int vlength = -1;

        protected rampGeneratorAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
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
    public static class constGeneratorAM extends analysisModule {
        //Defaults as string values as this might be set to a dataBuffer
        double vvalue = 0.;
        int vlength = -1;

        protected constGeneratorAM(phyphoxExperiment experiment, Vector<dataInput> inputs, Vector<dataOutput> outputs) {
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

}
