package de.rwth_aachen.phyphox.Experiments;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.List;
import java.util.Vector;

import de.rwth_aachen.phyphox.BuildConfig;
import de.rwth_aachen.phyphox.Experiment;
import de.rwth_aachen.phyphox.ExperimentList;
import de.rwth_aachen.phyphox.Helper.Helper;
import de.rwth_aachen.phyphox.R;

public class ExperimentListAdapter {
}

class ExperimentItemAdapter extends BaseAdapter {
    final private Activity parentActivity; //Reference to the main activity for the alertDialog when deleting files
    final private boolean isSimpleExperiment, isSavedState;

    private String preselectedBluetoothAddress = null;

    //Experiment data
    Vector<Integer> colors = new Vector<>(); //List of icons for each experiment
    Vector<Drawable> icons = new Vector<>(); //List of icons for each experiment
    Vector<String> titles = new Vector<>(); //List of titles for each experiment
    Vector<String> infos = new Vector<>(); //List of short descriptions for each experiment
    Vector<String> xmlFiles = new Vector<>(); //List of xmlFile name for each experiment (has to be provided in the intent if the user wants to load this)
    Vector<String> isTemp = new Vector<>(); //List of booleans for each experiment, which track whether the file is a temporary file
    Vector<Boolean> isAsset = new Vector<>(); //List of booleans for each experiment, which track whether the file is an asset or stored loacally (has to be provided in the intent if the user wants to load this)
    Vector<Integer> unavailableSensorList = new Vector<>(); //List of strings for each experiment, which give the name of the unavailable sensor if sensorReady is false
    Vector<String> isLinkList = new Vector<>(); //List of strings for each experiment, which are an URL is it is only a link entry

    //The constructor takes the activity reference. That's all.
    public ExperimentItemAdapter(Activity parentActivity, String category) {
        this.parentActivity = parentActivity;
        this.isSavedState = category.equals(res.getString(R.string.save_state_category));
        this.isSimpleExperiment = category.equals(res.getString(R.string.categoryNewExperiment));
    }

    public void setPreselectedBluetoothAddress(String preselectedBluetoothAddress) {
        this.preselectedBluetoothAddress = preselectedBluetoothAddress;
    }

    //The number of elements is just the number of icons. (Any of the lists should do)
    public int getCount() {
        return icons.size();
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
        intent.putExtra(EXPERIMENT_XML, xmlFiles.get(position));
        intent.putExtra(EXPERIMENT_ISTEMP, isTemp.get(position));
        intent.putExtra(EXPERIMENT_ISASSET, isAsset.get(position));
        intent.putExtra(EXPERIMENT_UNAVAILABLESENSOR, unavailableSensorList.get(position));
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
    public void addExperiment(int color, Drawable icon, String title, String info, String xmlFile, String isTemp, boolean isAsset, Integer unavailableSensor, String isLink) {
        //Insert it alphabetically into out list. So find the element before which the new
        //title belongs.
        int i;
        for (i = 0; i < titles.size(); i++) {
            if (titles.get(i).compareTo(title) >= 0)
                break;
        }

        //Now insert the experiment here
        colors.insertElementAt(color, i);
        icons.insertElementAt(icon, i);
        titles.insertElementAt(title, i);
        infos.insertElementAt(info, i);
        xmlFiles.insertElementAt(xmlFile, i);
        this.isTemp.insertElementAt(isTemp, i);
        this.isAsset.insertElementAt(isAsset, i);
        unavailableSensorList.insertElementAt(unavailableSensor, i);
        isLinkList.insertElementAt(isLink, i);

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
        if(convertView == null) { //No convertView there. Let's build from scratch.

            //Create the convertView from our layout and create an onClickListener
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.experiment_item, null);
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isLinkList.get(position) != null) {
                        try {
                            Uri uri = Uri.parse(isLinkList.get(position));
                            if (uri.getScheme().equals("http") || uri.getScheme().equals("https")) {
                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                if (intent.resolveActivity(getPackageManager()) != null) {
                                    startActivity(intent);
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
                    } else if (unavailableSensorList.get(position) < 0)
                        start(position, v);
                    else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                        builder.setMessage(res.getString(R.string.sensorNotAvailableWarningText1) + " " + res.getString(unavailableSensorList.get(position)) + " " + res.getString(R.string.sensorNotAvailableWarningText2))
                                .setTitle(R.string.sensorNotAvailableWarningTitle)
                                .setPositiveButton(R.string.ok, (dialog, id) -> {

                                })
                                .setNeutralButton(res.getString(R.string.sensorNotAvailableWarningMoreInfo), (dialog, id) -> {
                                    Uri uri = Uri.parse(res.getString(R.string.sensorNotAvailableWarningMoreInfoURL));
                                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                    if (intent.resolveActivity(getPackageManager()) != null) {
                                        startActivity(intent);
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
        holder.icon.setImageDrawable(icons.get(position));
        holder.title.setText(titles.get(position));
        holder.info.setText(infos.get(position));

        if (unavailableSensorList.get(position) >= 0) {
            holder.title.setTextColor(res.getColor(R.color.phyphox_white_50_black_50));
            holder.info.setTextColor(res.getColor(R.color.phyphox_white_50_black_50));
        }

        //Handle the menubutton. Set it visible only for non-assets
        if (isTemp.get(position) != null || isAsset.get(position))
            holder.menuBtn.setVisibility(ImageView.GONE); //Asset - no menu button
        else {
            //No asset. Menu button visible and it needs an onClickListener
            holder.menuBtn.setVisibility(ImageView.VISIBLE);
            if (Helper.luminance(colors.get(position)) > 0.1)
                holder.menuBtn.setColorFilter(colors.get(position), android.graphics.PorterDuff.Mode.SRC_IN);
            holder.menuBtn.setOnClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(new ContextThemeWrapper(ExperimentList.this, R.style.Theme_Phyphox_DayNight), v);
                popup.getMenuInflater().inflate(R.menu.experiment_item_context, popup.getMenu());

                popup.getMenu().findItem(R.id.experiment_item_rename).setVisible(isSavedState);

                popup.setOnMenuItemClickListener(menuItem -> {
                    switch (menuItem.getItemId()) {
                        case R.id.experiment_item_share: {
                            File file = new File(getFilesDir(), "/"+xmlFiles.get(position));

                            final Uri uri = FileProvider.getUriForFile(getBaseContext(), getPackageName() + ".exportProvider", file);
                            final Intent intent = ShareCompat.IntentBuilder.from(parentActivity)
                                    .setType("application/octet-stream") //mime type from the export filter
                                    .setSubject(getString(R.string.save_state_subject))
                                    .setStream(uri)
                                    .getIntent()
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                            List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(intent, 0);
                            for (ResolveInfo ri : resInfoList) {
                                grantUriPermission(ri.activityInfo.packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            }

                            //Create chooser
                            Intent chooser = Intent.createChooser(intent, getString(R.string.share_pick_share));
                            //And finally grant permissions again for any activities created by the chooser
                            resInfoList = getPackageManager().queryIntentActivities(chooser, 0);
                            for (ResolveInfo ri : resInfoList) {
                                if (ri.activityInfo.packageName.equals(BuildConfig.APPLICATION_ID
                                ))
                                    continue;
                                grantUriPermission(ri.activityInfo.packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            }
                            //Execute this intent
                            startActivity(chooser);
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
                                            deleteFile(xmlFiles.get(position));
                                            loadExperimentList();
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
                            edit.setText(titles.get(position));
                            builder.setView(edit)
                                    .setTitle(R.string.rename)
                                    .setPositiveButton(R.string.rename, (dialog, id) -> {
                                        String newName = edit.getText().toString();
                                        if (newName.replaceAll("\\s+", "").isEmpty())
                                            return;
                                        //Confirmed. Rename the item and reload the list
                                        if (isSavedState)
                                            Helper.replaceTagInFile(xmlFiles.get(position), getApplicationContext(), "/phyphox/state-title", newName);
                                        loadExperimentList();
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
