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
    public void codecsAndPacingAreShownByName() {
        // Every form a note can hold an id in (LavApollo/docs/benchmark.md, « Notes »), as the
        // desktop shows them (Benchmark::Notes::localized: codecLabel, pacingLabel)
        assertEquals("AV1 n'est pas disponible : l'appareil est revenu à HEVC",
                BenchmarkNotes.localize(ctx, "av1 is not available: the device fell back to hevc"));
        assertEquals("PyroWave n'est pas disponible : l'appareil est revenu à H.264",
                BenchmarkNotes.localize(ctx, "pyrowave is not available: the client fell back to h264"));
        assertEquals("Passé en HEVC, rythme par défaut à la place.",
                BenchmarkNotes.localize(ctx, "Ran as hevc, default instead."));
        assertEquals("Passé en H.264, latence ultra faible rythmée à la place.",
                BenchmarkNotes.localize(ctx, "Ran as h264, paced-ull instead."));
        assertEquals("Passé en AV1, VRR, équilibré à la place.",
                BenchmarkNotes.localize(ctx, "Ran as av1, vrr-balanced instead."));
        // The desktop's pacing ids, in a report made there
        assertEquals("Passé en HEVC, V-Sync + frame pacing à la place.",
                BenchmarkNotes.localize(ctx, "Ran as hevc, vsync-paced instead."));
        assertEquals("Passé en HEVC, V-Sync désactivée à la place.",
                BenchmarkNotes.localize(ctx, "Ran as hevc, vsync-off instead."));
        // An id neither client knows is shown as it is
        assertEquals("Passé en vp9, tearing à la place.", BenchmarkNotes.localize(ctx, "Ran as vp9, tearing instead."));
    }

    @Test
    public void labelsAreTheDesktopOnes() {
        assertEquals("VRR, faible latence", BenchmarkNotes.pacingLabel(ctx, "vrr-low-latency"));
        assertEquals("VRR, équilibré", BenchmarkNotes.pacingLabel(ctx, "vrr-balanced"));
        assertEquals("VRR, fluide", BenchmarkNotes.pacingLabel(ctx, "vrr-smooth"));
        assertEquals("H.264", BenchmarkNotes.codecLabel("h264"));
        assertEquals("PyroWave", BenchmarkNotes.codecLabel("pyrowave"));
    }

    @Test
    public void unknownNotesStayAsTheyAre() {
        assertEquals("", BenchmarkNotes.localize(ctx, ""));
        assertEquals("Le débit a été plafonné à 50 Mbit/s.", BenchmarkNotes.localize(ctx, "Le débit a été plafonné à 50 Mbit/s."));
    }
}
