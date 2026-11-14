package com.ganzithon.wool.upload.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ImageFormatTest {

    static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    }

    static byte[] png() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    }

    static byte[] webp() {
        return new byte[]{'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    }

    @Test
    void 파일_맨_앞_바이트로_형식을_판단한다() {
        assertThat(ImageFormat.detect(jpeg())).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.detect(png())).contains(ImageFormat.PNG);
        assertThat(ImageFormat.detect(webp())).contains(ImageFormat.WEBP);
    }

    @Test
    void 확장자_이름은_형식마다_정해져_있다() {
        assertThat(ImageFormat.JPEG.extension()).isEqualTo("jpg");
        assertThat(ImageFormat.PNG.extension()).isEqualTo("png");
        assertThat(ImageFormat.WEBP.extension()).isEqualTo("webp");
    }

    @Test
    void 허용하지_않는_형식은_판단하지_않는다() {
        assertThat(ImageFormat.detect("GIF89a....".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageFormat.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageFormat.detect("<html><script>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormat.detect("그냥 글자".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormat.detect(new byte[]{'M', 'Z', (byte) 0x90, 0})).isEmpty();
    }

    @Test
    void 너무_짧거나_비어_있으면_판단하지_않는다() {
        assertThat(ImageFormat.detect(new byte[0])).isEmpty();
        assertThat(ImageFormat.detect(new byte[]{(byte) 0xFF})).isEmpty();
        assertThat(ImageFormat.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(ImageFormat.detect(Arrays.copyOf(png(), 7))).isEmpty();
    }

    @Test
    void WebP는_RIFF만으로는_부족하고_WEBP_표지가_있어야_한다() {
        byte[] wave = new byte[]{'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' '};
        byte[] shortRiff = new byte[]{'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B'};

        assertThat(ImageFormat.detect(wave)).isEmpty();
        assertThat(ImageFormat.detect(shortRiff)).isEmpty();
    }

    @Test
    void 시그니처가_같아도_앞부분이_어긋나면_거부한다() {
        byte[] almostPng = png();
        almostPng[1] = 'X';
        byte[] almostJpeg = jpeg();
        almostJpeg[2] = 0x00;

        assertThat(ImageFormat.detect(almostPng)).isEmpty();
        assertThat(ImageFormat.detect(almostJpeg)).isEmpty();
    }
}
