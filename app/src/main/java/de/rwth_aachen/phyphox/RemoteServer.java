package de.rwth_aachen.phyphox;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.appcompat.content.res.AppCompatResources;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Vector;

//remoteServer implements a web-interface to remote control the experiment and receive the data
//Unfortunately, Google decided to depricate org.apache.http in Android 6, so until we move to
//something else, we need to suppress deprication warnings if we do not want to get flooded.

@SuppressWarnings( "deprecation" )
public class RemoteServer extends Thread {

    private final PhyphoxExperiment experiment; //Reference to the experiment we want to control

    HttpService httpService; //Holds our http service
    BasicHttpContext basicHttpContext; //A simple http context...
    static final int HttpServerPORT = 8080; //We have to pick a high port number. We may not use 80...
    boolean RUNNING = false; //Keeps the main loop alive...
    Context context; //Resource reference for comfortable access
    Experiment callActivity; //Reference to the parent activity. Needed to provide its status on the webinterface

    public String sessionID = "";

    public boolean forceFullUpdate = false; //Something has happened (clear) that makes it neccessary to force a full buffer update to the remote interface

    static String indexHTML, styleCSS; //These strings will hold the html and css document when loaded from our resources

    private Vector<Integer> htmlID2View = new Vector<>(); //This maps htmlIDs to the view of the element
    private Vector<Integer> htmlID2Element = new Vector<>(); //This maps htmlIDs to the view of the element

    //buildStyleCSS loads the css file from the resources and replaces some placeholders
    protected void buildStyleCSS () {
        //We use a stringbuilder to collect our strings
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            //...and we need to read from the resource file
            BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open("remote/style.css")));
            //While we get lines from the resource file, replace placeholders and hand the line to the stringbuilder
            while ((line = br.readLine()) != null) {
                //Set some drawables directly in the css as base64-encoded PNGs
                if (line.contains("###drawablePlay###"))
                    line = line.replace("###drawablePlay###", getBase64PNG(AppCompatResources.getDrawable(context, R.drawable.play)));
                if (line.contains("###drawableTimedPlay###"))
                    line = line.replace("###drawableTimedPlay###", getBase64PNG(AppCompatResources.getDrawable(context, R.drawable.timed_play)));
                if (line.contains("###drawablePause###"))
                    line = line.replace("###drawablePause###", getBase64PNG(AppCompatResources.getDrawable(context, R.drawable.pause)));
                if (line.contains("###drawableTimedPause###"))
                    line = line.replace("###drawableTimedPause###", getBase64PNG(AppCompatResources.getDrawable(context, R.drawable.timed_pause)));
                if (line.contains("###drawableClearData###"))
                    line = line.replace("###drawableClearData###", getBase64PNG(AppCompatResources.getDrawable(context, R.drawable.delete)));
//                if (line.contains("###drawableExport###"))
//                    line = line.replace("###drawableExport###", getBase64PNG(AppCompatResources.getDrawable(context, R.drawable.download)));
                if (line.contains("###drawableMore###"))
                    line = line.replace("###drawableMore###", getBase64PNG(AppCompatResources.getDrawable(context, R.drawable.more)));
                if (line.contains("###drawableMaximize###"))
                    line = line.replace("###drawableMaximize###", getBase64PNG(AppCompatResources.getDrawable(context, R.drawable.unfold_more)));
                if (line.contains("###drawableRestore###"))
                    line = line.replace("###drawableRestore###", getBase64PNG(AppCompatResources.getDrawable(context, R.drawable.unfold_less)));
                if (line.contains("###drawableWarning###"))
                    line = line.replace("###drawableWarning###", getBase64PNG(AppCompatResources.getDrawable(context, R.drawable.warning)));

                //Add the line and a linebreak
                sb.append(line);
                sb.append("\n");
            }
        } catch (Exception e) {
            Log.e("remoteServer","Error loading style.css", e);
        } finally {
            //Create a simple string
            styleCSS = sb.toString();
        }
    }

    //getBase64PNG takes a drawable and returns a string with the base64-encoded image
    protected String getBase64PNG(Drawable src) {
        //We need a bitmap first as the source might be any drawable resource
        Bitmap bm;
        if (src instanceof BitmapDrawable) {
            //It is already a bitmap resource. Fine. Get it!
            bm = ((BitmapDrawable) src).getBitmap();
        } else {
            //Not a bitmap, so we need to draw the drawable to a canvas to get a bitmap
            //Get its size
            final int w = !src.getBounds().isEmpty() ? src.getBounds().width() : src.getIntrinsicWidth();
            final int h = !src.getBounds().isEmpty() ? src.getBounds().height() : src.getIntrinsicHeight();

            //Create a bitmap and a canvas holding it
            bm = Bitmap.createBitmap(w <= 0 ? 1 : w, h <= 0 ? 1 : h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bm);

            //Draw the drawable to the bitmap
            src.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            src.draw(canvas);
        }

        //Receive a png datastream from the bitmap to create an array of bytes
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();

        //Now just encode and return it. Replace line-breaks to get a nasty one-liner into the css file.
        return Base64.encodeToString(byteArray, Base64.DEFAULT).replace("\n", "");

    }

    //Constructs the HTML file and replaces some placeholder.
    //This is where the experiment views place their HTML code.
    protected void buildIndexHTML () {
        //A string builder is great for collecting strings...
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            //Read from the resource file
            BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open("remote/index.html")));
            //While we receive new lines, look for placeholders. replace placeholders with data and append them to our stringbuilder
            while ((line = br.readLine()) != null) {
                if (line.contains("<!-- [[title]] -->")) { //The title. This one is easy...
                    sb.append(line.replace("<!-- [[title]] -->", experiment.title));
                    sb.append("\n");
                } else if (line.contains("<!-- [[clearDataTranslation]] -->")) { //The localized string for "clear data"
                        sb.append(line.replace("<!-- [[clearDataTranslation]] -->", context.getString(R.string.clear_data)));
                        sb.append("\n");
                } else if (line.contains("<!-- [[clearConfirmTranslation]] -->")) { //The localized string for "Clear data?"
                    sb.append(line.replace("<!-- [[clearConfirmTranslation]] -->", context.getString(R.string.clear_data_question)));
                    sb.append("\n");
                } else if (line.contains("<!-- [[exportTranslation]] -->")) { //The localized string for "clear data"
                    sb.append(line.replace("<!-- [[exportTranslation]] -->", context.getString(R.string.export)));
                    sb.append("\n");
                } else if (line.contains("<!-- [[switchToPhoneLayoutTranslation]] -->")) { //The localized string for "clear data"
                    sb.append(line.replace("<!-- [[switchToPhoneLayoutTranslation]] -->", context.getString(R.string.switchToPhoneLayout)));
                    sb.append("\n");
                } else if (line.contains("<!-- [[switchColumns1Translation]] -->")) { //The localized string for "clear data"
                    sb.append(line.replace("<!-- [[switchColumns1Translation]] -->", context.getString(R.string.switchColumns1)));
                    sb.append("\n");
                } else if (line.contains("<!-- [[switchColumns2Translation]] -->")) { //The localized string for "clear data"
                    sb.append(line.replace("<!-- [[switchColumns2Translation]] -->", context.getString(R.string.switchColumns2)));
                    sb.append("\n");
                } else if (line.contains("<!-- [[switchColumns3Translation]] -->")) { //The localized string for "clear data"
                    sb.append(line.replace("<!-- [[switchColumns3Translation]] -->", context.getString(R.string.switchColumns3)));
                    sb.append("\n");
                } else if (line.contains("<!-- [[toggleBrightModeTranslation]] -->")) {
                    sb.append(line.replace("<!-- [[toggleBrightModeTranslation]] -->", context.getString(R.string.toggleBrightMode)));
                    sb.append("\n");
                } else if (line.contains("<!-- [[fontSizeTranslation]] -->")) {
                    sb.append(line.replace("<!-- [[fontSizeTranslation]] -->", context.getString(R.string.fontSize)));
                    sb.append("\n");
                } else if (line.contains("<!-- [[viewLayout]] -->")) {
                    //The viewLayout is a JSON object with our view setup. All the experiment views
                    //and their view elements and their JavaScript functions and so on...

                    //Beginning of the JSON block
                    sb.append("var views = [");

                    int id = 0; //We will give each view a unique id to address them in JavaScript
                                //via a HTML id
                    htmlID2View.clear();
                    htmlID2Element.clear();

                    for (int i = 0; i < experiment.experimentViews.size(); i++) {
                        //For each ecperiment view

                        if (i > 0)  //Add a colon if this is not the first item to seperate the previous one.
                            sb.append(",\n"); //Not necessary, but debugging is so much more fun with a line-break.

                        //The name of this view
                        sb.append("{\"name\": \"");
                        sb.append(experiment.experimentViews.get(i).name.replace("\"","\\\""));

                        //Now for its elements
                        sb.append("\", \"elements\":[\n");
                        for (int j = 0; j < experiment.experimentViews.get(i).elements.size(); j++) {
                            //For each element within this view

                            //Store the mapping of htmlID to the experiment view hierarchy
                            htmlID2View.add(i);
                            htmlID2Element.add(j);

                            if (j > 0)  //Add a colon if this is not the first item to seperate the previous one.
                                sb.append(",");

                            //The element's label
                            sb.append("{\"label\":\"");
                            sb.append(experiment.experimentViews.get(i).elements.get(j).label.replace("\"","\\\""));

                            //The id, we just created
                            sb.append("\",\"index\":\"");
                            sb.append(id);

                            //The update method
                            sb.append("\",\"updateMode\":\"");
                            sb.append(experiment.experimentViews.get(i).elements.get(j).getUpdateMode());

                            //The label size
                            sb.append("\",\"labelSize\":\"");
                            sb.append(experiment.experimentViews.get(i).elements.get(j).labelSize);

                            //The HTML markup for this element - on this occasion we notify the element about its id
                            sb.append("\",\"html\":\"");
                            sb.append(experiment.experimentViews.get(i).elements.get(j).getViewHTML(id).replace("\"","\\\""));

                            //The Javascript function that handles data completion
                            sb.append("\",\"dataCompleteFunction\":");
                            sb.append(experiment.experimentViews.get(i).elements.get(j).dataCompleteHTML());

                            //If this element takes an x array, set the buffer and the JS function
                            if (experiment.experimentViews.get(i).elements.get(j).inputs != null) {
                                sb.append(",\"dataInput\":[");
                                boolean first = true;
                                for (String input : experiment.experimentViews.get(i).elements.get(j).inputs) {
                                    if (first)
                                        first = false;
                                    else
                                        sb.append(",");
                                    if (input == null)
                                        sb.append("null");
                                    else {
                                        sb.append("\"");
                                        sb.append(input.replace("\"", "\\\""));
                                        sb.append("\"");
                                    }
                                }
                                sb.append("],\"dataInputFunction\":\n");
                                sb.append(experiment.experimentViews.get(i).elements.get(j).setDataHTML());
                                sb.append("\n");
                            }

                            sb.append("}"); //The element is complete
                            id++;
                        }
                        sb.append("\n]}"); //The view is complete
                    }
                    sb.append("\n];\n"); //The views are complete -> JSON object complete
                    //That's it!
                } else if (line.contains("<!-- [[viewOptions]] -->")) {
                    //The option list for the view selector. Simple.
                    for (int i = 0; i < experiment.experimentViews.size(); i++) {
                        //For each view
                        sb.append("<li>");
                        sb.append(experiment.experimentViews.get(i).name);
                        sb.append("</li>\n");
                    }
                } else if (line.contains("<!-- [[exportFormatOptions]] -->")) {
                    //The export format
                    for (int i = 0; i < experiment.exporter.exportFormats.length; i++)
                        sb.append("<option value=\"").append(i).append("\">").append(experiment.exporter.exportFormats[i].getName()).append("</option>\n");
                } else {
                    //No placeholder. Just append this.
                    sb.append(line);
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            Log.e("remoteServer","Error loading index.html", e);
        } finally {
            indexHTML = sb.toString();
        }
    }

    //The constructor takes the experiment to control and the activity of which we need to show/control the status
    RemoteServer(PhyphoxExperiment experiment, Experiment callActivity, String sessionID) {
        this.experiment = experiment;
        this.callActivity = callActivity;
        this.context = callActivity;

        //Create the css and html files
        buildStyleCSS();
        buildIndexHTML();

        this.sessionID = sessionID;

        //Start the service...
        RUNNING = true;
        startHttpService();
    }

    RemoteServer(PhyphoxExperiment experiment, Experiment callActivity) {
        this(experiment, callActivity, String.format("%06x", (System.nanoTime() & 0xffffff)));
    }

    //This helper function lists all external IP adresses, so the user can be told, how to reach the webinterface
    public static String getAddresses(Context context) {
        String ret = "";
        Inet4Address filterMobile = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network networks[] = connectivityManager.getAllNetworks();
            for (Network network : networks) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    continue;
                }
                LinkProperties properties = connectivityManager.getLinkProperties(network);
                Iterator<LinkAddress> iterator = properties.getLinkAddresses().iterator();
                while (iterator.hasNext()) {
                    LinkAddress address = iterator.next();
                    if (address.getAddress() instanceof Inet4Address) {
                        filterMobile = (Inet4Address) address.getAddress();
                        break;
                    }
                }
            }
        }
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) { //For each network interface
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                while (enumInetAddress.hasMoreElements()) { //For each adress of this interface
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    //We want non-local, non-loopback IPv4 addresses (nobody really uses IPv6 on local networks and phyphox is not supposed to run over the internet - let's not make it too complicated for the user)
                    if (!inetAddress.isAnyLocalAddress() && !inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address && !inetAddress.equals(filterMobile)) {
                        ret += "http://" + inetAddress.getHostAddress() + ":" + HttpServerPORT + "\n";
                    }

                }

            }

        } catch (SocketException e) {
            Log.e("getAdresses", "Error getting the IP.", e);
        }
        return ret;
    }

    @Override
    //This is the main thread, which keeps running in a loop und handles incoming requests.
    public void run() {
        try {
            //Setup server socket
            ServerSocket serverSocket = new ServerSocket(HttpServerPORT);
            serverSocket.setReuseAddress(true);
            serverSocket.setSoTimeout(3000);

            //The actual loop
            while (RUNNING) {
                try {
                    //Wait for an incoming connection and accept it as a socket instance
                    Socket socket = serverSocket.accept();

                    //Turn this into a http server connection
                    DefaultHttpServerConnection httpServerConnection = new DefaultHttpServerConnection();
                    try {
                        //Take the connection
                        httpServerConnection.bind(socket, new BasicHttpParams());
                        //Do what has been requested and answer (see handle registry below)
                        //while (httpServerConnection.isOpen() && RUNNING) {
                            httpService.handleRequest(httpServerConnection, basicHttpContext);
                        //}
                        //Note about the commented while-loop:
                        //In theory, this loop should improve performance by allowing persistent connections, but for some reason many requests
                        //fail if they are send in rapid succession. Not sure why... One idea is, that the second request is send while the first
                        //one is still being handled and hence entirely ignored. If we close the connection instead, the browser is forced to
                        //send it again at a more convenient timing... Not entirely convinced. For now let's use the version that works better...
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        httpServerConnection.shutdown();
                    }
                } catch (SocketTimeoutException e) {
                    //A timeout is ok. We will just start listening again
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            //The loop has been shut down, so close our server socket
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //This starts the http service and registers the handlers for several requests
    private synchronized void startHttpService() {
        BasicHttpProcessor basicHttpProcessor = new BasicHttpProcessor();
        basicHttpContext = new BasicHttpContext();

        basicHttpProcessor.addInterceptor(new ResponseDate());
        basicHttpProcessor.addInterceptor(new ResponseServer());
        basicHttpProcessor.addInterceptor(new ResponseContent());
        basicHttpProcessor.addInterceptor(new ResponseConnControl());

        httpService = new HttpService(basicHttpProcessor,
                new DefaultConnectionReuseStrategy(),
                new DefaultHttpResponseFactory());

        //Now register a handler for different requests
        HttpRequestHandlerRegistry registry = new HttpRequestHandlerRegistry();
        registry.register("/", new HomeCommandHandler()); //The basic interface (index.html) when the user just calls the address
        registry.register("/style.css", new StyleCommandHandler()); //The style sheet (style.css) linked from index.html
        registry.register("/logo", new logoHandler()); //The phyphox logo, also included in style.css
        registry.register("/get", new getCommandHandler()); //A get command takes parameters which define, which buffers and how much of them is requested - the response is a JSON set with the data
        registry.register("/control", new controlCommandHandler()); //The control command starts and stops measurements
        registry.register("/export", new exportCommandHandler()); //The export command requests a data file containing sets as requested by the paramters
        registry.register("/config", new configCommandHandler()); //The config command requests information on the currently active experiment configuration
        registry.register("/meta", new metaCommandHandler()); //The meta command requests information on the device
        registry.register("/time", new timeCommandHandler()); //The meta command requests information on the current time reference
        httpService.setHandlerResolver(registry);
    }


    //Stop the server by siply setting RUNNING to false
    public synchronized void stopServer() {
        RUNNING = false;
    }

    //The home handler simply takes the already compiled index.html and pushes it through an OutputStreamWriter
    class HomeCommandHandler implements HttpRequestHandler {

        @Override
        public void handle(HttpRequest request, HttpResponse response,
                           HttpContext httpContext) throws HttpException, IOException {

            BasicHttpEntity entity = new BasicHttpEntity();
            InputStream inputStream = new ByteArrayInputStream(indexHTML.getBytes());
            entity.setContent(inputStream);
            entity.setContentLength(inputStream.available());

            //Set the header and THEN send the file
            response.setHeader("Content-Type", "text/html");
            response.setEntity(entity);
        }

    }

    //The style handler simply takes the already compiled style.css and pushes it through an OutputStreamWriter
    class StyleCommandHandler implements HttpRequestHandler {

        @Override
        public void handle(HttpRequest request, HttpResponse response,
                           HttpContext httpContext) throws HttpException, IOException {

            BasicHttpEntity entity = new BasicHttpEntity();
            InputStream inputStream = new ByteArrayInputStream(styleCSS.getBytes());
            entity.setContent(inputStream);
            entity.setContentLength(inputStream.available());

            //Set the header and THEN send the file
            response.setHeader("Content-Type", "text/css");
            response.setEntity(entity);
        }

    }

    //The logo handler simply reads the logo from resources and pushes it through an OutputStreamWriter
    class logoHandler implements HttpRequestHandler {

        @Override
        public void handle(HttpRequest request, HttpResponse response,
                           HttpContext httpContext) throws HttpException, IOException {

            BasicHttpEntity entity = new BasicHttpEntity();
            InputStream inputStream = context.getAssets().open("remote/phyphox_orange.png");
            entity.setContent(inputStream);

            //Set the header and THEN send the file
            response.setHeader("Content-Type", "image/png");
            response.setEntity(entity);
        }

    }

    //The get query has the form
    //get?buffer1=full&buffer2=12345&buffer3=67890|buffer1
    //This query requests the whole buffer1, all values from buffer2 greater than 12345 and all
    // values from buffer3 at indices at which values of buffer2 are greater than 67890
    //Example: If you have a graph of sensor data y against time t and already have data to
    // 20 seconds, you would request t=20 and y=20|t to receive any data beyond 20 seconds
    class getCommandHandler implements HttpRequestHandler {

        //This structure (ok, class) holds one element of the request corresponding to a buffer
        protected class bufferRequest {
            public String name;         //Name of the requested buffer
            public Double threshold;    //Threshold from which to read data
            public String reference;    //The buffer to which the threshold should be applied
        }

        @Override
        public void handle(HttpRequest request, HttpResponse response,
                           HttpContext httpContext) throws HttpException, IOException {

            Uri uri=Uri.parse(request.getRequestLine().getUri());

            //Based on code from getQueryParameterNames, see http://stackoverflow.com/questions/11642494/android-net-uri-getqueryparameternames-alternative
            String query = uri.getEncodedQuery();
            Set<bufferRequest> bufferList = new LinkedHashSet<>(); //This list will hold all requests
            int start = 0;
            if (query != null) {
                do { //We will iterate through each element of the query
                    int next = query.indexOf('&', start);
                    int end = (next == -1) ? query.length() : next;
                    int separator = query.indexOf('=', start);
                    if (separator > end || separator == -1) {
                        separator = end;
                    }

                    //Now the current element is query.substring(start, end) and the "=" will be at seperator

                    //Intrepret the request
                    bufferRequest br = new bufferRequest();
                    br.name = Uri.decode(query.substring(start, separator)); //The name (the part before "=")
                    br.reference = "";
                    if (separator == end)
                        br.threshold = Double.NaN; //No special request - the last value should be ok
                    else {
                        String th = query.substring(separator+1, end); //The part after "="
                        if (th.equals("full") || forceFullUpdate) {
                            br.threshold = Double.NEGATIVE_INFINITY; //Get every single value
                        } else {
                            //So we get a threshold. We just have to figure out the reference buffer
                            int subsplit = th.indexOf('|');
                            if (subsplit == -1)
                                br.threshold = Double.valueOf(th); //No reference specified
                            else { //A reference is given
                                br.threshold = Double.valueOf(th.substring(0, subsplit));
                                br.reference = th.substring(subsplit+1);
                            }
                            //We only offer 8-digit precision, so we need to move the threshold to avoid receiving a close number multiple times.
                            //Missing something will probably not be visible on a remote graph and a missing value will be recent after stopping anyway.
                            br.threshold += Math.pow(10, Math.floor(Math.log10(br.threshold/1e7)));
                        }
                    }

                    bufferList.add(br);
                    start = end + 1;
                } while (start < query.length());
                forceFullUpdate = false;
            }

            //We now know what the query request. Let's build our answer
            StringBuilder sb;

            //Lock the data, to get a consistent data block
            experiment.dataLock.lock();
            try {
                //First let's take a guess at how much memory we will need
                int sizeEstimate = 0;
                for (bufferRequest buffer : bufferList) {
                    if (experiment.dataMap.containsKey(buffer.name)) {
                        sizeEstimate += 14 * experiment.dataBuffers.get(experiment.dataMap.get(buffer.name)).size + 100;
                    }
                }

                //Create the string builder
                sb = new StringBuilder(sizeEstimate);

                boolean firstBuffer = true; //Helper to recognize the first iteration

                //Set our decimal format (English to make sure we use decimal points, not comma
                DecimalFormat format = (DecimalFormat) NumberFormat.getInstance(Locale.ENGLISH);
                format.applyPattern("0.#######E0");

                //Start building...
                sb.append("{\"buffer\":{\n");
                for (bufferRequest buffer : bufferList) {
                    if (experiment.dataMap.containsKey(buffer.name)) { //For each buffer that is requested
                        if (firstBuffer)
                            firstBuffer = false;
                        else
                            sb.append(",\n"); //Seperate the object with a comma, if this is not the first item

                        //Get the databuffers. The one requested and the threshold reference
                        DataBuffer db = experiment.getBuffer(buffer.name);
                        DataBuffer db_reference;
                        if (buffer.reference.equals(""))
                            db_reference = db;
                        else
                            db_reference = experiment.getBuffer(buffer.reference);

                        //Buffer name
                        sb.append("\"");
                        sb.append(db.name);

                        //Buffer size
                        sb.append("\":{\"size\":");
                        sb.append(db.size);

                        //Does the response contain a single value, the whole buffer or a part of it?
                        sb.append(",\"updateMode\":\"");
                        if (Double.isNaN(buffer.threshold))
                            sb.append("single");
                        else if (Double.isInfinite(buffer.threshold))
                            sb.append("full");
                        else
                            sb.append("partial");
                        sb.append("\", \"buffer\":[");

                        if (Double.isNaN(buffer.threshold)) //Single value. Get the last one directly from our buffer class
                            if (Double.isNaN(db.value) || Double.isInfinite(db.value))
                                sb.append("null");
                            else
                                sb.append(format.format(db.value));
                        else {
                            //Get all the values...
                            boolean firstValue = true; //Find first iteration, so the other ones can add a seperator
                            Double data[] = db.getArray();
                            int n = db.getFilledSize();
                            Double dataRef[];
                            if (db_reference == db)
                                dataRef = data;
                            else {
                                dataRef = db_reference.getArray();
                                n = Math.min(n, db_reference.getFilledSize());
                            }


                            Double v;
                            for (int i = 0; i < n; i++) {
                                //Simultaneously get the values from both iterators
                                v = data[i];
                                Double v_dep = dataRef[i];
                                if (v_dep <= buffer.threshold) //Skip this value if it is below the threshold or NaN
                                    continue;

                                //Add a seperator if this is not the first value
                                if (firstValue)
                                    firstValue = false;
                                else
                                    sb.append(",");

                                if (Double.isNaN(v) || Double.isInfinite(v))
                                    sb.append("null");
                                else
                                    sb.append(format.format(v));
                            }
                        }

                        sb.append("]}");
                    }
                }

                //We also send the experiment status
                sb.append("\n},\n\"status\":{\n");

                //Session ID
                sb.append("\"session\":\"");
                sb.append(sessionID);

                //Measuring?
                sb.append("\", \"measuring\":");
                if (callActivity.measuring)
                    sb.append("true");
                else
                    sb.append("false");

                //Timed run?
                sb.append(", \"timedRun\":");
                if (callActivity.timedRun)
                    sb.append("true");
                else
                    sb.append("false");

                //Countdown state
                sb.append(", \"countDown\":");
                sb.append(String.valueOf(callActivity.millisUntilFinished));
                sb.append("\n}\n}\n");
            } finally {
                experiment.dataLock.unlock();
            }

            //Done. Build a string and return it as usual
            final String result = sb.toString();

            BasicHttpEntity entity = new BasicHttpEntity();
            InputStream inputStream = new ByteArrayInputStream(result.getBytes());
            entity.setContent(inputStream);
            entity.setContentLength(inputStream.available());

            response.setHeader("Content-Type", "application/json");
            response.setEntity(entity);
        }

    }

    //This query has the simple form control?cmd=start or control?cmd=set&buffer=name&value=42
    //The first form starts or stops the measurement. The second one sends a user-given value (from
    //an editElement) to a buffer
    class controlCommandHandler implements HttpRequestHandler {

        @Override
        public void handle(HttpRequest request, HttpResponse response,
                           HttpContext httpContext) throws HttpException, IOException {

            Uri uri = Uri.parse(request.getRequestLine().getUri());
            String cmd = uri.getQueryParameter("cmd");

            final String result;

            if (cmd != null) {
                switch (cmd) {
                    case "start": //Start the measurement
                        callActivity.remoteStartMeasurement();
                        result = "{\"result\": true}";
                        break;
                    case "stop": //Stop the measurement
                        callActivity.remoteStopMeasurement();
                        result = "{\"result\": true}";
                        break;
                    case "clear": //Clear measurement data
                        callActivity.clearData();
                        result = "{\"result\": true}";
                        break;
                    case "set": //Set the value of a buffer
                        String buffer = uri.getQueryParameter("buffer"); //Which buffer?
                        String value = uri.getQueryParameter("value"); //Which value?
                        if (buffer != null && value != null) {
                            double v;
                            try {
                                v = Double.valueOf(value);
                            } catch (Exception e) {
                                //Invalid input
                                result = "{\"result\": false}";
                                break;
                            }
                            if (Double.isNaN(v)) {
                                //We do not allow to explicitly set NaN. The buffer initially contains NaN and this is probably a mistake
                                result = "{\"result\": false}";
                            } else {
                                callActivity.remoteInput = true;
                                experiment.newData = true;

                                //Defocus the input element in the API interface or it might not be updated and will reenter the old value
                                callActivity.requestDefocus();

                                //Send the value to the buffer, but aquire a lock first, so it does not interfere with data analysis
                                experiment.dataLock.lock();
                                try {
                                    experiment.dataBuffers.get(experiment.dataMap.get(buffer)).append(v);
                                } finally {
                                    experiment.dataLock.unlock();
                                }
                                result = "{\"result\": true}";
                            }
                        } else
                            result = "{\"result\": false}";
                        break;
                    case "trigger":
                        String elementStr = uri.getQueryParameter("element"); //Which element?
                        if (elementStr != null) {
                            int htmlID;
                            try {
                                htmlID = Integer.valueOf(elementStr);
                            } catch (Exception e) {
                                //Invalid input
                                result = "{\"result\": false}";
                                break;
                            }
                            experiment.experimentViews.get(htmlID2View.get(htmlID)).elements.get(htmlID2Element.get(htmlID)).trigger();
                            result = "{\"result\": true}";
                        } else {
                            result = "{\"result\": false}";
                        }
                        break;
                    default:
                        result = "{\"result\": false}";
                        break;
                }
            } else
                result = "{\"result\": false}";

            BasicHttpEntity entity = new BasicHttpEntity();
            InputStream inputStream = new ByteArrayInputStream(result.getBytes());
            entity.setContent(inputStream);
            entity.setContentLength(inputStream.available());

            response.setHeader("Content-Type", "application/json");
            response.setEntity(entity);
        }

    }

    //The export query has the form export?format=1&set1=On&set3=On
    //format gives the index of the export format selected by the user
    //Any set (set0, set1, set2...) given should be included in this export
    class exportCommandHandler implements HttpRequestHandler {

        @Override
        public void handle(HttpRequest request, HttpResponse response,
                           HttpContext httpContext) throws HttpException, IOException {

            //Get the parameters
            Uri uri = Uri.parse(request.getRequestLine().getUri());
            String format = uri.getQueryParameter("format");

            //We will build this entity depending on the format. If it is invalid we will repons
            //with an error
            BasicHttpEntity entity;
            int formatInt = Integer.parseInt(format);
            if (formatInt < 0 || formatInt >= experiment.exporter.exportFormats.length) {
                //Not good. Build an error entity
                final String result = "{\"error\" = \"Format out of range.\"}";

                entity = new BasicHttpEntity();
                InputStream inputStream = new ByteArrayInputStream(result.getBytes());
                entity.setContent(inputStream);
                entity.setContentLength(inputStream.available());

                response.setHeader("Content-Type", "application/json");
            } else {
                //Alright, let's go on with the export

                //Get the content-type
                String type = experiment.exporter.exportFormats[formatInt].getType(false);

                //Use the experiment's exporter to create the file
                final String fileName = experiment.title.replaceAll("[^0-9a-zA-Z \\-_]", "");
                final File exportFile = experiment.exporter.exportDirect(experiment.exporter.exportFormats[formatInt], callActivity.getCacheDir(), false, fileName.isEmpty() ? "phyphox" :  fileName);

                entity = new BasicHttpEntity();
                InputStream inputStream = new FileInputStream(exportFile);
                entity.setContent(inputStream);
                entity.setContentLength(inputStream.available());

                //Set the content type and set "Content-Disposition" to force the browser to handle this as a download with a default file name
                response.setHeader("Content-Type", type);
                response.setHeader("Content-Disposition", "attachment; filename=\""+experiment.exporter.exportFormats[formatInt].getFilename(false) + "\"");
            }

            //Send error or file
            response.setEntity(entity);

        }

    }

    //The config query does not take any parameters
    //It returns information on the currently active experiment configuration like title, category, checksum, inputs, buffers, ...
    class configCommandHandler implements HttpRequestHandler {

        @Override
        public void handle(HttpRequest request, HttpResponse response,
                           HttpContext httpContext) throws HttpException, IOException {

            String result;
            try {
                JSONObject json = new JSONObject();

                json.put("crc32", Long.toHexString(experiment.crc32).toLowerCase());
                json.put("title", experiment.baseTitle);
                json.put("localTitle", experiment.title);
                json.put("category", experiment.baseCategory);
                json.put("localCategory", experiment.category);

                JSONArray buffers = new JSONArray();
                for (DataBuffer buffer : experiment.dataBuffers) {
                    buffers.put(new JSONObject().put("name", buffer.name).put("size", buffer.size));
                }
                json.put("buffers", buffers);

                JSONArray inputs = new JSONArray();
                if (experiment.audioRecord != null) {
                    JSONArray outputs = new JSONArray();
                    outputs.put(new JSONObject().put("out", experiment.micOutput));
                    if (!experiment.micRateOutput.isEmpty())
                        outputs.put(new JSONObject().put("rate", experiment.micRateOutput));

                    inputs.put(new JSONObject()
                            .put("source", "audio")
                            .put("outputs", outputs)
                    );
                }
                if (experiment.gpsIn != null) {
                    JSONArray outputs = new JSONArray();
                    if (experiment.gpsIn.dataLat != null)
                        outputs.put(new JSONObject().put("lat", experiment.gpsIn.dataLat.name));
                    if (experiment.gpsIn.dataLon != null)
                        outputs.put(new JSONObject().put("lon", experiment.gpsIn.dataLon.name));
                    if (experiment.gpsIn.dataZ != null)
                        outputs.put(new JSONObject().put("z", experiment.gpsIn.dataZ.name));
                    if (experiment.gpsIn.dataZWGS84 != null)
                        outputs.put(new JSONObject().put("zwgs84", experiment.gpsIn.dataZWGS84.name));
                    if (experiment.gpsIn.dataV != null)
                        outputs.put(new JSONObject().put("v", experiment.gpsIn.dataV.name));
                    if (experiment.gpsIn.dataDir != null)
                        outputs.put(new JSONObject().put("dir", experiment.gpsIn.dataDir.name));
                    if (experiment.gpsIn.dataT != null)
                        outputs.put(new JSONObject().put("t", experiment.gpsIn.dataT.name));
                    if (experiment.gpsIn.dataAccuracy != null)
                        outputs.put(new JSONObject().put("accuracy", experiment.gpsIn.dataAccuracy.name));
                    if (experiment.gpsIn.dataZAccuracy != null)
                        outputs.put(new JSONObject().put("zAccuracy", experiment.gpsIn.dataZAccuracy.name));
                    if (experiment.gpsIn.dataStatus != null)
                        outputs.put(new JSONObject().put("status", experiment.gpsIn.dataStatus.name));
                    if (experiment.gpsIn.dataSatellites != null)
                        outputs.put(new JSONObject().put("satellites", experiment.gpsIn.dataSatellites.name));

                    inputs.put(new JSONObject()
                            .put("source", "location")
                            .put("outputs", outputs)
                    );
                }
                for (SensorInput input : experiment.inputSensors) {
                    JSONArray outputs = new JSONArray();

                    if (input.dataX != null)
                        outputs.put(new JSONObject().put("x", input.dataX.name));
                    if (input.dataY != null)
                        outputs.put(new JSONObject().put("y", input.dataY.name));
                    if (input.dataZ != null)
                        outputs.put(new JSONObject().put("z", input.dataZ.name));
                    if (input.dataAbs != null)
                        outputs.put(new JSONObject().put("abs", input.dataAbs.name));
                    if (input.dataT != null)
                        outputs.put(new JSONObject().put("t", input.dataT.name));
                    if (input.dataAccuracy != null)
                        outputs.put(new JSONObject().put("accuracy", input.dataAccuracy.name));

                    inputs.put(new JSONObject()
                            .put("source", input.sensorName.name())
                            .put("outputs", outputs)
                    );
                }
                if (experiment.bluetoothInputs.size() > 0) {
                    inputs.put(new JSONObject()
                            .put("source", "bluetooth"));
                }
                json.put("inputs", inputs);

                JSONArray export = new JSONArray();
                for (DataExport.ExportSet set : experiment.exporter.exportSets) {
                    JSONArray sources = new JSONArray();
                    for (DataExport.ExportSet.SourceMapping mapping : set.sources) {
                        sources.put(new JSONObject()
                                .put("label", mapping.name)
                                .put("buffer", mapping.source)
                        );
                    }
                    export.put(new JSONObject().put("set", set.name).put("sources", sources));
                }
                json.put("export", export);

                result = json.toString();
            } catch (JSONException e) {
                result = "{\"result\": false}";
                Log.e("configHandler", "Error: " + e.getMessage());
            }

            BasicHttpEntity entity = new BasicHttpEntity();
            InputStream inputStream = new ByteArrayInputStream(result.getBytes());
            entity.setContent(inputStream);
            entity.setContentLength(inputStream.available());

            response.setHeader("Content-Type", "application/json");
            response.setEntity(entity);

        }

    }

    //The meta query does not take any parameters
    //It returns information on the currently used device
    class metaCommandHandler implements HttpRequestHandler {
        @Override
        public void handle(HttpRequest request, HttpResponse response,
                           HttpContext httpContext) throws HttpException, IOException {

            String result;

            try {

                JSONObject deviceJson = new JSONObject();
                for (Metadata.DeviceMetadata deviceMetadata : Metadata.DeviceMetadata.values()) {
                    if (deviceMetadata == Metadata.DeviceMetadata.sensorMetadata || deviceMetadata == Metadata.DeviceMetadata.uniqueID)
                        continue;
                    String identifier = deviceMetadata.toString();
                    deviceJson.put(identifier, new Metadata(identifier, context).get(""));
                }

                JSONObject sensorsJson = new JSONObject();
                for (SensorInput.SensorName sensor : SensorInput.SensorName.values()) {
                    JSONObject sensorJson = new JSONObject();
                    for (Metadata.SensorMetadata sensorMetadata : Metadata.SensorMetadata.values()) {
                        String identifier = sensorMetadata.toString();
                        sensorJson.put(identifier, new Metadata(sensor.name()+identifier, context).get(""));
                    }
                    sensorsJson.put(sensor.name(), sensorJson);
                }
                deviceJson.put("sensors", sensorsJson);


                result = deviceJson.toString();
            } catch (JSONException e) {
                result = "{\"result\": false}";
                Log.e("configHandler", "Error: " + e.getMessage());
            }

            BasicHttpEntity entity = new BasicHttpEntity();
            InputStream inputStream = new ByteArrayInputStream(result.getBytes());
            entity.setContent(inputStream);
            entity.setContentLength(inputStream.available());

            response.setHeader("Content-Type", "application/json");
            response.setEntity(entity);

        }
    }

    //The time query does not take any parameters
    //It returns a list of time reference points, i.e. start and stop times of the current experiment
    class timeCommandHandler implements HttpRequestHandler {
        @Override
        public void handle(HttpRequest request, HttpResponse response,
                           HttpContext httpContext) throws HttpException, IOException {

            String result;

            try {

                JSONArray json = new JSONArray();
                for (ExperimentTimeReference.TimeMapping timeMapping : experiment.experimentTimeReference.timeMappings) {
                    JSONObject eventJson = new JSONObject();
                    eventJson.put("event", timeMapping.event.name());
                    eventJson.put("experimentTime", timeMapping.experimentTime);
                    eventJson.put("systemTime", timeMapping.systemTime/1000.);
                    json.put(eventJson);
                }

                result = json.toString();
            } catch (JSONException e) {
                result = "{\"result\": false}";
                Log.e("configHandler", "Error: " + e.getMessage());
            }

            BasicHttpEntity entity = new BasicHttpEntity();
            InputStream inputStream = new ByteArrayInputStream(result.getBytes());
            entity.setContent(inputStream);
            entity.setContentLength(inputStream.available());

            response.setHeader("Content-Type", "application/json");
            response.setEntity(entity);

        }
    }

}
