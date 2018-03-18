package de.rwth_aachen.phyphox;

import android.util.Log;

import java.io.Serializable;
import java.lang.reflect.Method;

// The class holds public static functions which convert double values to a byte array that can be written to a characteristic.
public class ConversionsOutput {

    public static class OutputConversion implements Serializable {
        OutputConversion() {

        }
        protected byte[] convert(dataBuffer data) {
            return null;
        }
    }

    public static class SimpleOutputConversion extends OutputConversion implements Serializable {
        private Method conversionFunction;
        SimpleOutputConversion(Method conversionFunction) {
            super();
            this.conversionFunction = conversionFunction;
        }

        @Override
        protected byte[] convert(dataBuffer data) {
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

    public static byte[] singleByte (double data) {
        return new byte[]{(byte)data};
    }

    public static byte[] byteArray (dataBuffer data) {
        Double[] dataArray = data.getArray();
        byte[] result = new byte[dataArray.length];
        for (int i = 0; i < dataArray.length; i++)
            result[i] = (byte)(double)dataArray[i];
        return result;
    }

}