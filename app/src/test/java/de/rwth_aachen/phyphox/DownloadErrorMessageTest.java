package de.rwth_aachen.phyphox;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNetworkCapabilities;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import de.rwth_aachen.phyphox.helper.Helper;

//A failed phyphox:// download (Android-TODO 2026-11-12, in step with iOS): a plain message for offline, an unreachable
//host and anything else, and no pointless http attempt where http cannot help
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DownloadErrorMessageTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    private String message(Exception e, int status, String host, int state) {
        return PhyphoxFile.downloadErrorMessage(e, status, host, state, context);
    }

    @Test
    public void offlineWinsOverTheException() {
        String offline = context.getString(R.string.downloadErrorOffline);
        assertThat(message(new UnknownHostException("Unable to resolve host"), -1, "phyphox.org", Helper.NETWORK_NONE)).isEqualTo(offline);
        assertThat(message(new IOException("x"), -1, "192.168.0.5", Helper.NETWORK_NONE)).isEqualTo(offline);
        //Captive portal or a LAN without internet: offline for a public host, unreachable for a local one
        assertThat(message(new javax.net.ssl.SSLHandshakeException("x"), -1, "phyphox.org", Helper.NETWORK_LOCAL)).isEqualTo(offline);
        assertThat(message(new ConnectException("x"), -1, "192.168.0.5", Helper.NETWORK_LOCAL))
                .isEqualTo(context.getString(R.string.downloadErrorUnreachable) + " 192.168.0.5");
    }

    @Test
    public void connectionFailuresNameTheHost() {
        String unreachable = context.getString(R.string.downloadErrorUnreachable) + " example.org";
        assertThat(message(new UnknownHostException("x"), -1, "example.org", Helper.NETWORK_INTERNET)).isEqualTo(unreachable);
        assertThat(message(new ConnectException("x"), -1, "example.org", Helper.NETWORK_INTERNET)).isEqualTo(unreachable);
        assertThat(message(new SocketTimeoutException("x"), -1, "example.org", Helper.NETWORK_INTERNET)).isEqualTo(unreachable);
    }

    @Test
    public void otherFailuresCarryTheDetail() {
        String general = context.getString(R.string.downloadErrorGeneral);
        assertThat(message(new java.io.FileNotFoundException("https://example.org/x"), 404, "example.org", Helper.NETWORK_INTERNET))
                .isEqualTo(general + "\n\nHTTP 404");
        //A status answer proves a connection, so it is never reported as offline
        assertThat(message(new java.io.FileNotFoundException("x"), 404, "example.org", Helper.NETWORK_LOCAL)).startsWith(general);
        assertThat(message(new javax.net.ssl.SSLHandshakeException("bad cert"), -1, "example.org", Helper.NETWORK_INTERNET))
                .isEqualTo(general + "\n\njavax.net.ssl.SSLHandshakeException: bad cert");
    }

    @Test
    public void httpFallbackOnlyWhereItCanHelp() {
        assertThat(PhyphoxFile.httpFallbackCanHelp(new UnknownHostException("x"), -1, Helper.NETWORK_INTERNET)).isFalse();
        assertThat(PhyphoxFile.httpFallbackCanHelp(new ConnectException("x"), -1, Helper.NETWORK_NONE)).isFalse();
        assertThat(PhyphoxFile.httpFallbackCanHelp(new ConnectException("x"), -1, Helper.NETWORK_INTERNET)).isTrue();
        assertThat(PhyphoxFile.httpFallbackCanHelp(new javax.net.ssl.SSLHandshakeException("x"), -1, Helper.NETWORK_INTERNET)).isTrue();
        //The server answered over https (a deleted share link): http cannot fix that
        assertThat(PhyphoxFile.httpFallbackCanHelp(new java.io.FileNotFoundException("x"), 404, Helper.NETWORK_INTERNET)).isFalse();
        //A classroom server on a LAN without internet may only speak http
        assertThat(PhyphoxFile.httpFallbackCanHelp(new ConnectException("x"), -1, Helper.NETWORK_LOCAL)).isTrue();
    }

    @Test
    public void networkStateFollowsTheDefaultNetwork() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        assertThat(network).isNotNull();

        NetworkCapabilities caps = ShadowNetworkCapabilities.newInstance();
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        shadowOf(cm).setNetworkCapabilities(network, caps);
        assertThat(Helper.networkState(context)).isEqualTo(Helper.NETWORK_LOCAL);

        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        shadowOf(cm).setNetworkCapabilities(network, caps);
        assertThat(Helper.networkState(context)).isEqualTo(Helper.NETWORK_INTERNET);

        shadowOf(cm).setDefaultNetworkActive(false);
        shadowOf(cm).setActiveNetworkInfo(null);
        assertThat(Helper.networkState(context)).isEqualTo(Helper.NETWORK_NONE);
    }
}
