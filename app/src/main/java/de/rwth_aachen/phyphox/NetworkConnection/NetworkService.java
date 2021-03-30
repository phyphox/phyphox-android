package de.rwth_aachen.phyphox.NetworkConnection;

import android.net.Uri;
import android.os.AsyncTask;

import org.apache.poi.util.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.rwth_aachen.phyphox.ExperimentTimeReference;

public class NetworkService {

    public enum ResultEnum {
        success, timeout, noConnection, conversionError, genericError
    }

    public static class ServiceResult {
        ResultEnum result;
        String message;
        public ServiceResult(ResultEnum result, String message) {
            this.result = result;
            this.message = message;
        }
    }

    public interface RequestCallback {
        void requestFinished(ServiceResult result);
    }

    interface HttpTaskCallback {
        void requestFinished(HttpTaskResult result);
    }

    public static abstract class Service {
        public abstract void connect(String address);
        public abstract void disconnect();
        public abstract void execute(Map<String, NetworkConnection.NetworkSendableData> send, List<RequestCallback> requestCallbacks);
        public abstract byte[][] getResults();
    }

    static class HttpTaskParameters {
        Map<String, NetworkConnection.NetworkSendableData> send;
        List<RequestCallback> requestCallbacks;
        HttpTaskCallback taskCallback;
        String address;
        boolean usePost;
    }

    static class HttpTaskResult {
        ServiceResult result;
        byte[] data;
        HttpTaskResult(ServiceResult result, byte[] data) {
            this.result = result;
            this.data = data;
        }
        List<RequestCallback> requestCallbacks;
    }

    private static class HttpTask extends AsyncTask<HttpTaskParameters, Void, HttpTaskResult> {
        HttpTaskParameters parameters;
        HttpURLConnection connection;

        public void cancel() {
            cancel(true);
            if (connection != null)
                connection.disconnect();
        }

        @Override
        protected HttpTaskResult doInBackground(HttpTaskParameters... params) {
            DecimalFormat longformat = (DecimalFormat) NumberFormat.getInstance(Locale.ENGLISH);
            longformat.applyPattern("############0.000");
            longformat.setGroupingUsed(false);

            parameters = params[0];

            try {
                Uri uri = Uri.parse(parameters.address);

                if (!parameters.usePost) {
                    for (Map.Entry<String, NetworkConnection.NetworkSendableData> item : parameters.send.entrySet()) {
                        String value = "";
                        if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.METADATA)
                            value = item.getValue().metadata.get(parameters.address);
                        else if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.BUFFER)
                            value = String.valueOf(item.getValue().buffer.value);
                        else if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.TIME)
                            value = String.valueOf(longformat.format(System.currentTimeMillis()/1000.0));
                        uri = uri.buildUpon().appendQueryParameter(item.getKey(), value).build();
                    }
                }

                URL url = new URL(uri.toString());

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(parameters.usePost ? "POST" : "GET");

                if (parameters.usePost) {
                    connection.setRequestProperty("Content-Type", "application/json; utf-8");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setDoOutput(true);

                    JSONObject json = new JSONObject();
                    for (Map.Entry<String, NetworkConnection.NetworkSendableData> item : parameters.send.entrySet()) {
                        if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.METADATA)
                            json.put(item.getKey(), item.getValue().metadata.get(parameters.address));
                        else if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.BUFFER) {
                            String datatype = item.getValue().additionalAttributes != null ? item.getValue().additionalAttributes.get("datatype") : null;
                            if (datatype != null && datatype.equals("number")) {
                                double v = item.getValue().buffer.value;
                                if (Double.isNaN(v) || Double.isInfinite(v))
                                    json.put(item.getKey(), null);
                                else
                                    json.put(item.getKey(), v);
                            } else {
                                JSONArray jsonArray = new JSONArray();
                                for (double v : item.getValue().buffer.getArray()) {
                                    if (Double.isNaN(v) || Double.isInfinite(v))
                                        jsonArray.put(null);
                                    else
                                        jsonArray.put(v);;
                                }
                                json.put(item.getKey(), jsonArray);
                            }
                        } else if (item.getValue().type == NetworkConnection.NetworkSendableData.DataType.TIME) {
                            JSONObject timeInfo = new JSONObject();
                            timeInfo.put("now", System.currentTimeMillis()/1000.0);
                            JSONArray events = new JSONArray();
                            for (ExperimentTimeReference.TimeMapping timeMapping : item.getValue().timeReference.timeMappings) {
                                JSONObject eventJson = new JSONObject();
                                eventJson.put("event", timeMapping.event.name());
                                eventJson.put("experimentTime", timeMapping.experimentTime);
                                eventJson.put("systemTime", timeMapping.systemTime/1000.);
                                events.put(eventJson);
                            }
                            timeInfo.put("events", events);
                            json.put(item.getKey(), timeInfo);
                        }
                    }

                    OutputStream os = connection.getOutputStream();
                    byte[] input = json.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                    os.close();
                }


                int responseCode = connection.getResponseCode();
                if (200 > responseCode || 300 <= responseCode) {
                    connection.disconnect();
                    return new HttpTaskResult(new ServiceResult(ResultEnum.genericError,"Http response code: " + responseCode), null);
                }

                InputStream is = connection.getInputStream();
                byte[] data = IOUtils.toByteArray(is);
                is.close();
                connection.disconnect();

                return new HttpTaskResult(new ServiceResult(ResultEnum.success,null), data);



            } catch (MalformedURLException e) {
                return new HttpTaskResult(new ServiceResult(ResultEnum.genericError,"No valid URL."), null);
            } catch (IOException e) {
                return new HttpTaskResult(new ServiceResult(ResultEnum.genericError,"IOException."), null);
            } catch (JSONException e) {
                return new HttpTaskResult(new ServiceResult(ResultEnum.genericError,"Could not build JSON."), null);
            }
        }

        @Override
        protected void onPostExecute(HttpTaskResult result) {
            result.requestCallbacks = parameters.requestCallbacks;
            parameters.taskCallback.requestFinished(result);
        }
    }

    public static class HttpGet extends Service implements HttpTaskCallback {
        String address = null;
        byte[] data = null;

        public void connect(String address) {
            this.address = address;
        }

        public void disconnect() {
            address = null;
        }

        public void execute(Map<String, NetworkConnection.NetworkSendableData> send, List<RequestCallback> requestCallbacks) {
            if (address == null) {
                HttpTaskResult result = new HttpTaskResult(new ServiceResult(ResultEnum.noConnection, null), null);
                for (RequestCallback callback : requestCallbacks) {
                    callback.requestFinished(result.result);
                }
                return;
            }

            HttpTaskParameters parameters = new HttpTaskParameters();
            parameters.requestCallbacks = requestCallbacks;
            parameters.taskCallback = this;
            parameters.address = address;
            parameters.send = send;
            parameters.usePost = false;
            new HttpTask().execute(parameters);
        }

        public byte[][] getResults() {
            byte[][] ret = new byte[1][];
            ret[0] = data;
            return ret;
        }

        public void requestFinished(HttpTaskResult result) {
            data = result.data;
            for (RequestCallback callback : result.requestCallbacks) {
                callback.requestFinished(result.result);
            }
        }
    }

    public static class HttpPost extends Service implements HttpTaskCallback {
        String address = null;
        byte[] data = null;

        public void connect(String address) {
            this.address = address;
        }

        public void disconnect() {
            address = null;
        }

        public void execute(Map<String, NetworkConnection.NetworkSendableData> send, List<RequestCallback> requestCallbacks) {
            if (address == null) {
                HttpTaskResult result = new HttpTaskResult(new ServiceResult(ResultEnum.noConnection, null), null);
                for (RequestCallback callback : requestCallbacks) {
                    callback.requestFinished(result.result);
                }
                return;
            }

            HttpTaskParameters parameters = new HttpTaskParameters();
            parameters.requestCallbacks = requestCallbacks;
            parameters.taskCallback = this;
            parameters.address = address;
            parameters.send = send;
            parameters.usePost = true;
            new HttpTask().execute(parameters);
        }

        public byte[][] getResults() {
            byte[][] ret = new byte[1][];
            ret[0] = data;
            return ret;
        }

        public void requestFinished(HttpTaskResult result) {
            data = result.data;
            for (RequestCallback callback : result.requestCallbacks) {
                callback.requestFinished(result.result);
            }
        }
    }
}
