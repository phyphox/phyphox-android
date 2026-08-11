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
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.PreferenceManager;

import net.freeutils.httpserver.HTTPServer;
import net.freeutils.httpserver.HTTPServer.Request;
import net.freeutils.httpserver.HTTPServer.Response;
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
import java.net.SocketException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import de.rwth_aachen.phyphox.helper.FileNameFormat;
import de.rwth_aachen.phyphox.helper.Helper;

//RemoteServer implements a web interface to remote control the experiment and receive the data

public class RemoteServer {

    private final PhyphoxExperiment experiment; //Reference to the experiment we want to control

    HTTPServer httpServer; //Holds our http service
    ExecutorService executor;
    static int httpServerPort = 8080; //We have to pick a high port number. We may not use 80...
    Context context; //Resource reference for comfortable access
    Experiment callActivity; //Reference to the parent activity. Needed to provide its status on the webinterface

    public String sessionID = "";

    public boolean forceFullUpdate = false; //Something has happened (clear) that makes it necessary to force a full buffer update to the remote interface

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

    protected void buildViewsJson(StringBuilder sb) {
        //The viewLayout is a JSON object with our view setup. All the experiment views
        //and their view elements and their JavaScript functions and so on...

        //Beginning of the JSON block
        sb.append("var views = [");

        int id = 0; //We will give each view a unique id to address them in JavaScript
        //via a HTML id
        htmlID2View.clear();
        htmlID2Element.clear();

        for (int i = 0; i < experiment.experimentViews.size(); i++) {
            //For each experiment view
            ExpView view = experiment.experimentViews.get(i);

            if (i > 0)  //Add a colon if this is not the first item to separate the previous one.
                sb.append(",\n"); //Not necessary, but debugging is so much more fun with a line-break.

            //The name of this view
            sb.append("{\"name\": \"");
            sb.append(view.name.replace("\"","\\\""));

            //Now for its elements
            sb.append("\", \"elements\":[\n");
            for (int j = 0; j < view.elements.size(); j++) {
                //For each element within this view
                ExpView.expViewElement element = view.elements.get(j);

                //Store the mapping of htmlID to the experiment view hierarchy
                htmlID2View.add(i);
                htmlID2Element.add(j);

                if (j > 0)  //Add a colon if this is not the first item to separate the previous one.
                    sb.append(",");

                //The element's label
                sb.append("{\"label\":\"");
                sb.append(element.label.replace("\"","\\\""));

                //The id, we just created
                sb.append("\",\"index\":\"");
                sb.append(id);

                //The update method
                sb.append("\",\"updateMode\":\"");
                sb.append(element.getUpdateMode());

                //The label size
                sb.append("\",\"labelSize\":\"");
                sb.append(element.labelSize);

                //The HTML markup for this element - on this occasion we notify the element about its id
                sb.append("\",\"html\":\"");
                sb.append(element.getViewHTML(id).replace("\"","\\\""));

                //The Javascript function that handles data completion
                sb.append("\",\"dataCompleteFunction\":");
                sb.append(element.dataCompleteHTML());

                if(element.visibility != null ){
                    sb.append(",\"visibilityInput\":");
                    sb.append("\"");
                    sb.append(element.visibility.replace("\"", "\\\""));
                    sb.append("\"");
                }

                //If this element takes an x array, set the buffer and the JS function
                if (element.inputs != null) {
                    sb.append(",\"dataInput\":[");
                    boolean first = true;
                    for (String input : element.inputs) {
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
                    sb.append(element.setDataHTML());
                    sb.append("\n");
                }

                sb.append("}"); //The element is complete
                id++;
            }
            sb.append("\n]}"); //The view is complete
        }
        sb.append("\n];"); //The views are complete -> JSON object complete

        sb.append("var clearGroups = [");
        String[] clearGroups = experiment.getClearGroups();
        for (int i = 0; i < clearGroups.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append("\"");
            sb.append(clearGroups[i].replace("\"", "\\\""));
            sb.append("\"");
        }
        sb.append("];");
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
                } else if (line.contains("<!-- [[translationOK]] -->")) { //The localized string for "clear data"
                    sb.append(line.replace("<!-- [[translationOK]] -->", context.getString(R.string.ok)));
                } else if (line.contains("<!-- [[translationCancel]] -->")) { //The localized string for "clear data"
                    sb.append(line.replace("<!-- [[translationCancel]] -->", context.getString(R.string.cancel)));
                } else if (line.contains("<!-- [[clearDataTranslation]] -->")) { //The localized string for "clear data"
                    sb.append(line.replace("<!-- [[clearDataTranslation]] -->", context.getString(R.string.clear_data)));
                } else if (line.contains("<!-- [[clearConfirmTranslation]] -->")) { //The localized string for "Clear data?"
                    sb.append(line.replace("<!-- [[clearConfirmTranslation]] -->", context.getString(R.string.clear_data_question)));
                } else if (line.contains("<!-- [[clearConfirmTranslationSelect]] -->")) { //The localized string for "Clear data?"
                    sb.append(line.replace("<!-- [[clearConfirmTranslationSelect]] -->", context.getString(R.string.clear_data_question_select)));
                } else if (line.contains("<!-- [[exportTranslation]] -->")) { //The localized string for "clear data"
                    sb.append(line.replace("<!-- [[exportTranslation]] -->", context.getString(R.string.export)));
                } else if (line.contains("<!-- [[switchToPhoneLayoutTranslation]] -->")) { //The localized string for "clear data"
                    sb.append(line.replace("<!-- [[switchToPhoneLayoutTranslation]] -->", context.getString(R.string.switchToPhoneLayout)));
                } else if (line.contains("<!-- [[switchColumns1Translation]] -->")) { //The localized string for "clear data"
                    sb.append(line.replace("<!-- [[switchColumns1Translation]] -->", context.getString(R.string.switchColumns1)));
                } else if (line.contains("<!-- [[switchColumns2Translation]] -->")) { //The localized string for "clear data"
                    sb.append(line.replace("<!-- [[switchColumns2Translation]] -->", context.getString(R.string.switchColumns2)));
                } else if (line.contains("<!-- [[switchColumns3Translation]] -->")) { //The localized string for "clear data"
                    sb.append(line.replace("<!-- [[switchColumns3Translation]] -->", context.getString(R.string.switchColumns3)));
                } else if (line.contains("<!-- [[toggleBrightModeTranslation]] -->")) {
                    sb.append(line.replace("<!-- [[toggleBrightModeTranslation]] -->", context.getString(R.string.toggleBrightMode)));
                } else if (line.contains("<!-- [[fontSizeTranslation]] -->")) {
                    sb.append(line.replace("<!-- [[fontSizeTranslation]] -->", context.getString(R.string.fontSize)));
                } else if (line.contains("<!-- [[viewLayout]] -->")) {
                    buildViewsJson(sb);
                } else if (line.contains("<!-- [[viewOptions]] -->")) {
                    //The option list for the view selector. Simple.
                    for (ExpView view : experiment.experimentViews) {
                        sb.append("<li>").append(view.name).append("</li>\n");
                    }
                } else if (line.contains("<!-- [[exportFormatOptions]] -->")) {
                    //The export format
                    for (int i = 0; i < experiment.exporter.exportFormats.length; i++) {
                        DataExport.ExportFormat format = experiment.exporter.exportFormats[i];
                        sb.append("<option value=\"").append(i).append("\">").append(format.getName()).append("</option>\n");
                    }
                } else {
                    //No placeholder. Just append this.
                    sb.append(line);
                }
                sb.append("\n");
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
    }

    RemoteServer(PhyphoxExperiment experiment, Experiment callActivity) {
        this(experiment, callActivity, String.format("%06x", (System.nanoTime() & 0xffffff)));
    }

    //This helper function lists all external IP addresses, so the user can be told, how to reach the webinterface
    public static String getAddresses(Context context) {
        String ret = "";
        Inet4Address filterMobile = null;
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network[] networks = connectivityManager.getAllNetworks();
        for (Network network : networks) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                continue;
            }
            LinkProperties properties = connectivityManager.getLinkProperties(network);
            for (LinkAddress address : properties.getLinkAddresses()) {
                if (address.getAddress() instanceof Inet4Address) {
                    filterMobile = (Inet4Address) address.getAddress();
                    break;
                }
            }
        }
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) { //For each network interface
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                while (enumInetAddress.hasMoreElements()) { //For each address of this interface
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    //We want non-local, non-loopback IPv4 addresses (nobody really uses IPv6 on local networks and phyphox is not supposed to run over the internet - let's not make it too complicated for the user)
                    if (!inetAddress.isAnyLocalAddress() && !inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address && !inetAddress.equals(filterMobile)) {
                        ret += "http://" + inetAddress.getHostAddress() + ":" + httpServerPort + "\n";
                    }

                }

            }

        } catch (SocketException e) {
            Log.e("getAddresses", "Error getting the IP.", e);
        }
        return ret;
    }

    //CORS: allow cross-origin browser pages to read remote-access responses, including error
    //responses - the case where a browser client most needs to read the body. The header is set
    //before the request is dispatched, so it is also present on the error pages jlhttp generates
    //itself (404, 405, ...), which never pass through respond().
    private static class CorsHTTPServer extends HTTPServer {
        CorsHTTPServer(int port) {
            super(port);
        }

        @Override
        protected void handleTransaction(Request req, Response resp) throws IOException {
            resp.getHeaders().add("Access-Control-Allow-Origin", "*");
            super.handleTransaction(req, resp);
        }
    }

    //Thrown by the parameter extraction when a request body cannot be understood (a POST that
    //declares a JSON body but does not contain a valid JSON object). Turned into a 400 by
    //withErrorResponse. A malformed body is a transport-level error, distinct from a
    //well-formed request carrying a bad value (which each endpoint answers in its own way).
    private static class BadRequestException extends RuntimeException {}

    //Wraps a context handler so that an unhandled exception becomes jlhttp's plain 500 error
    //response. Without this, the exception would escape to jlhttp's connection handling, which
    //builds a fresh response without the CORS header set in handleTransaction. A
    //BadRequestException instead becomes a clean 400.
    private HTTPServer.ContextHandler withErrorResponse(HTTPServer.ContextHandler handler) {
        return (request, response) -> {
            try {
                return handler.serve(request, response);
            } catch (BadRequestException e) {
                if (response.headersSent())
                    throw e;
                return respondStatus(response, 400);
            } catch (RuntimeException e) {
                Log.e("remoteServer", "Unhandled exception while serving " + request.getPath() + ".", e);
                if (response.headersSent())
                    throw e; //Too late for an error response, let jlhttp abort the connection
                return 500;
            }
        };
    }

    //A request's parameters, combining the query string with the POST body if one is present.
    //Every endpoint accepts POST as well as GET (see control-post in phyphox-docs), and a POST
    //may carry its parameters as either a JSON object or a form-encoded body, chosen by the
    //request's Content-Type:
    //  - application/json: a flat object like {"cmd":"set","buffer":"x","value":42}. Every value
    //    is coerced to its string form (so "value":42 and "value":"42" are equivalent), because
    //    the parameter model is string-based throughout - a JSON body is a convenience, not a
    //    second type system.
    //  - application/x-www-form-urlencoded (or no body): the classic form encoding, which jlhttp
    //    parses itself.
    //Body parameters take precedence over query-string parameters of the same name, matching the
    //order jlhttp already uses for form bodies. This mirror must stay in step with iOS.
    private boolean hasJsonBody(Request request) throws IOException {
        String contentType = request.getHeaders().get("Content-Type");
        return contentType != null && contentType.toLowerCase(Locale.US).startsWith("application/json");
    }

    private List<String[]> requestParamsList(Request request) throws IOException {
        if (!hasJsonBody(request))
            return request.getParamsList(); //form body (if any) + query, handled by jlhttp

        //A JSON body: jlhttp does not touch a non-form body, so we read and parse it ourselves
        //and then append the query parameters (which requestParamsList lists after the body, so
        //the body wins in the deduplicated map form below).
        List<String[]> params = new ArrayList<>();
        String body = readBody(request);
        try {
            JSONObject obj = new JSONObject(body);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = obj.get(key);
                //Coerce every scalar to its string form; JSON null becomes an empty value, as an
                //empty form field would. Nested objects/arrays are not part of this flat API and
                //simply stringify (and will then fail the value parsing of the endpoint).
                params.add(new String[]{key, value == JSONObject.NULL ? "" : value.toString()});
            }
        } catch (JSONException e) {
            throw new BadRequestException();
        }
        params.addAll(request.getParamsList());
        return params;
    }

    private Map<String, String> requestParams(Request request) throws IOException {
        //First occurrence wins, and requestParamsList lists body parameters before the query, so
        //the body takes precedence over a query parameter of the same name.
        Map<String, String> map = new LinkedHashMap<>();
        for (String[] param : requestParamsList(request)) {
            if (!map.containsKey(param[0]))
                map.put(param[0], param[1]);
        }
        return map;
    }

    private String readBody(Request request) throws IOException {
        InputStream in = request.getBody();
        if (in == null)
            throw new BadRequestException();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            total += n;
            if (total > 2097152) //2 MB, matching jlhttp's own form-body limit
                throw new BadRequestException();
            os.write(buffer, 0, n);
        }
        return os.toString("UTF-8");
    }

    //This starts the http server and registers the handlers for several requests
    public synchronized void start() {
        httpServerPort = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString("remoteAccessPort", "8080"));
        httpServer = new CorsHTTPServer(httpServerPort);
        executor = Executors.newCachedThreadPool();
        httpServer.setExecutor(executor);
        HTTPServer.VirtualHost host = httpServer.getVirtualHost(null);

        //Now register a handler for different requests. Every endpoint accepts POST as well as
        //GET (see control-post in phyphox-docs); a POST body may be JSON or form-encoded, chosen
        //by Content-Type. The handlers read their parameters through requestParams()/
        //requestParamsList(), which merge body and query, so they do not need to care.
        host.addContext("/", withErrorResponse(this::handleHome), "GET", "POST"); //The basic interface (index.html) when the user just calls the address
        host.addContext("/style.css", withErrorResponse(this::handleStyle), "GET", "POST"); //The style sheet (style.css) linked from index.html
        host.addContext("/logo", withErrorResponse(this::handleLogo), "GET", "POST"); //The phyphox logo, also included in style.css
        host.addContext("/get", withErrorResponse(this::handleGet), "GET", "POST"); //A get command takes parameters which define, which buffers and how much of them is requested - the response is a JSON set with the data
        host.addContext("/control", withErrorResponse(this::handleControl), "GET", "POST"); //The control command starts and stops measurements
        host.addContext("/export", withErrorResponse(this::handleExport), "GET", "POST"); //The export command requests a data file containing sets as requested by the parameters
        host.addContext("/config", withErrorResponse(this::handleConfig), "GET", "POST"); //The config command requests information on the currently active experiment configuration
        host.addContext("/meta", withErrorResponse(this::handleMeta), "GET", "POST"); //The meta command requests information on the device
        host.addContext("/time", withErrorResponse(this::handleTime), "GET", "POST"); //The meta command requests information on the current time reference
        host.addContext("/res", withErrorResponse(this::handleRes), "GET", "POST"); //Fetch resource files (like images embedded in the experiment configuration)
        try {
            httpServer.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //Stop the server by simply setting RUNNING to false
    public synchronized void stop() {
        httpServer.stop();
        executor.shutdown();
    }

    protected int respond(Response response, String contentType, InputStream in, long length) throws IOException {
        try {
            //The CORS header is already set for every response in CorsHTTPServer.handleTransaction
            response.sendHeaders(200, length, System.currentTimeMillis(), null, contentType, null);
            response.sendBody(in, -1, null);
        } finally {
            in.close();
        }
        return 0; // response fully handled
    }

    protected int respond(Response response, String contentType, String content) throws IOException {
        byte[] bytes = content.getBytes();
        InputStream in = new ByteArrayInputStream(bytes);
        return respond(response, contentType, in, bytes.length);
    }

    protected int respond(Response response, String contentType, File content) throws IOException {
        InputStream in = new FileInputStream(content);
        return respond(response, contentType, in, content.length());
    }

    protected int respond(Response response, String json) throws IOException {
        return respond(response, "application/json", json);
    }

    protected int respond(Response response, boolean result) throws IOException {
        return respond(response, result ? "{\"result\": true}" : "{\"result\": false}");
    }

    protected int respondStatus(Response response, int status) throws IOException {
        //A plain status code with an empty body (i.e. 400 for a bad request, like iOS)
        response.sendHeaders(status, 0, System.currentTimeMillis(), null, null, null);
        return 0; // response fully handled
    }

    //The home handler simply sends the already compiled index.html
    public int handleHome(Request request, Response response) throws IOException {
        return respond(response, "text/html", indexHTML);
    }

    //The style handler simply sends the already compiled style.css
    public int handleStyle(Request request, Response response) throws IOException {
        return respond(response, "text/css", styleCSS);
    }

    //The logo handler simply reads the logo from resources and sends it
    public int handleLogo(Request request, Response response) throws IOException {
        InputStream inputStream = context.getAssets().open("remote/phyphox_orange.png");
        return respond(response, "image/png", inputStream, -1);
    }

    //This structure (ok, class) holds one element of the request corresponding to a buffer
    protected static class BufferRequest {
        public String name;         //Name of the requested buffer
        public Double threshold;    //Threshold from which to read data
        public String reference;    //The buffer to which the threshold should be applied
    }

    protected BufferRequest parseBufferRequest(String name, String value, boolean forceFullUpdate) {
        BufferRequest br = new BufferRequest();
        br.name = name;
        br.reference = "";
        if (value == null || value.isEmpty()) {
            br.threshold = Double.NaN; //No special request - the last value should be ok
        } else if (value.equals("full") || forceFullUpdate) {
            br.threshold = Double.NEGATIVE_INFINITY; //Get every single value
        } else {
            //So we get a threshold. We just have to figure out the reference buffer
            int subsplit = value.indexOf('|');
            if (subsplit == -1)
                br.threshold = Double.valueOf(value); //No reference specified
            else { //A reference is given
                br.threshold = Double.valueOf(value.substring(0, subsplit));
                br.reference = value.substring(subsplit + 1);
            }
            //We only offer 8-digit precision, so we need to move the threshold to avoid receiving a close number multiple times.
            //Missing something will probably not be visible on a remote graph and a missing value will be recent after stopping anyway.
            //The nudge magnitude derives from the absolute value (log10 of a negative threshold would be NaN and break the request);
            //its direction stays positive, so a value equal to an already delivered negative threshold does not pass the comparison again.
            br.threshold += Math.pow(10, Math.floor(Math.log10(Math.abs(br.threshold) / 1e7)));
        }
        return br;
    }

    protected Set<BufferRequest> getBufferRequests(Request request) throws IOException {
        Set<BufferRequest> buffers = new LinkedHashSet<>(); //This list will hold all requests
        List<String[]> params = requestParamsList(request);
        if (!params.isEmpty()) {
            for (String[] param : params) {
                BufferRequest br = parseBufferRequest(param[0], param[1], forceFullUpdate);
                buffers.add(br);
            }
            forceFullUpdate = false;
        }
        return buffers;
    }

    protected void buildBuffer(BufferRequest buffer, DataBuffer db, DecimalFormat format, StringBuilder sb) {
        //Get the threshold reference data buffer
        DataBuffer db_reference = buffer.reference.isEmpty() ? db : experiment.getBuffer(buffer.reference);

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
            boolean firstValue = true; //Find first iteration, so the other ones can add a separator
            Double[] data = db.getArray();
            int n = db.getFilledSize();
            Double[] dataRef;
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

                //Add a separator if this is not the first value
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

    //The get query has the form
    //get?buffer1=full&buffer2=12345&buffer3=67890|buffer1
    //This query requests the whole buffer1, all values from buffer2 greater than 12345 and all
    // values from buffer3 at indices at which values of buffer2 are greater than 67890
    //Example: If you have a graph of sensor data y against time t and already have data to
    // 20 seconds, you would request t=20 and y=20|t to receive any data beyond 20 seconds
    public int handleGet(Request request, Response response) throws IOException {
        Set<BufferRequest> buffers;
        try {
            buffers = getBufferRequests(request);
        } catch (NumberFormatException e) {
            //A threshold that does not parse as a number: reject the bad request instead of
            //failing it with a server error (see get-invalid-threshold in phyphox-docs)
            return respondStatus(response, 400);
        }

        //A threshold referencing a buffer that does not exist is a bad request (unknown buffers
        //in the *requested* position are skipped below instead, so a client with a stale buffer
        //list still gets the session id and can notice the experiment change)
        for (BufferRequest buffer : buffers) {
            if (!buffer.reference.isEmpty() && experiment.getBuffer(buffer.name) != null && experiment.getBuffer(buffer.reference) == null)
                return respondStatus(response, 400);
        }

        //We now know what the query request. Let's build our answer
        StringBuilder sb;

        //Lock the data, to get a consistent data block
        experiment.dataLock.lock();
        try {
            //First let's take a guess at how much memory we will need
            int sizeEstimate = 0;
            for (BufferRequest buffer : buffers) {
                DataBuffer db = experiment.getBuffer(buffer.name);
                if (db != null)
                    sizeEstimate += 14 * db.size + 100;
            }

            //Create the string builder
            sb = new StringBuilder(sizeEstimate);

            boolean firstBuffer = true; //Helper to recognize the first iteration

            //Set our decimal format (English to make sure we use decimal points, not comma
            DecimalFormat format = (DecimalFormat) NumberFormat.getInstance(Locale.ENGLISH);
            format.applyPattern("0.#######E0");

            //Start building...
            sb.append("{\"buffer\":{\n");
            for (BufferRequest buffer : buffers) {
                DataBuffer db = experiment.getBuffer(buffer.name);
                if (db == null)
                    continue;
                if (firstBuffer)
                    firstBuffer = false;
                else
                    sb.append(",\n"); //Separate the object with a comma, if this is not the first item

                buildBuffer(buffer, db, format, sb);
            }

            //We also send the experiment status
            sb.append("\n},\n\"status\":{\n");

            //Session ID
            sb.append("\"session\":\"");
            sb.append(sessionID);

            //Measuring?
            sb.append("\", \"measuring\":");
            sb.append(callActivity.measuring);

            //Timed run?
            sb.append(", \"timedRun\":");
            sb.append(callActivity.timedRun);

            //Countdown state
            sb.append(", \"countDown\":");
            sb.append(callActivity.millisUntilFinished);
            sb.append("\n}\n}\n");
        } finally {
            experiment.dataLock.unlock();
        }

        //Done. Build a string and return it as usual
        return respond(response, sb.toString());
    }

    //This query has the simple form control?cmd=start or control?cmd=set&buffer=name&value=42
    //The first form starts or stops the measurement. The second one sends a user-given value (from
    //an editElement) to a buffer
    public int handleControl(Request request, Response response) throws IOException {
        Map<String, String> params = requestParams(request);
        String cmd = params.get("cmd");

        if (cmd != null) {
            switch (cmd) {
                case "start": //Start the measurement
                    callActivity.remoteStartMeasurement();
                    return respond(response, true);
                case "stop": //Stop the measurement
                    callActivity.remoteStopMeasurement();
                    return respond(response, true);
                case "clear": //Clear measurement data
                    List<String> clearGroups = new LinkedList<>();
                    int i = 1;
                    while (true) {
                        String clearGroup = params.get("clearGroup"+i);
                        if (clearGroup == null)
                            break;
                        clearGroups.add(clearGroup);
                        i++;
                    }
                    callActivity.clearData(clearGroups);
                    return respond(response, true);
                case "set": //Set the value of a buffer
                    String buffer = params.get("buffer"); //Which buffer?
                    String value = params.get("value"); //Which value?
                    if (buffer != null && value != null) {
                        double v;
                        try {
                            v = Double.valueOf(value);
                        } catch (Exception e) {
                            //Invalid input
                            return respond(response, false);
                        }
                        if (Double.isNaN(v) || Double.isInfinite(v)) {
                            //We do not allow to explicitly set a non-finite value. The buffer initially contains NaN, so this is probably a mistake - and /get reports any non-finite value as null anyway
                            return respond(response, false);
                        } else {
                            DataBuffer db = experiment.getBuffer(buffer);
                            if (db == null) {
                                //Unknown buffer: reject cleanly instead of failing the request
                                return respond(response, false);
                            }

                            callActivity.remoteInput = true;
                            experiment.newData = true;

                            //Defocus the input element in the API interface otherwise it might not be updated and will reenter the old value
                            callActivity.requestDefocus();

                            //Send the value to the buffer, but acquire a lock first, so it does not interfere with data analysis
                            experiment.dataLock.lock();
                            try {
                                db.append(v);
                            } finally {
                                experiment.dataLock.unlock();
                            }
                            return respond(response, true);
                        }
                    }
                    return respond(response, false);
                case "trigger":
                    String elementStr = params.get("element"); //Which element?
                    if (elementStr != null) {
                        int htmlID;
                        try {
                            htmlID = Integer.valueOf(elementStr);
                        } catch (Exception e) {
                            //Invalid input
                            return respond(response, false);
                        }
                        if (htmlID < 0 || htmlID >= htmlID2View.size() || htmlID >= htmlID2Element.size()) {
                            //Out-of-range element: reject cleanly instead of failing the request.
                            //(Note this deliberately answers false for a trigger that was not
                            //performed, see control-trigger-out-of-range in phyphox-docs.)
                            return respond(response, false);
                        }
                        experiment.experimentViews.get(htmlID2View.get(htmlID)).elements.get(htmlID2Element.get(htmlID)).trigger();
                        return respond(response, true);
                    }
                    return respond(response, false);
                default:
                    return respond(response, false);
            }
        } else
            return respond(response, false);
    }

    //The export query has the form export?format=1&set1=On&set3=On
    //format gives the index of the export format selected by the user
    //Any set (set0, set1, set2...) given should be included in this export
    public int handleExport(Request request, Response response) throws IOException {
        //Get the parameters
        String format = requestParams(request).get("format");

        int formatInt;
        try {
            formatInt = Integer.parseInt(format);
        } catch (NumberFormatException e) {
            //A missing or non-numeric format is rejected with the same error object as an
            //out-of-range one instead of failing the request
            return respond(response, "{\"error\": \"Invalid format.\"}");
        }
        if (formatInt < 0 || formatInt >= experiment.exporter.exportFormats.length) {
            //Not good. Build an error entity
            return respond(response, "{\"error\": \"Format out of range.\"}");
        } else {
            //Alright, let's go on with the export

            //Get the content-type
            DataExport.ExportFormat exportFormat = experiment.exporter.exportFormats[formatInt];
            String type = exportFormat.getType(false);

            //Use the experiment's exporter to create the file
            final String fileName = FileNameFormat.formatFilename(context, experiment.title, experiment.experimentTimeReference);
            final File exportFile = experiment.exporter.exportDirect(exportFormat, callActivity.getCacheDir(), false, fileName, context);

            //Set "Content-Disposition" to force the browser to handle this as a download with a default file name
            response.getHeaders().add("Content-Disposition", "attachment; filename=\"" + exportFormat.getFilename(false) + "\"");
            return respond(response, type, exportFile);
        }
    }

    //The config query does not take any parameters
    //It returns information on the currently active experiment configuration like title, category, checksum, inputs, buffers, ...
    public int handleConfig(Request request, Response response) throws IOException {
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

            return respond(response, json.toString());
        } catch (JSONException e) {
            Log.e("configHandler", "Error: " + e.getMessage());
            return respond(response, false);
        }
    }

    //The meta query does not take any parameters
    //It returns information on the currently used device
    public int handleMeta(Request request, Response response) throws IOException {
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

            return respond(response, deviceJson.toString());
        } catch (JSONException e) {
            Log.e("configHandler", "Error: " + e.getMessage());
            return respond(response, false);
        }
    }

    //The time query does not take any parameters
    //It returns a list of time reference points, i.e. start and stop times of the current experiment
    public int handleTime(Request request, Response response) throws IOException {
        try {

            JSONArray json = new JSONArray();
            for (ExperimentTimeReference.TimeMapping timeMapping : experiment.experimentTimeReference.getTimeMappings()) {
                JSONObject eventJson = new JSONObject();
                eventJson.put("event", timeMapping.event.name());
                eventJson.put("experimentTime", timeMapping.experimentTime);
                eventJson.put("systemTime", timeMapping.systemTime/1000.);
                json.put(eventJson);
            }

            return respond(response, json.toString());
        } catch (JSONException e) {
            Log.e("configHandler", "Error: " + e.getMessage());
            return respond(response, false);
        }
    }

    //The res handler serves resource files (like images embedded in the experiment configuration)
    public int handleRes(Request request, Response response) throws IOException {
        //Get the parameters
        String src = requestParams(request).get("src");
        if (src == null || src.isEmpty() || !experiment.resources.contains(src) || !Helper.isSafeResourceName(src))
            return respond(response, "{\"error\": \"Unknown file.\"}");

        //Externally loaded experiments deliver their resources in a res folder alongside the
        //XML file, so try this one first...
        if (experiment.resourceFolder != null && !experiment.resourceFolder.startsWith("ASSET")) {
            File file = new File(experiment.resourceFolder, src);
            if (file.isFile()) {
                try {
                    return respond(response, null, file);
                } catch (Exception e) {
                    return respond(response, "{\"error\": \"Unknown file.\"}");
                }
            }
        }

        //...and fall back to the internal images bundled with phyphox (at the moment only
        //hue.png), matching the fallback of the image view element in the app.
        try {
            InputStream is = context.getAssets().open("experiments/res/" + src);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = is.read(buffer)) != -1)
                os.write(buffer, 0, n);
            is.close();
            byte[] data = os.toByteArray();
            return respond(response, null, new ByteArrayInputStream(data), data.length);
        } catch (Exception e) {
            return respond(response, "{\"error\": \"Unknown file.\"}");
        }
    }

}
