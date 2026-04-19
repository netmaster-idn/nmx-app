package com.netmaster.nmx.service;

import com.lowagie.text.pdf.BarcodeDatamatrix;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class InvoiceMatrixCodeService {

    public String buildImageDataUri(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }

        try {
            BarcodeDatamatrix matrix = new BarcodeDatamatrix();
            int status = matrix.generate(payload);
            if (status == BarcodeDatamatrix.DM_NO_ERROR) {
                Image image = matrix.createAwtImage(Color.BLACK, Color.WHITE);
                return toPngDataUri(image);
            }
        } catch (Exception ignored) {
            // Falls back to deterministic SVG if built-in matrix generation fails.
        }

        return buildFallbackSvgDataUri(payload);
    }

    private String toPngDataUri(Image image) throws Exception {
        int sourceWidth = Math.max(image.getWidth(null), 1);
        int sourceHeight = Math.max(image.getHeight(null), 1);
        int scale = Math.max(4, 160 / Math.max(sourceWidth, sourceHeight));
        BufferedImage bufferedImage = new BufferedImage(sourceWidth * scale, sourceHeight * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = bufferedImage.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight());
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(image, 0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), null);
        graphics.dispose();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", outputStream);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    private String buildFallbackSvgDataUri(String payload) {
        int size = 21;
        boolean[][] modules = buildModules(payload, size);
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ")
                .append(size)
                .append(" ")
                .append(size)
                .append("' shape-rendering='crispEdges'>")
                .append("<rect width='100%' height='100%' fill='white'/>");

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (modules[y][x]) {
                    svg.append("<rect x='").append(x).append("' y='").append(y).append("' width='1' height='1' fill='#111'/>");
                }
            }
        }
        svg.append("</svg>");
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.toString().getBytes(StandardCharsets.UTF_8));
    }

    private boolean[][] buildModules(String payload, int size) {
        boolean[][] modules = new boolean[size][size];
        byte[] hash = digest(payload);

        addFinder(modules, 0, 0);
        addFinder(modules, size - 7, 0);
        addFinder(modules, 0, size - 7);

        int bitIndex = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (isFinderArea(size, x, y)) {
                    continue;
                }
                int byteIndex = (bitIndex / 8) % hash.length;
                int bitOffset = bitIndex % 8;
                boolean value = ((hash[byteIndex] >> bitOffset) & 1) == 1;
                if (((x + y) % 3) == 0) {
                    value = !value;
                }
                modules[y][x] = value;
                bitIndex++;
            }
        }
        return modules;
    }

    private void addFinder(boolean[][] modules, int startX, int startY) {
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 7; x++) {
                int absX = startX + x;
                int absY = startY + y;
                boolean border = x == 0 || y == 0 || x == 6 || y == 6;
                boolean center = x >= 2 && x <= 4 && y >= 2 && y <= 4;
                modules[absY][absX] = border || center;
            }
        }
    }

    private boolean isFinderArea(int size, int x, int y) {
        return (x < 7 && y < 7)
                || (x >= size - 7 && y < 7)
                || (x < 7 && y >= size - 7);
    }

    private byte[] digest(String payload) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return payload.getBytes(StandardCharsets.UTF_8);
        }
    }
}
