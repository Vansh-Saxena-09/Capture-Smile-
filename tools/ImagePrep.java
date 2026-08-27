import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Build-time asset tool: normalises the downloaded actor portraits into uniform
 * 3:4 JPEGs so the character emblem is always the same shape and the payload stays small.
 *
 *   javac -encoding UTF-8 -d out-tools tools/ImagePrep.java
 *   java -cp out-tools ImagePrep assets-raw web/static/img/actors
 *
 * Cropping is biased toward the top of the frame (TOP_BIAS) because these are
 * head-and-shoulders portraits — centre-cropping a tall photo would shave off foreheads.
 */
public final class ImagePrep {

    private static final int TARGET_W = 360;
    private static final int TARGET_H = 480;   // 3:4, matching .emblem's aspect-ratio
    private static final float QUALITY = 0.86f;
    private static final double TOP_BIAS = 0.15; // 0.0 = keep very top, 0.5 = centre

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: ImagePrep <inDir> <outDir>");
            System.exit(2);
        }
        File inDir = new File(args[0]);
        File outDir = new File(args[1]);
        if (!outDir.exists() && !outDir.mkdirs()) {
            System.err.println("cannot create " + outDir);
            System.exit(1);
        }

        File[] files = inDir.listFiles((d, n) -> {
            String l = n.toLowerCase(Locale.ROOT);
            return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png");
        });
        if (files == null || files.length == 0) {
            System.err.println("no images in " + inDir);
            System.exit(1);
        }
        java.util.Arrays.sort(files);

        long rawTotal = 0, outTotal = 0;
        int ok = 0, failed = 0;
        for (File f : files) {
            String id = f.getName().replaceFirst("\\.[^.]+$", "");
            File out = new File(outDir, id + ".jpg");
            try {
                BufferedImage src = ImageIO.read(f);
                if (src == null) throw new java.io.IOException("unreadable image");
                writeJpeg(fit(src), out);
                rawTotal += f.length();
                outTotal += out.length();
                ok++;
                System.out.printf(Locale.ROOT, "  %-20s %4dx%-4d %6dKB -> %5dKB%n",
                        id, src.getWidth(), src.getHeight(), f.length() / 1024, out.length() / 1024);
            } catch (Exception e) {
                failed++;
                System.out.printf(Locale.ROOT, "  %-20s FAILED: %s%n", id, e.getMessage());
            }
        }
        System.out.printf(Locale.ROOT, "%nprepped %d image(s), %d failed — %dKB -> %dKB%n",
                ok, failed, rawTotal / 1024, outTotal / 1024);
        if (failed > 0) System.exit(1);
    }

    /** Scale to cover TARGET_W x TARGET_H, then crop — horizontally centred, vertically top-biased. */
    private static BufferedImage fit(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        double scale = Math.max((double) TARGET_W / w, (double) TARGET_H / h);
        int sw = (int) Math.ceil(w * scale), sh = (int) Math.ceil(h * scale);
        int dx = (TARGET_W - sw) / 2;
        int dy = (int) Math.round((TARGET_H - sh) * TOP_BIAS);

        BufferedImage dst = new BufferedImage(TARGET_W, TARGET_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        // JPEG has no alpha channel; fill first so any transparent PNG edge lands on brand navy.
        g.setColor(new Color(0x13, 0x23, 0x3B));
        g.fillRect(0, 0, TARGET_W, TARGET_H);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, dx, dy, sw, sh, null);
        g.dispose();
        return dst;
    }

    private static void writeJpeg(BufferedImage img, File out) throws java.io.IOException {
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
        if (!it.hasNext()) throw new java.io.IOException("no JPEG writer available");
        ImageWriter writer = it.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(img, null, null), p);
        } finally {
            writer.dispose();
        }
    }

    private ImagePrep() {}
}
