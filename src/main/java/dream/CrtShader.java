package dream;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * The CRT post-processing pass, written in Java because this game renders with
 * Java2D and has no GPU pipeline to hang a fragment shader on.
 *
 * <p>The model follows Timothy Lottes' public domain {@code crt-lottes} shader
 * in spirit: a curved glass warp, a beam profile that thins each scanline, a
 * three-dot shadow mask, gamma-correct light mixing and a little phosphor
 * bleed. The arithmetic here is our own, written against {@code int[]} pixel
 * buffers, and the lens is a plain anisotropic barrel polynomial rather than
 * his. Nothing is copied from a GPL shader, so the project's licensing is
 * unaffected.
 *
 * <p>Three details do most of the work of keeping text readable, because the
 * obvious implementation of each one looks terrible on a terminal:
 * <ul>
 *   <li>Columns are combined with a maximum but rows with an average.
 *       Antialiasing is off, so a vertical stem is either lit or not and
 *       averaging it with black halves its brightness. A horizontal bar is only
 *       a row or two tall, and taking a maximum across rows makes glyphs
 *       bolder until their counters fill in and letters merge. The footprint
 *       also covers every source row the warp stretches under one filtered
 *       row, or thin bars fall between taps and letters lose strokes.</li>
 *   <li>Scanline gain is divided by its own mean, so switching the filter on
 *       changes the texture of the picture without changing how bright it is.</li>
 *   <li>The shadow mask is mostly a luminance mask with a slight colour tint.
 *       A full three-dot mask makes any two pixel wide stem a solid red or blue
 *       line, which reads as a broken display rather than a CRT.</li>
 * </ul>
 *
 * <p>The pass can run at a fraction of the screen resolution ({@code quality}),
 * which is cheaper but softens glyphs enough to merge strokes and lose letters
 * at terminal text sizes, so 1 is the default and the only setting that reads
 * cleanly. Everything in the hot loop is integer fixed point, and pixels that
 * are black in both the picture and the bleed buffer short-circuit, which is
 * most of a terminal's screen.
 *
 * <p>Measured cost on one machine: about 19 ms for a 1440x1080 picture and 45 ms
 * for a 1920x1440 one. That is affordable because a frame is only filtered when
 * the screen actually changes, and the finished frame is then cached.
 *
 * <p>Not thread safe. One instance, used only from the Swing event thread.
 */
public final class CrtShader {

    /** Filter strengths exposed as {@code crt} in settings.bin. */
    public enum Preset {
        /** No filter; the canvas draws its own scanlines instead. */
        OFF(new Tuning(0.00, 0.00, 1.5, 280, 0.00, 0.00, 0.00, 2.2)),
        /** Etched glass: visible scanlines and mask, still easy to read. */
        LIGHT(new Tuning(0.030, 0.30, 1.4, 260, 0.14, 0.20, 0.24, 2.2)),
        /** Full tube: stronger bulge, darker gaps, noticeable phosphor bleed. */
        FULL(new Tuning(0.060, 0.48, 1.7, 300, 0.22, 0.32, 0.32, 2.2));

        private final Tuning tuning;

        Preset(Tuning tuning) {
            this.tuning = tuning;
        }

        public Tuning tuning() {
            return tuning;
        }
    }

    /** Immutable dial settings. The presets above are the only instances used. */
    public static final class Tuning {
        final double curvature;
        final double scanStrength;
        final double scanBeam;
        final int scanLines;
        final double maskStrength;
        final double vignette;
        final double bloom;
        final double gamma;

        Tuning(double curvature, double scanStrength, double scanBeam, int scanLines,
               double maskStrength, double vignette, double bloom, double gamma) {
            this.curvature = curvature;
            this.scanStrength = scanStrength;
            this.scanBeam = scanBeam;
            this.scanLines = scanLines;
            this.maskStrength = maskStrength;
            this.vignette = vignette;
            this.bloom = bloom;
            this.gamma = gamma;
        }
    }

    /** Resolution of the linear-light samples: 12 bits. */
    private static final int LIN = 4096;

    /** Encode table bound: a pixel can reach 2x linear once masks and beam stack. */
    private static final int ENC_LEN = LIN * 2 + 4;

    /** Fixed point one. */
    private static final int ONE = 32768;

    /** Ceiling on any single gain, so the encode table index stays in range. */
    private static final int GAIN_CAP = 2 * ONE;

    /** How much smaller the phosphor bleed buffer is than the pass buffer. */
    private static final int GLOW_DIVISOR = 4;

    /**
     * Blur radius, in bleed-buffer pixels, and how many times to apply it. One
     * wide pass is cheaper than two narrow ones and reaches about as far, so
     * the halo spreads without eating the frame budget.
     */
    private static final int GLOW_BLUR_RADIUS = 3;
    private static final int GLOW_BLUR_PASSES = 1;

    /**
     * Screen pixels per filtered pixel, indexed by crtQuality. Index 1 is a
     * full resolution pass; 2 and 3 filter smaller buffers, which is cheaper but
     * softens glyphs. These are as high as they can go while text still reads.
     */
    private static final double[] PASS_FACTORS = {1.0, 1.0, 1.5, 2.0};

    /** How far the mask is pulled toward a plain luminance mask. 1 = no tint. */
    private static final double MASK_DESATURATION = 0.70;

    /** sRGB byte to linear light, Q12. */
    private final int[] linQ = new int[256];

    /** Linear light to sRGB byte, covering 0 to 2. */
    private final int[] enc = new int[ENC_LEN];
    private double builtGamma = -1.0;

    // Rebuilt only when the viewport, pass scale or preset changes.
    private int srcW;
    private int srcH;
    private int dstW;
    private int dstH;
    private int quality;
    /** Whole source pixels covered by one filtered pixel, for the footprint. */
    private int span = 1;
    private int builtLevel = -1;
    private Tuning built;
    private int[] warpSx = new int[0];
    private int[] warpSy = new int[0];
    private int[] rowQ = new int[0];
    private int[] colR = new int[0];
    private int[] colG = new int[0];
    private int[] colB = new int[0];
    private int[] glowCol = new int[0];
    private int[] glowA = new int[0];
    private int[] glowB = new int[0];
    private int glowW;
    private int glowH;
    private BufferedImage out;

    /**
     * Filters one finished frame.
     *
     * @param scene     the picture to filter, TYPE_INT_RGB
     * @param quality   pass resolution, 1-3: 1 filters at full resolution, 2
     *                  and 3 filter a smaller buffer and are cheaper
     * @param tuning    the dial settings
     * @return a filtered image owned by this shader, valid until the next call
     */
    public BufferedImage render(BufferedImage scene, int quality, Tuning tuning) {
        int sw = scene.getWidth();
        int sh = scene.getHeight();
        if (sw <= 0 || sh <= 0) {
            return scene;
        }

        int level = Math.max(1, Math.min(PASS_FACTORS.length - 1, quality));
        double factor = PASS_FACTORS[level];
        int dw = Math.max(1, (int) Math.round(sw / factor));
        int dh = Math.max(1, (int) Math.round(sh / factor));

        if (out == null || sw != srcW || sh != srcH || dw != dstW || dh != dstH
                || level != builtLevel || tuning != built) {
            rebuild(sw, sh, dw, dh, level, tuning);
        }

        int[] src = pixels(scene);
        int[] dst = pixels(out);
        buildGlow(src);
        filter(src, dst);
        return out;
    }

    // ------------------------------------------------------------------ setup

    private void rebuild(int sw, int sh, int dw, int dh, int level, Tuning tuning) {
        srcW = sw;
        srcH = sh;
        dstW = dw;
        dstH = dh;
        quality = level;
        builtLevel = level;
        span = (int) Math.ceil(PASS_FACTORS[level]);
        built = tuning;

        out = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB);

        buildGamma(tuning.gamma);
        buildWarp(tuning.curvature);
        buildScanAndMask(tuning);

        glowW = Math.max(1, dw / GLOW_DIVISOR);
        glowH = Math.max(1, dh / GLOW_DIVISOR);
        glowA = new int[glowW * glowH];
        glowB = new int[glowW * glowH];
        glowCol = new int[dw];
        for (int x = 0; x < dw; x++) {
            glowCol[x] = x * glowW / dw;
        }
    }

    private static int[] pixels(BufferedImage image) {
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    private void buildGamma(double gamma) {
        if (builtGamma == gamma) {
            return;
        }
        for (int i = 0; i < 256; i++) {
            linQ[i] = (int) Math.round(Math.pow(i / 255.0, 2.2) * LIN);
        }
        for (int i = 0; i < ENC_LEN; i++) {
            double linear = i / (double) LIN;
            int v = (int) Math.round(Math.pow(linear, 1.0 / gamma) * 255.0);
            enc[i] = Math.max(0, Math.min(255, v));
        }
        builtGamma = gamma;
    }

    /**
     * How much of the half-extent to leave as bezel, as a fraction. The barrel
     * already bends the raster's outline, so the picture has to sit slightly
     * inside the frame for that whole curved edge to be visible: at this value
     * the middle of each edge clears the frame by 5% and the corners by about
     * twice that, which is the shape of a tube's face.
     */
    private static final double EDGE_INSET = 0.05;

    /**
     * Destination to source coordinate table.
     *
     * <p>The lens model is anisotropic, x' = x * (1 + k*y^2) and the mirror of
     * that for y. The middle of each edge of the picture stays put while the
     * corners pull outside the source, so the raster's outline bows outwards on
     * all four sides, the way a tube's face does.
     *
     * <p>That outline is what makes the glass look curved, so the picture is
     * pulled inwards first and the overrun is left as dark bezel rather than
     * clamped to the edge. Without the pull-in the curve would run off the
     * frame at the middle of each edge and the picture would only be curved
     * where the corners happened to be clipped.
     */
    private void buildWarp(double curvature) {
        warpSx = new int[dstW * dstH];
        warpSy = new int[dstW * dstH];
        double k = curvature;
        double zoom = 1.0 / (1.0 - EDGE_INSET);
        // Last column and row a whole sampling window can start on.
        int lastCol = Math.max(0, srcW - span);
        int lastRow = Math.max(0, srcH - 1 - span);

        for (int y = 0; y < dstH; y++) {
            double qy = (((y + 0.5) / dstH) * 2.0 - 1.0) * zoom;
            int row = y * dstW;
            for (int x = 0; x < dstW; x++) {
                double qx = (((x + 0.5) / dstW) * 2.0 - 1.0) * zoom;
                double u = qx * (1.0 + k * qy * qy);
                double v = qy * (1.0 + k * qx * qx);
                int i = row + x;

                if (u <= -1.0 || u >= 1.0 || v <= -1.0 || v >= 1.0) {
                    // Past the edge of the face: bezel, not phosphor.
                    warpSy[i] = -1;
                    continue;
                }
                // Clamp here so the filter loop needs no bounds checks: the
                // window always fits inside the picture.
                double sxPix = (u * 0.5 + 0.5) * (srcW - 1);
                double syPix = (v * 0.5 + 0.5) * (srcH - 1);
                if (sxPix > lastCol) {
                    sxPix = lastCol;
                }
                if (syPix > lastRow) {
                    syPix = lastRow;
                }
                warpSx[i] = (int) Math.round(sxPix * 65536.0);
                warpSy[i] = (int) Math.round(syPix * 65536.0);
            }
        }
    }

    /**
     * Per-row beam and vignette gain, and per-column gain with the shadow mask
     * folded in, all Q15. Folding the mask here turns three multiplies per
     * pixel into one.
     */
    private void buildScanAndMask(Tuning t) {
        rowQ = new int[dstH];
        colR = new int[dstW];
        colG = new int[dstW];
        colB = new int[dstW];

        // Snap the beam period to a whole number of buffer rows, at least two,
        // so scanlines land on a stable grid instead of beating against the
        // buffer resolution.
        int period = Math.max(2, Math.round(dstH / (float) t.scanLines));

        double[] scan = new double[dstH];
        double total = 0.0;
        for (int y = 0; y < dstH; y++) {
            double beam = 0.5 + 0.5 * Math.cos(2.0 * Math.PI * y / period);
            double shape = Math.pow(beam, t.scanBeam);
            double gain = 1.0 - t.scanStrength * (1.0 - shape);
            scan[y] = gain;
            total += gain;
        }
        // Normalise to a mean of one, so the filter darkens bands of the
        // picture but never the picture as a whole.
        double norm = dstH / total;

        for (int y = 0; y < dstH; y++) {
            double py = ((y + 0.5) / dstH) * 2.0 - 1.0;
            double vig = 1.0 - t.vignette * py * py;
            rowQ[y] = clampGain(scan[y] * norm * vig);
        }

        double dark = 1.0 - t.maskStrength;
        // The mask dims two thirds of the light, so lift it back up.
        double boost = 3.0 / (1.0 + 2.0 * dark);
        // Pull each phosphor toward the plain average, keeping a hint of tint.
        double average = (1.0 + 2.0 * dark) / 3.0;
        double lit = 1.0 + (average - 1.0) * MASK_DESATURATION;
        double dim = dark + (average - dark) * MASK_DESATURATION;

        for (int x = 0; x < dstW; x++) {
            double px = ((x + 0.5) / dstW) * 2.0 - 1.0;
            double vig = 1.0 - t.vignette * px * px;
            // Aperture grille: every third column is a different phosphor.
            int dot = x % 3;
            colR[x] = clampGain(vig * (dot == 0 ? lit : dim) * boost);
            colG[x] = clampGain(vig * (dot == 1 ? lit : dim) * boost);
            colB[x] = clampGain(vig * (dot == 2 ? lit : dim) * boost);
        }
    }

    private static int clampGain(double gain) {
        int v = (int) Math.round(gain * ONE);
        return Math.max(0, Math.min(GAIN_CAP, v));
    }

    // ------------------------------------------------------------- the passes

    /**
     * Builds the phosphor bleed buffer: a quarter-scale copy of the scene in
     * warped screen space, softened, so bright glyphs leave a faint halo. Cheap
     * because it is 1/4 of an already reduced buffer.
     */
    private void buildGlow(int[] src) {
        if (built.bloom <= 0.0) {
            java.util.Arrays.fill(glowA, 0);
            return;
        }

        int limitX = Math.max(0, srcW - 2);
        int limitY = Math.max(0, srcH - 2);

        for (int gy = 0; gy < glowH; gy++) {
            int dy = Math.min(dstH - 1, gy * dstH / glowH);
            int drow = dy * dstW;
            int grow = gy * glowW;

            for (int gx = 0; gx < glowW; gx++) {
                int dx = Math.min(dstW - 1, gx * dstW / glowW);
                int warpedY = warpSy[drow + dx];
                if (warpedY < 0) {
                    // Bezel, so there is no phosphor here to bleed.
                    glowA[grow + gx] = 0;
                    continue;
                }

                int sx = Math.min(limitX, warpSx[drow + dx] >>> 16);
                int sy = Math.min(limitY, warpedY >>> 16);
                int srow = sy * srcW + sx;

                // 2x2 average, so thin bright strokes still register.
                int p0 = src[srow];
                int p1 = src[srow + 1];
                int p2 = src[srow + srcW];
                int p3 = src[srow + srcW + 1];
                int r = (((p0 >> 16) & 255) + ((p1 >> 16) & 255)
                    + ((p2 >> 16) & 255) + ((p3 >> 16) & 255)) >> 2;
                int g = (((p0 >> 8) & 255) + ((p1 >> 8) & 255)
                    + ((p2 >> 8) & 255) + ((p3 >> 8) & 255)) >> 2;
                int b = ((p0 & 255) + (p1 & 255) + (p2 & 255) + (p3 & 255)) >> 2;
                glowA[grow + gx] = (r << 16) | (g << 8) | b;
            }
        }

        for (int pass = 0; pass < GLOW_BLUR_PASSES; pass++) {
            separableBlur(glowA, glowB, glowA, GLOW_BLUR_RADIUS);
        }
    }

    /**
     * Horizontal then vertical box blur, edge-clamped, so the cost is linear in
     * the radius instead of quadratic and there is no division in the loop.
     */
    private void separableBlur(int[] from, int[] tmp, int[] to, int radius) {
        int taps = radius * 2 + 1;
        int inv = 65536 / taps;

        for (int y = 0; y < glowH; y++) {
            int row = y * glowW;
            for (int x = 0; x < glowW; x++) {
                int r = 0;
                int g = 0;
                int b = 0;
                for (int k = -radius; k <= radius; k++) {
                    int xx = x + k;
                    if (xx < 0) {
                        xx = 0;
                    } else if (xx >= glowW) {
                        xx = glowW - 1;
                    }
                    int p = from[row + xx];
                    r += (p >> 16) & 255;
                    g += (p >> 8) & 255;
                    b += p & 255;
                }
                tmp[row + x] = (((r * inv) >>> 16) << 16)
                    | (((g * inv) >>> 16) << 8)
                    | ((b * inv) >>> 16);
            }
        }

        for (int y = 0; y < glowH; y++) {
            int row = y * glowW;
            for (int x = 0; x < glowW; x++) {
                int r = 0;
                int g = 0;
                int b = 0;
                for (int k = -radius; k <= radius; k++) {
                    int yy = y + k;
                    if (yy < 0) {
                        yy = 0;
                    } else if (yy >= glowH) {
                        yy = glowH - 1;
                    }
                    int p = tmp[yy * glowW + x];
                    r += (p >> 16) & 255;
                    g += (p >> 8) & 255;
                    b += p & 255;
                }
                to[row + x] = (((r * inv) >>> 16) << 16)
                    | (((g * inv) >>> 16) << 8)
                    | ((b * inv) >>> 16);
            }
        }
    }

    /** The main pass: warp, pick down, beam, mask, gamma, bloom. */
    private void filter(int[] src, int[] dst) {
        final int[] lin = linQ;
        final int[] encTable = enc;
        final int[] hazeBuf = glowA;
        final int[] gcol = glowCol;
        final int[] rowGain = rowQ;
        final int[] cr = colR;
        final int[] cg = colG;
        final int[] cb = colB;
        final int q = span;
        final int sw = srcW;
        final int dw = dstW;
        final int dh = dstH;
        final int rows = q + 1;
        final int rowRecip = 65536 / rows;
        final int bloom = (int) Math.round(built.bloom * 256.0);
        final int gw = glowW;
        final int gh = glowH;

        for (int y = 0; y < dh; y++) {
            int rowBase = y * dw;
            int hazeBase = (y * gh / dh) * gw;
            int rq = rowGain[y];

            for (int x = 0; x < dw; x++) {
                int i = rowBase + x;
                int syFix = warpSy[i];
                if (syFix < 0) {
                    // Outside the curved edge of the face.
                    dst[i] = 0;
                    continue;
                }

                // The warp stretches the picture towards the edges, so one
                // filtered row can straddle more source rows than the pass
                // scale. Cover them all or thin strokes disappear.
                int i0 = (syFix >>> 16) * sw + (warpSx[i] >>> 16);

                int r0;
                int g0;
                int b0;
                if (q == 1) {
                    // The default path, unrolled. With no columns to combine
                    // and two rows to average, the loop machinery is nearly all
                    // of the work here.
                    int p0 = src[i0];
                    int p1 = src[i0 + sw];
                    r0 = (((p0 >> 16) & 255) + ((p1 >> 16) & 255)) >> 1;
                    g0 = (((p0 >> 8) & 255) + ((p1 >> 8) & 255)) >> 1;
                    b0 = ((p0 & 255) + (p1 & 255)) >> 1;
                } else {
                    int rSum = 0;
                    int gSum = 0;
                    int bSum = 0;
                    for (int k = 0; k < rows; k++) {
                        int base = i0 + k * sw;
                        int mr = 0;
                        int mg = 0;
                        int mb = 0;
                        for (int c = 0; c < q; c++) {
                            int p = src[base + c];
                            mr = Math.max(mr, (p >> 16) & 255);
                            mg = Math.max(mg, (p >> 8) & 255);
                            mb = Math.max(mb, p & 255);
                        }
                        rSum += mr;
                        gSum += mg;
                        bSum += mb;
                    }
                    r0 = (rSum * rowRecip) >>> 16;
                    g0 = (gSum * rowRecip) >>> 16;
                    b0 = (bSum * rowRecip) >>> 16;
                }

                int haze = hazeBuf[hazeBase + gcol[x]];

                if ((r0 | g0 | b0) == 0) {
                    // Background. Only the phosphor bleed can light this pixel,
                    // and across most of a terminal nothing does.
                    if (haze == 0) {
                        dst[i] = 0;
                        continue;
                    }
                    int hr = (((haze >> 16) & 255) * bloom) >> 8;
                    int hg = (((haze >> 8) & 255) * bloom) >> 8;
                    int hb = ((haze & 255) * bloom) >> 8;
                    dst[i] = (hr << 16) | (hg << 8) | hb;
                    continue;
                }

                int lr = lin[r0];
                int lg = lin[g0];
                int lb = lin[b0];

                int oR = encTable[(lr * ((rq * cr[x]) >> 15)) >> 15];
                int oG = encTable[(lg * ((rq * cg[x]) >> 15)) >> 15];
                int oB = encTable[(lb * ((rq * cb[x]) >> 15)) >> 15];

                if (bloom > 0) {
                    oR += (((haze >> 16) & 255) * bloom) >> 8;
                    oG += (((haze >> 8) & 255) * bloom) >> 8;
                    oB += ((haze & 255) * bloom) >> 8;
                    oR = oR > 255 ? 255 : oR;
                    oG = oG > 255 ? 255 : oG;
                    oB = oB > 255 ? 255 : oB;
                }

                dst[i] = (oR << 16) | (oG << 8) | oB;
            }
        }
    }
}
