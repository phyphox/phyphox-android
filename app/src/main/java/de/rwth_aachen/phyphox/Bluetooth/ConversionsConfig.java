package de.rwth_aachen.phyphox.Bluetooth;


import java.io.Serializable;
import java.lang.reflect.Method;

// The class holds public static functions which convert values from a string to a byte array.
public class ConversionsConfig {

    public static class ConfigConversion implements Serializable {
        ConfigConversion() {

        }
        public byte[] convert(String data) {
            return null;
        }
    }

    public static class SimpleConfigConversion extends ConfigConversion implements Serializable {
        private Method conversionFunction;
        public SimpleConfigConversion(Method conversionFunction) {
            super();
            this.conversionFunction = conversionFunction;
        }

        @Override
        public byte[] convert(String data) {
            try {
                return (byte[]) conversionFunction.invoke(null, data);
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static byte[] string (String data) {
        return (data).getBytes();
    }

    public static byte[] int16LittleEndian (String data) {
        return ConversionsOutput.int16LittleEndian(Double.parseDouble(data));
    }

    public static byte[] uInt16LittleEndian (String data) {
        return int16LittleEndian(data);
    }

    public static byte[] int24LittleEndian (String data) {
        return ConversionsOutput.int24LittleEndian(Double.parseDouble(data));
    }

    public static byte[] uInt24LittleEndian (String data) {
        return int24LittleEndian(data);
    }

    public static byte[] int32LittleEndian (String data) {
        return ConversionsOutput.int32LittleEndian(Double.parseDouble(data));
    }

    public static byte[] uInt32LittleEndian (String data) {
        return ConversionsOutput.uInt32LittleEndian(Double.parseDouble(data));
    }

    public static byte[] int16BigEndian (String data) {
        return ConversionsOutput.int16BigEndian(Double.parseDouble(data));
    }

    public static byte[] uInt16BigEndian (String data) {
        return int16BigEndian(data);
    }

    public static byte[] int24BigEndian (String data) {
        return ConversionsOutput.int24BigEndian(Double.parseDouble(data));
    }

    public static byte[] uInt24BigEndian (String data) {
        return int24BigEndian(data);
    }

    public static byte[] int32BigEndian (String data) {
        return ConversionsOutput.int32BigEndian(Double.parseDouble(data));
    }

    public static byte[] uInt32BigEndian (String data) {
        return ConversionsOutput.uInt32BigEndian(Double.parseDouble(data));
    }

    public static byte[] float32LittleEndian (String data) {
        return ConversionsOutput.float32LittleEndian(Double.parseDouble(data));
    }

    public static byte[] float32BigEndian (String data) {
        return ConversionsOutput.float32BigEndian(Double.parseDouble(data));
    }

    public static byte[] float64LittleEndian (String data) {
        return ConversionsOutput.float64LittleEndian(Double.parseDouble(data));
    }

    public static byte[] float64BigEndian (String data) {
        return ConversionsOutput.float64BigEndian(Double.parseDouble(data));
    }

    public static byte[] singleByte (String data) {
        //Parse as int and truncate, so both signed (-128..127) and unsigned (128..255) notations work
        return new byte[]{(byte) Integer.parseInt(data)};
    }

    public static byte[] int8 (String data) { //Just an intuitive alias
        return singleByte(data);
    }

    public static byte[] uInt8 (String data) {
        return singleByte(data);
    }

    public static byte[] hexadecimal (String data) {
        byte[] result = new byte[data.length()/2];
        for (int i = 0; i + 1 < data.length(); i+=2) { //A dangling character of an odd-length string is ignored
            result[i/2] = (byte) ((Character.digit(data.charAt(i), 16) << 4) + Character.digit(data.charAt(i+1), 16));
        }
        return result;
    }
}
