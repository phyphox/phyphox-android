package de.rwth_aachen.phyphox;

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

    public static class rangefilterAM extends analysisModule {
        private double[] min;
        private double[] max;

        protected rangefilterAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
        }

        protected rangefilterAM(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Vector<String> inputs, Vector<Boolean> isValue, Vector<dataBuffer> outputs, Vector<String> min, Vector<String> max) {
            super(dataBuffers, dataMap, inputs, isValue, outputs);
            this.min = new double[inputs.size()];
            this.max = new double[inputs.size()];
            for(int i = 0; i < inputs.size(); i++) {
                if (min.get(i) == null)
                    this.min[i] = Double.NEGATIVE_INFINITY;
                else
                    this.min[i] = Double.valueOf(min.get(i));
                if (max.get(i) == null)
                    this.max[i] = Double.POSITIVE_INFINITY;
                else
                    this.max[i] = Double.valueOf(max.get(i));
            }
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
                            if (data[i] < min[i] || data[i] > max[i])
                                filter = true;
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
}
