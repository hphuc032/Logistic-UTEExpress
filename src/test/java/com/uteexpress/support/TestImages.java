package com.uteexpress.support;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class TestImages {
    private static final String WEBP_ONE_PIXEL =
            "UklGRh4AAABXRUJQVlA4TBEAAAAvAAAAAAfQ//73v/+BiOh/AAA=";

    private TestImages() { }

    public static byte[] jpeg() { return image("jpeg", 2, 2); }
    public static byte[] png() { return image("png", 2, 2); }
    public static byte[] oversizedDimensionPng() { return image("png", 4097, 1); }
    public static byte[] webp() { return Base64.getDecoder().decode(WEBP_ONE_PIXEL); }

    private static byte[] image(String format, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.ORANGE.getRGB());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalStateException("Test image writer is unavailable: " + format);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
