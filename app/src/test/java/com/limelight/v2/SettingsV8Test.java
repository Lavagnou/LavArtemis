package com.limelight.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.limelight.v2.settings.SettingDef;
import com.limelight.v2.settings.SettingsCatalog;
import com.limelight.v2.settings.SettingsEnv;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/** Réglages as the prototype V8: hidden-rows count, EXPERT pages, ● follows the profile, LB / RB pages. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
public class SettingsV8Test {
    private final SettingsEnv env = SettingsEnv.forTests();

    @Test
    public void hiddenCountIsTheExpertRowsOfThePage() {
        int all = SettingsCatalog.visibleRows(SettingsCatalog.VIDEO, env, true).size();
        int normal = SettingsCatalog.visibleRows(SettingsCatalog.VIDEO, env, false).size();
        assertTrue("Vidéo has expert rows", all > normal);
        assertEquals(all - normal, SettingsCatalog.hiddenCount(SettingsCatalog.VIDEO, env, false));
        // Expert mode on: nothing is hidden
        assertEquals(0, SettingsCatalog.hiddenCount(SettingsCatalog.VIDEO, env, true));
        // Every counted row is an expert row the device offers
        int expert = 0;
        for (SettingDef d : SettingsCatalog.visibleRows(SettingsCatalog.VIDEO, env, true)) {
            if (d.expert) {
                expert++;
            }
        }
        assertEquals(expert, SettingsCatalog.hiddenCount(SettingsCatalog.VIDEO, env, false));
    }

    @Test
    public void expertOnlyPagesAreTheOnesWithoutNormalRows() {
        for (SettingsCatalog.Page p : SettingsCatalog.PAGES) {
            boolean shownOff = !SettingsCatalog.visibleRows(p.id, env, false).isEmpty();
            boolean shownOn = !SettingsCatalog.visibleRows(p.id, env, true).isEmpty();
            assertEquals(p.id, shownOn && !shownOff, SettingsCatalog.expertOnly(p.id, env));
        }
        assertFalse(SettingsCatalog.expertOnly(SettingsCatalog.VIDEO, env));
    }

    @Test
    public void dotMarksTheValuesAProfileCarries() {
        assertTrue(SettingsCatalog.followsProfile(SettingsCatalog.find(SettingsCatalog.KEY_BITRATE)));
        assertTrue(SettingsCatalog.followsProfile(SettingsCatalog.find("video_format")));
        // Common to every profile: the language, an action, the active profile itself
        assertFalse(SettingsCatalog.followsProfile(SettingsCatalog.find("list_languages")));
        assertFalse(SettingsCatalog.followsProfile(SettingsCatalog.find(SettingsCatalog.KEY_BITRATE_DEFAULT)));
        assertFalse(SettingsCatalog.followsProfile(SettingsCatalog.find(SettingsCatalog.KEY_PROFILE)));
        // The microphone is the device's (app-wide key)
        assertFalse(SettingsCatalog.followsProfile(SettingsCatalog.find("checkbox_mic_passthrough")));
    }

    @Test
    public void shouldersWrapThroughTheVisiblePages() {
        List<SettingsCatalog.Page> pages = SettingsCatalog.visiblePages(env, false);
        String first = pages.get(0).id, last = pages.get(pages.size() - 1).id;
        assertEquals(pages.get(1).id, SettingsCatalog.stepPage(pages, first, 1));
        assertEquals(last, SettingsCatalog.stepPage(pages, first, -1));
        assertEquals(first, SettingsCatalog.stepPage(pages, last, 1));
    }
}
