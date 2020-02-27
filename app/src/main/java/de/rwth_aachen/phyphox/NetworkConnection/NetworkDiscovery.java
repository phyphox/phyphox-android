package de.rwth_aachen.phyphox.NetworkConnection;

import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class NetworkDiscovery {

    interface DiscoveryCallback {
        void newItem(String name, String address);
    }

    public static abstract class Discovery {
        public abstract void startDiscovery(DiscoveryCallback resultCallback);

        public abstract void stopDiscovery();
    }

    public static class Http extends Discovery {
        String address;
        HttpTask task = null;

        public Http(String address) {
            this.address = address;
        }

        private class HttpTask extends AsyncTask<DiscoveryCallback, Void, String> {
            DiscoveryCallback resultCallback;
            HttpURLConnection connection;

            public void cancel() {
                cancel(true);
                if (connection != null)
                    connection.disconnect();
            }

            @Override
            protected String doInBackground(DiscoveryCallback... params) {
                resultCallback = params[0];

                try {
                    URL url = new URL(address);

                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    if (200 > responseCode || 300 <= responseCode) {
                        connection.disconnect();
                        return null;
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (isCancelled()) {
                            reader.close();
                            return null;
                        }
                        stringBuilder.append(line);
                    }

                    reader.close();

                    return stringBuilder.toString();


                } catch (MalformedURLException e) {
                    return null;
                } catch (IOException e) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (result != null)
                    resultCallback.newItem(result.substring(0, Math.max(50, result.length())), address);
            }
        }

        public void startDiscovery(DiscoveryCallback resultCallback) {
            new HttpTask().execute(resultCallback);
        }

        public void stopDiscovery() {
            if (task != null) {
                task.cancel();
            }
            task = null;
        }

    }
}
