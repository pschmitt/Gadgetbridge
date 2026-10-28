package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * XXTEA with little-endian words and a 128-bit key. The data length must be a multiple of 4 and at least 8.
 */
public final class Xxtea {
    private static final int DELTA = 0x9E3779B9;

    private Xxtea() {
    }

    public static byte[] encrypt(final byte[] data, final byte[] key) {
        final int[] v = toInts(data);
        final int[] k = toInts(key);
        final int n = v.length;
        int rounds = 6 + 52 / n;
        int sum = 0;
        int z = v[n - 1];
        while (rounds-- > 0) {
            sum += DELTA;
            final int e = (sum >>> 2) & 3;
            for (int p = 0; p < n; p++) {
                final int y = v[(p + 1) % n];
                v[p] += mx(sum, y, z, p, e, k);
                z = v[p];
            }
        }
        return toBytes(v);
    }

    public static byte[] decrypt(final byte[] data, final byte[] key) {
        final int[] v = toInts(data);
        final int[] k = toInts(key);
        final int n = v.length;
        final int rounds = 6 + 52 / n;
        int sum = rounds * DELTA;
        int y = v[0];
        while (sum != 0) {
            final int e = (sum >>> 2) & 3;
            for (int p = n - 1; p >= 0; p--) {
                final int z = v[(p + n - 1) % n];
                v[p] -= mx(sum, y, z, p, e, k);
                y = v[p];
            }
            sum -= DELTA;
        }
        return toBytes(v);
    }

    private static int mx(final int sum, final int y, final int z, final int p, final int e, final int[] k) {
        return (((z >>> 5) ^ (y << 2)) + ((y >>> 3) ^ (z << 4))) ^ ((sum ^ y) + (k[(p & 3) ^ e] ^ z));
    }

    private static int[] toInts(final byte[] bytes) {
        if (bytes.length % 4 != 0 || bytes.length < 8) {
            throw new IllegalArgumentException("Length must be a multiple of 4 and at least 8: " + bytes.length);
        }
        final int[] ints = new int[bytes.length / 4];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(ints);
        return ints;
    }

    private static byte[] toBytes(final int[] ints) {
        final ByteBuffer buf = ByteBuffer.allocate(ints.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.asIntBuffer().put(ints);
        return buf.array();
    }
}
