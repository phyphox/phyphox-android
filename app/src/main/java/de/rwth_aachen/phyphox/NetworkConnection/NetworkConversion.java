package de.rwth_aachen.phyphox.NetworkConnection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class NetworkConversion {

    public static class ConversionException extends Exception {
        public ConversionException(String message) {
            super(message);
        }
    }

    public static abstract class Conversion {
        protected abstract void prepare(byte[] data) throws ConversionException;
        protected abstract Double[] get(String id) throws ConversionException;
    }

    public static class None extends Conversion {
        public void prepare(byte[] data) throws ConversionException {
        }

        public Double[] get(String id) throws ConversionException {
            return new Double[0];
        }
    }

    public static class Json extends Conversion {
        JSONObject json = null;

        public void prepare(byte[] data) throws ConversionException {
            if (data.length == 0)
                throw new ConversionException("Empty input");

            try {
                json = new JSONObject(new String(data));
            } catch (JSONException e) {
                throw new ConversionException("Could not parse JSON.");
            }
        }

        public Double[] get(String id) throws ConversionException {
            String[] components = id.split("\\.");
            Object currentJson = json;
            try {
                for (String component : components) {
                    if (currentJson instanceof JSONObject) {
                        currentJson = ((JSONObject) currentJson).get(component);
                    } else {
                        throw new ConversionException("Could not find: " + id + " (No object)");
                    }

                }
                if (currentJson instanceof JSONArray) {
                    Double[] result = new Double[((JSONArray)currentJson).length()];
                    for (int i = 0; i < result.length; i++) {
                        result[i] = ((JSONArray)currentJson).getDouble(i);
                    }
                    return result;
                }
                Double[] result = new Double[1];
                if (currentJson instanceof Integer) {
                    result[0] = (double)(Integer)currentJson;
                    return result;
                }
                if (currentJson instanceof Long) {
                    result[0] = (double)(Long)currentJson;
                    return result;
                }
                if (currentJson instanceof Float) {
                    result[0] = (double)(Float)currentJson;
                    return result;
                }
                if (currentJson instanceof Double) {
                    result[0] = (double)(Double)currentJson;
                    return result;
                }
                throw new ConversionException(id + " is not a number or a numerical array.");
            } catch (JSONException e) {
                throw new ConversionException("Could not find: " + id + " (Not found)");
            }
        }
    }
}
