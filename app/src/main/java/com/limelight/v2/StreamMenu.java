package com.limelight.v2;

import java.util.ArrayList;
import java.util.List;

/**
 * Rules of the in-game menu that are not views (LavArtemis Dev): which tiles it shows and in what
 * order, what « Plus » lists, and when the « Menu » pill shows. The layout is the user's (workshop of
 * 2026-11-26, docs/superpowers/verif-2026-11-25/decisions-design.md « Menu en jeu »): the entries both
 * clients have, in the PC's order and with its labels. Pure logic, tested by StreamMenuTest; GameMenu
 * and Game apply it.
 */
public final class StreamMenu {
    private StreamMenu() {}

    /** The tiles, in the order shown (the PC's, without its « Libérer la souris et le clavier ») */
    public enum Tile {
        RESUME,
        DISCONNECT,
        /** Quitter {jeu}: only a game launched inside Playnite (closes the game, the stream goes back to Playnite) */
        QUIT_GAME,
        QUIT_SESSION,
        /** Passer en trackpad / multitouch: not on an external display (Trackpad is imposed there) */
        TOUCH_MODE,
        /** Ouvrir le clavier: the soft keyboard */
        KEYBOARD,
        /** Micro · Activé / Coupé, greyed « Micro non envoyé » when the microphone is not sent */
        MIC,
        STATS,
        MORE
    }

    /**
     * @param playniteGame a game runs inside Playnite: no Quitter {jeu} for the Bureau or Playnite
     *                     Fullscreen, where it would only repeat « Quitter la session »
     * @param touchSwitch  the touch mode can be switched (not on an external display)
     */
    public static List<Tile> tiles(boolean playniteGame, boolean touchSwitch) {
        List<Tile> out = new ArrayList<>();
        out.add(Tile.RESUME);
        out.add(Tile.DISCONNECT);
        if (playniteGame) {
            out.add(Tile.QUIT_GAME);
        }
        out.add(Tile.QUIT_SESSION);
        if (touchSwitch) {
            out.add(Tile.TOUCH_MODE);
        }
        out.add(Tile.KEYBOARD);
        out.add(Tile.MIC);
        out.add(Tile.STATS);
        out.add(Tile.MORE);
        return out;
    }

    /** The entries of « Plus »: the common ones first (the PC's order), then what only Android has */
    public enum More {
        CLIPBOARD,
        SERVER_COMMANDS,
        /** Activer / Désactiver le mode Zoom/Panoramique */
        ZOOM,
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
        /** Afficher / Masquer la pastille « Menu »; the gamepad's own entries follow it */
        MENU_PILL
    }

    public static List<More> more(boolean voiceShown, boolean canRotate, boolean onScreenController) {
        List<More> out = new ArrayList<>();
        out.add(More.CLIPBOARD);
        out.add(More.SERVER_COMMANDS);
        out.add(More.ZOOM);
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
