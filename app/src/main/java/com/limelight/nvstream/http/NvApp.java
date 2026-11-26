package com.limelight.nvstream.http;

import com.limelight.LimeLog;

public class NvApp {
    public static final String REMOTE_INPUT_UUID = "8CB5C136-DA67-4F99-B4A1-F9CD35005CF4";
    private String appName = "";
    private String appUUID = "";
    private int appId;
    private int appIndex;
    private boolean initialized;
    private boolean hdrSupported;
    // LavApollo Link: a game of the host's Playnite library, and the host's Playnite front end
    // (where those games start without ending the session)
    private boolean playniteGame;
    private boolean playniteHost;
    // A Playnite game's state, from LavApollo's applist (absent on older hosts: these defaults).
    // A game that is not installed is installed by Playnite when launched.
    private boolean playniteInstalled = true;
    // Last time it was played, Unix seconds (0: never, or an older host)
    private long playniteLastActivity;
    // Play time, seconds
    private long playnitePlaytime;
    // The host serves a background image for it (NvHTTP.getBackground)
    private boolean playniteBackground;
    
    public NvApp() {}
    
    public NvApp(String appName) {
        this.appName = appName;
    }
    
    public NvApp(String appName, String appUUID, int appId, boolean hdrSupported) {
        this.appName = appName;
        this.appUUID = appUUID;
        this.appId = appId;
        this.hdrSupported = hdrSupported;
        this.initialized = true;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }

    public void setAppUUID(String appUUID) {
        this.appUUID = appUUID;
    }
    
    public void setAppId(String appId) {
        try {
            this.appId = Integer.parseInt(appId);
            this.initialized = true;
        } catch (NumberFormatException e) {
            LimeLog.warning("Malformed app ID: "+appId);
        }
    }

    public void setAppIndex(String appIndex) {
        try {
            this.appIndex = Integer.parseInt(appIndex);
            this.initialized = true;
        } catch (NumberFormatException e) {
            LimeLog.warning("Malformed app index: "+appIndex);
        }
    }

    public void setAppId(int appId) {
        this.appId = appId;
        this.initialized = true;
    }

    public void setAppIndex(int appIndex) {
        this.appIndex = appIndex;
    }

    public void setHdrSupported(boolean hdrSupported) {
        this.hdrSupported = hdrSupported;
    }

    public void setPlayniteGame(boolean playniteGame) {
        this.playniteGame = playniteGame;
    }

    public boolean isPlayniteGame() {
        return this.playniteGame;
    }

    public void setPlayniteHost(boolean playniteHost) {
        this.playniteHost = playniteHost;
    }

    public boolean isPlayniteHost() {
        return this.playniteHost;
    }

    public void setPlayniteInstalled(boolean installed) {
        this.playniteInstalled = installed;
    }

    public boolean isPlayniteInstalled() {
        return this.playniteInstalled;
    }

    public void setPlayniteLastActivity(long unixSeconds) {
        this.playniteLastActivity = Math.max(0, unixSeconds);
    }

    /** Unix seconds, 0 when never played (or the host does not say) */
    public long getPlayniteLastActivity() {
        return this.playniteLastActivity;
    }

    public void setPlaynitePlaytime(long seconds) {
        this.playnitePlaytime = Math.max(0, seconds);
    }

    /** Seconds */
    public long getPlaynitePlaytime() {
        return this.playnitePlaytime;
    }

    public void setPlayniteBackground(boolean hasBackground) {
        this.playniteBackground = hasBackground;
    }

    public boolean hasPlayniteBackground() {
        return this.playniteBackground;
    }
    
    public String getAppName() {
        return this.appName;
    }

    public String getAppUUID() {
        return this.appUUID;
    }
    
    public int getAppId() {
        return this.appId;
    }

    public int getAppIndex() {
        return this.appIndex;
    }

    public boolean isHdrSupported() {
        return this.hdrSupported;
    }
    
    public boolean isInitialized() {
        return this.initialized;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("Name: ").append(appName).append("\n");
        str.append("UUID: ").append(appUUID).append("\n");
        str.append("ID: ").append(appId).append("\n");
        str.append("HDR Supported: ").append(hdrSupported ? "Yes" : "Unknown").append("\n");
        return str.toString();
    }
}
