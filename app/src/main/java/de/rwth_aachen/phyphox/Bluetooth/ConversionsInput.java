package de.rwth_aachen.phyphox.Bluetooth;


import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// The class holds public static functions which convert values from a byte array to a double value.
public class ConversionsInput {



    public static class InputConversion implements Serializable {
        InputConversion() {

        }
        protected List<Double> convert(byte[] data) {
            return new ArrayList<>();
        }
    }

    public static class SimpleInputConversion extends InputConversion implements Serializable {
        private final Method conversionFunction;
        int offset;
        int repeating;
        int length;
        public SimpleInputConversion(Method conversionFunction, XmlPullParser xpp) {
            super();
            this.conversionFunction = conversionFunction;
            try {
                this.offset = Integer.parseInt(xpp.getAttributeValue(null, "offset"));
            } catch (Exception e) {
                this.offset = 0;
            }
            try {
                this.repeating = Integer.parseInt(xpp.getAttributeValue(null, "repeating"));
            } catch (Exception e) {
                this.repeating = 0;
            }
            try {
                this.length = Integer.parseInt(xpp.getAttributeValue(null, "length"));
            } catch (Exception e) {
                this.length = 0;
            }
        }

        @Override
        protected List<Double> convert(byte[] data) {
            List<Double> out = new ArrayList<>();
            try {
                int index = offset;
                while (index < data.length) {
                    int actualLength = data.length - index;
                    if (length > 0 && length < actualLength)
                        actualLength = length;
                    byte[] subdata = Arrays.copyOfRange(data, index, index + actualLength);
                    out.add((Double) conversionFunction.invoke(null, subdata));
                    if (repeating > 0)
                        index += repeating;
                    else
                        break;
                }
                return out;
            } catch (Exception e) {
                return out;
            }
        }
    }

    /* private helper functions */

    private static Integer uInt16LittleEndian (byte lower, byte upper) {
        int lowerByte = (int) lower & 0xFF;
        int upperByte = (int) upper & 0xFF;
        return ((upperByte << 8) + lowerByte);
    }

    private static Integer int16LittleEndian (byte lower, byte upper) {
        int lowerByte = (int) lower & 0xFF;
        int upperByte = (int) upper;
        return ((upperByte << 8) + lowerByte);
    }

    private static Integer uInt24LittleEndian(byte lower, byte medium, byte upper) {
        int lowerByte = (int) lower & 0xFF;
        int mediumByte = (int) medium & 0xFF;
        int upperByte = (int) upper & 0xFF;
        return ((upperByte << 16) + (mediumByte << 8) + lowerByte);
    }

    private static Integer int24LittleEndian(byte lower, byte medium, byte upper) {
        int lowerByte = (int) lower & 0xFF;
        int mediumByte = (int) medium & 0xFF;
        int upperByte = (int) upper;
        return ((upperByte << 16) + (mediumByte << 8) + lowerByte);
    }

    private static Long uInt32LittleEndian (byte lower, byte mLower, byte mUpper, byte upper) {
        long lowerByte = (long) lower & 0xFF;
        long mLowerByte = (long) mLower & 0xFF;
        long mUpperByte = (long) mUpper & 0xFF;
        long upperByte = (long) upper & 0xFF;
        return ((upperByte << 24) + (mUpperByte << 16) + (mLowerByte << 8) + lowerByte);
    }

    private static Integer int32LittleEndian (byte lower, byte mLower, byte mUpper, byte upper) {
        int lowerByte = (int) lower & 0xFF;
        int mLowerByte = (int) mLower & 0xFF;
        int mUpperByte = (int) mUpper & 0xFF;
        int upperByte = (int) upper;
        return ((upperByte << 24) + (mUpperByte << 16) + (mLowerByte << 8) + lowerByte);
    }



    /* common functions */

    public static double int8(byte[] data) {
        return data[0];
    }

    public static double uInt8 (byte[] data) {
        return data[0] & 0xff;
    }

    public static double singleByte (byte[] data) {
        return uInt8(data);
    }

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
        int repeating = 0;
        int length = 0;

        string(XmlPullParser xpp) {
            super();
            this.decimalPoint = xpp.getAttributeValue(null, "decimalPoint");
            String offset= xpp.getAttributeValue(null, "offset");
            String repeating = xpp.getAttributeValue(null, "repeating");
            String length = xpp.getAttributeValue(null, "length");
            if (offset != null) {
                try {
                    this.offset = Integer.parseInt(offset);
                } catch (Exception e) {

                }
            }
            if (repeating != null) {
                try {
                    this.repeating = Integer.parseInt(repeating);
                } catch (Exception e) {

                }
            }
            if (length != null) {
                try {
                    this.length = Integer.parseInt(length);
                } catch (Exception e) {

                }
            }

        }

        public List<Double> convert (byte[] data) {
            List<Double> out = new ArrayList<>();
            int index = offset;

            while (index < data.length) {
                int actualLength = data.length - offset;
                if (length > 0 && length < actualLength)
                    actualLength = length;
                byte[] subdata = Arrays.copyOfRange(data, index, index + actualLength);

                String s;
                if (decimalPoint == null)
                    s = new String(subdata);
                else
                    s = (new String(subdata)).replace(this.decimalPoint, ".");
                try {
                    out.add(Double.parseDouble(s));
                } catch (Exception e) {
                    return out;
                }
                if (repeating > 0)
                    index += repeating;
                else
                    break;
            }
            return out;
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
                this.index = Integer.parseInt(xpp.getAttributeValue(null, "index"));
            } catch (Exception e) {
                this.index = 0;
            }
        }

        @Override
        protected List<Double> convert(byte[] data) {
            String[] s;
            if (separator.isEmpty()) {
                s = new String[1];
                s[0] = new String(data);
            } else
                s = (new String(data)).split(this.separator);
            List<Double> out = new ArrayList<>(1);
            if (s.length <= this.index)
                return out;
            if (this.label == null || this.label.isEmpty()) {
                //Use the index to pick the entry (CSV style)
                try {
                    out.add(Double.parseDouble(s[this.index]));
                    return out;
                } catch (Exception e) {
                    return out;
                }
            } else {
                //Use the label to pick the entry
                for (String entry : s) {
                    if (entry.startsWith(label)) {
                        try {
                            out.add(Double.parseDouble(entry.substring(label.length())));
                            return out;
                        } catch (Exception e) {
                            return out;
                        }
                    }
                }
            }
            return out;
        }
    }

}
