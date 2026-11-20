package com.ganzithon.wool.upload.service;

import java.io.ByteArrayOutputStream;

// 사진에 숨어 있는 촬영 위치(GPS), 기기 정보, 촬영 시각, 편집 이력 같은 부가 정보를 지운다.
// 휴대폰으로 찍은 손편지 사진은 위치 정보가 그대로 들어 있고, 업로드한 사진은 주소를 아는 누구나 열 수 있어서 어르신의 집 위치가 샐 수 있다.
//
// 화면에 보이는 그림(픽셀 데이터)은 건드리지 않는다. 다시 압축하지 않고 부가 정보 조각만 걷어내므로 화질이 그대로다.
// 단 사진이 회전되어 보이는지(Orientation)는 EXIF에 들어 있어서, 이 값만은 남긴다. 지워 버리면 세로로 찍은 편지가 옆으로 누워 보인다.
//
// 구조를 해석하지 못하는 파일은 저장하지 않는다. (원본을 그대로 돌려주면 일부러 깨뜨린 파일로 위치 정보 제거를 피해 갈 수 있다)
public final class ImageMetadataStripper {

    private static final int FF = 0xFF;
    private static final int SOI = 0xD8;
    private static final int EOI = 0xD9;
    private static final int SOS = 0xDA;
    private static final int APP0 = 0xE0;
    private static final int APP1 = 0xE1;
    private static final int APP2 = 0xE2;
    private static final int APP14 = 0xEE;
    private static final int APP15 = 0xEF;
    private static final int COM = 0xFE;
    private static final int ORIENTATION_TAG = 0x0112;

    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};
    private static final byte[] JFIF_HEADER = {'J', 'F', 'I', 'F', 0};
    private static final byte[] ICC_HEADER = {'I', 'C', 'C', '_', 'P', 'R', 'O', 'F', 'I', 'L', 'E', 0};
    private static final byte[] ADOBE_HEADER = {'A', 'd', 'o', 'b', 'e'};

    // PNG에서 남기는 부가(ancillary) 조각: 색·투명도·크기·애니메이션처럼 화면을 그리는 데 쓰이는 표준 조각만이다.
    // 필수 조각(IHDR, PLTE, IDAT, IEND 등 첫 글자가 대문자)은 모두 남기고, 이 목록에 없는 부가 조각(EXIF, 텍스트, 저장 시각, 제조사 전용 조각)은 지운다.
    private static final java.util.Set<String> PNG_KEPT_ANCILLARY = java.util.Set.of(
            "iCCP", "sRGB", "gAMA", "cHRM", "sBIT", "tRNS", "bKGD", "pHYs", "hIST", "sPLT",
            "acTL", "fcTL", "fdAT", "cICP", "mDCV", "cLLI");
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private ImageMetadataStripper() {
    }

    // 부가 정보를 지운 사진 내용을 돌려준다. 구조를 해석하지 못하면 IllegalArgumentException을 던진다.
    public static byte[] strip(ImageFormat format, byte[] content) {
        try {
            return switch (format) {
                case JPEG -> stripJpeg(content);
                case PNG -> stripPng(content);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("사진 구조를 읽을 수 없습니다.", e);
        }
    }

    // ---------- JPEG ----------
    // 구조: SOI, 부가 정보 조각들(APPn, COM), 화면 데이터(SOS 뒤), EOI. 필요한 조각(JFIF, ICC 색상 프로필, Adobe 색 변환)만 남긴다.
    // 일부 휴대폰 사진은 EOI 뒤에 미리보기·HDR용 사진이 한 장 더 붙어 있고 거기에도 위치 정보가 있을 수 있어서, 첫 사진이 끝나면 그 뒤는 버린다.
    private static byte[] stripJpeg(byte[] c) {
        if (c.length < 4 || u(c[0]) != FF || u(c[1]) != SOI) {
            throw new IllegalArgumentException("JPEG가 아닙니다.");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(c.length);
        out.write(FF);
        out.write(SOI);
        int pos = 2;
        while (true) {
            while (pos + 1 < c.length && u(c[pos]) == FF && u(c[pos + 1]) == FF) {
                pos++; // 마커 앞에 채워 넣은 FF
            }
            if (pos + 1 >= c.length || u(c[pos]) != FF) {
                throw new IllegalArgumentException("마커를 찾을 수 없습니다.");
            }
            int marker = u(c[pos + 1]);
            if (marker == EOI) {
                out.write(FF);
                out.write(EOI);
                return out.toByteArray();
            }
            if (marker == 0x00) {
                throw new IllegalArgumentException("잘못된 마커입니다.");
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD8)) {
                out.write(FF);
                out.write(marker); // 길이가 없는 마커
                pos += 2;
                continue;
            }
            if (pos + 3 >= c.length) {
                throw new IllegalArgumentException("조각 길이를 읽을 수 없습니다.");
            }
            int length = (u(c[pos + 2]) << 8) | u(c[pos + 3]);
            int end = pos + 2 + length;
            if (length < 2 || end > c.length) {
                throw new IllegalArgumentException("조각 길이가 잘못됐습니다.");
            }
            if (marker == SOS) {
                out.write(c, pos, end - pos);
                pos = copyScanData(c, end, out);
                continue;
            }
            if (marker == APP1 && startsWith(c, pos + 4, end, EXIF_HEADER)) {
                int orientation = readOrientation(c, pos + 4 + EXIF_HEADER.length, end);
                if (orientation >= 2 && orientation <= 8) {
                    writeOrientationOnly(out, orientation);
                }
            } else if (isKept(marker, c, pos + 4, end)) {
                out.write(c, pos, end - pos);
            }
            pos = end;
        }
    }

    // 남기는 조각: JFIF(APP0), ICC 색상 프로필(APP2), Adobe 색 변환(APP14) 각각 식별자까지 확인하고, 화면을 만드는 조각(APPn·COM 이외 전부)
    private static boolean isKept(int marker, byte[] c, int payloadStart, int end) {
        if (marker == APP0) {
            return startsWith(c, payloadStart, end, JFIF_HEADER);
        }
        if (marker == APP14) {
            return startsWith(c, payloadStart, end, ADOBE_HEADER);
        }
        if (marker == APP2) {
            return startsWith(c, payloadStart, end, ICC_HEADER);
        }
        boolean metadata = (marker >= APP1 && marker <= APP15) || marker == COM;
        return !metadata;
    }

    // 압축된 화면 데이터를 그대로 옮긴다. FF 00(채움)과 FF D0~D7(재시작)은 데이터의 일부이고, 그 밖의 FF 뒤 값이 다음 마커다.
    // 마커 시작 위치를 돌려준다.
    private static int copyScanData(byte[] c, int start, ByteArrayOutputStream out) {
        int i = start;
        while (i < c.length) {
            if (u(c[i]) == FF && i + 1 < c.length) {
                int next = u(c[i + 1]);
                if (next == 0x00 || (next >= 0xD0 && next <= 0xD7)) {
                    i += 2;
                    continue;
                }
                if (next == FF) {
                    i++;
                    continue;
                }
                out.write(c, start, i - start);
                return i;
            }
            i++;
        }
        throw new IllegalArgumentException("화면 데이터가 끝나지 않았습니다.");
    }

    // EXIF의 TIFF 영역에서 회전 값(1~8)을 읽는다. 없거나 읽을 수 없으면 0이다.
    private static int readOrientation(byte[] c, int tiff, int end) {
        if (end - tiff < 8) {
            return 0;
        }
        boolean little = c[tiff] == 'I' && c[tiff + 1] == 'I';
        boolean big = c[tiff] == 'M' && c[tiff + 1] == 'M';
        if (!little && !big) {
            return 0;
        }
        long ifd = readInt(c, tiff + 4, little);
        long entries = ifd + 2 <= end - tiff ? readShort(c, tiff + (int) ifd, little) : 0;
        for (int i = 0; i < entries; i++) {
            long entry = tiff + ifd + 2 + 12L * i;
            if (entry + 12 > end) {
                return 0;
            }
            if (readShort(c, (int) entry, little) == ORIENTATION_TAG) {
                int value = readShort(c, (int) entry + 8, little);
                return value >= 1 && value <= 8 ? value : 0;
            }
        }
        return 0;
    }

    // 회전 값 하나만 든 최소한의 EXIF 조각을 쓴다.
    // APP1 길이 0x0022 = 길이 필드 2 + "Exif\0\0" 6 + TIFF 26(머리 8 + 항목 수 2 + 항목 12 + 다음 IFD 4)
    private static void writeOrientationOnly(ByteArrayOutputStream out, int orientation) {
        byte[] segment = {
                (byte) 0xFF, (byte) APP1, 0x00, 0x22,
                'E', 'x', 'i', 'f', 0, 0,
                'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,
                0x00, 0x01,
                0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, (byte) orientation, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00};
        out.write(segment, 0, segment.length);
    }

    // ---------- PNG ----------
    // 구조: 서명 8바이트, 조각들(길이 4 + 종류 4 + 내용 + CRC 4)이 IEND까지. 부가 정보 조각을 통째로 건너뛰므로 나머지 조각의 CRC는 그대로 맞는다.
    private static byte[] stripPng(byte[] c) {
        if (c.length < PNG_SIGNATURE.length + 12 || !startsWith(c, 0, c.length, PNG_SIGNATURE)) {
            throw new IllegalArgumentException("PNG가 아닙니다.");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(c.length);
        out.write(c, 0, PNG_SIGNATURE.length);
        int pos = PNG_SIGNATURE.length;
        while (true) {
            if (pos + 12 > c.length) {
                throw new IllegalArgumentException("조각이 잘렸습니다.");
            }
            long length = ((long) u(c[pos]) << 24) | (u(c[pos + 1]) << 16) | (u(c[pos + 2]) << 8) | u(c[pos + 3]);
            long end = pos + 12L + length;
            if (end > c.length) {
                throw new IllegalArgumentException("조각 길이가 잘못됐습니다.");
            }
            String type = new String(c, pos + 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
            boolean critical = (c[pos + 4] & 0x20) == 0; // 첫 글자가 대문자면 필수 조각
            if (critical || PNG_KEPT_ANCILLARY.contains(type)) {
                out.write(c, pos, (int) (end - pos));
            }
            pos = (int) end;
            if (type.equals("IEND")) {
                return out.toByteArray(); // IEND 뒤에 붙은 데이터는 버린다.
            }
        }
    }

    // ---------- 공통 ----------
    private static int u(byte b) {
        return b & 0xFF;
    }

    private static boolean startsWith(byte[] c, int from, int end, byte[] prefix) {
        if (from + prefix.length > end || from < 0) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (c[from + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int readShort(byte[] c, int at, boolean little) {
        return little ? u(c[at]) | (u(c[at + 1]) << 8) : (u(c[at]) << 8) | u(c[at + 1]);
    }

    private static long readInt(byte[] c, int at, boolean little) {
        return little
                ? u(c[at]) | (u(c[at + 1]) << 8) | (u(c[at + 2]) << 16) | ((long) u(c[at + 3]) << 24)
                : ((long) u(c[at]) << 24) | (u(c[at + 1]) << 16) | (u(c[at + 2]) << 8) | u(c[at + 3]);
    }
}
