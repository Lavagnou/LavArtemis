package com.limelight.utils;

import android.content.Context;

import com.limelight.R;

/**
 * The connection stages moonlight-common-c names in English (stageNames[] in Connection.c),
 * shown to the user in the app's language: « Démarrage de la négociation RTSP », « Impossible de
 * démarrer le flux vidéo ». A name it does not know (an app name, a new stage) is shown as is.
 */
public final class StreamStages {
    private StreamStages() {}

    /** The string resource for a stage name of moonlight-common-c, 0 when it is not one */
    public static int labelRes(String stage) {
        if (stage == null) {
            return 0;
        }
        switch (stage) {
            case "platform initialization": return R.string.stage_platform_init;
            case "name resolution": return R.string.stage_name_resolution;
            case "audio stream initialization": return R.string.stage_audio_init;
            case "RTSP handshake": return R.string.stage_rtsp_handshake;
            case "control stream initialization": return R.string.stage_control_init;
            case "video stream initialization": return R.string.stage_video_init;
            case "input stream initialization": return R.string.stage_input_init;
            case "control stream establishment": return R.string.stage_control_start;
            case "video stream establishment": return R.string.stage_video_start;
            case "audio stream establishment": return R.string.stage_audio_start;
            case "input stream establishment": return R.string.stage_input_start;
            default: return 0;
        }
    }

    public static String label(Context context, String stage) {
        int res = labelRes(stage);
        return res != 0 ? context.getString(res) : stage;
    }
}
