import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Recreates the CRT icon using the game's bundled DOS font; no extra dependencies. */
public class GenerateIcons {
    private static final Path ROOT = Path.of("src/main/resources/assets/icons");
    private static final int[] SIZES = {16, 32, 48, 64, 128, 256, 512, 1024};

    public static void main(String[] args) throws Exception {
        Files.createDirectories(ROOT.resolve("dream.iconset"));
        Font font = Font.createFont(Font.TRUETYPE_FONT,
            Path.of("src/main/resources/assets/pixelFont.ttf").toFile());
        Map<Integer, byte[]> images = new LinkedHashMap<>();
        for (int size : SIZES) {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(render(size, font), "png", png);
            images.put(size, png.toByteArray());
            Files.write(ROOT.resolve("dream-" + size + ".png"), png.toByteArray());
        }
        writeIco(images);
        writeIcns(images);
        // Native iconutil input, useful when changing the artwork on macOS.
        for (int size : new int[] {16, 32, 128, 256, 512}) {
            Files.write(ROOT.resolve("dream.iconset/icon_" + size + "x" + size + ".png"),
                images.get(size));
            Files.write(ROOT.resolve("dream.iconset/icon_" + size + "x" + size + "@2x.png"),
                images.get(size * 2));
        }
        System.out.println("Generated PNG, ICO, ICNS and iconset assets in " + ROOT);
    }

    private static BufferedImage render(int size, Font font) {
        // Supersample the outline; give small icons a simpler, larger prompt.
        int resolution = Math.max(size, 256);
        BufferedImage image = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.scale(resolution / 1024.0, resolution / 1024.0);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

        RoundRectangle2D bezel = new RoundRectangle2D.Double(64, 64, 896, 896, 192, 192);
        g.setPaint(new GradientPaint(64, 64, new Color(94, 98, 99), 900, 960, new Color(35, 37, 38)));
        g.fill(bezel);
        g.setStroke(new BasicStroke(5));
        g.setColor(new Color(135, 140, 142));
        g.draw(bezel);
        RoundRectangle2D glass = new RoundRectangle2D.Double(96, 96, 832, 832, 148, 148);
        g.setPaint(new RadialGradientPaint(512, 490, 610,
            new float[] {0, 1}, new Color[] {new Color(30, 30, 30), new Color(12, 16, 14)}));
        g.fill(glass);
        g.setClip(glass);

        boolean detailed = size >= 128;
        if (detailed) {
            drawCentered(g, font.deriveFont(100f), "D.R.E.A.M", 326, new Color(235, 239, 235));
            g.setColor(new Color(93, 101, 94));
            g.fillRect(226, 747, 264, 12);
            g.fillRect(226, 784, 172, 12);
            g.setColor(new Color(0, 125, 0));
            g.fillRect(416, 784, 56, 12);
        }

        Font prompt = font.deriveFont(detailed ? 408f : 500f);
        int baseline = detailed ? 654 : 625;
        // The game uses Color.GREEN for boot text and the input prompt.
        drawCentered(g, prompt, ">_", baseline, Color.GREEN);

        // Same quadratic bow as CurveCanvas.rebuildScanlines(), restrained for icon scale.
        g.setColor(new Color(60, 60, 60, detailed ? 65 : 35));
        g.setStroke(new BasicStroke(2));
        for (int y = 108; y < 928; y += 13) {
            Path2D line = new Path2D.Double();
            line.moveTo(96, y);
            line.quadTo(512, y + (y - 512) * 0.3, 928, y);
            g.draw(line);
        }
        g.setClip(null);
        g.setColor(new Color(0, 0, 0, 110));
        g.setStroke(new BasicStroke(9));
        g.draw(glass);
        g.dispose();

        if (resolution == size) return image;
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D small = scaled.createGraphics();
        small.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        small.drawImage(image, 0, 0, size, size, null);
        small.dispose();
        return scaled;
    }

    private static void drawCentered(Graphics2D g, Font font, String text, int y, Color color) {
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, (1024 - g.getFontMetrics().stringWidth(text)) / 2, y);
    }

    private static void writeIco(Map<Integer, byte[]> images) throws Exception {
        int[] sizes = {16, 32, 48, 64, 128, 256};
        int length = 6 + sizes.length * 16;
        for (int size : sizes) length += images.get(size).length;
        ByteBuffer ico = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        ico.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = 6 + sizes.length * 16;
        for (int size : sizes) {
            byte[] png = images.get(size);
            ico.put((byte) (size == 256 ? 0 : size)).put((byte) (size == 256 ? 0 : size));
            ico.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
            ico.putInt(png.length).putInt(offset);
            offset += png.length;
        }
        for (int size : sizes) ico.put(images.get(size));
        Files.write(ROOT.resolve("dream.ico"), ico.array());
    }

    private static void writeIcns(Map<Integer, byte[]> images) throws Exception {
        String[] types = {"icp4", "icp5", "icp6", "ic07", "ic08", "ic09", "ic10",
            "ic11", "ic12", "ic13", "ic14"};
        int[] sizes = {16, 32, 64, 128, 256, 512, 1024, 32, 64, 256, 512};
        ByteArrayOutputStream chunks = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(chunks)) {
            for (int i = 0; i < types.length; i++) {
                byte[] png = images.get(sizes[i]);
                out.writeBytes(types[i]);
                out.writeInt(png.length + 8);
                out.write(png);
            }
        }
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(ROOT.resolve("dream.icns")))) {
            out.writeBytes("icns");
            out.writeInt(chunks.size() + 8);
            chunks.writeTo(out);
        }
    }
}
