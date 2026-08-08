package de.rwth_aachen.phyphox.Bluetooth;

import java.io.Serializable;

import de.rwth_aachen.phyphox.DataBuffer;

// The class holds public static functions which convert double values to a byte array that can be written to a characteristic.
public class ConversionsOutput {

    //A single value-to-bytes conversion function. Serializable so the conversion objects stay
    //serializable like they were when they wrapped a reflected Method.
    public interface DoubleToByteArray extends Serializable {
        byte[] apply(double data);
    }

    //Resolves a conversion function named in an experiment file to a conversion object. The name is
    //matched case-insensitively (see the enum-case-insensitive rule in phyphox-docs). Returns null
    //if there is no such function, so the caller can reject the file. The accepted names are the
    //file-format contract and must not change; this replaced a reflection lookup by method name.
    public static OutputConversion getConversion(String name) {
        if (name == null)
            return null;
        switch (name.toLowerCase()) {
            case "bytearray": return new OutputConversion() {
                @Override public byte[] convert(DataBuffer data) { return byteArray(data); }
            };
            case "string": return new SimpleOutputConversion(ConversionsOutput::string);
            case "int16littleendian": return new SimpleOutputConversion(ConversionsOutput::int16LittleEndian);
            case "uint16littleendian": return new SimpleOutputConversion(ConversionsOutput::uInt16LittleEndian);
            case "int24littleendian": return new SimpleOutputConversion(ConversionsOutput::int24LittleEndian);
            case "uint24littleendian": return new SimpleOutputConversion(ConversionsOutput::uInt24LittleEndian);
            case "int32littleendian": return new SimpleOutputConversion(ConversionsOutput::int32LittleEndian);
            case "uint32littleendian": return new SimpleOutputConversion(ConversionsOutput::uInt32LittleEndian);
            case "int16bigendian": return new SimpleOutputConversion(ConversionsOutput::int16BigEndian);
            case "uint16bigendian": return new SimpleOutputConversion(ConversionsOutput::uInt16BigEndian);
            case "int24bigendian": return new SimpleOutputConversion(ConversionsOutput::int24BigEndian);
            case "uint24bigendian": return new SimpleOutputConversion(ConversionsOutput::uInt24BigEndian);
            case "int32bigendian": return new SimpleOutputConversion(ConversionsOutput::int32BigEndian);
            case "uint32bigendian": return new SimpleOutputConversion(ConversionsOutput::uInt32BigEndian);
            case "float32littleendian": return new SimpleOutputConversion(ConversionsOutput::float32LittleEndian);
            case "float32bigendian": return new SimpleOutputConversion(ConversionsOutput::float32BigEndian);
            case "float64littleendian": return new SimpleOutputConversion(ConversionsOutput::float64LittleEndian);
            case "float64bigendian": return new SimpleOutputConversion(ConversionsOutput::float64BigEndian);
            case "singlebyte": return new SimpleOutputConversion(ConversionsOutput::singleByte);
            case "uint8": return new SimpleOutputConversion(ConversionsOutput::uInt8);
            case "int8": return new SimpleOutputConversion(ConversionsOutput::int8);
            default: return null;
        }
    }

    public static class OutputConversion implements Serializable {
        OutputConversion() {

        }
        public byte[] convert(DataBuffer data) {
            return null;
        }
    }

    public static class SimpleOutputConversion extends OutputConversion implements Serializable {
        private final DoubleToByteArray conversionFunction;
        public SimpleOutputConversion(DoubleToByteArray conversionFunction) {
            super();
            this.conversionFunction = conversionFunction;
        }

        @Override
        public byte[] convert(DataBuffer data) {
            try {
                return conversionFunction.apply(data.value);
            } catch (Exception e) {
                return null;
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
