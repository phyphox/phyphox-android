package de.rwth_aachen.phyphox.Helper;

import android.graphics.Color;

import java.util.HashMap;
import java.util.Map;

public abstract class ColorConverter {

    private static final double HUE_MAX = 360.0;

    // TODO: Need to check for the nullability
    public static String adjustableColor(int intColor) {
        String c = String.format("#%06X", (0xFFFFFF & intColor));
            Map<String, Integer> rgb = stringToRgb(c);

            if (rgb.get("r") == 0x40 && rgb.get("g") == 0x40 && rgb.get("b") == 0x40)
                return "#ffffff";
            Map<String, Double> hsv = rgbToHsv(rgb);
            double l = (2.0 - hsv.get("s")) * hsv.get("v") / 2.0;
            double s = l > 0 && l < 1 ? hsv.get("s") * hsv.get("v") / (l < 0.5 ? l * 2.0 : 2.0 - l * 2.0) : 0.0;
            l = 1.0 - l;
            double t = s * (l < 0.5 ? l : 1.0 - l);
            hsv.put("v", l + t);
            hsv.put("s", l > 0 ? 2 * t / hsv.get("v") : 0.0);
            return hsvToRgb(hsv.get("h"), hsv.get("s"), hsv.get("v"));

    }

    public static Map<String, Integer> stringToRgb(String rgb) {
        Map<String, Integer> result = new HashMap<>();
        String[] rgbsep;
        if (rgb.charAt(0) == '#') {
            int i = Integer.parseInt(rgb.substring(1), 16);
            result.put("r", (i >> 16) & 255);
            result.put("g", (i >> 8) & 255);
            result.put("b", i & 255);
        } else {
            rgbsep = rgb.replaceAll("[^\\d,]", "").split(",");
            if (rgbsep.length == 3) {
                result.put("r", Integer.parseInt(rgbsep[0]));
                result.put("g", Integer.parseInt(rgbsep[1]));
                result.put("b", Integer.parseInt(rgbsep[2]));
            }
        }
        return result;
    }

    private static Map<String, Double> rgbToHsv(Map<String, Integer> rgb) {
        Map<String, Double> result = new HashMap<>();
        int r = rgb.get("r");
        int g = rgb.get("g");
        int b = rgb.get("b");

        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        double d = max - min;

        result.put("v", max / 255.0);
        double s = (max == 0) ? 0 : d / max;

        double h;
        if (max == min) {
            h = 0;
        } else if (max == r) {
            h = (g - b + d * (g < b ? 6 : 0)) / (6 * d);
        } else if (max == g) {
            h = (b - r + d * 2) / (6 * d);
        } else {
            h = (r - g + d * 4) / (6 * d);
        }

        result.put("h", h * HUE_MAX);
        result.put("s", s);
        return result;
    }


    private static String hsvToRgb(double h, double s, double v) {
        int r, g, b, i;
        double f, p, q, t;
        i = (int) Math.floor(h * 6/HUE_MAX);
        f = h * 6/HUE_MAX - i;
        p = v * (1 - s);
        q = v * (1 - f * s);
        t = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0:
                r = (int) (v * 255);
                g = (int) (t * 255);
                b = (int) (p * 255);
                break;
            case 1:
                r = (int) (q * 255);
                g = (int) (v * 255);
                b = (int) (p * 255);
                break;
            case 2:
                r = (int) (p * 255);
                g = (int) (v * 255);
                b = (int) (t * 255);
                break;
            case 3:
                r = (int) (p * 255);
                g = (int) (q * 255);
                b = (int) (v * 255);
                break;
            case 4:
                r = (int) (t * 255);
                g = (int) (p * 255);
                b = (int) (v * 255);
                break;
            case 5:
                r = (int) (v * 255);
                g = (int) (p * 255);
                b = (int) (q * 255);
                break;
            default:
                return "#000000";
        }
        return String.format("#%06X", (0xFFFFFF & ((r << 16) | (g << 8) | b)));
    }

}

