package de.rwth_aachen.phyphox.Experiments;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;
import static android.content.Context.MODE_PRIVATE;

import static de.rwth_aachen.phyphox.GlobalConfig.EXPERIMENT_ISASSET;
import static de.rwth_aachen.phyphox.GlobalConfig.EXPERIMENT_XML;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;

import de.rwth_aachen.phyphox.Experiment;
import de.rwth_aachen.phyphox.Helper.DecimalTextWatcher;
import de.rwth_aachen.phyphox.R;

public class CreateSimpleExperimentDialog  extends AlertDialog.Builder {

    Context context;
    View neLayout;

    final EditText neTitle;
    final EditText neRate;

    final CheckBox neAccelerometer;
    final CheckBox neGyroscope;
    final CheckBox neHumidity;
    final CheckBox neLight;
    final CheckBox neLinearAcceleration;
    final CheckBox neLocation;
    final CheckBox neMagneticField;
    final CheckBox nePressure;
    final CheckBox neProximity;
    final CheckBox neTemperature;

    FileOutputStream output;

    HashMap<String,String> hMap=new HashMap<>();
    HashMap<String,String> inputMap=new HashMap<>();

    public CreateSimpleExperimentDialog(@NonNull Context context) {
        super(context);
        this.context = context;
        LayoutInflater neInflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
        neLayout = neInflater.inflate(R.layout.new_experiment, null);

        //Get a bunch of references to the dialog elements
        neTitle = (EditText) neLayout.findViewById(R.id.neTitle); //The edit box for the title of the new experiment
        neRate = (EditText) neLayout.findViewById(R.id.neRate);
        neRate.addTextChangedListener(new DecimalTextWatcher());//Edit box for the aquisition rate

        //More references: Checkboxes for sensors
        neAccelerometer = (CheckBox) neLayout.findViewById(R.id.neAccelerometer);
        neGyroscope = (CheckBox) neLayout.findViewById(R.id.neGyroscope);
        neHumidity = (CheckBox) neLayout.findViewById(R.id.neHumidity);
        neLight = (CheckBox) neLayout.findViewById(R.id.neLight);
        neLinearAcceleration = (CheckBox) neLayout.findViewById(R.id.neLinearAcceleration);
        neLocation = (CheckBox) neLayout.findViewById(R.id.neLocation);
        neMagneticField = (CheckBox) neLayout.findViewById(R.id.neMagneticField);
        nePressure = (CheckBox) neLayout.findViewById(R.id.nePressure);
        neProximity = (CheckBox) neLayout.findViewById(R.id.neProximity);
        neTemperature = (CheckBox) neLayout.findViewById(R.id.neTemperature);
    }

    @Override
    public AlertDialog.Builder setView(View view) {
        return super.setView(neLayout);
    }


    @Override
    public AlertDialog.Builder setPositiveButton(int textId, DialogInterface.OnClickListener listener) {
        return super.setPositiveButton(context.getText(R.string.ok), (dialog, which) -> {
            createExperimentFileDefinition();
        });
    }

    @Override
    public AlertDialog.Builder setNegativeButton(int textId, DialogInterface.OnClickListener listener) {
        return super.setNegativeButton(context.getText(R.string.cancel), (dialog, which) -> dialog.dismiss());
    }

    private void createExperimentFileDefinition(){

        String title = neTitle.getText().toString();

        //Collect the enabled sensors
        boolean acc = neAccelerometer.isChecked();
        boolean gyr = neGyroscope.isChecked();
        boolean hum = neHumidity.isChecked();
        boolean light = neLight.isChecked();
        boolean lin = neLinearAcceleration.isChecked();
        boolean loc = neLocation.isChecked();
        boolean mag = neMagneticField.isChecked();
        boolean pressure = nePressure.isChecked();
        boolean prox = neProximity.isChecked();
        boolean temp = neTemperature.isChecked();
        if (!(acc || gyr || light || lin || loc || mag || pressure || prox || hum || temp)) {
            acc = true;
            Toast.makeText(context, "No sensor selected. Adding accelerometer as default.", Toast.LENGTH_LONG).show();
        }

        //Generate random file name
        String file = UUID.randomUUID().toString().replaceAll("-", "") + ".phyphox";

        //Now write the whole file...
        try {
            output = context.openFileOutput(file, MODE_PRIVATE);
            output.write("<phyphox version=\"1.14\">".getBytes());

            //Title, standard category and standard description
            output.write(("<title>"+title
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;")
                    .replace("&", "&amp;")+"</title>").getBytes());
            output.write(("<category>"+context.getString(R.string.categoryNewExperiment)+"</category>").getBytes());
            output.write(("<color>red</color>").getBytes());
            output.write("<description>Get raw data from selected sensors.</description>".getBytes());

            //Buffers for all sensors
            output.write("<data-containers>".getBytes());
            writeDataContainers(acc, gyr ,light ,lin ,loc ,mag ,pressure ,prox ,hum ,temp);
            output.write("</data-containers>".getBytes());

            //Inputs for each sensor
            output.write("<input>".getBytes());
            writeInputForSensors(acc, gyr ,light ,lin ,loc ,mag ,pressure ,prox ,hum ,temp);
            output.write("</input>".getBytes());

            //Views for each sensor
            output.write("<views>".getBytes());
            writeViewsForSensor(acc, gyr ,light ,lin ,loc ,mag ,pressure ,prox ,hum ,temp);
            output.write("</views>".getBytes());

            //Export definitions for each sensor
            output.write("<export>".getBytes());
            exportAllData(acc, gyr ,light ,lin ,loc ,mag ,pressure ,prox ,hum ,temp);
            output.write("</export>".getBytes());

            //And finally, the closing tag
            output.write("</phyphox>".getBytes());

            output.close();

            startNewActivity(file);

        } catch (Exception e) {
            Log.e("newExperiment", "Could not create new experiment.", e);
        }

    }

    private void writeDataContainer(String[] containerKeys) throws IOException {
        for (String key: containerKeys) {
            output.write(("<container size=\"0\">"+key+"</container>").getBytes());
        }
    }

    private void writeDataContainers(boolean acc, boolean gyr, boolean light, boolean lin, boolean loc, boolean mag, boolean pressure, boolean prox,boolean hum, boolean temp) throws IOException {
        if (acc) {
            writeDataContainer(new String[] {"acc_time","accX","accY","accZ"});
        }
        if (gyr) {
            writeDataContainer(new String[] {"gyr_time","gyrX","gyrY","gyrZ"});
        }
        if (hum) {
            writeDataContainer(new String[] {"hum_time","hum"});
        }
        if (light) {
            writeDataContainer(new String[] {"light_time","light"});
        }
        if (lin) {
            writeDataContainer(new String[] {"lin_time","linX","linY","linZ"});
        }
        if (loc) {
            writeDataContainer(new String[] {"loc_time","locLat","locLon","locZ",
                    "locV","locDir","locAccuracy","locZAccuracy","locStatus","locSatellites"});
        }
        if (mag) {
            writeDataContainer(new String[] {"mag_time","magX","magY","magZ"});
        }
        if (pressure) {
            writeDataContainer(new String[] {"pressure_time","pressure"});
        }
        if (prox) {
            writeDataContainer(new String[] {"prox_time","prox"});
        }
        if (temp) {
            writeDataContainer(new String[] {"temp_time","temp"});
        }
    }

    private void simplifySensorWrite(String type, double rate, HashMap<String,String> outputComp) throws  IOException{
        StringBuilder sensorElement = new StringBuilder("<sensor type=\"" + type + "\" rate=\"" + rate + "\" >");
        for(Entry<String,String> mEntry: outputComp.entrySet()) {
            sensorElement
                    .append("<output component=\"")
                    .append(mEntry.getKey())
                    .append("\">")
                    .append(mEntry.getValue())
                    .append("</output>");
        }

        output.write(sensorElement.toString().getBytes());
    }

    private void writeInputForSensors(boolean acc, boolean gyr, boolean light, boolean lin, boolean loc, boolean mag, boolean pressure, boolean prox, boolean hum, boolean temp) throws IOException {
        //Prepare the rate
        double rate;
        try {
            rate = Double.parseDouble(neRate.getText().toString().replace(',', '.'));
        } catch (Exception e) {
            rate = 0;
            Toast.makeText(context, "Invaid sensor rate. Fall back to fastest rate.", Toast.LENGTH_LONG).show();
        }

        if (acc){
            hMap.put("x","accX");
            hMap.put("y","accY");
            hMap.put("z","accZ");
            hMap.put("t","acc_time");
            simplifySensorWrite("accelerometer", rate, hMap);
            hMap.clear();
        }
        if (gyr){
            hMap.put("x","gyrX");
            hMap.put("y","gyrY");
            hMap.put("z","gyrZ");
            hMap.put("t","gyr_time");
            simplifySensorWrite("gyroscope", rate, hMap);
            hMap.clear();
        }
        if (hum){
            hMap.put("x","hum");
            hMap.put("t","hum_time");
            simplifySensorWrite("humidity", rate, hMap);
            hMap.clear();
        }
        if (light){
            hMap.put("x","light");
            hMap.put("t","light_time");
            simplifySensorWrite("light", rate, hMap);
            hMap.clear();
        }
        if (lin){
            hMap.put("x","linX");
            hMap.put("y","linY");
            hMap.put("z","linZ");
            hMap.put("t","lin_time");
            simplifySensorWrite("linear_acceleration", rate, hMap);
            hMap.clear();
        }
        if (loc)
            output.write(("<location>" +
                    "<output component=\"lat\">locLat</output>" +
                    "<output component=\"lon\">locLon</output>" +
                    "<output component=\"z\">locZ</output>" +
                    "<output component=\"t\">loc_time</output>" +
                    "<output component=\"v\">locV</output>" +
                    "<output component=\"dir\">locDir</output>" +
                    "<output component=\"accuracy\">locAccuracy</output>" +
                    "<output component=\"zAccuracy\">locZAccuracy</output>" +
                    "<output component=\"status\">locStatus</output>" +
                    "<output component=\"satellites\">locSatellites</output>" +
                    "</location>").getBytes());
        if (mag){
            hMap.put("x","magX");
            hMap.put("y","magY");
            hMap.put("z","magZ");
            hMap.put("t","mag_time");
            simplifySensorWrite("magnetic_field", rate, hMap);
            hMap.clear();
        }
        if (pressure){
            hMap.put("x","pressure");
            hMap.put("t","pressure_time");
            simplifySensorWrite("pressure", rate, hMap);
            hMap.clear();
        }
        if (prox){
            hMap.put("x","prox");
            hMap.put("t","prox_time");
            simplifySensorWrite("proximity", rate, hMap);
            hMap.clear();
        }
        if (temp){
            hMap.put("x","temp");
            hMap.put("t","temp_time");
            simplifySensorWrite("temperature", rate, hMap);
            hMap.clear();
        }
    }

    private void writeViewsForSensor(boolean acc, boolean gyr, boolean light, boolean lin, boolean loc, boolean mag, boolean pressure, boolean prox, boolean hum, boolean temp) throws IOException{
        if (acc) {
            output.write("<view label=\"Accelerometer\">".getBytes());
            createAccelerometer("Acceleration X", "accX");
            createAccelerometer("Acceleration Y", "accY");
            createAccelerometer("Acceleration Z", "accZ");
            output.write("</view>".getBytes());
        }
        if (gyr) {
            output.write("<view label=\"Gyroscope\">".getBytes());
            createGyroscope("Gyroscope X", "gyrX");
            createGyroscope("Gyroscope Y", "gyrY");
            createGyroscope("Gyroscope Y", "gyrY");
            output.write("</view>".getBytes());
        }
        if (hum) {
            output.write("<view label=\"Humidity\">".getBytes());
            createAnySensor("Humidity", "Relative Humidity (%)", "hum_time", "hum" );
            output.write("</view>".getBytes());
        }
        if (light) {
            output.write("<view label=\"Light\">".getBytes());
            createAnySensor("Illuminance", "Ev (lx)", "light_time", "light" );
            output.write("</view>".getBytes());
        }
        if (lin) {
            output.write("<view label=\"Linear Acceleration\">".getBytes());
            createLinearAcceleration("Linear Acceleration X", "linX");
            createLinearAcceleration("Linear Acceleration Y", "linY");
            createLinearAcceleration("Linear Acceleration Z", "linZ");
            output.write("</view>".getBytes());
        }
        if (loc) {
            output.write("<view label=\"Location\">".getBytes());
            createLocation("Latitude","Latitude (°)" ,"locLat");
            createLocation("Longitude","Longitude (°)", "locLon");
            createLocation("Height","z (m)", "locZ");
            createLocation("Velocity","v (m/s)", "locV");
            createLocation("Direction","heading (°)", "locDir");

            createLocationValue("Horizontal Accuracy", "1", true, "locAccuracy", false);
            createLocationValue("Vertical Accuracy", "1", true, "locZAccuracy", false);
            createLocationValue("Satellites", "0", false, "locSatellites", false);
            createLocationValue("Status", "0", false, "locSatellites", true);

            output.write("</view>".getBytes());
        }
        if (mag) {
            output.write("<view label=\"Magnetometer\">".getBytes());
            createMagnetometer("Magnetic field X", "magX");
            createMagnetometer("Magnetic field Y", "magY");
            createMagnetometer("Magnetic field Z", "magZ");
            output.write("</view>".getBytes());
        }
        if (pressure) {
            output.write("<view label=\"Pressure\">".getBytes());
            createAnySensor("Pressure", "P (hPa)","pressure_time", "pressure" );
            output.write("</view>".getBytes());
        }
        if (prox) {
            output.write("<view label=\"Proximity\">".getBytes());
            createAnySensor("Proximity", "Distance (cm)","prox_time", "prox" );
            output.write("</view>".getBytes());
        }
        if (temp) {
            output.write("<view label=\"Temperature\">".getBytes());
            createAnySensor("Temperature", "t (s)","temp_time", "temp" );
            output.write("</view>".getBytes());
        }
    }

    private String createInputComponent(StringBuilder element, HashMap<String,String> inputs){
        for (Entry<String,String> mEntry: inputs.entrySet()) {
            element
                    .append("<input axis=\"")
                    .append(mEntry.getKey())
                    .append("\">")
                    .append(mEntry.getValue())
                    .append("</input>");
        }
        return element.toString();
    }

    private String createMapComponent(StringBuilder element, HashMap<String,String> maps){
        for (Entry<String,String> mEntry: maps.entrySet()) {
            element
                    .append("<map max=\"")
                    .append(mEntry.getKey())
                    .append("\">")
                    .append(mEntry.getValue())
                    .append("</map>");
        }
        return element.toString();
    }

    private void createGraphView(HashMap<String,String> graphComp, HashMap<String,String> inputComp) throws IOException{
        StringBuilder viewElement = new StringBuilder("<graph ");
        for (Entry<String,String> mEntry: graphComp.entrySet()) {
            viewElement
                    .append(mEntry.getKey())
                    .append("=\"")
                    .append(mEntry.getValue())
                    .append("\" ");
        }
        viewElement.append(createInputComponent(viewElement, inputComp));
        viewElement.append("</graph>");

        output.write(viewElement.toString().getBytes());
    }

    private void createSensorValue(HashMap<String,String> valueComponent, String input, HashMap<String,String> mapComponent) throws IOException {
        StringBuilder viewElement = new StringBuilder("<value ");
        for (Entry<String,String> mEntry: valueComponent.entrySet()) {
            viewElement
                    .append(mEntry.getKey())
                    .append("=\"")
                    .append(mEntry.getValue())
                    .append("\" ");
        }
        viewElement.append("<input>").append(input).append("</input>");

        if(mapComponent !=null){
            viewElement.append(createMapComponent(viewElement,mapComponent));
        }

        viewElement.append("</value>");

        output.write(viewElement.toString().getBytes());
    }

    private void createAccelerometer(String title, String inputY) {
        hMap.put("label",title);
        hMap.put("timeOnX","true");
        hMap.put("labelX","t (s)");
        hMap.put("labelY","a (m/s²)");
        hMap.put("partialUpdate","true");

        inputMap.put("x", "acc_time");
        inputMap.put("y",inputY);

        try {
            createGraphView(hMap, inputMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();
        inputMap.clear();
    }

    private void createGyroscope(String title, String inputY){

        hMap.put("label",title);
        hMap.put("timeOnX","true");
        hMap.put("labelX","t (s)");
        hMap.put("labelY","w (rad/s)");
        hMap.put("partialUpdate","true");

        inputMap.put("x", "gyr_time");
        inputMap.put("y",inputY);

        try {
            createGraphView(hMap, inputMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();
        inputMap.clear();
    }

    private void createAnySensor(String title, String labelY, String inputX, String inputY){
        hMap.put("label",title);
        hMap.put("timeOnX","true");
        hMap.put("labelX", "t (s)");
        hMap.put("labelY",labelY);
        hMap.put("partialUpdate","true");

        inputMap.put("x", inputX);
        inputMap.put("y",inputY);

        try {
            createGraphView(hMap, inputMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();
        inputMap.clear();
    }

    private void createLinearAcceleration(String title, String inputY){

        hMap.put("label",title);
        hMap.put("timeOnX","true");
        hMap.put("labelX","t (s)");
        hMap.put("labelY","a (m/s²)");
        hMap.put("partialUpdate","true");

        inputMap.put("x", "lin_time");
        inputMap.put("y",inputY);

        try {
            createGraphView(hMap, inputMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();
        inputMap.clear();
    }

    private void createLocation(String title, String labelY, String inputY){
        hMap.put("label",title);
        hMap.put("timeOnX","true");
        hMap.put("labelX","t (s)");
        hMap.put("labelY",labelY);
        hMap.put("partialUpdate","true");

        inputMap.put("x", "loc_time");
        inputMap.put("y",inputY);

        try {
            createGraphView(hMap, inputMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();
        inputMap.clear();
    }

    private void createMagnetometer(String title, String inputY){
        hMap.put("label",title);
        hMap.put("timeOnX","true");
        hMap.put("labelX","t (s)");
        hMap.put("labelY","B (µT)");
        hMap.put("partialUpdate","true");

        inputMap.put("x", "mag_time");
        inputMap.put("y",inputY);

        try {
            createGraphView(hMap, inputMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();
        inputMap.clear();
    }

    private void createLocationValue(String title, String precision, boolean hasUnit, String input, boolean hasMap){

        hMap.put("label",title);
        hMap.put("size","1");
        hMap.put("precision",precision);
        if(hasUnit)
            hMap.put("unit","m");

        if(hasMap){
            inputMap.put("-1", "GPS disabled");
            inputMap.put("0","Waiting for signal");
            inputMap.put("1","Active");
            try {
                createSensorValue(hMap, input, inputMap );
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else{
            try {
                createSensorValue(hMap, input, null );
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        hMap.clear();
        inputMap.clear();
    }

    private void extractData(String title, HashMap<String, String> data) throws IOException{
        output.write(("<set name=\"" + title + "\">").getBytes());
        for(Entry<String,String> mEntry: data.entrySet()){
            output.write(("<data name=\"" +
                    mEntry.getKey() +
                    "\">" +
                    mEntry.getValue() +
                    "</data>").getBytes());
        }
        output.write("</set>".getBytes());
    }

    private void extractAccelerometer(){
        hMap.put("Time (s)","acc_time");
        hMap.put("Acceleration x (m/s^2)","accX");
        hMap.put("Acceleration y (m/s^2)","accY");
        hMap.put("Acceleration z (m/s^2)","accZ");

        try {
            extractData("Accelerometer", hMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();
    }

    private void extractGyroscope(){
        hMap.put("Time (s)","gyr_time");
        hMap.put("Gyroscope x (rad/s)","gyrX");
        hMap.put("Gyroscope y (rad/s)","gyrY");
        hMap.put("Gyroscope z (rad/s)","gyrZ");

        try {
            extractData("Gyroscope", hMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();
    }

    private void extractHumidity(){
        hMap.put("Time (s)","hum_time");
        hMap.put("Relative Humidity (%)","hum");

        try {
            extractData("Humidity", hMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();
    }

    private void extractIlluminance(){
        hMap.put("Time (s)","light_time");
        hMap.put("Illuminance (lx)","light");

        try {
            extractData("Light", hMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();
    }

    private void extractLinearAcc(){
        hMap.put("Time (s)","lin_time");
        hMap.put("Linear Acceleration x (m/s^2)","linX");
        hMap.put("Linear Acceleration y (m/s^2)","linY");
        hMap.put("Linear Acceleration z (m/s^2)","linZ");

        try {
            extractData("Linear Acceleration", hMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();
    }

    private void extractLocation(){
        hMap.put("Time (s)","loc_time");
        hMap.put("Latitude (°)","locLat");
        hMap.put("Longitude (°)","locLon");
        hMap.put("Height (m)","locZ");
        hMap.put("Velocity (m/s)","locV");
        hMap.put("Direction (°)","locDir");
        hMap.put("Horizontal Accuracy (m)","locAccuracy");
        hMap.put("Vertical Accuracy (m)","locZAccuracy");

        try {
            extractData("Location", hMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();

    }

    private void extractMagnetometer(){
        hMap.put("Time (s)","mag_time");
        hMap.put("Magnetic field x (µT)","magX");
        hMap.put("Magnetic field y (µT)","magY");
        hMap.put("Magnetic field z (µT)","magZ");


        try {
            extractData("Magnetometer", hMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();
    }

    private void extractPressure(){
        hMap.put("Time (s)","pressure_time");
        hMap.put("Pressure (hPa)","pressure");

        try {
            extractData("Pressure", hMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();
    }

    private void extractProximity(){
        hMap.put("Time (s)","prox_time");
        hMap.put("Distance (cm)","prox");

        try {
            extractData("Proximity", hMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();
    }

    private void extractTemperature(){
        hMap.put("Time (s)","temp_time");
        hMap.put("Temperature (°C)","temp");

        try {
            extractData("Temperature", hMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        hMap.clear();

    }

    private void exportAllData(boolean acc, boolean gyr, boolean light, boolean lin, boolean loc, boolean mag, boolean pressure, boolean prox, boolean hum, boolean temp){
        if (acc)
            extractAccelerometer();
        if (gyr)
            extractGyroscope();
        if (hum)
            extractHumidity();
        if (light)
            extractIlluminance();
        if (lin)
            extractLinearAcc();
        if (loc)
            extractLocation();
        if (mag)
            extractMagnetometer();
        if (pressure)
            extractPressure();
        if (prox)
            extractProximity();
        if (temp)
            extractTemperature();
    }

    private void startNewActivity(String file){
        //Create an intent for this new file
        Intent intent = new Intent(context, Experiment.class);
        intent.putExtra(EXPERIMENT_XML, file);
        intent.putExtra(EXPERIMENT_ISASSET, false);
        intent.setAction(Intent.ACTION_VIEW);

        //Start the new experiment
        context.startActivity(intent);
    }

}
