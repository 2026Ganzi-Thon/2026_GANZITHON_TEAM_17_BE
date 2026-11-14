package com.ganzithon.wool.global.json;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdScalarDeserializer;

// JSON 문자열("...")만 String으로 받는다. Jackson 기본 설정은 숫자나 true 같은 값도 "123", "true"로 바꿔 받아 주는데,
// 입력 실수를 조용히 통과시키지 않도록 그런 값은 거부한다. (본문 파싱 오류로 처리되어 COMMON_400이 된다)
// 필요한 필드에 @JsonDeserialize(using = StrictStringDeserializer.class)로 붙여 쓴다.
public class StrictStringDeserializer extends StdScalarDeserializer<String> {

    public StrictStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            return parser.getString();
        }
        return (String) context.handleUnexpectedToken(String.class, parser);
    }
}
