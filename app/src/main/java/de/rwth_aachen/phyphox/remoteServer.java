package de.rwth_aachen.phyphox;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

public class remoteServer extends Thread {

    private String title;

    private final Vector<expView> experimentViews;
    private final Vector<dataBuffer> dataBuffers;
    private final Map<String, Integer> dataMap;

    ServerSocket serverSocket;
    Socket socket;
    HttpService httpService;
    BasicHttpContext basicHttpContext;
    static final int HttpServerPORT = 8080;
    boolean RUNNING = false;
    Resources res;
    Experiment callActivity;

    static String indexHTML, styleCSS;

    protected void buildStyleCSS () {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(this.res.openRawResource(R.raw.style)));
        String line;
        try {
            while ((line = br.readLine()) != null) {
                //Set color placeholders...
                line = line.replace("###background-color###", "#"+String.format("%08x", res.getColor(R.color.background)).substring(2));
                line = line.replace("###main-color###", "#"+String.format("%08x", res.getColor(R.color.main)).substring(2));
                line = line.replace("###highlight-color###", "#"+String.format("%06x", res.getColor(R.color.highlight)).substring(2));
                if (line.contains("###drawablePlay###"))
                    line = line.replace("###drawablePlay###", getBase64PNG(res.getDrawable(R.drawable.play)));
                if (line.contains("###drawableTimedPlay###"))
                    line = line.replace("###drawableTimedPlay###", getBase64PNG(res.getDrawable(R.drawable.timed_play)));
                if (line.contains("###drawablePause###"))
                    line = line.replace("###drawablePause###", getBase64PNG(res.getDrawable(R.drawable.pause)));
                if (line.contains("###drawableTimedPause###"))
                    line = line.replace("###drawableTimedPause###", getBase64PNG(res.getDrawable(R.drawable.timed_pause)));
                if (line.contains("###drawableExport###"))
                    line = line.replace("###drawableExport###", getBase64PNG(res.getDrawable(R.drawable.download)));
                sb.append(line);
                sb.append("\n");
            }
        } catch (Exception e) {
            Log.d("remoteServer","Error loading style.css", e);
        } finally {
            styleCSS = sb.toString();
        }
    }

    protected String getBase64PNG(Drawable src) {
        Bitmap bm;
        if (src instanceof BitmapDrawable) {
            bm = ((BitmapDrawable) src).getBitmap();
        } else {
            final int w = !src.getBounds().isEmpty() ? src.getBounds().width() : src.getIntrinsicWidth();
            final int h = !src.getBounds().isEmpty() ? src.getBounds().height() : src.getIntrinsicHeight();
            bm = Bitmap.createBitmap(w <= 0 ? 1 : w, h <= 0 ? 1 : h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bm);
            src.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            src.draw(canvas);
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT).replace("\n", "");

    }

    protected void buildIndexHTML () {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(this.res.openRawResource(R.raw.index)));
        String line;
        try {
            while ((line = br.readLine()) != null) {
                if (line.contains("<!-- [[title]] -->")) {
                    sb.append(line.replace("<!-- [[title]] -->", "phyPhoX: "+title));
                    sb.append("\n");
                } else if (line.contains("<!-- [[viewLayout]] -->")) {
                    sb.append("var views = [");
                    int id = 0;
                    for (int i = 0; i < experimentViews.size(); i++) {
                        if (i > 0)
                            sb.append(",\n");
                        sb.append("{\"name\": \"");
                        sb.append(experimentViews.get(i).name.replace("\"","\\\""));
                        sb.append("\", \"elements\":[\n");
                        for (int j = 0; j < experimentViews.get(i).elements.size(); j++) {
                            if (j > 0)
                                sb.append(",");
                            sb.append("{\"label\":\"");
                            sb.append(experimentViews.get(i).elements.get(j).label.replace("\"","\\\""));
                            sb.append("\",\"index\":\"");
                            sb.append(id);
                            sb.append("\",\"partialUpdate\":\"");
                            sb.append(experimentViews.get(i).elements.get(j).getUpdateMode());
                            sb.append("\",\"labelSize\":\"");
                            sb.append(experimentViews.get(i).elements.get(j).labelSize);
                            sb.append("\",\"html\":\"");
                            sb.append(experimentViews.get(i).elements.get(j).getViewHTML(id).replace("\"","\\\""));
                            sb.append("\",\"dataCompleteFunction\":");
                            sb.append(experimentViews.get(i).elements.get(j).dataCompleteHTML());
                            if (experimentViews.get(i).elements.get(j).getValueInput() != null) {
                                sb.append(",\"valueInput\":\"");
                                sb.append(experimentViews.get(i).elements.get(j).getValueInput().replace("\"","\\\""));
                                sb.append("\",\"valueInputFunction\":\n");
                                sb.append(experimentViews.get(i).elements.get(j).setValueHTML());
                                sb.append("\n");
                            }
                            if (experimentViews.get(i).elements.get(j).getDataXInput() != null) {
                                sb.append(",\"dataXInput\":\"");
                                sb.append(experimentViews.get(i).elements.get(j).getDataXInput().replace("\"","\\\""));
                                sb.append("\",\"dataXInputFunction\":\n");
                                sb.append(experimentViews.get(i).elements.get(j).setDataXHTML());
                                sb.append("\n");
                            }
                            if (experimentViews.get(i).elements.get(j).getDataYInput() != null) {
                                sb.append(",\"dataYInput\":\"");
                                sb.append(experimentViews.get(i).elements.get(j).getDataYInput().replace("\"","\\\""));
                                sb.append("\",\"dataYInputFunction\":\n");
                                sb.append(experimentViews.get(i).elements.get(j).setDataYHTML());
                                sb.append("\n");
                            }
                            sb.append("}");
                            id++;
                        }
                        sb.append("\n]}");
                    }
                    sb.append("\n];\n");
                } else if (line.contains("<!-- [[viewOptions]] -->")) {
                    for (int i = 0; i < experimentViews.size(); i++) {
                        sb.append("<option value=\"");
                        sb.append(i);
                        sb.append("\">");
                        sb.append(experimentViews.get(i).name);
                        sb.append("</option>\n");
                    }
                } else if (line.contains("<!-- [[exportSetSelectors]] -->")) {
                    for (int i = 0; i < callActivity.exporter.exportSets.size(); i++)
                        sb.append("<div class=\"setSelector\"><input type=\"checkbox\" id=\"set"+i+"\" name=\"set"+i+"\" /><label for=\"set"+i+"\">"+callActivity.exporter.exportSets.get(i).name+"</label></div>\n");
                } else if (line.contains("<!-- [[exportFormatOptions]] -->")) {
                    for (int i = 0; i < callActivity.exporter.exportFormats.length; i++)
                        sb.append("<option value=\""+i+"\">"+callActivity.exporter.exportFormats[i].getName()+"</option>\n");
                } else {
                    sb.append(line);
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            Log.d("remoteServer","Error loading index.html", e);
        } finally {
            indexHTML = sb.toString();
        }
    }

    remoteServer(Vector<expView> experimentViews, Vector<dataBuffer> dataBuffers, Map<String, Integer> dataMap, Resources res, String title, Experiment callActivity) {
        this.experimentViews = experimentViews;
        this.dataBuffers = dataBuffers;
        this.dataMap = dataMap;
        this.title = title;
        this.res = res;
        this.callActivity = callActivity;

        buildStyleCSS();
        buildIndexHTML();


        RUNNING = true;
        startHttpService();
    }

    public static String getAddresses() {
        String ret = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (!inetAddress.isAnyLocalAddress() && !inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        ret += "http://" + inetAddress.getHostAddress() + ":" + HttpServerPORT + "\n";
                    }

                }

            }

        } catch (SocketException e) {
            Log.d("getAdresses", "Error getting the IP.", e);
        }
        return ret;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(HttpServerPORT);
            serverSocket.setReuseAddress(true);
            serverSocket.setSoTimeout(500);

            while (RUNNING) {
                try {
                    socket = serverSocket.accept();
                    DefaultHttpServerConnection httpServerConnection = new DefaultHttpServerConnection();
                    try {
                        httpServerConnection.bind(socket, new BasicHttpParams());
                        httpService.handleRequest(httpServerConnection, basicHttpContext);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        httpServerConnection.shutdown();
                    }
                } catch (SocketTimeoutException e) {

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

        HttpRequestHandlerRegistry registry = new HttpRequestHandlerRegistry();
        registry.register("/", new HomeCommandHandler());
        registry.register("/style.css", new StyleCommandHandler());
        registry.register("/logo", new logoHandler());
        registry.register("/get", new getCommandHandler());
        registry.register("/control", new controlCommandHandler());
        registry.register("/export", new exportCommandHandler());
        httpService.setHandlerResolver(registry);
    }

    public synchronized void stopServer() {
        RUNNING = false;
    }

    class HomeCommandHandler implements HttpRequestHandler {

        @Override
        public void handle(HttpRequest request, HttpResponse response,
                           HttpContext httpContext) throws HttpException, IOException {

            HttpEntity httpEntity = new EntityTemplate(
                    new ContentProducer() {

                        public void writeTo(final OutputStream outstream)
                                throws IOException {

                            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                                    outstream, "UTF-8");

                            outputStreamWriter.write(indexHTML);
                            outputStreamWriter.flush();
                        }
                    });
            response.setHeader("Content-Type", "text/html");
            response.setEntity(httpEntity);
        }

    }

    class StyleCommandHandler implements HttpRequestHandler {

        @Override
        public void handle(HttpRequest request, HttpResponse response,
                           HttpContext httpContext) throws HttpException, IOException {

            HttpEntity httpEntity = new EntityTemplate(
                    new ContentProducer() {

                        public void writeTo(final OutputStream outstream)
                                throws IOException {

                            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                                    outstream, "UTF-8");

                            outputStreamWriter.write(styleCSS);
                            outputStreamWriter.flush();
                        }
                    });
            response.setHeader("Content-Type", "text/css");
            response.setEntity(httpEntity);
        }

    }

    class logoHandler implements HttpRequestHandler {

        @Override
        public void handle(HttpRequest request, HttpResponse response,
                           HttpContext httpContext) throws HttpException, IOException {

            HttpEntity httpEntity = new EntityTemplate(
                    new ContentProducer() {

                        public void writeTo(final OutputStream outstream)
                                throws IOException {

                            InputStream is = res.openRawResource(R.raw.phyphox_200);

                            byte[] buffer = new byte[1024];
                            int len = is.read(buffer);
                            while (len != -1) {
                                outstream.write(buffer, 0, len);
                                len = is.read(buffer);
                            }
                        }
                    });
            response.setHeader("Content-Type", "image/png");
            response.setEntity(httpEntity);
        }

    }


    class getCommandHandler implements HttpRequestHandler {

        protected class bufferRequest {
            public String name;
            public Double threshold;
            public String dependent;
        }

        @Override
        public void handle(HttpRequest request, HttpResponse response,
                           HttpContext httpContext) throws HttpException, IOException {

            Uri uri=Uri.parse(request.getRequestLine().getUri());

            //Based on code from getQueryParameterNames, see http://stackoverflow.com/questions/11642494/android-net-uri-getqueryparameternames-alternative
            String query = uri.getEncodedQuery();
            Set<bufferRequest> bufferList = new LinkedHashSet<>();
            int start = 0;
            if (query != null) {
                do {
                    int next = query.indexOf('&', start);
                    int end = (next == -1) ? query.length() : next;
                    int separator = query.indexOf('=', start);
                    if (separator > end || separator == -1) {
                        separator = end;
                    }
                    bufferRequest br = new bufferRequest();
                    br.name = Uri.decode(query.substring(start, separator));
                    br.dependent = "";
                    if (separator == end)
                        br.threshold = Double.NaN; //No special request - the last value should be ok
                    else {
                        String th = query.substring(separator+1, end);
                        if (th.equals("full")) {
                            br.threshold = Double.NEGATIVE_INFINITY; //Get every single value
                        } else {
                            int subsplit = th.indexOf('|');
                            if (subsplit == -1)
                                br.threshold = Double.valueOf(th); //This threshold represents the last value already present in the remote JavaScript data set
                            else {
                                br.threshold = Double.valueOf(th.substring(0, subsplit));
                                br.dependent = th.substring(subsplit+1);
                            }
                        }
                    }

                    bufferList.add(br);
                    start = end + 1;
                } while (start < query.length());
            }

            StringBuilder sb;

            synchronized(dataBuffers) {
                int sizeEstimate = 0;
                for (bufferRequest buffer : bufferList) {
                    if (dataMap.containsKey(buffer.name)) {
                        sizeEstimate += 14 * dataBuffers.get(dataMap.get(buffer.name)).size + 100;
                    }
                }

                sb = new StringBuilder(sizeEstimate);
                boolean firstBuffer = true;
                sb.append("{\"buffer\":{\n");
                DecimalFormat format = (DecimalFormat) NumberFormat.getInstance(Locale.ENGLISH);
                format.applyPattern("0.#######E0");
                for (bufferRequest buffer : bufferList) {
                    if (dataMap.containsKey(buffer.name)) {
                        if (firstBuffer)
                            firstBuffer = false;
                        else
                            sb.append(",\n");
                        dataBuffer db = dataBuffers.get(dataMap.get(buffer.name));
                        dataBuffer db_dependent;
                        if (buffer.dependent.equals(""))
                            db_dependent = db;
                        else
                            db_dependent = dataBuffers.get(dataMap.get(buffer.dependent));
                        sb.append("\"");
                        sb.append(db.name);
                        sb.append("\":{\"size\":");
                        sb.append(db.size);
                        sb.append(",\"updateMode\":\"");
                        if (Double.isNaN(buffer.threshold))
                            sb.append("single");
                        else if (Double.isInfinite(buffer.threshold))
                            sb.append("full");
                        else
                            sb.append("partial");
                        sb.append("\", \"buffer\":[");

                        boolean firstValue = true;
                        Iterator i = db.getIterator();
                        Iterator i_dependent = db_dependent.getIterator();
                        Double v = Double.NaN;
                        while (i.hasNext() && i_dependent.hasNext()) {
                            v = (Double)i.next();
                            Double v_dep = (Double)i_dependent.next();
                            if (Double.isNaN(buffer.threshold) || v_dep <= buffer.threshold)
                                continue;
                            if (firstValue)
                                firstValue = false;
                            else
                                sb.append(",");
                            sb.append(format.format(v));
                        }
                        if (Double.isNaN(buffer.threshold))
                            sb.append(format.format(v));

                        sb.append("]}");
                    }
                }
                sb.append("\n},\n\"status\":{\n");
                sb.append("\"measuring\":");
                if (callActivity.measuring)
                    sb.append("true");
                else
                    sb.append("false");
                sb.append(", \"timedRun\":");
                if (callActivity.timedRun)
                    sb.append("true");
                else
                    sb.append("false");
                sb.append(", \"countDown\":");
                sb.append(String.valueOf(callActivity.millisUntilFinished));
                sb.append("\n}\n}\n");
            }

            final String result = sb.toString();

            HttpEntity httpEntity = new EntityTemplate(
                    new ContentProducer() {

                        public void writeTo(final OutputStream outstream)
                                throws IOException {

                            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                                    outstream, "UTF-8");
                            outputStreamWriter.write(result);
                            outputStreamWriter.flush();
                        }
                    });
            response.setHeader("Content-Type", "application/json");
            response.setEntity(httpEntity);
        }

    }

    class controlCommandHandler implements HttpRequestHandler {

        @Override
        public void handle(HttpRequest request, HttpResponse response,
                           HttpContext httpContext) throws HttpException, IOException {

            Uri uri = Uri.parse(request.getRequestLine().getUri());
            String cmd = uri.getQueryParameter("cmd");

            final String result;

            if (cmd != null) {
                if (cmd.equals("start")) {
                    callActivity.remoteStartMeasurement();
                    result = "{\"result\" = true}";
                } else if (cmd.equals("stop")) {
                    callActivity.remoteStopMeasurement();
                    result = "{\"result\" = true}";
                } else
                    result = "{\"result\" = false}";
            } else
                result = "{\"result\" = false}";

            HttpEntity httpEntity = new EntityTemplate(
                    new ContentProducer() {

                        public void writeTo(final OutputStream outstream)
                                throws IOException {

                            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                                    outstream, "UTF-8");
                            outputStreamWriter.write(result);
                            outputStreamWriter.flush();
                        }
                    });
            response.setHeader("Content-Type", "application/json");
            response.setEntity(httpEntity);
        }

    }

    class exportCommandHandler implements HttpRequestHandler {

        @Override
        public void handle(HttpRequest request, HttpResponse response,
                           HttpContext httpContext) throws HttpException, IOException {

            Uri uri = Uri.parse(request.getRequestLine().getUri());
            String format = uri.getQueryParameter("format");

            HttpEntity httpEntity;

            int formatInt = Integer.parseInt(format);
            if (formatInt < 0 || formatInt >= callActivity.exporter.exportFormats.length) {
                final String result = "{\"error\" = \"Format out of range.\"}";
                httpEntity = new EntityTemplate(
                        new ContentProducer() {

                            public void writeTo(final OutputStream outstream)
                                    throws IOException {

                                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                                        outstream, "UTF-8");
                                outputStreamWriter.write(result);
                                outputStreamWriter.flush();
                            }
                        });
                response.setHeader("Content-Type", "application/json");
                response.setEntity(httpEntity);
            } else {
                String type = callActivity.exporter.exportFormats[formatInt].getType();
                ArrayList<Integer> selectedItems = new ArrayList<>();
                for (int i = 0; i < callActivity.exporter.exportSets.size(); i++) {
                    if (uri.getQueryParameter("set"+i) != null) {
                        selectedItems.add(i);
                    }
                }
                final File exportFile = callActivity.exporter.exportDirect(callActivity.dataBuffers, callActivity.dataMap, selectedItems, callActivity.exporter.exportFormats[formatInt]);
                httpEntity = new EntityTemplate(
                        new ContentProducer() {
                            public void writeTo(final OutputStream outstream)
                                throws IOException {


                                InputStream is = new FileInputStream(exportFile);
                                byte[] buffer = new byte[1024];
                                int len = is.read(buffer);
                                while (len != -1) {
                                    outstream.write(buffer, 0, len);
                                    len = is.read(buffer);
                                }
                            }
                        });
                response.setHeader("Content-Type", type);
            }

            response.setEntity(httpEntity);

        }

    }

}
