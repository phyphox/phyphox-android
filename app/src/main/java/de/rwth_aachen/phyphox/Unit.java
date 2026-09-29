package de.rwth_aachen.phyphox;

import android.content.res.Resources;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

//A parsed unit attribute: a reference to a known unit (id, convertible, shown with the app's symbol) or custom text
//shown as written (docs/file-format/units.md)
public final class Unit implements Serializable {
    public final String id; //known unit, or null
    public final String text; //custom text, or null for a reference

    private Unit(String id, String text) {
        this.id = id;
        this.text = text;
    }

    public static Unit reference(String id) {
        return new Unit(id, null);
    }

    public static Unit text(String text) {
        return new Unit(null, text);
    }

    public boolean isReference() {
        return id != null;
    }

    public boolean isEmpty() {
        return id == null && (text == null || text.isEmpty());
    }

    //The symbol of the experiment's unit
    public String symbol(Resources res) {
        if (id != null)
            return Units.symbol(res, id);
        return text == null ? "" : text;
    }

    //The web interface receives the unit logically (readme.md, "The view layout")
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id == null ? JSONObject.NULL : id);
        json.put("text", text == null ? JSONObject.NULL : text);
        return json;
    }

    public static String jsonOf(Unit unit) {
        try {
            return (unit == null ? text("").toJson() : unit.toJson()).toString();
        } catch (JSONException e) {
            return "{\"id\":null,\"text\":null}";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Unit))
            return false;
        Unit other = (Unit) o;
        return (id == null ? other.id == null : id.equals(other.id)) && (text == null ? other.text == null : text.equals(other.text));
    }

    @Override
    public int hashCode() {
        return (id == null ? 0 : id.hashCode()) * 31 + (text == null ? 0 : text.hashCode());
    }
}
