package de.rwth_aachen.phyphox;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

//The dataExport class provides export functionality for a phyphoxExperiment.
//it provides multiple export formats and the dialogs to control them
public class dataExport {

    //exportSet class
    //An export set is a collection of related dataBuffers, which (ideally) have the same size
    //exportSets are defined for each experiments and represent logical subsets of all dataBuffers which the user might want to export
    public class exportSet {
        String name;

        //This class maps dataBuffers (by their key name) to a name in this exportSet
        protected class sourceMapping {
            String name;
            String source;

            sourceMapping(String name, String source) {
                this.name = name;
                this.source = source;
            }
        }

        //The set consists of an arbitrary number of sourceMappings. So each entry in the dataSet has a name and a dataBuffer-source
        Vector<sourceMapping> sources = new Vector<>();

        //We will also hold an array with all the data. The idea is to let all dataSets collect their
        // data as fast as possible (like a snapshot) and then take care of pushing the data to an
        // exporter. So after instantiating an exportSet (with a name), addSource is called for
        // each dataBuffer that should be added to the collection. Then getData is called so the
        // content of these buffers is collected and finally the dataSet is given to an instance
        // of exportFormat (see below).
        Double[][] data;

        //constructor with name for this set
        exportSet(String name) {
            this.name = name;
        }

        //Add dataBuffers with names to this set
        public void addSource(String name, String source) {
            this.sources.add(new sourceMapping(name, source));
        }

        //Retrieve all data from the dataBuffers
        public void getData() {
            data = new Double[sources.size()][];
            for (int i = 0; i < sources.size(); i++) {
                dataBuffer buffer = experiment.getBuffer(sources.get(i).source); //Get the buffer for this source
                data[i] = buffer.getArray(); //Get all data as a double array
            }
        }
    }

    private phyphoxExperiment experiment; //The phyphoxExperiment which uses this dataExport
    public Vector<exportSet> exportSets = new Vector<>(); //The available export sets

    //This abstract class defines the interface for a specific export format
    protected abstract class exportFormat {
        protected abstract String getName(); //Returns the name or description of the format
        protected abstract File export (Vector<exportSet> sets, File exportPath); //The actual export routine, which returns a datafile
        protected abstract String getType(); //Returns the mime-type of the exported file.
        protected abstract String getFilename(); //Returns a default file name for the exported file
    }

    //Implements the CSV (Comma-separated values) format.
    //Despite its name you can change the separator to something mot practical (i.e. tab-separated)
    //To provite multiple datasets, the plain-text files are grouped into a single zip-file.
    protected class csvFormat extends exportFormat {
        protected String separator; //The separator, typically "," or "\t"
        protected String name; //The name of this format can be changed to describe different separators

        //This constructor allows to set a separator and a name
        csvFormat(String separator, String name) {
            this.separator = separator;
            this.name = name;
        }

        //the default constructor uses a comma-separator (",") and an appropriate name
        csvFormat() {
            this(",", "Comma-separated values (CSV)");
        }

        @Override
        protected String getName() {
            return name;
        }

        @Override
        protected File export (Vector<exportSet> sets, File exportPath) {
            File file = new File(exportPath, "/"+getFilename()); // Create a file with default filename in the given path

            try { // A lot can go wrong here... Let's catch em all...
                FileOutputStream stream = new FileOutputStream(file); //Open a basic output stream
                ZipOutputStream zstream = new ZipOutputStream(stream); //We will pack all datasets into a single zip
                try {
                    for (exportSet set : sets) { // For each dataset...
                        ZipEntry entry = new ZipEntry(set.name + ".csv"); //Create a new file for this dataset...
                        zstream.putNextEntry(entry); //...and add it to the zip-file

                        //Contruct the table header in the first line
                        String header = "";
                        for (int j = 0; j < set.data.length; j++) { //Each column gets a name...
                            header += set.sources.get(j).name;
                            if (j < set.data.length -1)
                                header += separator;
                        }
                        header += "\n";
                        zstream.write(header.getBytes()); //Write the header to the zip-file

                        //Then add all the data
                        for (int i = 0; i < set.data[0].length; i++) { //For each row of data... The first column determines the number of rows
                            //Construct the data row
                            String data = "";
                            for (int j = 0; j < set.data.length; j++) { //For each column within this row
                                if (i < set.data[j].length) //Do we have data for this cell?
                                    data += set.data[j][i]; //Add it to the row
                                else
                                    data += "NaN"; //No data? Enter NaN in the row
                                if (j < set.data.length - 1)
                                    data += separator;
                            }
                            data += "\n";
                            zstream.write(data.getBytes()); //Write to zip-file
                        }

                        zstream.closeEntry(); //This dataset is complete. Close its file within the zip
                    }
                } catch (Exception e) {
                    //This could be done better. Any error during CSV/ZIP compiling ends up here
                    Log.e("csvExport", "Unhandled exception during write.", e);
                } finally {
                    zstream.close();
                }
            } catch (Exception e) {
                //This could be done better. Any error during file opening ends up here
                Log.e("csvExport", "Unhandled exception.", e);
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

    //This class implements an Microsoft Excel export using the Apache POI library
    protected class excelFormat extends exportFormat {
        //Nothing to do or configure in the costructor
        excelFormat() {
        }

        @Override
        protected String getName() {
            return "Excel";
        }

        @Override
        protected File export (Vector<exportSet> sets, File exportPath) {
            //New excel workbook
            Workbook wb = new HSSFWorkbook();
            File file = new File(exportPath, "/"+getFilename()); //Create file with default filename

            try { // A lot can go wrong here. Catch em all...
                for (exportSet set : sets) { //For each dataset...
                    Sheet sheet = wb.createSheet(set.name);//..create a new sheet within the Excel document

                    //Create a style (just bold font) for the table header
                    Font font= wb.createFont();
                    font.setBold(true);
                    CellStyle cs = wb.createCellStyle();
                    cs.setFont(font);

                    //Create the header row and fill it
                    Row row = sheet.createRow(0);
                    for (int j = 0; j < set.data.length; j++) {
                        Cell c = row.createCell(j);
                        c.setCellValue(set.sources.get(j).name);
                        c.setCellStyle(cs);
                    }

                    //Create all the data rows
                    for (int i = 0; i < set.data[0].length; i++) { //For each row of data (number of rows determined by first entry in dataset)
                        row = sheet.createRow(i+1);
                        for (int j = 0; j < set.data.length; j++) { //For each column
                            Cell c = row.createCell(j);
                            if (i < set.data[j].length) //Is there data for this cell?
                                c.setCellValue(set.data[j][i]); //Yepp, enter it
                            else
                                c.setCellValue("NaN"); //Nope, no data. Fill NaN into this cell
                        }
                    }

                    //We now have our Excel document. Let's write it to the file.
                    FileOutputStream os = null;
                    try { //Let's catch errors while writing separately
                        os = new FileOutputStream(file);
                        wb.write(os);
                    } catch (Exception e) {
                        Log.e("excelExport", "Unhandled exception during write.", e);
                    } finally {
                        if (os != null)
                            os.close();
                    }
                }

            } catch (Exception e) {
                Log.e("excelExport", "Unhandled exception.", e);
            }

            return file;
        }

        @Override
        //This mime-typ is ugly, but seems to be the "official" one, while there are many others in use.
        protected String getType () {
            return "application/vnd.ms-excel";
        }

        @Override
        protected String getFilename () {
            return "phyphox.xlsx";
        }
    }

    //This array holds instances of all export formats that should be presented to the user
    public final exportFormat[] exportFormats = {
            new csvFormat(),
            new csvFormat("\t", "Tab-separated values (CSV)"),
            new excelFormat()
    };

    //The constructor just has to store a reference to the experiment
    dataExport(phyphoxExperiment experiment) {
        this.experiment = experiment;
    }

    //Add an exportSet to this exporter
    public void addSet(exportSet set) {
        this.exportSets.add(set);
    }

    //Export the data (this will show dialogs to the user)
    public void export(Activity c) {

        //Retrieve all the data
        for (int i = 0; i < exportSets.size(); i++) {
            exportSets.get(i).getData();
        }

        //Show the dialog to the user to select the exportSets
        showSetDialog(c);
    }

    //Show a dialog from which the user may select exportSets to export
    protected void showSetDialog(final Activity c) {
        final ArrayList<Integer> mSelectedItems = new ArrayList<>(); //Will hold the indices of selected sets
        AlertDialog.Builder builder = new AlertDialog.Builder(c); //Dialog builder...

        CharSequence[] options = new CharSequence[exportSets.size()]; //The strings presented to the user as options
        boolean[] enabled = new boolean[exportSets.size()]; //Holds whether the options are checked
        for (int i = 0; i < exportSets.size(); i++) { //Initialize the arrays as enabled and add them to the indices list
            options[i] = exportSets.get(i).name; //The name of the set
            enabled[i] = true; //Start with all sets enabled
            mSelectedItems.add(i); //As we start with all sets enabled, add the indices to the result set
        }

        //Create dialog...
        builder.setTitle(R.string.pick_exportSets) //Set the dialog title from language resources
                .setMultiChoiceItems(options, enabled, //Create item selection menu
                        new DialogInterface.OnMultiChoiceClickListener() { //Callback when user changes the selection
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                if (isChecked) { //If the user checked an item...
                                    mSelectedItems.add(which); //...add it to the collection...
                                } else if (mSelectedItems.contains(which)) { //...if it is deselected...
                                    mSelectedItems.remove(Integer.valueOf(which)); //...remove it.
                                }
                            }
                        })
                .setPositiveButton(R.string.next, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) { //Callback when the user confirms his selection
                        //Create a list of the exportSets which should be used
                        Vector<exportSet> chosenSets = new Vector<>();
                        for (int i = 0; i < exportSets.size(); i++) {
                            if (mSelectedItems.contains(i))
                                chosenSets.add(exportSets.get(i));
                        }

                        //Use give the result to the next dialog, which lets the user choose a file format
                        showFormatDialog(chosenSets, c);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {//Callback if the user aborts the dialog.
                        //Nothing to do here. We shall not export.
                    }
                });

        builder.create().show(); //Show the dialog we have just constructed
    }

    //Annoying class to make the integer mutable.
    //The point is that we will show "radio buttons" when the user selects an export format. The
    //  result is a single index to the format selected by the user. So this should be an integer,
    //  which has to be mutable if we want to change it in the callback.
    protected class mutableInteger {
        public int value;
    }

    //Let the user select a fiile format. This takes a list of chosen sets as it is supposed to be
    //  called after the user has already chosen the sets to export.
    //If successfull it will trigger the actual export as a share intent
    protected void showFormatDialog(final Vector<exportSet> chosenSets, final Activity c) {
        final mutableInteger selected = new mutableInteger(); //This will hold the result

        //Create the charsequences that should be presented to the user
        final CharSequence[] options = new CharSequence[exportFormats.length];
        for (int i = 0; i < exportFormats.length; i++) {
            options[i] = exportFormats[i].getName();
        }

        //Build the dialog...
        AlertDialog.Builder builder = new AlertDialog.Builder(c);
        builder.setTitle(R.string.pick_exportFormat) //Title from internationalization
                .setSingleChoiceItems(options, 0, //Callback if the user changes the selection
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                               selected.value = which; //Remember the selection
                            }
                        })
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) { //Callback when the user confirms his selection
                        //Lets do the actual export

                        //Call the export filter to write the data to a file
                        File exportFile = exportFormats[selected.value].export(chosenSets, c.getCacheDir());

                        //Use a FileProvider so we can send this file to other apps
                        final Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".exportProvider", exportFile);
                        c.grantUriPermission(c.getPackageName(), uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        //Create a share intent
                        final Intent intent = ShareCompat.IntentBuilder.from(c)
                                .setType(exportFormats[selected.value].getType()) //mime type from the export filter
                                .setSubject(c.getString(R.string.export_subject))
                                .setStream(uri)
                                .setChooserTitle(R.string.export_pick_share)
                                .createChooserIntent()
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        //Execute this intent
                        c.startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {//Callback if the user aborts the dialog.
                        //Nothing to do here. We shall not export.
                    }
                });

        //Show the dialog which we have just built
        builder.create().show();
    }

    //This function allows to export the data without dialogs. Hence it takes a list of selected
    //   exportSets (as an array of their indices), the selected exportFormat and the directory to
    //   write to.
    //This function is used when all the dialogs are not done in the app, but on the web interface.
    //The user will select the exportSets and file format in the browser and will download the
    //   resulting file there as well.
    protected File exportDirect(ArrayList<Integer> selectedItems, exportFormat format, File cacheDir) {
        Vector<exportSet> chosenSets = new Vector<>();
        for (int i = 0; i < exportSets.size(); i++) {
            if (selectedItems.contains(i)) {
                exportSets.get(i).getData();
                chosenSets.add(exportSets.get(i));
            }
        }
        return format.export(chosenSets, cacheDir);
    }

}
