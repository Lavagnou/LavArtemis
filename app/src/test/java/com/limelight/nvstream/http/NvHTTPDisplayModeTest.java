package com.limelight.nvstream.http;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class NvHTTPDisplayModeTest {
    @Test
    public void parsesCurrentDisplayMode() throws Exception {
        String xml = "<root status_code=\"200\"><hostname>ATLAS-PC</hostname>"
                + "<CurrentDisplayWidth>2400</CurrentDisplayWidth>"
                + "<CurrentDisplayHeight>1080</CurrentDisplayHeight>"
                + "<CurrentDisplayRefreshRate>90</CurrentDisplayRefreshRate></root>";
        assertArrayEquals(new int[]{2400, 1080, 90}, NvHTTP.getCurrentDisplayMode(xml));
    }

    @Test
    public void missingFieldMeansNoMode() throws Exception {
        String xml = "<root status_code=\"200\"><CurrentDisplayWidth>2400</CurrentDisplayWidth>"
                + "<CurrentDisplayHeight>1080</CurrentDisplayHeight></root>";
        assertNull(NvHTTP.getCurrentDisplayMode(xml));
    }

    @Test
    public void otherHostMeansNoMode() throws Exception {
        assertNull(NvHTTP.getCurrentDisplayMode("<root status_code=\"200\"><hostname>x</hostname></root>"));
    }
}
