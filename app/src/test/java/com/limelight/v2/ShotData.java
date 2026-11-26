package com.limelight.v2;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.limelight.nvstream.http.NvApp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The data of the screenshot tests. By default: made-up games and drawn covers (what is in git).
 * With {@code V2_SHOT_COVERS} pointing at a folder holding the prototype's {@code shots-data.json}
 * and its images (real game art, kept out of git), the same games, hours, install states, hidden
 * game and covers as the prototype, so that a capture can be laid next to the prototype's render.
 */
final class ShotData {
    static final String ENV = "V2_SHOT_COVERS";

    /** Made-up library (recent first) when the prototype's data is not there */
    private static final String[] FAKE_GAMES = {
            "Red Dead Redemption 2", "Uncrashed", "Rocket League", "Velocidrone", "Split Fiction",
            "Flight Simulator", "Hades II", "Cyberpunk 2077", "Elden Ring", "Celeste", "Forza Horizon 5", "Tunic"};
    private static final int[] FAKE_HOURS = {212, 38, 640, 12, 9, 75, 41, 66, 120, 18, 30, 0};
    private static final int FAKE_LIBRARY = 266;
    static final long NOW = 1_800_000_000L;

    private static ShotData instance;

    final boolean real;
    final List<String> games = new ArrayList<>();
    final List<Integer> hours = new ArrayList<>();
    final List<Boolean> installed = new ArrayList<>();
    final List<Integer> daysAgo = new ArrayList<>();
    final List<String> coverFiles = new ArrayList<>();
    final List<String> backgroundFiles = new ArrayList<>();
    final Set<String> hiddenNames = new HashSet<>();
    final Map<String, String> appCovers = new HashMap<>();
    final File dir;

    private ShotData(File dir) {
        this.dir = dir;
        boolean ok = false;
        if (dir != null) {
            try {
                String json = new String(Files.readAllBytes(new File(dir, "shots-data.json").toPath()), StandardCharsets.UTF_8);
                JSONObject o = new JSONObject(json);
                JSONArray g = o.getJSONArray("games");
                for (int i = 0; i < g.length(); i++) {
                    JSONObject x = g.getJSONObject(i);
                    games.add(x.getString("name"));
                    hours.add(x.optInt("hours", 0));
                    installed.add(x.optBoolean("installed", false));
                    daysAgo.add(x.isNull("days_ago") ? -1 : x.optInt("days_ago", -1));
                    coverFiles.add(x.optString("cover", null));
                    backgroundFiles.add(x.optString("background", null));
                }
                JSONArray h = o.optJSONArray("hidden");
                for (int i = 0; h != null && i < h.length(); i++) {
                    hiddenNames.add(h.getString(i));
                }
                JSONObject ac = o.optJSONObject("app_covers");
                if (ac != null) {
                    for (java.util.Iterator<String> it = ac.keys(); it.hasNext(); ) {
                        String k = it.next();
                        appCovers.put(k, ac.getString(k));
                    }
                }
                ok = !games.isEmpty();
            } catch (Exception e) {
                games.clear();
            }
        }
        real = ok;
        if (!real) {
            for (int i = 0; i < FAKE_GAMES.length; i++) {
                games.add(FAKE_GAMES[i]);
                hours.add(FAKE_HOURS[i]);
                installed.add(true);
                daysAgo.add(i);
                coverFiles.add(null);
                backgroundFiles.add(null);
            }
        }
    }

    static synchronized ShotData get() {
        if (instance == null) {
            String env = System.getenv(ENV);
            instance = new ShotData(env != null && !env.isEmpty() ? new File(env) : null);
        }
        return instance;
    }

    /** Name of the n-th most recent game */
    String game(int n) {
        return games.get(n);
    }

    /**
     * "266 jeux" in the prototype, which shows its 15 games: the rest of the library is made of
     * never-played games without covers, after them (and not installed with the real data)
     */
    int libraryCount() {
        return FAKE_LIBRARY;
    }

    static NvApp app(String name, int id) {
        return new NvApp(name, "uuid-" + id, id, false);
    }

    /** Desktop, Playnite, Steam Big Picture (an app the home does not show), then the games (id 100 + n) */
    List<NvApp> apps() {
        List<NvApp> list = new ArrayList<>();
        list.add(app("Desktop", 1));
        NvApp playnite = app("Playnite", 2);
        playnite.setPlayniteHost(true);
        list.add(playnite);
        list.add(app("Steam Big Picture", 3));
        int count = libraryCount();
        for (int i = 0; i < count; i++) {
            NvApp g = app(i < games.size() ? games.get(i) : "Jeu " + (i + 1), 100 + i);
            g.setPlayniteGame(true);
            if (i < games.size()) {
                g.setPlayniteInstalled(installed.get(i));
                g.setPlaynitePlaytime(hours.get(i) * 3600L);
                if (daysAgo.get(i) >= 0) {
                    g.setPlayniteLastActivity(NOW - daysAgo.get(i) * 86_400L);
                }
                g.setPlayniteBackground(backgroundFiles.get(i) != null);
            } else {
                g.setPlayniteInstalled(!real);
            }
            list.add(g);
        }
        return list;
    }

    /** App ids of the hidden games (the prototype hides Death Stranding) */
    Set<Integer> hidden(List<NvApp> apps) {
        Set<Integer> out = new HashSet<>();
        for (NvApp a : apps) {
            if (hiddenNames.contains(a.getAppName())) {
                out.add(a.getAppId());
            }
        }
        return out;
    }

    CoverSource covers(List<NvApp> apps) {
        return real ? new RealCovers(apps) : new FakeCovers(apps);
    }

    private Bitmap decode(String file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        File f = new File(dir, file);
        return f.isFile() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
    }

    /** The prototype's own covers and backgrounds */
    private final class RealCovers implements CoverSource {
        private final Map<Integer, Bitmap> covers = new HashMap<>();
        private final Map<Integer, Bitmap> backgrounds = new HashMap<>();

        RealCovers(List<NvApp> apps) {
            for (NvApp a : apps) {
                int id = a.getAppId();
                if (id == 1) {
                    put(covers, id, decode(appCovers.get("Bureau")));
                } else if (id == 2) {
                    put(covers, id, decode(appCovers.get("Playnite Fullscreen")));
                } else if (id >= 100 && id - 100 < games.size()) {
                    put(covers, id, decode(coverFiles.get(id - 100)));
                    put(backgrounds, id, decode(backgroundFiles.get(id - 100)));
                }
            }
        }

        private void put(Map<Integer, Bitmap> m, int id, Bitmap b) {
            if (b != null) {
                m.put(id, b);
            }
        }

        @Override
        public void load(NvApp app, Sink sink) {
            sink.onCover(app.getAppId(), covers.get(app.getAppId()));
        }

        @Override
        public Bitmap peek(NvApp app) {
            return covers.get(app.getAppId());
        }

        @Override
        public void loadBackground(NvApp app, Sink sink) {
            sink.onCover(app.getAppId(), backgrounds.get(app.getAppId()));
        }

        @Override
        public Bitmap peekBackground(NvApp app) {
            return backgrounds.get(app.getAppId());
        }
    }

    /** Box-art-like images: a gradient per game, its title in big letters. Two games have none. */
    private final class FakeCovers implements CoverSource {
        private final Map<Integer, Bitmap> bitmaps = new HashMap<>();

        FakeCovers(List<NvApp> apps) {
            int[][] palette = {{0xFF8E2B1C, 0xFF2A0F0A}, {0xFF3B1E6B, 0xFF0E0A1F}, {0xFFE0C43A, 0xFF4A2A06},
                    {0xFF5B6B3A, 0xFF101A0C}, {0xFF2F7FB5, 0xFF0B1E33}, {0xFFB53A6B, 0xFF2A0A18}};
            int i = 0;
            for (NvApp a : apps) {
                if (a.getAppId() >= 100 + games.size()) {
                    continue;
                }
                if (a.getAppId() == 104 || a.getAppId() == 109) {
                    continue; // no cover on the host: the name shows
                }
                int[] c = palette[i++ % palette.length];
                bitmaps.put(a.getAppId(), cover(a.getAppName(), c[0], c[1], a.getAppId() < 100));
            }
        }

        private Bitmap cover(String title, int top, int bottom, boolean landscapeArt) {
            Bitmap b = Bitmap.createBitmap(300, 450, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(0, 0, 0, 450, top, bottom, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, 300, 450, p);
            p.setShader(null);
            p.setColor(Color.argb(60, 255, 255, 255));
            c.drawCircle(220, 140, 110, p);
            p.setColor(Color.WHITE);
            p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            String[] words = title.split(" ");
            float size = landscapeArt ? 44 : (words.length > 3 ? 32 : 38);
            p.setTextSize(size);
            for (String w : words) {
                while (p.measureText(w.toUpperCase()) > 256 && size > 18) {
                    size -= 2;
                    p.setTextSize(size);
                }
            }
            float line = size * 1.15f;
            float y = landscapeArt ? 330 : 450 - 24 - (words.length - 1) * line;
            for (String word : words) {
                c.drawText(word.toUpperCase(), 22, y, p);
                y += line;
            }
            return b;
        }

        @Override
        public void load(NvApp app, Sink sink) {
            sink.onCover(app.getAppId(), bitmaps.get(app.getAppId()));
        }

        @Override
        public Bitmap peek(NvApp app) {
            return bitmaps.get(app.getAppId());
        }
    }
}
