package com.limelight;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.limelight.utils.UpdateDownloader;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests the rule the in-app updater applies before handing an APK to the system installer.
 * Pure logic: no PackageManager, no Robolectric.
 */
public class UpdateVerificationTest {

    private static final String PKG = "com.limelight.lav";
    private static final Set<String> RELEASE_KEY = set("release");
    private static final Set<String> OTHER_KEY = set("attacker");

    private static Set<String> set(String... digests) {
        return new HashSet<>(Arrays.asList(digests));
    }

    private static void assertAccepted(String pkg, long version, Set<String> certs) {
        try {
            UpdateDownloader.checkUpdateCandidate(PKG, 74, RELEASE_KEY, pkg, version, certs);
        } catch (UpdateDownloader.VerificationException e) {
            fail("expected the update to be accepted, got: " + e.getMessage());
        }
    }

    private static void assertRejected(String pkg, long version, Set<String> certs, String reasonFragment) {
        try {
            UpdateDownloader.checkUpdateCandidate(PKG, 74, RELEASE_KEY, pkg, version, certs);
            fail("expected the update to be rejected");
        } catch (UpdateDownloader.VerificationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(reasonFragment));
        }
    }

    @Test
    public void acceptsNewerBuildWithSameKey() {
        assertAccepted(PKG, 75, RELEASE_KEY);
    }

    @Test
    public void acceptsRotatedKeyWhoseHistoryContainsTheInstalledOne() {
        assertAccepted(PKG, 75, set("rotated", "release"));
    }

    @Test
    public void rejectsWrongPackage() {
        assertRejected("com.limelight.lavdebug", 75, RELEASE_KEY, "is for");
    }

    @Test
    public void rejectsSameOrOlderVersion() {
        assertRejected(PKG, 74, RELEASE_KEY, "not newer");
        assertRejected(PKG, 60, RELEASE_KEY, "not newer");
    }

    @Test
    public void rejectsDifferentSigner() {
        assertRejected(PKG, 75, OTHER_KEY, "different key");
    }

    @Test
    public void rejectsMissingCertificates() {
        assertRejected(PKG, 75, Collections.<String>emptySet(), "different key");
        try {
            UpdateDownloader.checkUpdateCandidate(PKG, 74, Collections.<String>emptySet(), PKG, 75, RELEASE_KEY);
            fail("an unreadable installed signature must not accept anything");
        } catch (UpdateDownloader.VerificationException expected) {
        }
    }
}
