package de.rwth_aachen.phyphox.NetworkConnection;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

public class NetworkConversion {

    public static class ConversionException extends Exception {
        public ConversionException(String message) {
            super(message);
        }
    }

    public static abstract class Conversion {
        protected abstract void prepare(byte[][] data) throws ConversionException;
        protected abstract Double[] get(String id) throws ConversionException;
    }

    public static class None extends Conversion {
        public void prepare(byte[][] data) throws ConversionException {
        }

        public Double[] get(String id) throws ConversionException {
            return new Double[0];
        }
    }

    public static class Csv extends Conversion {
        String data[] = null;

        public void prepare(byte[][] data) throws ConversionException {
            this.data = new String[data.length];
            for (int i = 0; i < data.length; i++)
                this.data[i] = new String(data[i]);
        }

        public Double[] get(String id) throws ConversionException {
            ArrayList<Double> result = new ArrayList<>();
            int index;
            try {
                index = Integer.parseInt(id);
            } catch (NumberFormatException e) {
                index = -1;
            }

            for (int i = 0; i < data.length; i++) {
                String[] lines = data[i].split("\\r?\\n", -1);
                for (String line : lines) {
                    String[] columns = line.split("[,;]", -1);
                    if (index < 0) {
                        for (String column : columns) {
                            try {
                                result.add(Double.parseDouble(column.trim()));
                            } catch (NumberFormatException e) {
                                result.add(Double.NaN);
                            }
                        }
                    } else {
                        if (columns.length > index) {
                            try {
                                result.add(Double.parseDouble(columns[index].trim()));
                            } catch (NumberFormatException e) {
                                result.add(Double.NaN);
                            }
                        }
                    }
                }
            }
            return result.toArray(new Double[result.size()]);
        }
    }

    public static class Json extends Conversion {
        JSONObject json[] = null;

        public void prepare(byte[][] data) throws ConversionException {
            try {
                json = new JSONObject[data.length];
                for (int i = 0; i < data.length; i++) {
                    json[i] = new JSONObject(new String(data[i]));
                }
            } catch (JSONException e) {
                throw new ConversionException("Could not parse JSON.");
            }
        }

        public Double[] get(String id) throws ConversionException {
            ArrayList<Double> result = new ArrayList<>();
            String[] components = id.split("\\.");
            for (Object jsonObject : json) {
                Object currentJson = jsonObject;
                try {
                    for (String component : components) {
                        if (currentJson instanceof JSONObject) {
                            currentJson = ((JSONObject) currentJson).get(component);
                        } else {
                            throw new ConversionException("Could not find: " + id + " (No object)");
                        }

                    }
                    if (currentJson instanceof JSONArray) {
                        int n = ((JSONArray) currentJson).length();
                        for (int j = 0; j < n; j++) {
                            result.add(((JSONArray) currentJson).getDouble(j));
                        }
                        continue;
                    }
                    if (currentJson instanceof Integer) {
                        result.add((double) (Integer) currentJson);
                        continue;
                    }
                    if (currentJson instanceof Long) {
                        result.add((double) (Long) currentJson);
                        continue;
                    }
                    if (currentJson instanceof Float) {
                        result.add((double) (Float) currentJson);
                        continue;
                    }
                    if (currentJson instanceof Double) {
                        result.add((double) (Double) currentJson);
                        continue;
                    }
                } catch (JSONException e) {
                }
            }
            return result.toArray(new Double[result.size()]);
        }
    }
}
