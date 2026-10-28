package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ZeppOsBitmapFormatTest {
    private static final int IMAGE_OFFSET = 18 + 46 + 256 * 4;

    private int decodePixel(final byte[] tga, final int pos) {
        final int offset = 18 + 46 + (tga[IMAGE_OFFSET + pos] & 255) * 4;
        return Color.argb(tga[offset + 3] & 255, tga[offset] & 255,
                tga[offset + 1] & 255, tga[offset + 2] & 255);
    }

    @Test
    public void indexedImagePreservesExactColorsAndAlpha() {
        final int[] pixels = {Color.RED, Color.GREEN, Color.BLUE, Color.TRANSPARENT,
                0x80402010, Color.RED, Color.WHITE, Color.BLACK};
        final Bitmap bitmap = Bitmap.createBitmap(pixels, 4, 2, Bitmap.Config.ARGB_8888);
        final byte[] tga = ZeppOsBitmapFormat.TGA_L8_ARGB8888_GCNANOLITE.encode(bitmap, 4, 2);

        assertEquals(IMAGE_OFFSET + pixels.length, tga.length);
        for (int pos = 0; pos < pixels.length; pos++) {
            // Compare against the bitmap to account for premultiplied alpha rounding.
            assertEquals(bitmap.getPixel(pos % 4, pos / 4), decodePixel(tga, pos));
        }
        assertFalse(bitmap.isRecycled());
        bitmap.recycle();
    }

    @Test
    public void detailedImageRetainsTransparencyOutsideThePaletteSample() {
        final int[] pixels = new int[64 * 64];
        final Random random = new Random(321);
        for (int pos = 0; pos < pixels.length; pos++) {
            pixels[pos] = 0xff000000 | random.nextInt(1 << 24);
        }
        pixels[0] = Color.TRANSPARENT;
        final Bitmap bitmap = Bitmap.createBitmap(pixels, 64, 64, Bitmap.Config.ARGB_8888);
        final byte[] tga = ZeppOsBitmapFormat.TGA_L8_ARGB8888_GCNANOLITE.encode(bitmap, 64, 64);
        assertEquals(0, Color.alpha(decodePixel(tga, 0)));
        bitmap.recycle();
    }

    @Test
    public void detailedPhotoProducesValidIndexedImageAtThumbnailAndFullSize() {
        for (final int[] dimensions : new int[][]{{240, 240}, {512, 632}}) {
            final int width = dimensions[0];
            final int height = dimensions[1];
            final int[] pixels = new int[width * height];
            final Random random = new Random(123);
            for (int pos = 0; pos < pixels.length; pos++) {
                pixels[pos] = 0xff000000 | random.nextInt(1 << 24);
            }
            final Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
            final byte[] tga = ZeppOsBitmapFormat.TGA_L8_ARGB8888_GCNANOLITE.encode(bitmap, width, height);
            final ByteBuffer header = ByteBuffer.wrap(tga).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(IMAGE_OFFSET + pixels.length, tga.length);
            assertEquals(1, tga[1]); // color map present
            assertEquals(1, tga[2]); // uncompressed indexed image
            assertEquals(256, header.getShort(5));
            assertEquals(width, header.getShort(12));
            assertEquals(height, header.getShort(14));
            assertEquals(8, tga[16]);

            long squaredError = 0;
            for (int pos = 0; pos < pixels.length; pos++) {
                final int decoded = decodePixel(tga, pos);
                assertEquals(255, Color.alpha(decoded));
                for (int shift : new int[]{0, 8, 16}) {
                    final int delta = ((pixels[pos] >>> shift) & 255) - ((decoded >>> shift) & 255);
                    squaredError += delta * delta;
                }
            }
            assertTrue("Photo palette should cover the source colors", squaredError / (3 * pixels.length) < 600);
            assertFalse(bitmap.isRecycled());
            bitmap.recycle();
        }
    }
}
