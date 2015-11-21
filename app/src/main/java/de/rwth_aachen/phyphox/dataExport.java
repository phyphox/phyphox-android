package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;

import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Map;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class dataExport {
    public Vector<exportSet> exportSets = new Vector<>();
    private phyphoxExperiment experiment;

    protected abstract class exportFormat {
        protected abstract String getName();
        protected abstract File export (Vector<exportSet> sets, File exportPath);
        protected abstract String getType();
        protected abstract String getFilename();
    }

    protected class csvFormat extends exportFormat {
        protected String separator;
        protected String name;

        csvFormat(String separator, String name) {
            this.separator = separator;
            this.name = name;
        }

        csvFormat() {
            this(",", "Comma-separated values (CSV)");
        }

        @Override
        protected String getName() {
            return name;
        }

        @Override
        protected File export (Vector<exportSet> sets, File exportPath) {
            File file = new File(exportPath, "/"+getFilename());

            try {
                FileOutputStream stream = new FileOutputStream(file);
                ZipOutputStream zstream = new ZipOutputStream(stream);
                try {
                    for (exportSet set : sets) {
                        ZipEntry entry = new ZipEntry(set.name + ".csv");
                        zstream.putNextEntry(entry);

                        String header = "";
                        for (int j = 0; j < set.data.length; j++) {
                            header += set.sources.get(j).name;
                            if (j < set.data.length -1)
                                header += separator;
                        }
                        header += "\n";
                        zstream.write(header.getBytes());

                        for (int i = 0; i < set.data[0].length; i++) {
                            String data = "";
                            for (int j = 0; j < set.data.length; j++) {
                                if (i < set.data[j].length)
                                    data += set.data[j][i];
                                else
                                    data += "NaN";
                                if (j < set.data.length - 1)
                                    data += separator;
                            }
                            data += "\n";
                            zstream.write(data.getBytes());
                        }

                        zstream.closeEntry();
                    }
                } catch (Exception e) {
                    Log.d("csvExport", "Unhandled exception during write.", e);
                } finally {
                    zstream.close();
                }
            } catch (Exception e) {
                Log.d("csvExport", "Unhandled exception.", e);
            }

            return file;
        }

        @Override
        protected String getType () {
            return "application/zip";
        }
        @Override
        protected String getFilename () {
            return "phyphox.zip";
        }
    }

    protected class excelFormat extends exportFormat {
        excelFormat() {

        }

        @Override
        protected String getName() {
            return "Excel";
        }

        @Override
        protected File export (Vector<exportSet> sets, File exportPath) {
            Workbook wb = new HSSFWorkbook();
            File file = new File(exportPath, "/"+getFilename());

            try {
                for (exportSet set : sets) {
                    Sheet sheet = wb.createSheet(set.name);

                    Font font= wb.createFont();
                    font.setBold(true);

                    CellStyle cs = wb.createCellStyle();
                    cs.setFont(font);
                    Row row = sheet.createRow(0);
                    for (int j = 0; j < set.data.length; j++) {
                        Cell c = row.createCell(j);
                        c.setCellValue(set.sources.get(j).name);
                        c.setCellStyle(cs);
                    }


                    for (int i = 0; i < set.data[0].length; i++) {
                        row = sheet.createRow(i+1);
                        for (int j = 0; j < set.data.length; j++) {
                            Cell c = row.createCell(j);
                            if (i < set.data[j].length)
                                c.setCellValue(set.data[j][i]);
                            else
                                c.setCellValue("NaN");
                        }
                    }

                    FileOutputStream os = null;
                    try {
                        os = new FileOutputStream(file);
                        wb.write(os);
                    } catch (Exception e) {
                        Log.d("excelExport", "Unhandled exception during write.", e);
                    } finally {
                        if (os != null)
                            os.close();
                    }
                }

            } catch (Exception e) {
                Log.d("excelExport", "Unhandled exception.", e);
            }

            return file;
        }

        @Override
        protected String getType () {
            return "application/vnd.ms-excel";
        }

        @Override
        protected String getFilename () {
            return "phyphox.xlsx";
        }
    }

    public final exportFormat[] exportFormats = {new csvFormat(), new csvFormat("\t", "Tab-separated values (CSV)"), new excelFormat()};

    public class exportSet {
        String name;

        protected class sourceMapping {
            String name;
            String source;

            sourceMapping(String name, String source) {
                this.name = name;
                this.source = source;
            }
        }

        Vector<sourceMapping> sources = new Vector<>();

        Double[][] data;

        exportSet(String name) {
            this.name = name;
        }

        public void addSource(String name, String source) {
            this.sources.add(new sourceMapping(name, source));
        }

        public void getData(Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap) {
            data = new Double[sources.size()][];
            for (int i = 0; i < sources.size(); i++) {
                dataBuffer buffer = dataBuffers.get(dataMap.get(sources.get(i).source));
                data[i] = buffer.getArray();
            }
        }
    }

    dataExport(phyphoxExperiment experiment) {
        this.experiment = experiment;
    }

    public void addSet(exportSet set) {
        this.exportSets.add(set);
    }

    public void export(Activity c) {

        for (int i = 0; i < exportSets.size(); i++) {
            exportSets.get(i).getData(experiment.dataBuffers, experiment.dataMap);
        }

        showDialog(exportSets, c);
    }

    protected void showDialog(final Vector<exportSet> exportSets, final Activity c) {
        final ArrayList<Integer> mSelectedItems = new ArrayList<>();
        AlertDialog.Builder builder = new AlertDialog.Builder(c);

        CharSequence[] options = new CharSequence[exportSets.size()];
        boolean[] enabled = new boolean[exportSets.size()];
        for (int i = 0; i < exportSets.size(); i++) {
            options[i] = exportSets.get(i).name;
            enabled[i] = true;
            mSelectedItems.add(i);
        }

        builder.setTitle(R.string.pick_exportSets)
                .setMultiChoiceItems(options, enabled,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                if (isChecked) {
                                    mSelectedItems.add(which);
                                } else if (mSelectedItems.contains(which)) {
                                    mSelectedItems.remove(Integer.valueOf(which));
                                }
                            }
                        })
                .setPositiveButton(R.string.next, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        Vector<exportSet> chosenSets = new Vector<>();
                        for (int i = 0; i < exportSets.size(); i++) {
                            if (mSelectedItems.contains(i))
                                chosenSets.add(exportSets.get(i));
                        }
                        showFormatDialog(chosenSets, c);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });

        builder.create().show();
    }

    protected class mutableInteger {
        public int value;
    }

    protected void showFormatDialog(final Vector<exportSet> chosenSets, final Activity c) {
        final CharSequence[] options = new CharSequence[exportFormats.length];

        final mutableInteger selected = new mutableInteger();

        for (int i = 0; i < exportFormats.length; i++) {
            options[i] = exportFormats[i].getName();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(c);
        builder.setTitle(R.string.pick_exportFormat)
                .setSingleChoiceItems(options, 0,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                               selected.value = which;
                            }
                        })
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        File exportFile = exportFormats[selected.value].export(chosenSets, c.getCacheDir());

                        final Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".exportProvider", exportFile);
                        c.grantUriPermission(c.getPackageName(), uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        final Intent intent = ShareCompat.IntentBuilder.from(c)
                                .setType(exportFormats[selected.value].getType())
                                .setSubject(c.getString(R.string.export_subject))
                                .setStream(uri)
                                .setChooserTitle(R.string.export_pick_share)
                                .createChooserIntent()
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        c.startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });

        builder.create().show();
    }

    protected File exportDirect(ArrayList<Integer> selectedItems, exportFormat format, File cacheDir) {
        Vector<exportSet> chosenSets = new Vector<>();
        for (int i = 0; i < exportSets.size(); i++) {
            if (selectedItems.contains(i)) {
                exportSets.get(i).getData(experiment.dataBuffers, experiment.dataMap);
                chosenSets.add(exportSets.get(i));
            }
        }
        return format.export(chosenSets, cacheDir);
    }

}
