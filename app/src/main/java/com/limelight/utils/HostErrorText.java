package com.limelight.utils;

import android.content.res.Resources;

import com.limelight.R;
import com.limelight.nvstream.http.HostHttpResponseException;

import java.io.FileNotFoundException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

/**
 * A host's refusal or a lost connection, as the end of a sentence (« erreur 500 », « connexion
 * refusée »): translated, never the exception's English message. The desktop says the same
 * (ComputerManager::hostErrorDetail), and both build the same sentences from it: the quit that
 * failed (quit_app_failed) and the clipboard that could not be sent or fetched.
 */
public final class HostErrorText {
    private HostErrorText() {}

    public static String detail(Resources r, Exception e) {
        if (e instanceof HostHttpResponseException) {
            return r.getString(R.string.host_error_http, ((HostHttpResponseException) e).getErrorCode());
        }
        if (e instanceof FileNotFoundException) {
            // What NvHTTP throws for a 404
            return r.getString(R.string.host_error_http, 404);
        }
        if (e instanceof UnknownHostException) {
            return r.getString(R.string.host_error_unknown_host);
        }
        if (e instanceof InterruptedIOException) {
            // SocketTimeoutException included
            return r.getString(R.string.host_error_timeout);
        }
        if (e instanceof ConnectException) {
            return r.getString(R.string.host_error_refused);
        }
        if (e instanceof SSLException) {
            return r.getString(R.string.host_error_tls);
        }
        if (e instanceof SocketException) {
            return r.getString(R.string.host_error_closed);
        }
        return r.getString(R.string.host_error_network);
    }

    /** « Impossible de quitter Bureau sur le PC (connexion refusée). », or 599's own sentence */
    public static String quitFailed(Resources r, String appName, Exception e) {
        if (e instanceof HostHttpResponseException && ((HostHttpResponseException) e).getErrorCode() == 599) {
            return r.getString(R.string.applist_quit_not_started_here);
        }
        return r.getString(R.string.quit_app_failed, appName, detail(r, e));
    }
}
