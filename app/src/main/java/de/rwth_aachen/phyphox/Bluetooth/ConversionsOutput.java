package de.rwth_aachen.phyphox.Bluetooth;

import java.io.Serializable;
import java.lang.reflect.Method;

import de.rwth_aachen.phyphox.DataBuffer;

// The class holds public static functions which convert double values to a byte array that can be written to a characteristic.
public class ConversionsOutput {

    public static class OutputConversion implements Serializable {
        OutputConversion() {

        }
        protected byte[] convert(DataBuffer data) {
            return null;
        }
    }

    public static class SimpleOutputConversion extends OutputConversion implements Serializable {
        private Method conversionFunction;
        public SimpleOutputConversion(Method conversionFunction) {
            super();
            this.conversionFunction = conversionFunction;
        }

        @Override
        protected byte[] convert(DataBuffer data) {
            try {
                return (byte[]) conversionFunction.invoke(null, data);
            } catch (Exception e) {
                try {
                    return (byte[]) conversionFunction.invoke(null, data.value);
                } catch (Exception e2) {
                    return null;
                }
            }
        }
    }

    public static byte[] string (double data) {
        return (data+"").getBytes();
    }

    public static byte[] int16LittleEndian (double data) {
        byte lowerByte = (byte) data;
        byte upperByte = (byte) ((int)data >> 8);
        return new byte[] {lowerByte, upperByte};
    }

    public static byte[] uInt16LittleEndian (double data) {
        return int16LittleEndian(data);
    }

    public static byte[] int24LittleEndian (double data) {
        byte lowerByte = (byte) data;
        byte mediumByte = (byte) ((int) data >> 8);
        byte upperByte = (byte) ((int) data >> 16);
        return new byte[] {lowerByte, mediumByte, upperByte};
    }

    public static byte[] uInt24LittleEndian (double data) {
        return int24LittleEndian(data);
    }

    public static byte[] int32LittleEndian (double data) {
        byte lowerByte = (byte) data;
        byte mLowerByte = (byte) ((int) data >> 8);
        byte mUpperByte = (byte) ((int) data >> 16);
        byte upperByte = (byte) ((int) data >> 24);
        return new byte[] {lowerByte, mLowerByte, mUpperByte, upperByte};
    }

    public static byte[] uInt32LittleEndian (double data) {
        byte lowerByte = (byte) ((long)data);
        byte mLowerByte = (byte) ((long) data >> 8);
        byte mUpperByte = (byte) ((long) data >> 16);
        byte upperByte = (byte) ((long) data >> 24);
        return new byte[] {lowerByte, mLowerByte, mUpperByte, upperByte};
    }

    public static byte[] int16BigEndian (double data) {
        byte lowerByte = (byte) data;
        byte upperByte = (byte) ((int)data >> 8);
        return new byte[] {upperByte, lowerByte};
    }

    public static byte[] uInt16BigEndian (double data) {
        return int16BigEndian(data);
    }

    public static byte[] int24BigEndian (double data) {
        byte lowerByte = (byte) data;
        byte mediumByte = (byte) ((int) data >> 8);
        byte upperByte = (byte) ((int) data >> 16);
        return new byte[] {upperByte, mediumByte, lowerByte};
    }

    public static byte[] uInt24BigEndian (double data) {
        return int24BigEndian(data);
    }

    public static byte[] int32BigEndian (double data) {
        byte lowerByte = (byte) data;
        byte mLowerByte = (byte) ((int) data >> 8);
        byte mUpperByte = (byte) ((int) data >> 16);
        byte upperByte = (byte) ((int) data >> 24);
        return new byte[] {upperByte, mUpperByte, mLowerByte, lowerByte};
    }

    public static byte[] uInt32BigEndian (double data) {
        byte lowerByte = (byte) ((long)data);
        byte mLowerByte = (byte) ((long) data >> 8);
        byte mUpperByte = (byte) ((long) data >> 16);
        byte upperByte = (byte) ((long) data >> 24);
        return new byte[] {upperByte, mUpperByte, mLowerByte, lowerByte};
    }

    public static byte[] float32LittleEndian (double data) {
        int bits = Float.floatToIntBits((float)data);

        byte lowerByte = (byte) (bits);
        byte mLowerByte = (byte) (bits >> 8);
        byte mUpperByte = (byte) (bits >> 16);
        byte upperByte = (byte) (bits >> 24);
        return new byte[] {lowerByte, mLowerByte, mUpperByte, upperByte};
    }

    public static byte[] float32BigEndian (double data) {
        int bits = Float.floatToIntBits((float)data);

        byte lowerByte = (byte) bits;
        byte mLowerByte = (byte) (bits >> 8);
        byte mUpperByte = (byte) (bits >> 16);
        byte upperByte = (byte) (bits >> 24);
        return new byte[] {upperByte, mUpperByte, mLowerByte, lowerByte};
    }

    public static byte[] float64LittleEndian (double data) {
        long bits = Double.doubleToLongBits(data);

        byte b0 = (byte) (bits);
        byte b1 = (byte) (bits >> 8);
        byte b2 = (byte) (bits >> 16);
        byte b3 = (byte) (bits >> 24);
        byte b4 = (byte) (bits >> 32);
        byte b5 = (byte) (bits >> 40);
        byte b6 = (byte) (bits >> 48);
        byte b7 = (byte) (bits >> 56);
        return new byte[] {b0, b1, b2, b3, b4, b5, b6, b7};
    }

    public static byte[] float64BigEndian (double data) {
        long bits = Double.doubleToLongBits(data);

        byte b0 = (byte) (bits);
        byte b1 = (byte) (bits >> 8);
        byte b2 = (byte) (bits >> 16);
        byte b3 = (byte) (bits >> 24);
        byte b4 = (byte) (bits >> 32);
        byte b5 = (byte) (bits >> 40);
        byte b6 = (byte) (bits >> 48);
        byte b7 = (byte) (bits >> 56);
        return new byte[] {b7, b6, b5, b4, b3, b2, b1, b0};
    }

    public static byte[] singleByte (double data) {
        return new byte[]{(byte)data};
    }

    public static byte[] uInt8 (double data) {
        return singleByte(data);
    }
    public static byte[] int8 (double data) {
        return singleByte(data);
    }

    public static byte[] byteArray (DataBuffer data) {
        Double[] dataArray = data.getArray();
        byte[] result = new byte[dataArray.length];
        for (int i = 0; i < dataArray.length; i++)
            result[i] = (byte)(double)dataArray[i];
        return result;
    }

}
