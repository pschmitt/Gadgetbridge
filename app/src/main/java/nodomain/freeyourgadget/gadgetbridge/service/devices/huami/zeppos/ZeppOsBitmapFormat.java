package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos;

import android.graphics.Bitmap;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.nQuant.PnnQuantizer;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions;
import nodomain.freeyourgadget.gadgetbridge.util.BitmapUtil;

public enum ZeppOsBitmapFormat {
    TGA_RGB565_GCNANOLITE(0x04) {
        @Override
        public byte[] encode(final Bitmap bmp, final int width, final int height) {
            return BitmapUtil.convertToTgaRGB565(bmp, width, height, getTgaIdBytes(width));
        }
    },

    // Zepp OS 4
    TGA_L8_ARGB8888_GCNANOLITE(0x05) {
        @Override
        public byte[] encode(final Bitmap bmp, final int width, final int height) {
            final Bitmap resizedBmp = BitmapUtil.convert(bmp, Bitmap.Config.ARGB_8888, width, height);

            final int[] pixels = new int[width * height];
            resizedBmp.getPixels(pixels, 0, width, 0, 0, width, height);
            final Map<Integer, Integer> exactColors = new LinkedHashMap<>();
            for (final int pixel : pixels) {
                if (!exactColors.containsKey(pixel)) {
                    exactColors.put(pixel, exactColors.size());
                    if (exactColors.size() > 256) {
                        break;
                    }
                }
            }

            final int[] palette;
            if (exactColors.size() <= 256) {
                palette = new int[exactColors.size()];
                for (final Map.Entry<Integer, Integer> entry : exactColors.entrySet()) {
                    palette[entry.getValue()] = entry.getKey();
                }
            } else {
                // Full-image pairwise LAB quantization can take minutes on photos, blocking
                // Bluetooth acknowledgements. Bound palette generation to 1024 samples.
                final int sampleWidth = Math.min(width, 32);
                final int sampleHeight = Math.min(height, 32);
                final int[] samplePixels = new int[sampleWidth * sampleHeight];
                for (int y = 0; y < sampleHeight; y++) {
                    for (int x = 0; x < sampleWidth; x++) {
                        samplePixels[y * sampleWidth + x] = pixels[
                                ((2 * y + 1) * height / (2 * sampleHeight)) * width
                                        + (2 * x + 1) * width / (2 * sampleWidth)];
                        if ((samplePixels[y * sampleWidth + x] >>> 24) == 0) {
                            samplePixels[y * sampleWidth + x] = 0xff000000;
                        }
                    }
                }
                // Retain transparency even when it falls between sampled pixels.
                boolean hasTransparency = false;
                for (final int pixel : pixels) {
                    if ((pixel >>> 24) == 0) {
                        hasTransparency = true;
                        break;
                    }
                }
                final Bitmap sample = Bitmap.createBitmap(samplePixels, sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888);
                final Pair<Bitmap, int[]> converted = new PnnQuantizer(sample).convert(hasTransparency ? 255 : 256, true);
                // Reserve a transparent entry explicitly: pairwise merging may otherwise
                // blend rare transparent pixels into an opaque palette color.
                palette = hasTransparency ? Arrays.copyOf(converted.second, converted.second.length + 1) : converted.second;
                converted.first.recycle();
                sample.recycle();
            }

            // we need to allocate 256b even if we don't use all of them, otherwise
            // the watches misbehave
            final byte[] paletteBytes = new byte[256 * 4];
            for (int i = 0; i < palette.length; i++) {
                // ARGB -> RGBA
                final int color = palette[i];
                paletteBytes[i * 4] = (byte) ((color >> 16) & 0xff);
                paletteBytes[i * 4 + 1] = (byte) ((color >> 8) & 0xff);
                paletteBytes[i * 4 + 2] = (byte) (color & 0xff);
                paletteBytes[i * 4 + 3] = (byte) ((color >> 24) & 0xff);
            }

            final byte[] imageData = new byte[pixels.length];
            final int[] colorLookup = new int[65536];
            Arrays.fill(colorLookup, -1);
            for (int pos = 0; pos < pixels.length; pos++) {
                final int pixel = pixels[pos];
                if (exactColors.size() <= 256) {
                    imageData[pos] = exactColors.get(pixel).byteValue();
                    continue;
                }
                // Cache nearest palette entries by ARGB4444, bounding color searches even
                // for noisy photographs. Expand each bin consistently to avoid scan-order bias.
                final int key = ((pixel >>> 16) & 0xf000) | ((pixel >>> 12) & 0xf00)
                        | ((pixel >>> 8) & 0xf0) | ((pixel >>> 4) & 0xf);
                int index = colorLookup[key];
                if (index < 0) {
                    int bestDistance = Integer.MAX_VALUE;
                    for (int i = 0; i < palette.length; i++) {
                        final int color = palette[i];
                        final int da = ((key >>> 12) & 15) * 17 - (color >>> 24);
                        final int dr = ((key >>> 8) & 15) * 17 - ((color >>> 16) & 255);
                        final int dg = ((key >>> 4) & 15) * 17 - ((color >>> 8) & 255);
                        final int db = (key & 15) * 17 - (color & 255);
                        final int distance = 4 * da * da + 2 * dr * dr + 3 * dg * dg + db * db;
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            index = i;
                        }
                    }
                    colorLookup[key] = index;
                }
                imageData[pos] = (byte) index;
            }
            if (resizedBmp != bmp) {
                resizedBmp.recycle();
            }
            return BitmapUtil.buildTga(imageData, 8, paletteBytes, width, height, getTgaIdBytes(width));
        }
    },

    TGA_RGB565_DAVE2D(0x08) {
        @Override
        public byte[] encode(final Bitmap bmp, final int width, final int height) {
            return BitmapUtil.convertToTgaRGB565(bmp, width, height, getTgaIdBytes(width));
        }
    },

    TGA_RGB565_MHS002SNEMAP(0x0a) {
        @Override
        public byte[] encode(final Bitmap bmp, final int width, final int height) {
            return BitmapUtil.convertToTgaRGB565(bmp, width, height, getTgaIdBytes(width));
        }
    },
    ;

    private final byte code;

    ZeppOsBitmapFormat(final int code) {
        this.code = (byte) code;
    }

    public byte getCode() {
        return code;
    }

    public byte[] getTgaIdBytes(final int width) {
        final byte[] tgaIdBytes = new byte[46];
        tgaIdBytes[0] = 'S';
        tgaIdBytes[1] = 'O';
        tgaIdBytes[2] = 'M';
        tgaIdBytes[3] = 'H';
        BLETypeConversions.writeUint32(tgaIdBytes, 4, width);
        return tgaIdBytes;
    }

    @Nullable
    public abstract byte[] encode(final Bitmap bmp, final int width, final int height);

    @Nullable
    public static ZeppOsBitmapFormat fromCode(final byte code) {
        for (final ZeppOsBitmapFormat format : ZeppOsBitmapFormat.values()) {
            if (format.code == code) {
                return format;
            }
        }

        return null;
    }
}
