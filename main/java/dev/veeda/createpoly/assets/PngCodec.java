package dev.veeda.createpoly.assets;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public final class PngCodec {
    private PngCodec() {}

    public static byte[] encode(BufferedImage image) throws IOException {
        try (var bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", bytes)) throw new IOException("PNG encoder unavailable");
            return bytes.toByteArray();
        }
    }

    public static BufferedImage decode(byte[] data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(data));
    }
}
