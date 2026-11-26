package com.limelight.utils;

import android.content.res.Resources;

import com.limelight.R;
import com.limelight.nvstream.http.HostHttpResponseException;

import java.io.FileNotFoundException;
import java.net.UnknownHostException;

/**
 * A pairing that failed on an exception, as the sentence Mes PC shows: the desktop's
 * (ComputerManager::pairingErrorText), never the exception's English text. What follows « perdue
 * pendant l'appairage » is {@link HostErrorText}'s short reason, as the desktop's hostErrorDetail.
 */
public final class PairingErrorText {
    private PairingErrorText() {}

    public static String forException(Resources r, String name, Exception e) {
        if (isExpiredLinkCode(e)) {
            return r.getString(R.string.v2_pair_link_expired, name);
        }
        if (e instanceof UnknownHostException) {
            return r.getString(R.string.v2_pair_unknown_host, name);
        }
        if (e instanceof FileNotFoundException) {
            // What NvHTTP throws for a 404
            return r.getString(R.string.v2_pair_not_found, name);
        }
        return r.getString(R.string.v2_pair_network, name, HostErrorText.detail(r, e));
    }

    /**
     * LavApollo (like Apollo) answers a pairing link whose code expired (3 minutes) or was used
     * already with {@code status_code="503" status_message="OTP auth not available."}: the
     * desktop's test, 503 and « OTP » in the message.
     */
    static boolean isExpiredLinkCode(Exception e) {
        if (!(e instanceof HostHttpResponseException)) {
            return false;
        }
        HostHttpResponseException h = (HostHttpResponseException) e;
        String message = h.getErrorMessage();
        return h.getErrorCode() == 503 && message != null && message.toUpperCase(java.util.Locale.ROOT).contains("OTP");
    }
}
