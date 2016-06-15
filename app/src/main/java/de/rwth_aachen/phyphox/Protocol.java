package de.rwth_aachen.phyphox;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Vector;

public class Protocol implements Serializable {
    protocolImplementation protocol;

    Protocol(protocolImplementation protocol) {
        this.protocol = protocol;
    }

    public boolean canSend() {
        return protocol.canSend();
    }

    public boolean canReceive() {
        return protocol.canReceive();
    }

    public void send(BufferedOutputStream outStream, Vector<Vector<Double>> data) {
        protocol.processOutData(outStream, data);
    }

    public Vector<Vector<Double>> receive(StringBuilder buffer) {
        return protocol.processInData(buffer);
    }

    protected abstract static class protocolImplementation implements Serializable {

        protected abstract boolean canSend();
        protected abstract boolean canReceive();

        protected Vector<Vector<Double>> processInData(StringBuilder buffer) {
            return null;
        }

        protected void processOutData(BufferedOutputStream outStram, Vector<Vector<Double>> data) {
        }

    }

    public static class simple extends protocolImplementation implements Serializable {
        String separator = "\n";

        simple() {

        }

        simple(String separator) {
            this.separator = separator;
        }

        @Override
        protected boolean canReceive() {
            return true;
        }

        @Override
        protected boolean canSend() {
            return true;
        }

        @Override
        protected Vector<Vector<Double>> processInData(StringBuilder buffer) {
            Vector<Vector<Double>> result = new Vector<>();
            int i = buffer.indexOf(separator);
            while (i >= 0) {
                if (result.size() == 0)
                    result.add(new Vector<Double>());
                double v = Double.NaN;
                try {
                    v = Double.valueOf(buffer.substring(0, i));
                    result.get(0).add(v);
                } catch (NumberFormatException e) {
                } finally {
                    buffer.delete(0, i+1);
                    i = buffer.indexOf(separator);
                }
            }
            return result;
        }

        @Override
        protected void processOutData(BufferedOutputStream outStram, Vector<Vector<Double>> data) {
            if (data.size() < 1)
                return;

            Iterator<Double> it = data.get(0).iterator();
            while (it.hasNext()) {
                try {
                    outStram.write((String.valueOf(it.next()) + separator).getBytes());
                } catch (Exception e) {

                }
            }
        }
    }

    public static class csv extends protocolImplementation implements Serializable {
        String separator = ",";

        csv() {

        }

        csv(String separator) {
            this.separator = separator;
        }

        @Override
        protected boolean canReceive() {
            return true;
        }

        @Override
        protected boolean canSend() {
            return true;
        }

        @Override
        protected Vector<Vector<Double>> processInData(StringBuilder buffer) {
            Vector<Vector<Double>> result = new Vector<>();
            int nLine = 0;
            int i = buffer.indexOf("\n");
            while (i >= 0) {
                int column = 0;
                String line = buffer.substring(0, i);
                buffer = buffer.delete(0, i+1);
                int j = line.indexOf(separator);
                while (j >= 0 || line.length() > 0) {
                    double v = Double.NaN;
                    String valueStr;
                    if (j >= 0) {
                        valueStr = line.substring(0, j);
                        line = line.substring(j+1);
                        j = line.indexOf(separator);
                    } else {
                        valueStr = line;
                        line = "";
                    }
                    try {
                        v = Double.valueOf(valueStr);
                    } catch (NumberFormatException e) {
                    } finally {
                        if (column > result.size()-1) {
                            result.add(new Vector<Double>());
                            for (int k = 0; k < nLine-1; k++) {
                                result.get(column).add(Double.NaN);
                            }
                        }

                        result.get(column).add(v);

                        column++;
                    }
                }
                i = buffer.indexOf("\n");
                nLine++;
            }
            return result;
        }

        @Override
        protected void processOutData(BufferedOutputStream outStram, Vector<Vector<Double>> data) {
            Vector<Iterator<Double>> iterators = new Vector<>();
            for (Vector<Double> column : data) {
                iterators.add(column.iterator());
            }
            boolean dataLeft = true;
            while (dataLeft) {
                dataLeft = false;
                boolean first = true;
                String line = "";
                for (Iterator<Double> iterator : iterators) {
                    if (first) {
                        first = false;
                    } else {
                        line += separator;
                    }
                    if (iterator.hasNext()) {
                        dataLeft = true;
                        line += String.valueOf(iterator.next());
                    }
                }
                if (dataLeft) {
                    try {
                        outStram.write((line + "\n").getBytes());
                    } catch (Exception e) {

                    }
                }
            }
        }
    }

    public static class json extends protocolImplementation implements Serializable {
        Vector<String> names = null;

        json() {

        }

        json(Vector<String> names) {
            this.names = names;
        }

        @Override
        protected boolean canReceive() {
            return true;
        }

        @Override
        protected boolean canSend() {
            return true;
        }

        @Override
        protected Vector<Vector<Double>> processInData(StringBuilder buffer) {
            Vector<Vector<Double>> result = new Vector<>();

            int i = buffer.indexOf("\n");
            while (i >= 0) {
                String line = buffer.substring(0, i);

                try {
                    JSONObject json = new JSONObject(line);
                    for (int j = 0; j < names.size(); j++) {
                        result.add(new Vector<Double>());
                        if (json.has(names.get(j))) {
                            try {
                                JSONArray a = json.getJSONArray(names.get(j));
                                for (int k = 0; k < a.length(); k++) {
                                    double v = a.getDouble(k);
                                    result.get(j).add(v);
                                }
                            } catch (JSONException e1) {
                                try {
                                    double v = json.getDouble(names.get(j));
                                    result.get(j).add(v);
                                } catch (JSONException e2) {
                                    Log.e("bluetoothInput", "Could not parse " + names.get(j) + " as an array (" + e1.getMessage() + ") or a double (" + e2.getMessage() + ")");
                                }
                            }
                        }
                    }
                } catch (JSONException e) {
                    Log.e("bluetoothInput", "Could not parse JSON data: " + e.getMessage());
                }

                buffer = buffer.delete(0, i+1);
                i = buffer.indexOf("\n");
            }
            return result;
        }

        @Override
        protected void processOutData(BufferedOutputStream outStream, Vector<Vector<Double>> data) {
            JSONObject json = new JSONObject();
            for (int i = 0; i < names.size(); i++) {
                JSONArray a = new JSONArray();
                Iterator it = data.get(i).iterator();
                while (it.hasNext())
                    a.put(it.next());
                try {
                    json.put(names.get(i), a);
                } catch (JSONException e) {
                    Log.e("bluetoothInput", "Could not construct JSON data: " + e.getMessage());
                }
            }
            try {
                outStream.write((json.toString()+"\n").getBytes());
            } catch (Exception e) {

            }
        }
    }

}
