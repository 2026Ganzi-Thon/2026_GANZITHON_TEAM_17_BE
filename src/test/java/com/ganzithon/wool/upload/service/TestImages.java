package com.ganzithon.wool.upload.service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

// 위치 정보 제거 테스트용 사진 만들기. 실제로 그려지는 JPEG/PNG를 만들고, 휴대폰 사진처럼 부가 정보를 끼워 넣는다.
public final class TestImages {

    public static final String GPS_TEXT = "GPSLatitude=37.5665 GPSLongitude=126.9780 Samsung SM-G991N";

    private TestImages() {
    }

    public static BufferedImage picture() {
        BufferedImage image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 64; x++) {
                image.setRGB(x, y, ((x * 4) << 16) | ((y * 5) << 8) | ((x + y) * 2 % 256));
            }
        }
        return image;
    }

    public static byte[] jpeg(boolean progressive) {
        try {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (progressive) {
                param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            }
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.9f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writer.setOutput(new MemoryCacheImageOutputStream(out));
            writer.write(null, new IIOImage(picture(), null, null), param);
            writer.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // 실제 JPEG 뒤에 0 바이트를 덧붙여 원하는 크기로 만든다. (첫 사진이 끝난 뒤의 데이터라 저장할 때는 버려진다)
    public static byte[] jpegOfSize(int size) {
        byte[] base = jpeg(false);
        return size <= base.length ? base : concat(base, new byte[size - base.length]);
    }

    public static byte[] pngTail(byte[] png) {
        return java.util.Arrays.copyOfRange(png, indexOf(png, ascii("IDAT")) - 4, png.length);
    }

    // 화면 데이터가 시작되는 SOS 마커부터 끝까지
    public static byte[] jpegTail(byte[] jpeg) {
        return java.util.Arrays.copyOfRange(jpeg, indexOf(jpeg, new byte[]{(byte) 0xFF, (byte) 0xDA}), jpeg.length);
    }

    public static byte[] png() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(picture(), "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static BufferedImage decode(byte[] content) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new IllegalStateException("그림으로 읽히지 않습니다.");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean samePixels(byte[] a, byte[] b) {
        BufferedImage x = decode(a);
        BufferedImage y = decode(b);
        if (x.getWidth() != y.getWidth() || x.getHeight() != y.getHeight()) {
            return false;
        }
        for (int j = 0; j < x.getHeight(); j++) {
            for (int i = 0; i < x.getWidth(); i++) {
                if (x.getRGB(i, j) != y.getRGB(i, j)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---------- JPEG 조각 ----------
    public static byte[] segment(int marker, byte[] payload) {
        int length = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(marker);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.write(payload, 0, payload.length);
        return out.toByteArray();
    }

    // orientation이 0이면 회전 항목 없이 위치 문구만 든 EXIF를 만든다.
    public static byte[] exifWithGps(int orientation) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        byte[] header = {'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08};
        tiff.write(header, 0, header.length);
        if (orientation > 0) {
            byte[] ifd = {0x00, 0x01, 0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, (byte) orientation, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
            tiff.write(ifd, 0, ifd.length);
        } else {
            tiff.write(new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00}, 0, 6);
        }
        byte[] gps = GPS_TEXT.getBytes(StandardCharsets.US_ASCII);
        tiff.write(gps, 0, gps.length);
        return concat("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), tiff.toByteArray());
    }

    // SOI 바로 뒤에 조각들을 끼워 넣는다.
    public static byte[] insertAfterSoi(byte[] jpeg, byte[]... segments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        for (byte[] segment : segments) {
            out.write(segment, 0, segment.length);
        }
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    public static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }

    public static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    // ---------- PNG 조각 ----------
    public static byte[] pngChunk(String type, byte[] data) {
        byte[] typeBytes = ascii(type);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(data.length >>> 24);
        out.write(data.length >>> 16);
        out.write(data.length >>> 8);
        out.write(data.length);
        out.write(typeBytes, 0, 4);
        out.write(data, 0, data.length);
        long value = crc.getValue();
        out.write((int) (value >>> 24));
        out.write((int) (value >>> 16));
        out.write((int) (value >>> 8));
        out.write((int) value);
        return out.toByteArray();
    }

    // PNG의 IDAT(화면 데이터) 바로 앞에 조각들을 끼워 넣는다.
    public static byte[] insertBeforeIdat(byte[] png, byte[]... chunks) {
        int idat = indexOf(png, ascii("IDAT")) - 4;
        return concat(java.util.Arrays.copyOfRange(png, 0, idat), concat(chunks), java.util.Arrays.copyOfRange(png, idat, png.length));
    }

    public static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public static boolean contains(byte[] haystack, String text) {
        return indexOf(haystack, ascii(text)) >= 0;
    }
}
