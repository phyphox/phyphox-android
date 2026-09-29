package de.rwth_aachen.phyphox;

import android.content.Context;
import android.content.res.Resources;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//The known units of the phyphox file format (phyphox-docs spec/units.yml, docs/file-format/units.md) and the
//conversion rules the apps and the web interface share. A unit attribute written as "@<id>" (file format 1.21) or
//the deprecated "[[unit_short_<id>]]" refers to one of these; its symbol is the string resource
//common_unit_short_<id>. Units of one quantity convert through the quantity's base unit: base = v * scale + offset.
//This is the only copy on Android; the web interface (index.html, phyphoxUnits) and iOS carry the same table.
public final class Units {
    public enum System { metric, imperial, common }

    //The "Unit system" setting: units as the experiment names them, or every unit of the other system replaced
    //by its declared counterpart
    public enum Setting {
        experiment, metric, imperial;

        public static final String PREF_KEY = "unitSystem";

        public static Setting fromString(String s) {
            if (s == null)
                return experiment;
            for (Setting setting : values())
                if (setting.name().equals(s))
                    return setting;
            return experiment;
        }

        public static Setting read(Context context) {
            if (context == null)
                return experiment;
            return fromString(PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KEY, null));
        }
    }

    public static final class Definition {
        public final String id;
        public final String symbol; //English, the string table translates it
        public final String quantity; //null: known but not convertible (dB, %, arb. unit)
        public final double scale, offset;
        public final System system;
        public final String counterpart; //the unit the setting switches to, null: stays

        Definition(String id, String symbol, String quantity, double scale, double offset, System system, String counterpart) {
            this.id = id;
            this.symbol = symbol;
            this.quantity = quantity;
            this.scale = scale;
            this.offset = offset;
            this.system = system;
            this.counterpart = counterpart;
        }
    }

    private static final Map<String, Definition> TABLE = new LinkedHashMap<>();

    private static void add(String id, String symbol, String quantity, double scale, double offset, System system, String counterpart) {
        TABLE.put(id, new Definition(id, symbol, quantity, scale, offset, system, counterpart));
    }

    static {
        add("nano_meter", "nm", "length", 1e-9, 0, System.metric, null);
        add("micro_meter", "µm", "length", 1e-6, 0, System.metric, null);
        add("milli_meter", "mm", "length", 1e-3, 0, System.metric, "inch");
        add("centi_meter", "cm", "length", 1e-2, 0, System.metric, "inch");
        add("meter", "m", "length", 1, 0, System.metric, "foot");
        add("kilo_meter", "km", "length", 1000, 0, System.metric, "mile");
        add("inch", "in", "length", 0.0254, 0, System.imperial, "centi_meter");
        add("foot", "ft", "length", 0.3048, 0, System.imperial, "meter");
        add("yard", "yd", "length", 0.9144, 0, System.imperial, "meter");
        add("mile", "mi", "length", 1609.344, 0, System.imperial, "kilo_meter");
        add("micro_second", "µs", "time", 1e-6, 0, System.common, null);
        add("milli_second", "ms", "time", 1e-3, 0, System.common, null);
        add("second", "s", "time", 1, 0, System.common, null);
        add("minute", "min", "time", 60, 0, System.common, null);
        add("hour", "h", "time", 3600, 0, System.common, null);
        add("hertz", "Hz", "frequency", 1, 0, System.common, null);
        add("kilo_hertz", "kHz", "frequency", 1000, 0, System.common, null);
        add("per_minute", "1/min", "frequency", 1.0 / 60.0, 0, System.common, null);
        add("meter_per_second", "m/s", "speed", 1, 0, System.metric, "foot_per_second");
        add("kilo_meter_per_hour", "km/h", "speed", 1.0 / 3.6, 0, System.metric, "mile_per_hour");
        add("foot_per_second", "ft/s", "speed", 0.3048, 0, System.imperial, "meter_per_second");
        add("mile_per_hour", "mph", "speed", 0.44704, 0, System.imperial, "kilo_meter_per_hour");
        add("meter_per_square_second", "m/s²", "acceleration", 1, 0, System.metric, "foot_per_square_second");
        add("foot_per_square_second", "ft/s²", "acceleration", 0.3048, 0, System.imperial, "meter_per_square_second");
        add("standard_gravity", "g", "acceleration", 9.80665, 0, System.common, null);
        add("radian_per_second", "rad/s", "angular_velocity", 1, 0, System.common, null);
        add("degree_per_second", "°/s", "angular_velocity", Math.PI / 180.0, 0, System.common, null);
        add("revolution_per_minute", "rpm", "angular_velocity", 2.0 * Math.PI / 60.0, 0, System.common, null);
        add("degree", "°", "angle", Math.PI / 180.0, 0, System.common, null);
        add("radian", "rad", "angle", 1, 0, System.common, null);
        add("micro_tesla", "µT", "magnetic_flux_density", 1e-6, 0, System.common, null);
        add("milli_tesla", "mT", "magnetic_flux_density", 1e-3, 0, System.common, null);
        add("tesla", "T", "magnetic_flux_density", 1, 0, System.common, null);
        add("gauss", "G", "magnetic_flux_density", 1e-4, 0, System.common, null);
        add("lux", "lx", "illuminance", 1, 0, System.metric, "foot_candle");
        add("foot_candle", "fc", "illuminance", 10.7639, 0, System.imperial, "lux");
        add("pascal", "Pa", "pressure", 1, 0, System.metric, null);
        add("hecto_pascal", "hPa", "pressure", 100, 0, System.metric, "inch_of_mercury");
        add("kilo_pascal", "kPa", "pressure", 1000, 0, System.metric, "psi");
        add("milli_bar", "mbar", "pressure", 100, 0, System.metric, "inch_of_mercury");
        add("bar", "bar", "pressure", 100000, 0, System.metric, null);
        add("inch_of_mercury", "inHg", "pressure", 3386.389, 0, System.imperial, "hecto_pascal");
        add("psi", "psi", "pressure", 6894.757, 0, System.imperial, "kilo_pascal");
        add("degree_celsius", "°C", "temperature", 1, 273.15, System.metric, "degree_fahrenheit");
        add("kelvin", "K", "temperature", 1, 0, System.common, null);
        add("degree_fahrenheit", "°F", "temperature", 5.0 / 9.0, 255.37222222222223, System.imperial, "degree_celsius");
        add("decibel", "dB", null, 1, 0, System.common, null);
        add("percent", "%", null, 1, 0, System.common, null);
        add("arbitrary_unit", "arb. unit", null, 1, 0, System.common, null);
    }

    private Units() {
    }

    public static Definition get(String id) {
        return id == null ? null : TABLE.get(id);
    }

    public static boolean isKnown(String id) {
        return get(id) != null;
    }

    //A unit with a quantity can be shown in every other unit of that quantity
    public static boolean isConvertible(String id) {
        Definition d = get(id);
        return d != null && d.quantity != null;
    }

    public static boolean sameQuantity(String a, String b) {
        Definition da = get(a), db = get(b);
        return da != null && db != null && da.quantity != null && da.quantity.equals(db.quantity);
    }

    public static List<Definition> all() {
        return new ArrayList<>(TABLE.values());
    }

    //Every unit of the tapped unit's quantity, in table order; empty for a unit without one
    public static List<Definition> alternatives(String id) {
        List<Definition> result = new ArrayList<>();
        Definition d = get(id);
        if (d == null || d.quantity == null)
            return result;
        for (Definition candidate : TABLE.values())
            if (d.quantity.equals(candidate.quantity))
                result.add(candidate);
        return result;
    }

    //The unit shown under a setting: metric replaces imperial units, imperial replaces metric units that have a
    //counterpart; everything else stays
    public static String forSetting(String id, Setting setting) {
        Definition d = get(id);
        if (d == null || d.quantity == null || setting == null || setting == Setting.experiment || d.counterpart == null)
            return id;
        if (setting == Setting.metric && d.system == System.imperial)
            return d.counterpart;
        if (setting == Setting.imperial && d.system == System.metric)
            return d.counterpart;
        return id;
    }

    //Display factor from one unit to another of the same quantity (m -> cm: 100); 1 where no conversion applies
    public static double scale(String from, String to) {
        Definition df = get(from), dt = get(to);
        if (df == null || dt == null || from.equals(to) || !sameQuantity(from, to))
            return 1;
        return df.scale / dt.scale;
    }

    //A position (value, range end, tick, picked point): scale and offset
    public static double convert(double v, String from, String to) {
        Definition df = get(from), dt = get(to);
        if (df == null || dt == null || from.equals(to) || !sameQuantity(from, to))
            return v;
        return (v * df.scale + df.offset - dt.offset) / dt.scale;
    }

    //A difference (Δ read-out, slope, window width): scale only
    public static double convertDifference(double v, String from, String to) {
        return v * scale(from, to);
    }

    //Decimals after a conversion by display factor f: never coarser than the author's resolution
    public static int precision(int p, double f) {
        if (p < 0 || !(f > 0))
            return p;
        return Math.max(0, p - (int) Math.floor(Math.log10(f)));
    }

    //The (translated) symbol of a known unit from the string table, the English symbol if the resource is missing
    public static String symbol(Resources res, String id) {
        Definition d = get(id);
        if (d == null)
            return id;
        if (res != null) {
            int resId = res.getIdentifier("common_unit_short_" + id, "string", BuildConfig.APPLICATION_ID);
            if (resId > 0)
                return res.getString(resId);
        }
        return d.symbol;
    }
}
