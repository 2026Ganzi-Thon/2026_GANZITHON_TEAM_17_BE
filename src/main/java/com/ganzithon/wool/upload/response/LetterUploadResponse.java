package com.ganzithon.wool.upload.response;

// imageUrl은 서버 기준 상대 경로(/files/letters/{uuid}.jpg)다. 프론트는 API 서버 주소를 앞에 붙여 쓴다.
public record LetterUploadResponse(String imageUrl) {
}
