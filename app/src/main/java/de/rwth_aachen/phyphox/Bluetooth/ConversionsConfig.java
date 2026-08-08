package de.rwth_aachen.phyphox.Bluetooth;


import java.io.Serializable;

// The class holds public static functions which convert values from a string to a byte array.
public class ConversionsConfig {

    //A single string-to-bytes conversion function. Serializable so the conversion objects stay
    //serializable like they were when they wrapped a reflected Method.
    public interface StringToByteArray extends Serializable {
        byte[] apply(String data);
    }

    //Resolves a conversion function named in an experiment file to a conversion object. The name is
    //matched case-insensitively (see the enum-case-insensitive rule in phyphox-docs). Returns null
    //if there is no such function, so the caller can reject the file. The accepted names are the
    //file-format contract and must not change; this replaced a reflection lookup by method name.
    public static ConfigConversion getConversion(String name) {
        if (name == null)
            return null;
        switch (name.toLowerCase()) {
            case "string": return new SimpleConfigConversion(ConversionsConfig::string);
            case "hexadecimal": return new SimpleConfigConversion(ConversionsConfig::hexadecimal);
            case "int16littleendian": return new SimpleConfigConversion(ConversionsConfig::int16LittleEndian);
            case "uint16littleendian": return new SimpleConfigConversion(ConversionsConfig::uInt16LittleEndian);
            case "int24littleendian": return new SimpleConfigConversion(ConversionsConfig::int24LittleEndian);
            case "uint24littleendian": return new SimpleConfigConversion(ConversionsConfig::uInt24LittleEndian);
            case "int32littleendian": return new SimpleConfigConversion(ConversionsConfig::int32LittleEndian);
            case "uint32littleendian": return new SimpleConfigConversion(ConversionsConfig::uInt32LittleEndian);
            case "int16bigendian": return new SimpleConfigConversion(ConversionsConfig::int16BigEndian);
            case "uint16bigendian": return new SimpleConfigConversion(ConversionsConfig::uInt16BigEndian);
            case "int24bigendian": return new SimpleConfigConversion(ConversionsConfig::int24BigEndian);
            case "uint24bigendian": return new SimpleConfigConversion(ConversionsConfig::uInt24BigEndian);
            case "int32bigendian": return new SimpleConfigConversion(ConversionsConfig::int32BigEndian);
            case "uint32bigendian": return new SimpleConfigConversion(ConversionsConfig::uInt32BigEndian);
            case "float32littleendian": return new SimpleConfigConversion(ConversionsConfig::float32LittleEndian);
            case "float32bigendian": return new SimpleConfigConversion(ConversionsConfig::float32BigEndian);
            case "float64littleendian": return new SimpleConfigConversion(ConversionsConfig::float64LittleEndian);
            case "float64bigendian": return new SimpleConfigConversion(ConversionsConfig::float64BigEndian);
            case "singlebyte": return new SimpleConfigConversion(ConversionsConfig::singleByte);
            case "int8": return new SimpleConfigConversion(ConversionsConfig::int8);
            case "uint8": return new SimpleConfigConversion(ConversionsConfig::uInt8);
            default: return null;
        }
    }

    public static class ConfigConversion implements Serializable {
        ConfigConversion() {

        }
        public byte[] convert(String data) {
            return null;
        }
    }

    public static class SimpleConfigConversion extends ConfigConversion implements Serializable {
        private final StringToByteArray conversionFunction;
        public SimpleConfigConversion(StringToByteArray conversionFunction) {
            super();
            this.conversionFunction = conversionFunction;
        }

        @Override
        public byte[] convert(String data) {
            try {
                return conversionFunction.apply(data);
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
        String hex = data.replace(" ", "");
        byte[] result = new byte[hex.length()/2];
        for (int i = 0; i + 1 < hex.length(); i+=2) { //A dangling character of an odd-length string is ignored
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i+1), 16);
            if (high < 0 || low < 0)
                return new byte[0]; //An invalid character yields an empty array rather than junk bytes
            result[i/2] = (byte) ((high << 4) + low);
        }
        return result;
    }
}
