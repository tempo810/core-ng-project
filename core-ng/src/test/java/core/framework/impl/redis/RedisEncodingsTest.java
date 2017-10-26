package core.framework.impl.redis;

import core.framework.util.Charsets;
import core.framework.util.Strings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * @author neo
 */
class RedisEncodingsTest {
    @Test
    void encodeString() {
        assertArrayEquals("value".getBytes(Charsets.UTF_8), RedisEncodings.encode("value"));
    }

    @Test
    void encodeInteger() {
        assertArrayEquals(Strings.bytes("-1"), RedisEncodings.encode(-1));
        assertArrayEquals(Strings.bytes("9"), RedisEncodings.encode(9));
        assertArrayEquals(Strings.bytes("88"), RedisEncodings.encode(88));
        assertArrayEquals(Strings.bytes("777"), RedisEncodings.encode(777));
        assertArrayEquals(Strings.bytes("6666"), RedisEncodings.encode(6666));
        assertArrayEquals(Strings.bytes("55555"), RedisEncodings.encode(55555));
        assertArrayEquals(Strings.bytes("444444"), RedisEncodings.encode(444444));
        assertArrayEquals(Strings.bytes("3333333"), RedisEncodings.encode(3333333));
        assertArrayEquals(Strings.bytes("-1234567890"), RedisEncodings.encode(-1234567890));
    }
}
