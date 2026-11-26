package com.limelight.v2.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.limelight.v2.V2Theme;

/**
 * The LavArtemis logo of the prototype's headers (Proto.dc.html, viewBox 100): a tile with rounded
 * corners (rx 22), a screen outline on its stand, and inside it a crescent moon with a play
 * triangle and two stars. Braise: tile gradient #FF7A3D -> #C93A12, glyphs #FFF4EC, play #4A1505.
 * Affiche light: tile #111, glyphs #EDEDE8, play yellow; dark: yellow tile, glyphs and play #111.
 * Ludique: gradient #25325A -> #0E1222, glyphs #DDE6F5, play #8FB4FF.
 */
public final class LogoDrawable extends Drawable {
    private final V2Theme theme;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    public LogoDrawable(V2Theme theme) {
        this.theme = theme;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 100f;
        int save = canvas.save();
        canvas.translate(b.left, b.top);
        canvas.scale(s, s);

        int glyph, play;
        if (theme.isAffiche()) {
            fill.setShader(null);
            fill.setColor(theme.dark ? V2Theme.YELLOW : 0xFF111111);
            glyph = theme.dark ? 0xFF111111 : 0xFFEDEDE8;
            play = theme.dark ? 0xFF111111 : V2Theme.YELLOW;
        } else if (theme.isLudique()) {
            fill.setShader(new LinearGradient(0, 0, 30, 100, 0xFF25325A, 0xFF0E1222, Shader.TileMode.CLAMP));
            glyph = 0xFFDDE6F5;
            play = 0xFF8FB4FF;
        } else {
            fill.setShader(new LinearGradient(0, 0, 30, 100, 0xFFFF7A3D, 0xFFC93A12, Shader.TileMode.CLAMP));
            glyph = 0xFFFFF4EC;
            play = 0xFF4A1505;
        }
        canvas.drawRoundRect(new RectF(0, 0, 100, 100), 22, 22, fill);
        fill.setShader(null);

        // Screen and stand, stroke 5
        stroke.setColor(glyph);
        stroke.setStrokeWidth(5);
        canvas.drawRoundRect(new RectF(16, 22, 84, 70), 8, 8, stroke);
        canvas.drawLine(50, 70, 50, 81, stroke);
        canvas.drawLine(40, 82, 60, 82, stroke);

        // Moon group: translate(50 46) scale(.55) translate(-50 -50). The crescent is the circle of
        // radius 32 at (50, 50) minus the one of radius 27 at (62, 42) (the path's two arcs).
        Path moon = new Path();
        moon.addCircle(50 + (50 - 50) * 0.55f, 46 + (50 - 50) * 0.55f, 32 * 0.55f, Path.Direction.CW);
        if (Build.VERSION.SDK_INT >= 19) {
            Path bite = new Path();
            bite.addCircle(50 + (62 - 50) * 0.55f, 46 + (42 - 50) * 0.55f, 27 * 0.55f, Path.Direction.CW);
            moon.op(bite, Path.Op.DIFFERENCE);
        }
        fill.setColor(glyph);
        canvas.drawPath(moon, fill);
        fill.setColor(glyph);
        canvas.drawCircle(50 + (22 - 50) * 0.55f, 46 + (24 - 50) * 0.55f, 2.4f * 0.55f, fill);
        canvas.drawCircle(50 + (85 - 50) * 0.55f, 46 + (30 - 50) * 0.55f, 2f * 0.55f, fill);

        Path tri = new Path();
        tri.moveTo(50 + (57 - 50) * 0.55f, 46 + (33 - 50) * 0.55f);
        tri.lineTo(50 + (57 - 50) * 0.55f, 46 + (51 - 50) * 0.55f);
        tri.lineTo(50 + (71 - 50) * 0.55f, 46 + (42 - 50) * 0.55f);
        tri.close();
        fill.setColor(play);
        canvas.drawPath(tri, fill);
        stroke.setColor(play);
        stroke.setStrokeWidth(3 * 0.55f);
        canvas.drawPath(tri, stroke);

        canvas.restoreToCount(save);
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        stroke.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
        stroke.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
