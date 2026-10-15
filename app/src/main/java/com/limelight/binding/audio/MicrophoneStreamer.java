package com.limelight.binding.audio;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Process;

import com.limelight.LimeLog;
import com.limelight.nvstream.jni.MoonBridge;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures this device's microphone and sends it to the host while streaming. LavApollo plays it
 * into a virtual microphone (Steam Streaming Microphone by default), so voice chat and games on
 * the host hear the player.
 *
 * 20 ms frames of 48 kHz mono PCM are Opus-encoded in native code (simplejni.c) and sent inside
 * the encrypted control stream. One thread reads, encodes and sends: LiSendMicrophoneData() must
 * only ever be called from one thread.
 */
public class MicrophoneStreamer {
    private static final int SAMPLE_RATE = 48000;
    private static final int FRAME_SAMPLES = SAMPLE_RATE / 50;
    private static final int BITRATE = 32000;

    private final Context context;
    private final String preferredDevice;

    private AudioRecord record;
    private Thread thread;
    private volatile boolean running;
    private volatile boolean muted;

    /**
     * @param preferredDevice A key from {@link #listDevices(Context)}, or empty for the default
     */
    public MicrophoneStreamer(Context context, String preferredDevice) {
        this.context = context;
        this.preferredDevice = preferredDevice;
    }

    public static boolean hasPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Recording devices, as {key, label} pairs. The key is "type:product name" rather than the
     * device id, which changes every time a device reconnects.
     */
    public static List<String[]> listDevices(Context context) {
        List<String[]> devices = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return devices;
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            String name = String.valueOf(device.getProductName());
            devices.add(new String[] { deviceKey(device), name + " (" + typeLabel(device.getType()) + ")" });
        }
        return devices;
    }

    private static String deviceKey(AudioDeviceInfo device) {
        return device.getType() + ":" + device.getProductName();
    }

    private static String typeLabel(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                return "built-in";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return "headset";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return "USB";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return "Bluetooth";
            default:
                return "type " + type;
        }
    }

    /**
     * Opens the microphone and starts sending. Must be called once the connection has started.
     * @return false when there is no usable microphone or no permission; the stream goes on.
     */
    @SuppressLint("MissingPermission") // Checked by hasPermission()
    public boolean start() {
        if (!hasPermission(context)) {
            LimeLog.warning("Microphone: RECORD_AUDIO not granted");
            return false;
        }

        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) {
            LimeLog.warning("Microphone: 48 kHz mono capture isn't supported");
            return false;
        }
        int bufferSize = Math.max(minBuffer, FRAME_SAMPLES * 2 * 4);

        try {
            // VOICE_COMMUNICATION turns on the device's echo cancellation and noise suppression:
            // the game's sound coming out of the speaker must not go back to the host.
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        } catch (IllegalArgumentException e) {
            LimeLog.warning("Microphone: " + e.getMessage());
            return false;
        }

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            LimeLog.warning("Microphone: no microphone could be opened");
            record.release();
            record = null;
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && preferredDevice != null && !preferredDevice.isEmpty()) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                if (deviceKey(device).equals(preferredDevice)) {
                    record.setPreferredDevice(device);
                    break;
                }
            }
        }

        if (!MoonBridge.micStart(BITRATE)) {
            LimeLog.warning("Microphone: couldn't create the Opus encoder");
            record.release();
            record = null;
            return false;
        }

        try {
            record.startRecording();
        } catch (IllegalStateException e) {
            LimeLog.warning("Microphone: " + e.getMessage());
            MoonBridge.micStop();
            record.release();
            record = null;
            return false;
        }

        running = true;
        thread = new Thread(this::captureLoop, "Microphone");
        thread.start();

        LimeLog.info("Microphone: sending to the host (" + BITRATE / 1000 + " kbps Opus)");
        return true;
    }

    /**
     * Stops sending and releases the microphone. Must be called before the connection stops.
     */
    public void stop() {
        if (record == null) {
            return;
        }

        running = false;
        try {
            // Unblocks the read in progress
            record.stop();
        } catch (IllegalStateException ignored) {
        }

        if (thread != null) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }

        record.release();
        record = null;

        // Only once the capture thread is gone: it is the encoder's only user
        MoonBridge.micStop();
    }

    /**
     * Muting keeps the microphone open, so unmuting is instant; nothing is sent meanwhile.
     */
    public void setMuted(boolean muted) {
        this.muted = muted;
        LimeLog.info("Microphone " + (muted ? "muted" : "unmuted"));
    }

    public boolean isMuted() {
        return muted;
    }

    private void captureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

        short[] frame = new short[FRAME_SAMPLES];
        boolean sendFailureLogged = false;

        while (running) {
            int filled = 0;
            while (filled < FRAME_SAMPLES && running) {
                int read = record.read(frame, filled, FRAME_SAMPLES - filled);
                if (read < 0) {
                    LimeLog.warning("Microphone: read failed (" + read + ")");
                    return;
                }
                filled += read;
            }

            if (!running || muted || filled < FRAME_SAMPLES) {
                continue;
            }

            if (MoonBridge.micSendPcm(frame, FRAME_SAMPLES) < 0 && !sendFailureLogged) {
                // Only once: this runs 50 times a second
                sendFailureLogged = true;
                LimeLog.warning("Microphone: the host isn't taking microphone audio on this connection");
            }
        }
    }
}
