package com.limelight.v2;

import java.util.ArrayList;
import java.util.List;

/**
 * Rules of the in-game menu that are not views (LavArtemis Dev, prototype V8): what « Plus » lists
 * (prototype MORE_AN and moreIds()) and when the « Menu » pill shows. Pure logic, tested by
 * StreamMenuTest; GameMenu and Game apply it.
 */
public final class StreamMenu {
    private StreamMenu() {}

    /** The entries of « Plus », prototype order, then what only the app has */
    public enum More {
        /** Micro · Activé / Coupé, or « Micro non envoyé » */
        MIC,
        /** Afficher / Masquer le bouton vocal */
        VOICE,
        /** Déplacer le bouton vocal: only while it shows */
        VOICE_MOVE,
        /** Pivoter l’écran: not while the stream follows the phone's rotation, not on an external display */
        ROTATE,
        /** Basculer le contrôleur à l’écran: not on an external display */
        ON_SCREEN_CONTROLLER,
        /** Basculer les touches spéciales */
        SPECIAL_KEYS,
        /** Basculer le clavier virtuel complet */
        FULL_KEYBOARD,
        /** Changer la sensibilité tactile */
        TOUCH_SENSITIVITY,
        /** Quitter la session: only when a game runs inside Playnite (the « Quitter » tile then closes the game alone) */
        QUIT_SESSION,
        /** Afficher / Masquer la pastille « Menu »: the app's own (the prototype has none) */
        MENU_PILL
    }

    public static List<More> more(boolean voiceShown, boolean canRotate, boolean onScreenController, boolean playniteGame) {
        List<More> out = new ArrayList<>();
        out.add(More.MIC);
        out.add(More.VOICE);
        if (voiceShown) {
            out.add(More.VOICE_MOVE);
        }
        if (canRotate) {
            out.add(More.ROTATE);
        }
        if (onScreenController) {
            out.add(More.ON_SCREEN_CONTROLLER);
        }
        out.add(More.SPECIAL_KEYS);
        out.add(More.FULL_KEYBOARD);
        out.add(More.TOUCH_SENSITIVITY);
        if (playniteGame) {
            out.add(More.QUIT_SESSION);
        }
        out.add(More.MENU_PILL);
        return out;
    }

    /**
     * Label of an entry of « Plus » with its value (prototype: « {label} · {value} »): « Micro · Coupé »
     */
    public static String withValue(String label, String value) {
        return value == null || value.isEmpty() ? label : label + " · " + value;
    }

    /**
     * The « Menu » pill over the stream (prototype: shown when the menu is closed and the input is
     * not the gamepad, which opens the menu with Start): the user did not hide it, no overlay hides
     * it (picture in picture), and the last input was not a gamepad.
     */
    public static boolean pillVisible(boolean userShown, boolean hidingOverlays, V2Input.Kind lastInput) {
        return userShown && !hidingOverlays && lastInput != V2Input.Kind.PAD;
    }

    /** Where the statistics sit (prototype statsTop): under the pill when it shows, else at the top */
    public static int statsTopPx(boolean pillShown) {
        return pillShown ? 68 : 16;
    }
}
