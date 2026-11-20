package com.ganzithon.wool.upload.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static com.ganzithon.wool.upload.service.TestImages.GPS_TEXT;
import static com.ganzithon.wool.upload.service.TestImages.ascii;
import static com.ganzithon.wool.upload.service.TestImages.concat;
import static com.ganzithon.wool.upload.service.TestImages.contains;
import static com.ganzithon.wool.upload.service.TestImages.exifWithGps;
import static com.ganzithon.wool.upload.service.TestImages.indexOf;
import static com.ganzithon.wool.upload.service.TestImages.insertAfterSoi;
import static com.ganzithon.wool.upload.service.TestImages.insertBeforeIdat;
import static com.ganzithon.wool.upload.service.TestImages.pngChunk;
import static com.ganzithon.wool.upload.service.TestImages.samePixels;
import static com.ganzithon.wool.upload.service.TestImages.segment;
import static org.assertj.core.api.Assertions.assertThat;

// 사진의 촬영 위치·기기 정보를 지우되, 그림(픽셀)과 회전 방향은 그대로여야 한다.
class ImageMetadataStripperTest {

    private static byte[] withAllMetadata(byte[] jpeg) {
        return insertAfterSoi(jpeg,
                segment(0xE1, exifWithGps(0)),
                segment(0xE1, concat(ascii("http://ns.adobe.com/xap/1.0/\0"), ascii("<x:xmpmeta>" + GPS_TEXT + "</x:xmpmeta>"))),
                segment(0xED, concat(ascii("Photoshop 3.0\0"), ascii("editor=" + GPS_TEXT))),
                segment(0xFE, ascii("comment " + GPS_TEXT)),
                segment(0xE2, concat(ascii("MPF\0"), ascii(GPS_TEXT))));
    }

    // ---------- JPEG ----------

    @Test
    void 위치_기기_정보와_설명을_모두_지우고_그림은_그대로_둔다() {
        byte[] original = TestImages.jpeg(false);
        byte[] dirty = withAllMetadata(original);
        assertThat(contains(dirty, "GPSLatitude")).isTrue();

        byte[] clean = ImageMetadataStripper.strip(ImageFormat.JPEG, dirty);

        assertThat(contains(clean, "GPSLatitude")).isFalse();
        assertThat(contains(clean, "SM-G991N")).isFalse();
        assertThat(contains(clean, "xmpmeta")).isFalse();
        assertThat(contains(clean, "Photoshop")).isFalse();
        assertThat(contains(clean, "comment")).isFalse();
        assertThat(contains(clean, "Exif")).isFalse();
        assertThat(contains(clean, "MPF")).isFalse();
        // 그림은 다시 압축하지 않고 걷어내기만 하므로 픽셀이 완전히 같다.
        assertThat(samePixels(clean, original)).isTrue();
        // 픽셀이 같은 것과 별개로, 압축된 화면 데이터가 한 바이트도 바뀌지 않았다. (다시 압축하지 않았다)
        assertThat(TestImages.jpegTail(clean)).isEqualTo(TestImages.jpegTail(original));
    }

    @Test
    void 회전_방향은_남기고_나머지_EXIF는_지운다() {
        byte[] original = TestImages.jpeg(false);
        byte[] dirty = insertAfterSoi(original, segment(0xE1, exifWithGps(6)));

        byte[] clean = ImageMetadataStripper.strip(ImageFormat.JPEG, dirty);

        assertThat(contains(clean, "GPSLatitude")).isFalse();
        assertThat(contains(clean, "SM-G991N")).isFalse();
        // 남은 EXIF는 회전 항목 하나(태그 0112, 종류 SHORT, 개수 1, 값 6)뿐이다.
        int exif = indexOf(clean, ascii("Exif\0\0"));
        assertThat(exif).isPositive();
        assertThat(Arrays.copyOfRange(clean, exif - 4, exif - 2)).containsExactly((byte) 0xFF, (byte) 0xE1);
        assertThat(Arrays.copyOfRange(clean, exif + 6 + 8 + 2, exif + 6 + 8 + 2 + 10))
                .containsExactly(0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x06);
        assertThat(samePixels(clean, original)).isTrue();
    }

    @Test
    void 회전이_필요_없는_사진은_EXIF_조각을_아예_남기지_않는다() {
        byte[] original = TestImages.jpeg(false);

        assertThat(contains(ImageMetadataStripper.strip(ImageFormat.JPEG, insertAfterSoi(original, segment(0xE1, exifWithGps(1)))), "Exif")).isFalse();
        assertThat(contains(ImageMetadataStripper.strip(ImageFormat.JPEG, insertAfterSoi(original, segment(0xE1, exifWithGps(0)))), "Exif")).isFalse();
    }

    @Test
    void 사진을_보여_주는_데_필요한_조각은_남긴다() {
        byte[] original = TestImages.jpeg(false);
        byte[] icc = segment(0xE2, concat(ascii("ICC_PROFILE\0"), new byte[]{1, 2, 3}));
        byte[] adobe = segment(0xEE, concat(ascii("Adobe"), new byte[]{0, 100, 0, 0, 0, 0, 1}));

        byte[] clean = ImageMetadataStripper.strip(ImageFormat.JPEG, insertAfterSoi(original, icc, adobe));

        assertThat(contains(clean, "JFIF")).isTrue();
        assertThat(contains(clean, "ICC_PROFILE")).isTrue();
        assertThat(contains(clean, "Adobe")).isTrue();
    }

    @Test
    void 첫_사진이_끝난_뒤_붙어_있는_다른_사진과_데이터는_버린다() {
        byte[] original = TestImages.jpeg(false);
        byte[] second = insertAfterSoi(TestImages.jpeg(false), segment(0xE1, exifWithGps(0)));
        byte[] withTrailer = concat(original, second, ascii("SEFT" + GPS_TEXT));

        byte[] clean = ImageMetadataStripper.strip(ImageFormat.JPEG, withTrailer);

        assertThat(contains(clean, "GPSLatitude")).isFalse();
        assertThat(contains(clean, "SEFT")).isFalse();
        assertThat(Arrays.copyOfRange(clean, clean.length - 2, clean.length)).containsExactly((byte) 0xFF, (byte) 0xD9);
        assertThat(samePixels(clean, original)).isTrue();
    }

    @Test
    void 여러_번에_나눠_압축된_사진도_그림이_그대로다() {
        byte[] original = TestImages.jpeg(true);

        byte[] clean = ImageMetadataStripper.strip(ImageFormat.JPEG, withAllMetadata(original));

        assertThat(contains(clean, "GPSLatitude")).isFalse();
        assertThat(samePixels(clean, original)).isTrue();
    }

    @Test
    void 두_번_지워도_결과가_같다() {
        byte[] once = ImageMetadataStripper.strip(ImageFormat.JPEG, withAllMetadata(TestImages.jpeg(false)));

        assertThat(ImageMetadataStripper.strip(ImageFormat.JPEG, once)).isEqualTo(once);
    }

    @Test
    void 식별자가_맞지_않는_APP0_APP2_APP14는_지운다() {
        byte[] original = TestImages.jpeg(false);
        byte[] odd = insertAfterSoi(original,
                segment(0xE0, concat(ascii("JFXX\0"), ascii(GPS_TEXT))),
                segment(0xE2, concat(ascii("MAKERNOTE\0"), ascii(GPS_TEXT))),
                segment(0xEE, concat(ascii("Vendor"), ascii(GPS_TEXT))));

        byte[] clean = ImageMetadataStripper.strip(ImageFormat.JPEG, odd);

        assertThat(contains(clean, "GPSLatitude")).isFalse();
        assertThat(contains(clean, "MAKERNOTE")).isFalse();
        assertThat(contains(clean, "JFXX")).isFalse();
        assertThat(contains(clean, "JFIF")).isTrue(); // 원래 있던 JFIF는 남는다.
    }

    @Test
    void 구조를_읽을_수_없는_JPEG는_받지_않는다() {
        byte[] fake = new byte[100];
        fake[0] = (byte) 0xFF;
        fake[1] = (byte) 0xD8;
        fake[2] = (byte) 0xFF;
        byte[] truncated = Arrays.copyOf(withAllMetadata(TestImages.jpeg(false)), 120);

        assertThatThrownBy(() -> ImageMetadataStripper.strip(ImageFormat.JPEG, fake)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageMetadataStripper.strip(ImageFormat.JPEG, truncated)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- PNG ----------

    @Test
    void PNG의_텍스트_EXIF_저장_시각_조각을_지우고_그림은_그대로_둔다() {
        byte[] original = TestImages.png();
        byte[] dirty = concat(insertBeforeIdat(original,
                pngChunk("tEXt", ascii("Location\0" + GPS_TEXT)),
                pngChunk("eXIf", exifWithGps(0)),
                pngChunk("tIME", new byte[]{7, (byte) 0xEA, 11, 18, 12, 0, 0}),
                pngChunk("iTXt", ascii("XML:com.adobe.xmp\0\0\0\0\0" + GPS_TEXT)),
                pngChunk("zTXt", ascii("Comment\0\0xx"))), ascii("TRAILER" + GPS_TEXT));

        byte[] clean = ImageMetadataStripper.strip(ImageFormat.PNG, dirty);

        for (String gone : new String[]{"tEXt", "eXIf", "tIME", "iTXt", "zTXt", "GPSLatitude", "TRAILER"}) {
            assertThat(contains(clean, gone)).as(gone).isFalse();
        }
        assertThat(contains(clean, "IHDR")).isTrue();
        assertThat(contains(clean, "IDAT")).isTrue();
        assertThat(contains(clean, "IEND")).isTrue();
        assertThat(samePixels(clean, original)).isTrue();
        assertThat(TestImages.pngTail(clean)).isEqualTo(TestImages.pngTail(original));
    }

    // 알려지지 않은 부가 조각(제조사 전용 등)은 지우고, 색·투명도·크기 같은 표준 조각은 남긴다.
    @Test
    void PNG는_허용한_표준_조각만_남기고_알_수_없는_부가_조각은_지운다() {
        byte[] original = TestImages.png();
        byte[] dirty = insertBeforeIdat(original,
                pngChunk("prVt", ascii("private " + GPS_TEXT)),
                pngChunk("gAMA", new byte[]{0, 1, (byte) 0x86, (byte) 0xA0}),
                pngChunk("pHYs", new byte[]{0, 0, 0x0B, 0x13, 0, 0, 0x0B, 0x13, 1}),
                pngChunk("sRGB", new byte[]{0}));

        byte[] clean = ImageMetadataStripper.strip(ImageFormat.PNG, dirty);

        assertThat(contains(clean, "prVt")).isFalse();
        assertThat(contains(clean, "GPSLatitude")).isFalse();
        for (String kept : new String[]{"gAMA", "pHYs", "sRGB"}) {
            assertThat(contains(clean, kept)).as(kept).isTrue();
        }
        assertThat(samePixels(clean, original)).isTrue();
    }

    @Test
    void 구조를_읽을_수_없는_PNG는_받지_않는다() {
        byte[] truncated = Arrays.copyOf(TestImages.png(), 40);

        assertThatThrownBy(() -> ImageMetadataStripper.strip(ImageFormat.PNG, truncated)).isInstanceOf(IllegalArgumentException.class);
    }
}
