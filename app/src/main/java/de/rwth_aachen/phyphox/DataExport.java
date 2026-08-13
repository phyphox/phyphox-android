package de.rwth_aachen.phyphox;

import static de.rwth_aachen.phyphox.helper.DataExportUtility.MIME_TYPE_CSV_MINI;
import static de.rwth_aachen.phyphox.helper.DataExportUtility.MIME_TYPE_CSV_ZIP;
import static de.rwth_aachen.phyphox.helper.DataExportUtility.MIME_TYPE_XLSX;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.rwth_aachen.phyphox.helper.DataExportUtility;
import de.rwth_aachen.phyphox.helper.FileNameFormat;
import de.rwth_aachen.phyphox.helper.XlsxWriter;

//The DataExport class provides export functionality for a phyphoxExperiment.
//it provides multiple export formats and the dialogs to control them
public class DataExport implements Serializable {

    //ExportSet class
    //An export set is a collection of related dataBuffers, which (ideally) have the same size
    //exportSets are defined for each experiments and represent logical subsets of all dataBuffers which the user might want to export
    public class ExportSet implements Serializable {
        String name;

        //This class maps dataBuffers (by their key name) to a name in this ExportSet
        protected class SourceMapping implements Serializable {
            String name;
            String source;

            SourceMapping(String name, String source) {
                this.name = name;
                this.source = source;
            }

            String getSecureName() {
                //Returns a sanitized version of name to prevent creating malicious data files through phyphox.

                //Prevent Excel formula injection (seriously, in case of csv, Excel should not be executing anyways, but let's protect the users...)
                if (name.startsWith("=") || name.startsWith("+") || name.startsWith("-") || name.startsWith("@")) {
                    return "'" + name;
                }

                return name;
            }
        }

        //The set consists of an arbitrary number of sourceMappings. So each entry in the dataSet has a name and a dataBuffer-source
        Vector<SourceMapping> sources = new Vector<>();

        //We will also hold an array with all the data. The idea is to let all dataSets collect their
        // data as fast as possible (like a snapshot) and then take care of pushing the data to an
        // exporter. So after instantiating an ExportSet (with a name), addSource is called for
        // each dataBuffer that should be added to the collection. Then getData is called so the
        // content of these buffers is collected and finally the dataSet is given to an instance
        // of ExportFormat (see below).
        Double[][] data;

        //constructor with name for this set
        ExportSet(String name) {
            this.name = name;
        }

        //Add dataBuffers with names to this set
        public void addSource(String name, String source) {
            this.sources.add(new SourceMapping(name, source));
        }

        //Retrieve all data from the dataBuffers
        public void getData() {
            data = new Double[sources.size()][];
            for (int i = 0; i < sources.size(); i++) {
                DataBuffer buffer = experiment.getBuffer(sources.get(i).source); //Get the buffer for this source
                data[i] = buffer.getArray(); //Get all data as a double array
            }
        }
    }

    private PhyphoxExperiment experiment; //The phyphoxExperiment which uses this DataExport
    public List<ExportSet> exportSets = new ArrayList<>(); //The available export sets

    //This abstract class defines the interface for a specific export format
    protected abstract class ExportFormat implements Serializable {
        protected String filenameBase = "phyphox";

        //fb is expected to be a complete file name base (without extension), typically generated
        // from the user's template by FileNameFormat
        public void setFilenameBase (String fb) {
            filenameBase = fb;
        }

        protected abstract String getName(); //Returns the name or description of the format
        protected abstract File export (List<ExportSet> sets, File exportPath, boolean minimalistic, Context ctx); //The actual export routine, which returns a datafile
        protected abstract String getType(boolean minimalistic); //Returns the mime-type of the exported file.
        protected abstract String getFilename(boolean minimalistic); //Returns a default file name for the exported file
    }

    //Implements the CSV (Comma-separated values) format.
    //Despite its name you can change the separator to something mot practical (i.e. tab-separated)
    //To provite multiple datasets, the plain-text files are grouped into a single zip-file.
    protected class CsvFormat extends ExportFormat implements Serializable {
        protected char separator; //The separator, typically "," or "\t"
        protected char decimalPoint; //The separator, typically "," or "\t"
        protected String name; //The name of this format can be changed to describe different separators

        //This constructor allows to set a separator and a name
        CsvFormat(char separator, char decimalPoint, String name) {
            this.separator = separator;
            this.decimalPoint = decimalPoint;
            this.name = name;
        }

        //the default constructor uses a comma-separator (",") and an appropriate name
        CsvFormat() {
            this(',', '.', "Comma-separated values (CSV)");
        }

        @Override
        protected String getName() {
            return name;
        }

        @Override
        protected File export (List<ExportSet> sets, File exportPath, boolean minimalistic, Context ctx) {
            File file = new File(exportPath, "/"+getFilename(minimalistic)); // Create a file with default filename in the given path

            DecimalFormat format = (DecimalFormat) NumberFormat.getInstance(Locale.ENGLISH);
            format.applyPattern("0.000000000E0");
            DecimalFormatSymbols dfs = format.getDecimalFormatSymbols();
            dfs.setDecimalSeparator(decimalPoint);
            format.setDecimalFormatSymbols(dfs);
            format.setGroupingUsed(false);

            try { // A lot can go wrong here... Let's catch em all...
                FileOutputStream stream = new FileOutputStream(file); //Open a basic output stream
                ZipOutputStream zstream = null;
                if (!minimalistic)
                    zstream = new ZipOutputStream(stream); //We will pack all datasets into a single zip
                try {
                    for (ExportSet set : sets) { // For each dataset...
                        ZipEntry entry;
                        if (!minimalistic) {
                            entry = new ZipEntry(set.name + ".csv"); //Create a new file for this dataset...
                            zstream.putNextEntry(entry); //...and add it to the zip-file
                        }

                        //Contruct the table header in the first line
                        String header = "";
                        for (int j = 0; j < set.data.length; j++) { //Each column gets a name...
                            header += "\"" + set.sources.get(j).getSecureName() + "\"";
                            if (j < set.data.length -1)
                                header += separator;
                        }
                        header += "\n";
                        if (minimalistic)
                            stream.write(header.getBytes());
                        else
                            zstream.write(header.getBytes()); //Write the header to the zip-file

                        //Then add all the data
                        for (int i = 0; i < set.data[0].length; i++) { //For each row of data... The first column determines the number of rows
                            //Construct the data row
                            StringBuilder data = new StringBuilder();
                            for (int j = 0; j < set.data.length; j++) { //For each column within this row
                                if (i < set.data[j].length) //Do we have data for this cell?
                                    data.append(format.format(set.data[j][i])); //Add it to the row
                                else
                                    data.append("NaN"); //No data? Enter NaN in the row
                                if (j < set.data.length - 1)
                                    data.append(separator);
                            }
                            data.append("\n");
                            if (minimalistic)
                                stream.write(data.toString().getBytes());
                            else
                                zstream.write(data.toString().getBytes()); //Write to zip-file
                        }

                        if (!minimalistic)
                            zstream.closeEntry(); //This dataset is complete. Close its file within the zip
                    }
                    //Add meta data in a separate folder
                    if (!minimalistic) {
                        ZipEntry entry;
                        entry = new ZipEntry("meta/device.csv");
                        zstream.putNextEntry(entry);
                        zstream.write(("\"property\""+separator+"\"value\"\n").getBytes());

                        StringBuilder data = new StringBuilder();
                        for (Metadata.DeviceMetadata deviceMetadata : Metadata.DeviceMetadata.values()) {
                            if (deviceMetadata == Metadata.DeviceMetadata.sensorMetadata || deviceMetadata == Metadata.DeviceMetadata.uniqueID || deviceMetadata == Metadata.DeviceMetadata.camera2api || deviceMetadata == Metadata.DeviceMetadata.camera2apiFull)
                                continue;
                            String identifier = deviceMetadata.toString();
                            data.append("\"").append(identifier).append("\"").append(separator);
                            data.append("\"").append(new Metadata(identifier, ctx).get("")).append("\"").append("\n");
                        }
                        for (SensorInput.SensorName sensor : SensorInput.SensorName.values()) {
                            for (Metadata.SensorMetadata sensorMetadata : Metadata.SensorMetadata.values()) {
                                String identifier = sensorMetadata.toString();
                                data.append("\"").append(sensor.name()).append(" ").append(identifier).append("\"").append(separator);
                                data.append("\"").append(new Metadata(sensor.name()+identifier, ctx).get("")).append("\"").append("\n");
                            }
                        }
                        zstream.write(data.toString().getBytes()); //Write to zip-file
                        zstream.closeEntry();

                        entry = new ZipEntry("meta/time.csv");
                        zstream.putNextEntry(entry);
                        zstream.write(("\"event\""+separator+"\"experiment time\""+separator+"\"system time\""+separator+"\"system time text\"\n").getBytes());

                        DecimalFormat longformat = (DecimalFormat) NumberFormat.getInstance(Locale.ENGLISH);
                        longformat.applyPattern("############0.000");
                        longformat.setDecimalFormatSymbols(dfs);
                        longformat.setGroupingUsed(false);

                        data = new StringBuilder();
                        SimpleDateFormat dateFormat;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS 'UTC'XXX");
                        else
                            dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS 'UTC'Z");
                        for (ExperimentTimeReference.TimeMapping timeMapping : experiment.experimentTimeReference.getTimeMappings()) {
                            data.append("\"").append(timeMapping.event.name()).append("\"").append(separator);
                            data.append(format.format(timeMapping.experimentTime)).append(separator);
                            data.append(longformat.format(timeMapping.systemTime/1000.)).append(separator);
                            data.append("\"").append(dateFormat.format(timeMapping.systemTime)).append("\"").append("\n");
                        }
                        zstream.write(data.toString().getBytes()); //Write to zip-file
                        zstream.closeEntry();
                    }
                } catch (Exception e) {
                    //This could be done better. Any error during CSV/ZIP compiling ends up here
                    Log.e("csvExport", "Unhandled exception during write.", e);
                } finally {
                    if (minimalistic)
                        stream.close();
                    else
                        zstream.close();
                }
            } catch (Exception e) {
                //This could be done better. Any error during file opening ends up here
                Log.e("csvExport", "Unhandled exception.", e);
            }

            return file;
        }

        @Override
        protected String getType (boolean minimalistic) {
            if (minimalistic)
                return MIME_TYPE_CSV_MINI;
            else
                return MIME_TYPE_CSV_ZIP;
        }
        @Override
        protected String getFilename (boolean minimalistic) {
            if (minimalistic)
                return this.filenameBase + ".csv";
            else
                return this.filenameBase + ".zip";
        }
    }

    //This class implements a Microsoft Excel (xlsx) export using our own minimal implementation
    // of the file format (see XlsxWriter)
    protected class ExcelFormat extends ExportFormat implements Serializable {
        //Nothing to do or configure in the constructor
        ExcelFormat() {
        }

        @Override
        protected String getName() {
            return "Excel";
        }

        @Override
        protected File export (List<ExportSet> sets, File exportPath, boolean minimalistic, Context ctx) {
            File file = new File(exportPath, "/"+getFilename(minimalistic)); //Create file with default filename

            try { // A lot can go wrong here. Catch em all...
                XlsxWriter xlsx = new XlsxWriter(new FileOutputStream(file));
                try {
                    for (ExportSet set : sets) { //For each dataset...
                        xlsx.startSheet(set.name); //..create a new sheet within the Excel document

                        //Create the header row and fill it
                        xlsx.startRow();
                        for (int j = 0; j < set.data.length; j++)
                            xlsx.stringCell(set.sources.get(j).getSecureName(), true);
                        xlsx.endRow();

                        //Create all the data rows
                        for (int i = 0; i < set.data[0].length; i++) { //For each row of data (number of rows determined by first entry in dataset)
                            xlsx.startRow();
                            for (int j = 0; j < set.data.length; j++) { //For each column
                                if (i < set.data[j].length) //Is there data for this cell?
                                    xlsx.numberCell(set.data[j][i]); //Yepp, enter it
                                else
                                    xlsx.stringCell("NaN", false); //Nope, no data. Fill NaN into this cell
                            }
                            xlsx.endRow();
                        }
                    }

                    if (!minimalistic) {
                        xlsx.startSheet("Metadata Device");
                        xlsx.startRow();
                        xlsx.stringCell("property", true);
                        xlsx.stringCell("value", true);
                        xlsx.endRow();

                        for (Metadata.DeviceMetadata deviceMetadata : Metadata.DeviceMetadata.values()) {
                            if (deviceMetadata == Metadata.DeviceMetadata.sensorMetadata || deviceMetadata == Metadata.DeviceMetadata.uniqueID || deviceMetadata == Metadata.DeviceMetadata.camera2api || deviceMetadata == Metadata.DeviceMetadata.camera2apiFull)
                                continue;
                            String identifier = deviceMetadata.toString();

                            xlsx.startRow();
                            xlsx.stringCell(identifier, false);
                            xlsx.stringCell(new Metadata(identifier, ctx).get(""), false);
                            xlsx.endRow();
                        }
                        for (SensorInput.SensorName sensor : SensorInput.SensorName.values()) {
                            for (Metadata.SensorMetadata sensorMetadata : Metadata.SensorMetadata.values()) {
                                String identifier = sensorMetadata.toString();

                                xlsx.startRow();
                                xlsx.stringCell(sensor.name() + " " + identifier, false);
                                xlsx.stringCell(new Metadata(sensor.name()+identifier, ctx).get(""), false);
                                xlsx.endRow();
                            }
                        }

                        xlsx.startSheet("Metadata Time");
                        xlsx.startRow();
                        xlsx.stringCell("event", true);
                        xlsx.stringCell("experiment time", true);
                        xlsx.stringCell("system time", true);
                        xlsx.stringCell("system time text", true);
                        xlsx.endRow();

                        SimpleDateFormat dateFormat;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS 'UTC'XXX");
                        else
                            dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS 'UTC'Z");
                        for (ExperimentTimeReference.TimeMapping timeMapping : experiment.experimentTimeReference.getTimeMappings()) {
                            xlsx.startRow();
                            xlsx.stringCell(timeMapping.event.name(), false);
                            xlsx.numberCell(timeMapping.experimentTime);
                            xlsx.numberCell(timeMapping.systemTime / 1000.);
                            xlsx.stringCell(dateFormat.format(timeMapping.systemTime), false);
                            xlsx.endRow();
                        }
                    }
                } catch (Exception e) {
                    //This could be done better. Any error during xlsx compiling ends up here
                    Log.e("excelExport", "Unhandled exception during write.", e);
                } finally {
                    xlsx.close();
                }
            } catch (Exception e) {
                //This could be done better. Any error during file opening ends up here
                Log.e("excelExport", "Unhandled exception.", e);
            }

            return file;
        }

        @Override
        protected String getType (boolean minimalistic) {
            return MIME_TYPE_XLSX;
        }

        @Override
        protected String getFilename (boolean minimalistic) {
            return filenameBase + ".xlsx";
        }
    }

    //This array holds instances of all export formats that should be presented to the user
    public final ExportFormat[] exportFormats = {
            new ExcelFormat(),
            new CsvFormat(',', '.', "CSV (Comma, decimal point)"),
            new CsvFormat('\t', '.', "CSV (Tabulator, decimal point)"),
            new CsvFormat(';', '.', "CSV (Semicolon, decimal point)"),
            new CsvFormat('\t', ',', "CSV (Tabulator, decimal comma)"),
            new CsvFormat(';', ',', "CSV (Semicolon, decimal comma)")
    };

    //The constructor just has to store a reference to the experiment
    DataExport(PhyphoxExperiment experiment) {
        this.experiment = experiment;
    }

    //Add an ExportSet to this exporter
    public void addSet(ExportSet set) {
        this.exportSets.add(set);
    }

    //Export the data (this will show dialogs to the user)
    public void export(Activity c, boolean minimalistic) {

        //Retrieve all the data
        for (int i = 0; i < exportSets.size(); i++) {
            exportSets.get(i).getData();
        }

        final String fileName = FileNameFormat.formatFilename(c, experiment.title, experiment.experimentTimeReference);
        showFormatDialog(exportSets, c, minimalistic, fileName);
    }

    //Annoying class to make the integer mutable.
    //The point is that we will show "radio buttons" when the user selects an export format. The
    //  result is a single index to the format selected by the user. So this should be an integer,
    //  which has to be mutable if we want to change it in the callback.
    protected class mutableInteger implements Serializable {
        public int value;
    }

    /**
     * Displays a Bottom Sheet to the user to select a file format for exporting data.
     * <p>
     * Upon successful selection of a format and an action (share or download), it will trigger the actual export process.
     *
     * @param chosenSets A Vector of {@link ExportSet} objects representing the data sets selected by the user for export.
     * @param c The Activity context from which this dialog is being displayed.
     * @param minimalistic A boolean flag indicating whether to perform a minimalistic export.
     *                     This might affect the content or formatting of the exported file.
     * @param fileName The base name for the exported file (without the extension).
     */
    protected void showFormatDialog(final List<ExportSet> chosenSets, final Activity c, final boolean minimalistic, final String fileName) {
        final mutableInteger selected = new mutableInteger(); //This will hold the result

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(c);
        View view = LayoutInflater.from(c).inflate(R.layout.action_bottom_sheet, null);
        RadioGroup radioGroupFileFormat = view.findViewById(R.id.radioGroupFormat);
        Button buttonShare = view.findViewById(R.id.buttonShare);
        Button buttonDownload = view.findViewById(R.id.buttonDownload);

        //Populate the radio buttons with the available export formats
        int selectedIndex = 0;
        for (ExportFormat exportFormat : exportFormats) {
            RadioButton radioButton = new RadioButton(c);
            radioButton.setText(exportFormat.getName());
            radioButton.setId("RadioButton".hashCode() + selectedIndex);
            radioGroupFileFormat.addView(radioButton);
            if(selectedIndex == 0){
                radioGroupFileFormat.check(radioButton.getId());
                selected.value = 0;
            }
            selectedIndex ++;
        }

        radioGroupFileFormat.setOnCheckedChangeListener((radioGroup1, i) -> selected.value = radioGroup1.indexOfChild(radioGroup1.findViewById(i)));

        buttonShare.setOnClickListener(v -> {
            exportFormats[selected.value].setFilenameBase(fileName );
            File exportFile = exportFormats[selected.value].export(chosenSets, c.getCacheDir(), minimalistic, c);
            String mimeType = exportFormats[selected.value].getType(minimalistic);

            DataExportUtility.shareFile(exportFile, mimeType, c);
            bottomSheetDialog.dismiss();
        });
        buttonDownload.setOnClickListener(view1 -> {
            exportFormats[selected.value].setFilenameBase(fileName);
            File exportFile = exportFormats[selected.value].export(chosenSets, c.getCacheDir(), minimalistic, c);
            String mimeType = exportFormats[selected.value].getType(minimalistic);

            DataExportUtility.createFileInDownloads(exportFile, fileName, mimeType, c);
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.setContentView(view);
        bottomSheetDialog.show();
    }

    //This function allows to export the data without dialogs. Hence it takes a list of selected
    //   exportSets (as an array of their indices), the selected ExportFormat and the directory to
    //   write to.
    //This function is used when all the dialogs are not done in the app, but on the web interface.
    //The user will select the exportSets and file format in the browser and will download the
    //   resulting file there as well.
    protected File exportDirect(ExportFormat format, File cacheDir, boolean minimalistic, final String fileName, Context ctx) {
        for (int i = 0; i < exportSets.size(); i++) {
            exportSets.get(i).getData();
        }

        format.setFilenameBase(fileName);

        return format.export(exportSets, cacheDir, minimalistic, ctx);
    }

}
