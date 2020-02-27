package de.rwth_aachen.phyphox.Bluetooth;


import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;

// The class holds public static functions which convert values from a byte array to a double value.
public class ConversionsInput {



    public static class InputConversion implements Serializable {
        InputConversion() {

        }
        protected double convert(byte[] data) {
            return Double.NaN;
        }
    }

    public static class SimpleInputConversion extends InputConversion implements Serializable {
        private Method conversionFunction;
        int offset;
        int length;
        public SimpleInputConversion(Method conversionFunction, XmlPullParser xpp) {
            super();
            this.conversionFunction = conversionFunction;
            try {
                this.offset = Integer.valueOf(xpp.getAttributeValue(null, "offset"));
            } catch (Exception e) {
                this.offset = 0;
            }
            try {
                this.length = Integer.valueOf(xpp.getAttributeValue(null, "length"));
            } catch (Exception e) {
                this.length = 0;
            }
        }

        @Override
        protected double convert(byte[] data) {
            try {
                int actualLength = data.length - offset;
                if (length > 0 && length < actualLength)
                    actualLength = length;
                byte[] subdata = Arrays.copyOfRange(data, offset, offset + actualLength);
                return (double) conversionFunction.invoke(null, subdata);
            } catch (Exception e) {
                return Double.NaN;
            }
        }
    }

    /* private helper functions */

    private static Integer uInt16LittleEndian (byte lower, byte upper) {
        Integer lowerByte = (int) lower & 0xFF;
        Integer upperByte = (int) upper & 0xFF;
        return ((upperByte << 8) + lowerByte);
    }

    private static Integer int16LittleEndian (byte lower, byte upper) {
        Integer lowerByte = (int) lower & 0xFF;
        Integer upperByte = (int) upper;
        return ((upperByte << 8) + lowerByte);
    }

    private static Integer uInt24LittleEndian(byte lower, byte medium, byte upper) {
        Integer lowerByte = (int) lower & 0xFF;
        Integer mediumByte = (int) medium & 0xFF;
        Integer upperByte = (int) upper & 0xFF;
        return ((upperByte << 16) + (mediumByte << 8) + lowerByte);
    }

    private static Integer int24LittleEndian(byte lower, byte medium, byte upper) {
        Integer lowerByte = (int) lower & 0xFF;
        Integer mediumByte = (int) medium & 0xFF;
        Integer upperByte = (int) upper;
        return ((upperByte << 16) + (mediumByte << 8) + lowerByte);
    }

    private static Long uInt32LittleEndian (byte lower, byte mLower, byte mUpper, byte upper) {
        Long lowerByte = (long) lower & 0xFF;
        Long mLowerByte = (long) mLower & 0xFF;
        Long mUpperByte = (long) mUpper & 0xFF;
        Long upperByte = (long) upper & 0xFF;
        return ((upperByte << 24) + (mUpperByte << 16) + (mLowerByte << 8) + lowerByte);
    }

    private static Integer int32LittleEndian (byte lower, byte mLower, byte mUpper, byte upper) {
        Integer lowerByte = (int) lower & 0xFF;
        Integer mLowerByte = (int) mLower & 0xFF;
        Integer mUpperByte = (int) mUpper & 0xFF;
        Integer upperByte = (int) upper;
        return ((upperByte << 24) + (mUpperByte << 16) + (mLowerByte << 8) + lowerByte);
    }



    /* common functions */

    public static double uInt16LittleEndian(byte[] data) {
        return uInt16LittleEndian(data[0], data[1]);
    }

    public static double uInt16BigEndian(byte[] data) {
        return uInt16LittleEndian(data[1], data[0]);
    }

    public static double int16LittleEndian (byte[] data) {
        return int16LittleEndian(data[0], data[1]);
    }

    public static double int16BigEndian (byte[] data) {
        return int16LittleEndian(data[1], data[0]);
    }

    public static double uInt24LittleEndian (byte[] data) {
        return uInt24LittleEndian(data[0], data[1], data[2]);
    }

    public static double uInt24BigEndian (byte[] data) {
        return uInt24LittleEndian(data[2], data[1], data[0]);
    }

    public static double int24LittleEndian (byte[] data) {
        return int24LittleEndian(data[0], data[1], data[2]);
    }

    public static double int24BigEndian (byte[] data) {
        return int24LittleEndian(data[2], data[1], data[0]);
    }

    public static double uInt32LittleEndian (byte[] data) {
        return uInt32LittleEndian(data[0], data[1], data[2], data[3]);
    }

    public static double uInt32BigEndian (byte[] data) {
        return uInt32LittleEndian(data[3], data[2], data[1], data[0]);
    }

    public static double int32LittleEndian (byte[] data) {
        return int32LittleEndian(data[0], data[1], data[2], data[3]);
    }

    public static double int32BigEndian (byte[] data) {
        return int32LittleEndian(data[3], data[2], data[1], data[0]);
    }

    public static double float32LittleEndian (byte[] data) {
        int bits = 0;
        for (int i = 0; i < 4; i++) {
            bits |= (data[i] & 0xFF) << (8 * i);
        }
        return Float.intBitsToFloat(bits);
    }

    public static double float32BigEndian (byte[] data) {
        int bits = 0;
        for (int i = 0; i < 4; i++) {
            bits |= (data[i] & 0xFF) << (8 * (3-i));
        }
        return Float.intBitsToFloat(bits);
    }

    public static double float64LittleEndian (byte[] data) {
        long bits = 0;
        for (int i = 0; i < 8; i++) {
            bits |= (data[i] & 0xFF) << (8 * i);
        }
        return Double.longBitsToDouble(bits);
    }

    public static double float64BigEndian (byte[] data) {
        long bits = 0;
        for (int i = 0; i < 8; i++) {
            bits |= (data[i] & 0xFF) << (8 * (7-i));
        }
        return Double.longBitsToDouble(bits);
    }

    public static class string extends InputConversion implements Serializable {
        String decimalPoint;
        int offset = 0;
        int length = 0;

        string(XmlPullParser xpp) {
            super();
            this.decimalPoint = xpp.getAttributeValue(null, "decimalPoint");
            String offset= xpp.getAttributeValue(null, "offset");
            String length = xpp.getAttributeValue(null, "length");
            if (offset != null) {
                try {
                    this.offset = Integer.valueOf(offset);
                } catch (Exception e) {

                }
            }
            if (length != null) {
                try {
                    this.length = Integer.valueOf(length);
                } catch (Exception e) {

                }
            }

        }

        public double convert (byte[] data) {
            int actualLength = data.length - offset;
            if (length > 0 && length < actualLength)
                actualLength = length;
            byte[] subdata = Arrays.copyOfRange(data, offset, offset + actualLength);

            String s;
            if (decimalPoint == null)
                s = new String(subdata);
            else
                s = (new String(subdata)).replace(this.decimalPoint, ".");
            try {
                return Double.parseDouble(s);
            } catch (Exception e) {
                return Double.NaN;
            }
        }

    }

    public static class formattedString extends InputConversion implements Serializable {
        String separator;
        String label;
        int index;
        formattedString(XmlPullParser xpp) {
            super();
            this.separator = xpp.getAttributeValue(null, "separator");
            this.label = xpp.getAttributeValue(null, "label");
            try {
                this.index = Integer.valueOf(xpp.getAttributeValue(null, "index"));
            } catch (Exception e) {
                this.index = 0;
            }
        }

        @Override
        protected double convert(byte[] data) {
            String[] s;
            if (separator.isEmpty()) {
                s = new String[1];
                s[0] = new String(data);
            } else
                s = (new String(data)).split(this.separator);
            if (s.length <= this.index)
                return Double.NaN;
            if (this.label == null || this.label.isEmpty()) {
                //Use the index to pick the entry (CSV style)
                try {
                    return Double.parseDouble(s[this.index]);
                } catch (Exception e) {
                    return Double.NaN;
                }
            } else {
                //Use the label to pick the entry
                for (String entry : s) {
                    if (entry.startsWith(label)) {
                        try {
                            return Double.parseDouble(entry.substring(label.length()));
                        } catch (Exception e) {
                            return Double.NaN;
                        }
                    }
                }
            }
            return Double.NaN;
        }
    }

    public static double int8(byte[] data) {
        return data[0];
    }

    public static double uInt8 (byte[] data) {
        return data[0] & 0xff;
    }

    public static double singleByte (byte[] data) {
        return uInt8(data);
    }

}
