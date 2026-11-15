package dream;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The CRT screen itself: a stack of named layers holding text and scanlines,
 * drawn with a barrel distortion so the picture looks bowed like old glass.
 *
 * <p>The screen is locked to 4:3. However the window is shaped, the picture is
 * rendered into the largest 4:3 rectangle that fits and the remaining space is
 * left as plain bezel, the way a 4:3 monitor sits on a widescreen desk.
 *
 * <p>When the {@code crt} setting is not off, the finished picture is run
 * through {@link CrtShader} before being shown. That replaces the hand-drawn
 * scanline layer, which would otherwise double up with the shader's own.
 *
 * <p>All mutation happens on the Swing event thread.
 */
public class CurveCanvas extends JPanel {

    /** Dialogue and the input prompt live here. */
    public static final String TEXT_LAYER = "TERMINAL_TEXT";

    /** The curved scanlines drawn over the top of everything. */
    public static final String SCANLINE_LAYER = "SCANLINES";

    private static final int SCANLINE_COUNT = 84;

    /** The picture is always this shape, whatever the window is. */
    private static final int ASPECT_W = 4;
    private static final int ASPECT_H = 3;

    private final Map<String, Layer> layerMap = new HashMap<>();
    private final List<Layer> renderOrder = new ArrayList<>();

    /** The canvas currently on screen, so settings changes can invalidate it. */
    private static CurveCanvas instance;

    private final CrtShader crt = new CrtShader();

    // The 4:3 rectangle inside the window, and the offscreen picture drawn
    // into it. Text coordinates are all in this smaller space.
    private final int[] viewport = new int[4];
    private BufferedImage sceneBuffer;
    private int bufferW;
    private int bufferH;

    // Unlit phosphor: a dark green-grey wash that darkens towards the edges,
    // instead of plain black. Cached because it only changes on resize.
    private BufferedImage background;
    private int backgroundW;
    private int backgroundH;

    // The last finished frame. Text only changes when something repaints, so
    // holding on to it makes static screens cost nothing to display.
    private BufferedImage cachedFrame;
    private boolean frameValid;

    public CurveCanvas() {
        instance = this;
        setBackground(new Color(14, 16, 15));
        setFocusable(true);

        // A JPanel does not take keyboard focus just because it was clicked, so
        // without this a single click anywhere in the window leaves the game
        // unable to hear the keyboard.
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
            }
        });

        // Text underneath, scanlines on top.
        createLayer(TEXT_LAYER, 0);
        createLayer(SCANLINE_LAYER, 10);

        // Scanlines are sized to the screen, so rebuild them whenever it
        // changes. The original built them once from the raw screen size,
        // which is why the effect drifted out of alignment at other sizes.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                frameValid = false;
                rebuildScanlines();
                // Keep the text column centred and correctly sized for the
                // new dimensions.
                Terminal.relayout();
            }
        });
    }

    public void createLayer(String name, int zIndex) {
        if (!layerMap.containsKey(name)) {
            Layer newLayer = new Layer(name, zIndex);
            layerMap.put(name, newLayer);
            renderOrder.add(newLayer);
            renderOrder.sort(Comparator.comparingInt(l -> l.zIndex));
        }
    }

    // ------------------------------------------------------------- the screen

    /** Recomputes the centred 4:3 rectangle inside the window. */
    private void updateViewport() {
        int canvasW = getWidth();
        int canvasH = getHeight();
        if (canvasW <= 0 || canvasH <= 0) {
            viewport[2] = 0;
            viewport[3] = 0;
            return;
        }

        int viewW;
        int viewH;
        if ((long) canvasW * ASPECT_H <= (long) canvasH * ASPECT_W) {
            // Window is narrower than 4:3 (or exactly): width is the limit.
            viewW = canvasW & ~3;
            viewH = viewW / ASPECT_W * ASPECT_H;
        } else {
            viewH = canvasH & ~3;
            viewW = viewH / ASPECT_H * ASPECT_W;
        }

        if (viewW <= 0 || viewH <= 0) {
            viewport[2] = 0;
            viewport[3] = 0;
            return;
        }

        viewport[0] = (canvasW - viewW) / 2;
        viewport[1] = (canvasH - viewH) / 2;
        viewport[2] = viewW;
        viewport[3] = viewH;
    }

    /** Width of the 4:3 picture, in pixels. Layout code measures against this. */
    public int getViewWidth() {
        updateViewport();
        return viewport[2];
    }

    /** Height of the 4:3 picture, in pixels. */
    public int getViewHeight() {
        updateViewport();
        return viewport[3];
    }

    /** True when the CRT pass, rather than the drawn scanlines, is running. */
    private static boolean crtEnabled() {
        return Settings.crt != CrtShader.Preset.OFF;
    }

    /** Drops the cached frame after a settings change. */
    public static void refreshCrt() {
        if (instance != null) {
            instance.frameValid = false;
            instance.repaint();
        }
    }

    // ---------------------------------------------------------------- layers

    /** Lays the curved scanlines out to fit the current 4:3 picture. */
    public void rebuildScanlines() {
        Layer layer = layerMap.get(SCANLINE_LAYER);
        if (layer == null) {
            return;
        }

        int width = getViewWidth();
        int height = getViewHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        layer.curves.clear();
        int spacing = Math.max(1, height / SCANLINE_COUNT);
        Color lineColor = new Color(60, 60, 60, 100);

        for (int y = spacing; y < height; y += spacing) {
            double bow = y + (y - height / 2.0) * 0.3;
            layer.curves.add(new CurveData(0, y, width / 2.0, bow, width, y, lineColor));
        }
        repaint();
    }

    public void addCurveToLayer(String layerName, CurveData curve) {
        Layer layer = layerMap.get(layerName);
        if (layer != null) {
            layer.curves.add(curve);
            repaint();
        }
    }

    public void addTextToLayer(String layerName, TextData text) {
        Layer layer = layerMap.get(layerName);
        if (layer != null) {
            layer.textItems.add(text);
            repaint();
        } else {
            Log.warn("Layer " + layerName + " does not exist.");
        }
    }

    /** Shifts every line of text vertically. Used by the scroll wheel. */
    public void scrollAllText(int scrollAmount) {
        for (Layer layer : renderOrder) {
            for (TextData td : layer.textItems) {
                td.setY(td.y + scrollAmount);
            }
        }
        repaint();
    }

    /** Drops text that has scrolled far enough away to be unreachable. */
    public void pruneText(int keepNewest) {
        Layer layer = layerMap.get(TEXT_LAYER);
        if (layer == null || layer.textItems.size() <= keepNewest) {
            return;
        }
        int excess = layer.textItems.size() - keepNewest;
        layer.textItems.subList(0, excess).clear();
    }

    // ---------------------------------------------------------------- painting

    /**
     * Anything that asks for a repaint has changed the picture: typing, a new
     * message, a scroll, a resize. Text is edited in place without telling us
     * (Terminal calls setText and then repaint), so this is the one hook that
     * reliably marks the cached frame stale.
     */
    @Override
    public void repaint() {
        frameValid = false;
        super.repaint();
    }

    @Override
    public void repaint(long tm) {
        frameValid = false;
        super.repaint(tm);
    }

    @Override
    public void repaint(int x, int y, int width, int height) {
        frameValid = false;
        super.repaint(x, y, width, height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        updateViewport();
        int viewW = viewport[2];
        int viewH = viewport[3];
        if (viewW <= 0 || viewH <= 0) {
            return;
        }

        if (!frameValid || cachedFrame == null) {
            ensureSceneBuffer(viewW, viewH);
            ensureBackground(viewW, viewH);

            Graphics2D scene = sceneBuffer.createGraphics();
            scene.drawImage(background, 0, 0, null);
            paintScene(scene, viewW, viewH);
            scene.dispose();

            cachedFrame = crtEnabled()
                ? crt.render(sceneBuffer, Settings.crtQuality, Settings.crt.tuning())
                : sceneBuffer;
            frameValid = true;
        }

        Graphics2D g2 = (Graphics2D) g;
        // Nearest neighbour keeps the mask crisp; the filter's output is
        // already low resolution, so smoothing it would just look mushy.
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // The picture fills the viewport; the filter's output is the same 4:3
        // shape, just at the pass resolution.
        int drawnW = viewW;
        int drawnH = viewH;
        int x = viewport[0];
        int y = viewport[1];

        // Rounded tube corners: the glass does not have sharp ones.
        double radius = Math.max(10.0, viewH * 0.05);
        Shape previousClip = g2.getClip();
        g2.clip(new RoundRectangle2D.Double(x, y, drawnW, drawnH, radius, radius));
        g2.drawImage(cachedFrame, x, y, drawnW, drawnH, null);
        g2.setClip(previousClip);
    }

    /**
     * Builds the unlit phosphor wash: a soft green-grey pool that darkens
     * towards the corners, so the screen reads as a tinted tube rather than a
     * black rectangle.
     */
    private void ensureBackground(int viewW, int viewH) {
        if (background != null && backgroundW == viewW && backgroundH == viewH) {
            return;
        }

        background = new BufferedImage(viewW, viewH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = background.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);

        float radius = (float) (Math.hypot(viewW, viewH) * 0.62);
        RadialGradientPaint wash = new RadialGradientPaint(
            new Point2D.Float(viewW / 2f, viewH / 2f),
            Math.max(1f, radius),
            new float[] {0f, 0.55f, 1f},
            new Color[] {new Color(34, 46, 38), new Color(21, 29, 24), new Color(8, 11, 9)},
            MultipleGradientPaint.CycleMethod.NO_CYCLE);
        g.setPaint(wash);
        g.fillRect(0, 0, viewW, viewH);
        g.dispose();

        backgroundW = viewW;
        backgroundH = viewH;
    }

    private void ensureSceneBuffer(int viewW, int viewH) {
        if (sceneBuffer == null || bufferW != viewW || bufferH != viewH) {
            sceneBuffer = new BufferedImage(viewW, viewH, BufferedImage.TYPE_INT_RGB);
            bufferW = viewW;
            bufferH = viewH;
            frameValid = false;
        }
    }

    /** Draws every layer into the offscreen picture, in viewport coordinates. */
    private void paintScene(Graphics2D g2, int width, int height) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        // Aliased text is deliberate: it keeps the chunky DOS-terminal look.
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
            RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        boolean skipDrawnScanlines = crtEnabled();

        for (Layer layer : renderOrder) {
            if (!layer.visible) {
                continue;
            }
            drawText(g2, layer, width, height);
            if (skipDrawnScanlines && SCANLINE_LAYER.equals(layer.name)) {
                continue;
            }
            drawCurves(g2, layer);
        }
    }

    /**
     * Draws each line one character at a time, nudging every glyph along a
     * quadratic curve so the whole screen appears to bulge outwards.
     */
    private void drawText(Graphics2D g2, Layer layer, int width, int height) {
        for (TextData td : layer.textItems) {
            // Skip anything scrolled well clear of the viewport. With per-glyph
            // transforms this matters a lot once the log gets long.
            if (td.y < -120 || td.y > height + 120 || td.text.isEmpty()) {
                continue;
            }

            g2.setColor(td.color);
            g2.setFont(td.font);
            FontMetrics metrics = g2.getFontMetrics(td.font);

            double currentX = td.x;
            double tY = td.y / (double) height;

            for (char c : td.text.toCharArray()) {
                double tX = currentX / (double) width;

                double yBendFactor = (td.y - height / 2.0) * 0.14;
                double distortedY = td.y + (2 * (1 - tX) * tX * yBendFactor);

                double xBendFactor = (currentX - width / 2.0) * 0.09;
                double distortedX = currentX + (2 * (1 - tY) * tY * xBendFactor);

                // Tilt each glyph to sit tangent to the curve it rides on.
                double slope = (1.5 * yBendFactor * (1 - 2 * tX)) / width;
                double angle = Math.atan(slope);
                angle = td.y < height / 2 ? -angle * 0.90 : -angle;

                AffineTransform old = g2.getTransform();
                g2.translate(distortedX, distortedY);
                g2.rotate(angle);
                g2.drawString(String.valueOf(c), 0, 0);
                g2.setTransform(old);

                currentX += metrics.getStringBounds(String.valueOf(c), g2).getWidth();
            }
        }
    }

    private void drawCurves(Graphics2D g2, Layer layer) {
        g2.setStroke(new BasicStroke(2));
        for (CurveData curveData : layer.curves) {
            g2.setColor(curveData.color);
            g2.draw(curveData.curve);
        }
    }
}
