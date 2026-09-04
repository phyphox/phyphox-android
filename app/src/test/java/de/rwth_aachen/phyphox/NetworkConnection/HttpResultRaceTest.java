package de.rwth_aachen.phyphox.NetworkConnection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

//Each HTTP request runs on a thread of its own, and a callback does not receive the response:
//it reads it back through the service's getResults(). So two responses finishing close
//together could store A, store B, and have both callbacks read B - one poll parked twice and
//one lost, which the t1 network fixtures saw as a duplicate poll counter (2026-09-04).
//
//This pins the order: the first callback is held while the second response arrives, and it
//must still read its own body. The hold is the window the race needs; the service's lock is
//what keeps the second request out of it.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class HttpResultRaceTest {

    private ServerSocket server;
    private String base;
    //released by the test once the first callback is inside requestFinished
    private final CountDownLatch secondMayRespond = new CountDownLatch(1);

    //A stub rather than com.sun.net.httpserver, which the Android compile classpath does not
    //have. HttpURLConnection needs nothing more than a status line and a Content-Length.
    @Before
    public void serve() throws Exception {
        server = new ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress());
        base = "http://127.0.0.1:" + server.getLocalPort() + "/?v=";
        Thread accept = new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    Socket socket = server.accept();
                    new Thread(() -> answer(socket)).start();
                } catch (Exception e) {
                    return;         //closed by stop()
                }
            }
        });
        accept.setDaemon(true);
        accept.start();
    }

    private void answer(Socket socket) {
        try (Socket s = socket) {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            String requestLine = in.readLine();
            //The body is the request's own v, so a callback can tell whose response it read.
            String v = requestLine.replaceAll("^.*v=(\\d+).*$", "$1");
            for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
                //headers, not needed
            }
            if (v.equals("2")) {
                secondMayRespond.await(10, TimeUnit.SECONDS);
            }
            byte[] body = v.getBytes(StandardCharsets.UTF_8);
            OutputStream os = s.getOutputStream();
            os.write(("HTTP/1.1 200 OK\r\nContent-Length: " + body.length
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            os.write(body);
            os.flush();
        } catch (Exception ignored) {
        }
    }

    @After
    public void stop() throws Exception {
        server.close();
    }

    @Test
    public void everyCallbackReadsItsOwnResponse() throws Exception {
        final NetworkService.HttpGet service = new NetworkService.HttpGet();
        final CountDownLatch firstInCallback = new CountDownLatch(1);
        final CountDownLatch firstMayRead = new CountDownLatch(1);
        final CountDownLatch firstDone = new CountDownLatch(1);
        final CountDownLatch secondDone = new CountDownLatch(1);
        final AtomicReference<String> first = new AtomicReference<>();
        final AtomicReference<String> second = new AtomicReference<>();

        NetworkService.RequestCallback cb1 = result -> {
            firstInCallback.countDown();
            try {
                firstMayRead.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            first.set(new String(service.getResults()[0], StandardCharsets.UTF_8));
            firstDone.countDown();
        };
        NetworkService.RequestCallback cb2 = result -> {
            second.set(new String(service.getResults()[0], StandardCharsets.UTF_8));
            secondDone.countDown();
        };

        //execute copies the address synchronously before its thread starts, so two requests
        //can be given two addresses this way
        service.connect(base + "1");
        service.execute(new HashMap<>(), new ArrayList<>(Collections.singletonList(cb1)));
        service.connect(base + "2");
        service.execute(new HashMap<>(), new ArrayList<>(Collections.singletonList(cb2)));

        assertTrue("first response never arrived", firstInCallback.await(10, TimeUnit.SECONDS));
        //The first callback is now inside requestFinished. Let the second response through
        //and give its thread time to reach the point where it would overwrite the result.
        secondMayRespond.countDown();
        Thread.sleep(500);
        firstMayRead.countDown();
        assertTrue("second callback never ran", secondDone.await(10, TimeUnit.SECONDS));
        assertTrue("first callback never finished", firstDone.await(10, TimeUnit.SECONDS));

        assertEquals("the first callback read the second request's response", "1", first.get());
        assertEquals("2", second.get());
    }
}
