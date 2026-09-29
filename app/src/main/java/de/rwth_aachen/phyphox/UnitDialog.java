package de.rwth_aachen.phyphox;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

//The dialog behind a tapped unit: every unit of the same quantity, grouped by system, the experiment's own unit
//marked as its default (docs/file-format/units.md, "Switching a unit by hand"). Shared by value, edit and graph.
public final class UnitDialog {
    public interface Listener {
        void onUnitChosen(String id);
    }

    private static final class Row {
        final String header; //a group header, or
        final Units.Definition unit;

        Row(String header, Units.Definition unit) {
            this.header = header;
            this.unit = unit;
        }
    }

    private UnitDialog() {
    }

    public static void show(Context context, String experimentUnitId, String currentUnitId, Listener listener) {
        List<Row> rows = rows(context, experimentUnitId);
        if (rows.isEmpty())
            return;
        int checked = -1;
        for (int i = 0; i < rows.size(); i++)
            if (rows.get(i).unit != null && rows.get(i).unit.id.equals(currentUnitId))
                checked = i;
        final AlertDialog[] dialog = new AlertDialog[1];
        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return rows.size();
            }

            @Override
            public Object getItem(int position) {
                return rows.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public int getViewTypeCount() {
                return 2;
            }

            @Override
            public int getItemViewType(int position) {
                return rows.get(position).unit == null ? 0 : 1;
            }

            @Override
            public boolean areAllItemsEnabled() {
                return false;
            }

            @Override
            public boolean isEnabled(int position) {
                return rows.get(position).unit != null;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                Row row = rows.get(position);
                if (row.unit == null) {
                    TextView header = convertView instanceof TextView && !(convertView instanceof CheckedTextView) ? (TextView) convertView
                            : (TextView) LayoutInflater.from(context).inflate(android.R.layout.preference_category, parent, false);
                    header.setText(row.header);
                    return header;
                }
                CheckedTextView item = convertView instanceof CheckedTextView ? (CheckedTextView) convertView
                        : (CheckedTextView) LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_single_choice, parent, false);
                String label = Units.symbol(context.getResources(), row.unit.id);
                if (row.unit.id.equals(experimentUnitId))
                    label += " (" + context.getString(R.string.unit_dialog_experiment_default) + ")";
                item.setText(label);
                return item;
            }
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.unit_dialog_title);
        builder.setSingleChoiceItems(adapter, checked, (d, which) -> {
            Row row = rows.get(which);
            if (row.unit != null && listener != null)
                listener.onUnitChosen(row.unit.id);
            d.dismiss();
        });
        builder.setNegativeButton(R.string.cancel, null);
        dialog[0] = builder.create();
        dialog[0].show();
    }

    //Rows of the list: a header per system that has units of this quantity, then its units in table order
    static List<Row> rows(Context context, String unitId) {
        List<Row> rows = new ArrayList<>();
        List<Units.Definition> alternatives = Units.alternatives(unitId);
        Units.System[] order = {Units.System.metric, Units.System.imperial, Units.System.common};
        for (Units.System system : order) {
            boolean any = false;
            for (Units.Definition d : alternatives) {
                if (d.system != system)
                    continue;
                if (!any) {
                    rows.add(new Row(groupTitle(context, system), null));
                    any = true;
                }
                rows.add(new Row(null, d));
            }
        }
        return rows;
    }

    //The symbols the list shows, in order, for the tests
    public static List<String> listedUnitIds(Context context, String unitId) {
        List<String> ids = new ArrayList<>();
        for (Row row : rows(context, unitId))
            if (row.unit != null)
                ids.add(row.unit.id);
        return ids;
    }

    private static String groupTitle(Context context, Units.System system) {
        switch (system) {
            case metric: return context.getString(R.string.settingsUnitSystemMetric);
            case imperial: return context.getString(R.string.settingsUnitSystemImperial);
            default: return context.getString(R.string.unit_dialog_other);
        }
    }
}
