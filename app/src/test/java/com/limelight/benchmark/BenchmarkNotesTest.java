package com.limelight.benchmark;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Run notes are stored in English and shown in the device language. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, qualifiers = "fr")
public class BenchmarkNotesTest {
    private final Context ctx = ApplicationProvider.getApplicationContext();

    @Test
    public void knownNotesAreTranslated() {
        assertEquals("Aucune image n'a été affichée.", BenchmarkNotes.localize(ctx, "No frames were shown."));
        assertEquals("Le passage a été interrompu.", BenchmarkNotes.localize(ctx, "The run was cut short."));
        assertEquals("Le pacing VRR n'est pas disponible", BenchmarkNotes.localize(ctx, "VRR is not available on this display"));
        assertEquals("AV1 n'est pas disponible : l'appareil est revenu à HEVC",
                BenchmarkNotes.localize(ctx, "AV1 is not available: the device fell back to HEVC"));
        assertEquals("La connexion s'est coupée (erreur -1).", BenchmarkNotes.localize(ctx, "The connection ended (error -1)."));
        // The desktop's forms (Benchmark::Notes): its reports open here too
        assertEquals("Le stream n'a pas démarré.", BenchmarkNotes.localize(ctx, "The stream did not start."));
        assertEquals("Le stream n'a pas pu démarrer.", BenchmarkNotes.localize(ctx, "The stream could not start."));
        assertEquals("Le débit a été plafonné à 12.5 Mbit/s.", BenchmarkNotes.localize(ctx, "The bitrate was capped at 12.5 Mbps."));
    }

    @Test
    public void unknownNotesStayAsTheyAre() {
        assertEquals("", BenchmarkNotes.localize(ctx, ""));
        assertEquals("Le débit a été plafonné à 50 Mbit/s.", BenchmarkNotes.localize(ctx, "Le débit a été plafonné à 50 Mbit/s."));
    }
}
