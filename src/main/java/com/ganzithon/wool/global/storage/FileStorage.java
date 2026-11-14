package com.ganzithon.wool.global.storage;

// 파일 저장소. 서버 로컬 폴더(LocalFileStorage)에 저장한다. 저장 방식이 바뀌어도 호출하는 쪽이 영향받지 않도록 인터페이스 뒤에 둔다.
public interface FileStorage {

    // directory 아래에 filename으로 저장하고 클라이언트가 쓸 공개 경로(예: /files/letters/abc.jpg)를 돌려준다.
    // 이름은 호출하는 쪽이 만든 안전한 값(소문자·숫자·하이픈·점)만 받는다.
    String save(String directory, String filename, byte[] content);

    // save가 돌려준 공개 경로의 파일이 실제로 저장되어 있는지. 형식이 맞지 않는 경로나 저장 폴더 밖 경로는 없는 것으로 본다.
    boolean exists(String publicPath);
}
