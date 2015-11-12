package de.rwth_aachen.phyphox;

import android.util.Log;

import java.util.Iterator;
import java.util.Map;
import java.util.Vector;


public class Analysis {
    public static class analysisModule {
        protected Vector<String> inputs;
        protected Vector<dataBuffer> outputs;
        protected Vector<Boolean> isValue;
        protected Vector<Double> values;
        protected Vector<dataBuffer> dataBuffers;
        protected Map<String, Integer> dataMap;
        protected boolean isStatic = false;
        protected boolean executed = false;

        protected analysisModule(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            this.dataBuffers = dataBuffers;
            this.dataMap = dataMap;
            this.inputs = inputs;
            this.outputs = outputs;
            this.isValue = isValue;
            this.values = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                if (isValue.get(i)) {
                    this.values.add(Double.valueOf(inputs.get(i)));
                    this.inputs.set(i, null);
                } else
                    this.values.add(0.);
            }
        }

        protected void setStatic(boolean isStatic) {
            this.isStatic = isStatic;
            for (dataBuffer output : outputs) {
                output.setStatic(isStatic);
            }
        }

        protected void updateIfNotStatic() {
            if (!(isStatic && executed)) {
                update();
                executed = true;
            }
        }

        protected void update() {

        }
    }

    public static class addAM extends analysisModule {

        protected addAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>();
            Vector<Double> lastValues = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    lastValues.add(values.get(i));
                    its.add(null);
                } else {
                    its.add(dataBuffers.get(dataMap.get(inputs.get(i))).getIterator());
                    lastValues.add(0.);
                }
            }
            outputs.get(0).clear();
            for (int i = 0; i < outputs.get(0).size; i++) {
                double sum = 0;
                boolean anyInput = false;
                for (int j = 0; j < its.size(); j++) {
                    if (its.get(j) != null && its.get(j).hasNext()) {
                        lastValues.set(j, (double)its.get(j).next());
                        anyInput = true;
                    }
                    sum += lastValues.get(j);
                }
                if (anyInput)
                    outputs.get(0).append(sum);
                else
                    break;
            }
        }
    }

    public static class subtractAM extends analysisModule {

        protected subtractAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>();
            Vector<Double> lastValues = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    lastValues.add(values.get(i));
                    its.add(null);
                } else {
                    its.add(dataBuffers.get(dataMap.get(inputs.get(i))).getIterator());
                    lastValues.add(0.);
                }
            }
            outputs.get(0).clear();
            for (int i = 0; i < outputs.get(0).size; i++) {
                double sum = 0.;
                boolean anyInput = false;
                for (int j = 0; j < its.size(); j++) {
                    if (its.get(j) != null && its.get(j).hasNext()) {
                        lastValues.set(j, (double)its.get(j).next());
                        anyInput = true;
                    }
                    if (j == 0)
                        sum = lastValues.get(j);
                    else
                        sum -= lastValues.get(j);
                }
                if (anyInput)
                    outputs.get(0).append(sum);
                else
                    break;
            }
        }
    }

    public static class multiplyAM extends analysisModule {

        protected multiplyAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>();
            Vector<Double> lastValues = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    lastValues.add(values.get(i));
                    its.add(null);
                } else {
                    its.add(dataBuffers.get(dataMap.get(inputs.get(i))).getIterator());
                    lastValues.add(0.);
                }
            }
            outputs.get(0).clear();
            for (int i = 0; i < outputs.get(0).size; i++) {
                double product = 1.;
                boolean anyInput = false;
                for (int j = 0; j < its.size(); j++) {
                    if (its.get(j) != null && its.get(j).hasNext()) {
                        lastValues.set(j, (double)its.get(j).next());
                        anyInput = true;
                    }
                    product *= lastValues.get(j);
                }
                if (anyInput)
                    outputs.get(0).append(product);
                else
                    break;
            }
        }
    }

    public static class divideAM extends analysisModule {

        protected divideAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>();
            Vector<Double> lastValues = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    lastValues.add(values.get(i));
                    its.add(null);
                } else {
                    its.add(dataBuffers.get(dataMap.get(inputs.get(i))).getIterator());
                    lastValues.add(0.);
                }
            }
            outputs.get(0).clear();
            for (int i = 0; i < outputs.get(0).size; i++) {
                double product = 1.;
                boolean anyInput = false;
                for (int j = 0; j < its.size(); j++) {
                    if (its.get(j) != null && its.get(j).hasNext()) {
                        lastValues.set(j, (double)its.get(j).next());
                        anyInput = true;
                    }
                    if (j == 0)
                        product = lastValues.get(j);
                    else
                        product /= lastValues.get(j);
                }
                if (anyInput)
                    outputs.get(0).append(product);
                else
                    break;
            }
        }
    }

    public static class powerAM extends analysisModule {

        protected powerAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>();
            Vector<Double> lastValues = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    lastValues.add(values.get(i));
                    its.add(null);
                } else {
                    its.add(dataBuffers.get(dataMap.get(inputs.get(i))).getIterator());
                    lastValues.add(0.);
                }
            }
            outputs.get(0).clear();
            for (int i = 0; i < outputs.get(0).size; i++) {
                double power = 1.;
                boolean anyInput = false;
                for (int j = 0; j < its.size(); j++) {
                    if (its.get(j) != null && its.get(j).hasNext()) {
                        lastValues.set(j, (double)its.get(j).next());
                        anyInput = true;
                    }
                    if (j == 0)
                        power = lastValues.get(j);
                    else
                        power = Math.pow(power, lastValues.get(j));
                }
                if (anyInput)
                    outputs.get(0).append(power);
                else
                    break;
            }
        }
    }

    public static class sinAM extends analysisModule {

        protected sinAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
        }

        @Override
        protected void update() {
            Iterator it;
            double lastValue;
            if (inputs.get(0) == null) {
                lastValue = values.get(0);
                it = null;
            } else {
                it = dataBuffers.get(dataMap.get(inputs.get(0))).getIterator();
                lastValue = 0.;
            }
            outputs.get(0).clear();
            for (int i = 0; i < outputs.get(0).size; i++) {
                if (it != null && it.hasNext()) {
                    lastValue = (double)it.next();
                }
                outputs.get(0).append(Math.sin(lastValue));
            }
        }
    }

    public static class cosAM extends analysisModule {

        protected cosAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
        }

        @Override
        protected void update() {
            Iterator it;
            double lastValue;
            if (inputs.get(0) == null) {
                lastValue = values.get(0);
                it = null;
            } else {
                it = dataBuffers.get(dataMap.get(inputs.get(0))).getIterator();
                lastValue = 0.;
            }
            outputs.get(0).clear();
            for (int i = 0; i < outputs.get(0).size; i++) {
                if (it != null && it.hasNext()) {
                    lastValue = (double)it.next();
                }
            }
            outputs.get(0).append(Math.cos(lastValue));
        }
    }

    public static class tanAM extends analysisModule {

        protected tanAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
        }

        @Override
        protected void update() {
            Iterator it;
            double lastValue;
            if (inputs.get(0) == null) {
                lastValue = values.get(0);
                it = null;
            } else {
                it = dataBuffers.get(dataMap.get(inputs.get(0))).getIterator();
                lastValue = 0.;
            }
            outputs.get(0).clear();
            for (int i = 0; i < outputs.get(0).size; i++) {
                if (it != null && it.hasNext()) {
                    lastValue = (double)it.next();
                }
            }
            outputs.get(0).append(Math.tan(lastValue));
        }
    }

    public static class maxAM extends analysisModule {

        protected maxAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    its.add(null);
                } else {
                    its.add(dataBuffers.get(dataMap.get(inputs.get(i))).getIterator());
                }
            }

            double max = Double.NEGATIVE_INFINITY;
            double x = Double.NEGATIVE_INFINITY;
            double currentX = -1;
            while (its.get(0).hasNext()) {
                double v = (double)its.get(0).next();
                if (outputs.size() > 1 && its.get(1).hasNext())
                    currentX = (double)its.get(1).next();
                else
                    currentX += 1;
                if (v > max) {
                    max = v;
                    x = currentX;
                }
            }
            outputs.get(0).append(max);
            if (outputs.size() > 1) {
                outputs.get(1).append(x);
            }

        }
    }

    public static class thresholdAM extends analysisModule {
        double threshold;
        boolean falling = false;

        protected thresholdAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs, double threshold, boolean falling) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
            this.threshold = threshold;
            this.falling = falling;
        }

        @Override
        protected void update() {
            Vector<Iterator> its = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    its.add(null);
                } else {
                    its.add(dataBuffers.get(dataMap.get(inputs.get(i))).getIterator());
                }
            }

            double last = Double.NaN;
            double currentX = -1;
            while (its.get(0).hasNext()) {
                double v = (double)its.get(0).next();
                if (inputs.size() > 1 && its.get(1).hasNext())
                    currentX = (double)its.get(1).next();
                else
                    currentX += 1;
                if (!(Double.isNaN(last) || Double.isNaN(v))) {
                    if (falling) {
                        if (last > threshold && v < threshold) {
                            break;
                        }
                    } else {
                        if (last < threshold && v > threshold) {
                            break;
                        }
                    }
                }
                last = v;
            }
            outputs.get(0).append(currentX);

        }
    }

    public static class appendAM extends analysisModule {

        protected appendAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
        }

        @Override
        protected void update() {

            outputs.get(0).clear();

            for (int i = 0; i < inputs.size(); i++) {
                Iterator it = dataBuffers.get(dataMap.get(inputs.get(i))).getIterator();
                if (it == null)
                    continue;
                while (it.hasNext()) {
                    outputs.get(0).append((double)it.next());
                }
            }

        }
    }

    public static class fftAM extends analysisModule {
        private int n, np2, logn;
        private double [] cos, sin;

        protected fftAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);

            n = dataBuffers.get(dataMap.get(inputs.get(0))).size;
            logn = (int)(Math.log(n)/Math.log(2));
            if (n != (1 << logn)) {
                logn++;
                np2 = (1 << logn);
            } else
                np2 = n;

            cos = new double[np2/2];
            sin = new double[np2/2];

            for (int i = 0; i < np2 / 2; i++) {
                cos[i] = Math.cos(-2 * Math.PI * i / np2);
                sin[i] = Math.sin(-2 * Math.PI * i / np2);
            }
        }

        @Override
        protected void update() {

            double x[] = new double[np2];
            double y[] = new double[np2];
            Iterator ix = dataBuffers.get(dataMap.get(inputs.get(0))).getIterator();
            int i = 0;
            while (ix.hasNext())
                x[i++] = (double)ix.next();
            if (inputs.size() > 1) {
                Iterator iy = dataBuffers.get(dataMap.get(inputs.get(1))).getIterator();
                i = 0;
                while (iy.hasNext())
                    y[i++] = (double)iy.next();
            } else {
                for (i = 0; i < np2; i++)
                    y[i] = 0.;
            }


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


            n1 = 0; /* FFT */
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

            for (i = 0; i < n; i++) {
                outputs.get(0).append(x[i]);
                if (outputs.size() > 1)
                    outputs.get(1).append(y[i]);
            }
        }
    }

    public static class autocorrelationAM extends analysisModule {
        private double mint = Double.NEGATIVE_INFINITY;
        private double maxt = Double.POSITIVE_INFINITY;

        protected autocorrelationAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
        }

        protected void setMinMaxT(double mint, double maxt) {
            this.mint = mint;
            this.maxt = maxt;
        }

        @Override
        protected void update() {

            Double y[] = dataBuffers.get(dataMap.get(inputs.get(0))).getArray();
            Double x[] = new Double[y.length];
            if (inputs.size() > 1) {
                Double xraw[] = dataBuffers.get(dataMap.get(inputs.get(1))).getArray();
                for (int i = 0; i < x.length; i++) {
                    if (i < xraw.length)
                        x[i] = xraw[i]-xraw[0];
                    else
                        x[i] = xraw[xraw.length - 1]-xraw[0];
                }
            } else {
                for (int i = 0; i < x.length; i++) {
                    x[i] = (double)i;
                }
            }

            outputs.get(0).clear();
            if (outputs.size() > 1)
                outputs.get(1).clear();


            for (int i = 0; i < y.length; i++) {
                if (x[i] < mint || x[i] > maxt)
                    continue;
                double sum = 0.;
                for (int j = 0; j < y.length-i; j++) {
                    sum += y[j]*y[j+i];
                }
                sum /= (double)(y.length-i);
                outputs.get(0).append(sum);
                if (outputs.size() > 1)
                    outputs.get(1).append(x[i]);
            }
        }
    }

    public static class crosscorrelationAM extends analysisModule {
        protected crosscorrelationAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
        }

        @Override
        protected void update() {
            Double a[], b[];
            if (dataBuffers.get(dataMap.get(inputs.get(0))).size > dataBuffers.get(dataMap.get(inputs.get(1))).size) {
                a = dataBuffers.get(dataMap.get(inputs.get(0))).getArray();
                b = dataBuffers.get(dataMap.get(inputs.get(1))).getArray();
            } else {
                b = dataBuffers.get(dataMap.get(inputs.get(0))).getArray();
                a = dataBuffers.get(dataMap.get(inputs.get(1))).getArray();
            }

            outputs.get(0).clear();

            int compRange = a.length - b.length;
            for (int i = 0; i < compRange; i++) {
                double sum = 0.;
                for (int j = 0; j < b.length; j++) {
                    sum += a[j+i]*b[j];
                }
                sum /= (double)(compRange);
                outputs.get(0).append(sum);
            }
        }
    }

    public static class gaussSmoothAM extends analysisModule {
        private double sigma;
        int calcWidth;
        double[] gauss;

        protected gaussSmoothAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
            setSigma(3);
        }

        protected void setSigma(double sigma) {
            this.sigma = sigma;
            this.calcWidth = (int)Math.round(sigma*3);

            gauss = new double[calcWidth*2+1];
            for (int i = -calcWidth; i <= calcWidth; i++) {
                gauss[i+calcWidth] = Math.exp(-(i/sigma*i/sigma)/2.)/(sigma*Math.sqrt(2.*Math.PI));
            }
        }

        @Override
        protected void update() {
            Double y[] = dataBuffers.get(dataMap.get(inputs.get(0))).getArray();
            outputs.get(0).clear();

            for (int i = 0; i < y.length; i++) {
                double sum = 0;
                for (int j = -calcWidth; j <= calcWidth; j++) {
                    int k = i+j;
                    if (k >= 0 && k < y.length)
                        sum += gauss[j+calcWidth]*y[k];
                }
                outputs.get(0).append(sum);
            }
        }
    }

    public static class rangefilterAM extends analysisModule {
        private Vector<String> min;
        private Vector<String> max;

        protected rangefilterAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs, Vector<String> min, Vector<String> max) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
            this.min = min;
            this.max = max;
        }

        @Override
        protected void update() {
            double[] min;
            double[] max;

            min = new double[inputs.size()];
            max = new double[inputs.size()];
            for(int i = 0; i < inputs.size(); i++) {
                if (this.min.get(i) == null)
                    min[i] = Double.NEGATIVE_INFINITY;
                else {
                    if (dataMap.get(this.min.get(i)) != null && dataBuffers.get(dataMap.get(this.min.get(i))) != null)
                        min[i] = dataBuffers.get(dataMap.get(this.min.get(i))).value;
                    else
                        min[i] = Double.valueOf(this.min.get(i));
                }
                if (this.max.get(i) == null)
                    max[i] = Double.POSITIVE_INFINITY;
                else {
                    if (dataMap.get(this.max.get(i)) != null && dataBuffers.get(dataMap.get(this.max.get(i))) != null)
                        max[i] = dataBuffers.get(dataMap.get(this.max.get(i))).value;
                    else
                        max[i] = Double.valueOf(this.max.get(i));
                }

            }

            Vector<Iterator> its = new Vector<>();
            for (int i = 0; i < inputs.size(); i++) {
                if (inputs.get(i) == null) {
                    its.add(null);
                } else {
                    its.add(dataBuffers.get(dataMap.get(inputs.get(i))).getIterator());
                }
            }

            for (dataBuffer output : outputs) {
                output.clear();
            }

            double []data = new double[inputs.size()];
            boolean hasNext = true;
            while (hasNext) {
                hasNext = false;
                for (Iterator it : its) {
                    if (it.hasNext())
                        hasNext = true;
                }
                if (hasNext) {
                    boolean filter = false;
                    for (int i = 0; i < inputs.size(); i++) {
                        if (its.get(i).hasNext()) {
                            data[i] = (double) its.get(i).next();
                            if (data[i] < min[i] || data[i] > max[i]) {
                                filter = true;
                            }
                        } else
                            data[i] = Double.NaN;
                    }
                    if (!filter) {
                        for (int i = 0; i < inputs.size(); i++) {
                            outputs.get(i).append(data[i]);
                        }
                    }

                }
            }
        }
    }

    public static class rampGeneratorAM extends analysisModule {
        private double start = 0;
        private double stop = 100;
        private int length = -1;

        protected rampGeneratorAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
        }

        protected void setParameters(double start, double stop, int length) {
            this.start = start;
            this.stop = stop;
            this.length = length;
        }

        @Override
        protected void update() {
            outputs.get(0).clear();
            if (this.length < 0)
                this.length = outputs.get(0).size;

            for (int i = 0; i < this.length; i++) {
                outputs.get(0).append(start+(stop-start)/(length-1)*i);
            }
        }
    }

    public static class constGeneratorAM extends analysisModule {
        private double value = 0;
        private int length = -1;

        protected constGeneratorAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
        }

        protected void setParameters(double value, int length) {
            this.value = value;
            this.length = length;
        }

        @Override
        protected void update() {
            outputs.get(0).clear();
            if (this.length < 0)
                this.length = outputs.get(0).size;

            for (int i = 0; i < this.length; i++) {
                outputs.get(0).append(value);
            }
        }
    }

}
