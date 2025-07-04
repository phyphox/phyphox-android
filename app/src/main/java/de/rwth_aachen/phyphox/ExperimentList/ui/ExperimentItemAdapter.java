package de.rwth_aachen.phyphox.ExperimentList.ui;

import static de.rwth_aachen.phyphox.ExperimentList.model.Const.EXPERIMENT_ISASSET;
import static de.rwth_aachen.phyphox.ExperimentList.model.Const.EXPERIMENT_ISTEMP;
import static de.rwth_aachen.phyphox.ExperimentList.model.Const.EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS;
import static de.rwth_aachen.phyphox.ExperimentList.model.Const.EXPERIMENT_UNAVAILABLESENSOR;
import static de.rwth_aachen.phyphox.ExperimentList.model.Const.EXPERIMENT_XML;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import de.rwth_aachen.phyphox.BuildConfig;
import de.rwth_aachen.phyphox.Helper.DataExportUtility;
import de.rwth_aachen.phyphox.Experiment;
import de.rwth_aachen.phyphox.ExperimentList.datasource.ExperimentRepository;
import de.rwth_aachen.phyphox.ExperimentList.model.ExperimentShortInfo;
import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.Helper.RGB;
import de.rwth_aachen.phyphox.R;

//This adapter is used to fill the gridView of the categories in the experiment list.
//So, this can be considered to be the experiment entries within an category
public class ExperimentItemAdapter extends BaseAdapter {

    final private Activity parentActivity; //Reference to the main activity for the alertDialog when deleting files
    final private boolean isSimpleExperiment, isSavedState;

    private String preselectedBluetoothAddress = null;

    private final ExperimentRepository experimentRepository;

    Resources res;

    //Experiment data
    Vector<ExperimentShortInfo> experimentShortInfos = new Vector<>();

    //The constructor takes the activity reference. That's all.
    public ExperimentItemAdapter(Activity parentActivity, String category, ExperimentRepository experimentRepository) {
        this.parentActivity = parentActivity;
        res = parentActivity.getResources();
        this.isSavedState = category.equals(res.getString(R.string.save_state_category));
        this.isSimpleExperiment = category.equals(res.getString(R.string.categoryNewExperiment));

        this.experimentRepository = experimentRepository;
    }

    public void setPreselectedBluetoothAddress(String preselectedBluetoothAddress) {
        this.preselectedBluetoothAddress = preselectedBluetoothAddress;
    }

    //The number of elements is just the number of icons. (Any of the lists should do)
    public int getCount() {
        return experimentShortInfos.size();
    }

    //We don't need to pick an object with this interface, but it has to be implemented
    public Object getItem(int position) {
        return null;
    }

    //The index is used as an id. That's enough, but has to be implemented
    public long getItemId(int position) {
        return position;
    }

    //This starts the intent for an experiment if the user clicked an experiment.
    //It takes the index and the view that has been clicked (just for the animation)
    public void start(int position, View v) {
        //Create the intent and place the experiment location in it
        Intent intent = new Intent(v.getContext(), Experiment.class);
        intent.putExtra(EXPERIMENT_XML, experimentShortInfos.get(position).xmlFile);
        intent.putExtra(EXPERIMENT_ISTEMP, experimentShortInfos.get(position).isTemp);
        intent.putExtra(EXPERIMENT_ISASSET, experimentShortInfos.get(position).isAsset);
        intent.putExtra(EXPERIMENT_UNAVAILABLESENSOR, experimentShortInfos.get(position).unavailableSensor);
        if (this.preselectedBluetoothAddress != null)
            intent.putExtra(EXPERIMENT_PRESELECTED_BLUETOOTH_ADDRESS, this.preselectedBluetoothAddress);
        intent.setAction(Intent.ACTION_VIEW);

        //If we are on a recent API, we can add a nice zoom animation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ActivityOptions options = ActivityOptions.makeScaleUpAnimation(v, 0,
                    0, v.getWidth(), v.getHeight());
            v.getContext().startActivity(intent, options.toBundle());
        } else { //old API? Just fire up the experiment.
            v.getContext().startActivity(intent);
        }
    }

    //Called to fill the adapter with experiment.
    //For each experiment we need an icon, a title, a short description, the location of the
    // file and whether it can be found as an asset or a local file.
    public void addExperiment(ExperimentShortInfo shortInfo) {
        //Insert it alphabetically into out list. So find the element before which the new
        //title belongs.
        int i;
        for (i = 0; i < experimentShortInfos.size(); i++) {
            if (experimentShortInfos.get(i).title.compareTo(shortInfo.title) >= 0)
                break;
        }

        experimentShortInfos.insertElementAt(shortInfo, i);

        //Notify the adapter that we changed its contents
        this.notifyDataSetChanged();
    }

    //This mini class holds all the Android views to be displayed
    public class Holder {
        ImageView icon; //The icon
        TextView title; //The title text
        TextView info;  //The short description text
        ImageButton menuBtn; //A button for a context menu for local experiments (if they are not an asset)
    }

    //Construct the view for an element.
    public View getView(final int position, View convertView, ViewGroup parent) {
        Holder holder; //Holds all views. loaded from convertView or reconstructed
        if (convertView == null) { //No convertView there. Let's build from scratch.

            //Create the convertView from our layout and create an onClickListener
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.experiment_item, null);
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (experimentShortInfos.get(position).isLink != null) {
                        try {
                            Uri uri = Uri.parse(experimentShortInfos.get(position).isLink);
                            if (uri.getScheme().equals("http") || uri.getScheme().equals("https")) {
                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                if (intent.resolveActivity(parentActivity.getPackageManager()) != null) {
                                    parentActivity.startActivity(intent);
                                    return;
                                }
                            }
                        } catch (Exception ignored) {

                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                        builder.setMessage("This entry is just a link, but its URL is invalid.")
                                .setTitle("Invalid URL")
                                .setPositiveButton(R.string.ok, (dialog, id) -> {

                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    } else if (experimentShortInfos.get(position).unavailableSensor < 0)
                        start(position, v);
                    else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                        builder.setMessage(res.getString(R.string.sensorNotAvailableWarningText1) + " " + res.getString(experimentShortInfos.get(position).unavailableSensor) + " " + res.getString(R.string.sensorNotAvailableWarningText2))
                                .setTitle(R.string.sensorNotAvailableWarningTitle)
                                .setPositiveButton(R.string.ok, (dialog, id) -> {

                                })
                                .setNeutralButton(res.getString(R.string.sensorNotAvailableWarningMoreInfo), (dialog, id) -> {
                                    Uri uri = Uri.parse(res.getString(R.string.sensorNotAvailableWarningMoreInfoURL));
                                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                    if (intent.resolveActivity(parentActivity.getPackageManager()) != null) {
                                        parentActivity.startActivity(intent);
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                }
            });

            //Create our holder and set its refernces to the views
            holder = new Holder();
            holder.icon = convertView.findViewById(R.id.expIcon);
            holder.title = convertView.findViewById(R.id.expTitle);
            holder.info = convertView.findViewById(R.id.expInfo);
            holder.menuBtn = convertView.findViewById(R.id.menuButton);

            //Connect the convertView and the holder to retrieve it later
            convertView.setTag(holder);
        } else {
            //There is an existing view. Retrieve its holder
            holder = (Holder) convertView.getTag();
        }

        //Update icons and texts
        holder.icon.setImageDrawable(experimentShortInfos.get(position).icon);
        holder.title.setText(experimentShortInfos.get(position).title);
        holder.info.setText(experimentShortInfos.get(position).description);

        if (experimentShortInfos.get(position).unavailableSensor >= 0) {
            holder.title.setTextColor(res.getColor(R.color.phyphox_white_50_black_50));
            holder.info.setTextColor(res.getColor(R.color.phyphox_white_50_black_50));
        }

        //Handle the menubutton. Set it visible only for non-assets
        if (experimentShortInfos.get(position).isTemp != null || experimentShortInfos.get(position).isAsset)
            holder.menuBtn.setVisibility(ImageView.GONE); //Asset - no menu button
        else {
            //No asset. Menu button visible and it needs an onClickListener
            holder.menuBtn.setVisibility(ImageView.VISIBLE);
            holder.menuBtn.setColorFilter(RGB.fromRGB(255, 255, 255).autoLightColor(res).intColor(), android.graphics.PorterDuff.Mode.SRC_IN);
            holder.menuBtn.setOnClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(new ContextThemeWrapper(parentActivity, R.style.Theme_Phyphox_DayNight), v);
                popup.getMenuInflater().inflate(R.menu.experiment_item_context, popup.getMenu());

                popup.getMenu().findItem(R.id.experiment_item_rename).setVisible(isSavedState);

                File xmlFile = new File(parentActivity.getFilesDir(), "/" + experimentShortInfos.get(position).xmlFile);
                popup.setOnMenuItemClickListener(menuItem -> {
                    switch (menuItem.getItemId()) {
                        case R.id.experiment_item_share: {
                            DataExportUtility.startPhyphoxFileSharing(parentActivity, xmlFile);
                            return true;
                        }

                        case R.id.experiment_item_download: {
                            DataExportUtility.createFileInDownloads(xmlFile, xmlFile.getName(), DataExportUtility.MIME_TYPE_PHYPHOX, parentActivity  );
                            return true;
                        }
                        case R.id.experiment_item_delete: {
                            //Create dialog to ask the user if he REALLY wants to delete...
                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                            builder.setMessage(res.getString(R.string.confirmDelete))
                                    .setTitle(R.string.confirmDeleteTitle)
                                    .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            //Confirmed. Delete the item and reload the list
                                            long crc32 = Helper.getCRC32(new File(parentActivity.getFilesDir(), experimentShortInfos.get(position).xmlFile));
                                            File resFolder = new File(parentActivity.getFilesDir(), Long.toHexString(crc32).toLowerCase());
                                            Log.d("ExperimentList", "Deleting " + experimentShortInfos.get(position).xmlFile);
                                            parentActivity.deleteFile(experimentShortInfos.get(position).xmlFile);
                                            if (resFolder.isDirectory()) {
                                                Log.d("ExperimentList", "Also deleting resource folder " + Long.toHexString(crc32).toLowerCase());
                                                String[] files = resFolder.list();
                                                for (String file : files) {
                                                    if (new File(resFolder, file).delete()) {
                                                        Log.d("ExperimentList", "Done.");
                                                    } else {
                                                        Log.d("ExperimentList", "Failed.");
                                                    }
                                                }
                                            } else {
                                                Log.d("ExperimentList", "No resource folder found at " + resFolder.getAbsolutePath());
                                            }
                                            experimentRepository.loadAndShowMainExperimentList(parentActivity);
                                        }
                                    })
                                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            //Aborted by user. Nothing to do.
                                        }
                                    });
                            AlertDialog dialog = builder.create();
                            dialog.show();
                            return true;
                        }
                        case R.id.experiment_item_rename: {
                            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                            final EditText edit = new EditText(parentActivity);
                            edit.setText(experimentShortInfos.get(position).title);
                            builder.setView(edit)
                                    .setTitle(R.string.rename)
                                    .setPositiveButton(R.string.rename, (dialog, id) -> {
                                        String newName = edit.getText().toString();
                                        if (newName.replaceAll("\\s+", "").isEmpty())
                                            return;
                                        //Confirmed. Rename the item and reload the list

                                        long oldCrc32 = Helper.getCRC32(new File(parentActivity.getFilesDir(), experimentShortInfos.get(position).xmlFile));
                                        File oldResFolder = new File(parentActivity.getFilesDir(), Long.toHexString(oldCrc32).toLowerCase());

                                        if (isSavedState)
                                            Helper.replaceTagInFile(experimentShortInfos.get(position).xmlFile, parentActivity.getApplicationContext(), "/phyphox/state-title", newName);

                                        long newCrc32 = Helper.getCRC32(new File(parentActivity.getFilesDir(), experimentShortInfos.get(position).xmlFile));
                                        File newResFolder = new File(parentActivity.getFilesDir(), Long.toHexString(newCrc32).toLowerCase());

                                        if (oldResFolder.isDirectory()) {
                                            newResFolder.mkdirs();
                                            String[] files = oldResFolder.list();
                                            for (String file : files) {
                                                Log.d("ExperimentList", "Moving resource file " + file);
                                                if (new File(oldResFolder, file).renameTo(new File(newResFolder, file))) {
                                                    Log.d("ExperimentList", "Done.");
                                                } else {
                                                    Log.d("ExperimentList", "Failed.");
                                                }
                                            }
                                        }

                                        experimentRepository.loadAndShowMainExperimentList(parentActivity);
                                    })
                                    .setNegativeButton(R.string.cancel, (dialog, id) -> {
                                        //Aborted by user. Nothing to do.
                                    });
                            AlertDialog dialog = builder.create();
                            dialog.show();
                            return true;
                        }

                    }
                    return false;
                });

                popup.show();
            });
        }

        return convertView;
    }
}
