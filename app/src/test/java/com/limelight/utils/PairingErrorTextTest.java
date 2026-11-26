package com.limelight.utils;

import static org.junit.Assert.assertEquals;

import android.content.res.Resources;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.nvstream.http.HostHttpResponseException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * A pairing that fails on an exception: the desktop's sentences (ComputerManager::pairingErrorText),
 * never the exception's English text. LavApollo answers an expired or already used link code with
 * {@code status_code="503" status_message="OTP auth not available."}, which NvHTTP throws.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, qualifiers = "fr")
public class PairingErrorTextTest {
    private final Resources r = ApplicationProvider.getApplicationContext().getResources();

    @Test
    public void expiredLinkCodeHasItsOwnSentence() {
        assertEquals("Le code du lien n'est plus valable sur Tour : il ne sert qu'une fois et expire au bout "
                        + "de 3 minutes. Crée un nouveau lien dans LavApollo.",
                PairingErrorText.forException(r, "Tour", new HostHttpResponseException(503, "OTP auth not available.")));
    }

    @Test
    public void anotherRefusalSaysItsCode() {
        // A 503 that is not about the link code stays a lost connection, as on the desktop
        assertEquals("La connexion avec Tour a été perdue pendant l'appairage (erreur 503).",
                PairingErrorText.forException(r, "Tour", new HostHttpResponseException(503, "Service Unavailable")));
        assertEquals("La connexion avec Tour a été perdue pendant l'appairage (erreur 500).",
                PairingErrorText.forException(r, "Tour", new HostHttpResponseException(500, "Internal error")));
    }

    @Test
    public void networkFailuresSayTheShortReason() {
        assertEquals("La connexion avec Tour a été perdue pendant l'appairage (connexion refusée).",
                PairingErrorText.forException(r, "Tour", new ConnectException("failed to connect to /10.0.0.2")));
        assertEquals("La connexion avec Tour a été perdue pendant l'appairage (pas de réponse à temps).",
                PairingErrorText.forException(r, "Tour", new SocketTimeoutException("timeout")));
        assertEquals("La connexion avec Tour a été perdue pendant l'appairage (erreur réseau).",
                PairingErrorText.forException(r, "Tour", new XmlPullParserException("unexpected end")));
        assertEquals("La connexion avec Tour a été perdue pendant l'appairage (erreur réseau).",
                PairingErrorText.forException(r, "Tour", new IOException("Host PC returned error")));
    }

    @Test
    public void unknownHostAndNotFoundKeepTheirSentences() {
        assertEquals("L'adresse de Tour est introuvable.",
                PairingErrorText.forException(r, "Tour", new UnknownHostException("tour.local")));
        assertEquals("Tour n'a pas répondu à la demande d'appairage (erreur 404). Vérifie que c'est bien LavApollo qui tourne.",
                PairingErrorText.forException(r, "Tour", new FileNotFoundException("http://10.0.0.2:47989/pair")));
    }
}
