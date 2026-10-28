package nodomain.freeyourgadget.gadgetbridge.util.language.impl;

import java.util.HashMap;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.util.language.Transliterator;

public class ThaiTransliterator implements Transliterator {

    private static final Map<Character, String> MAP = new HashMap<>();

    static {
        // พยัญชนะ (Consonants)
        MAP.put('ก', "k");
        MAP.put('ข', "kh");
        MAP.put('ฃ', "kh");
        MAP.put('ค', "kh");
        MAP.put('ฅ', "kh");
        MAP.put('ฆ', "kh");
        MAP.put('ง', "ng");
        MAP.put('จ', "ch");
        MAP.put('ฉ', "ch");
        MAP.put('ช', "ch");
        MAP.put('ซ', "s");
        MAP.put('ฌ', "ch");
        MAP.put('ญ', "y");
        MAP.put('ฎ', "d");
        MAP.put('ฏ', "t");
        MAP.put('ฐ', "th");
        MAP.put('ฑ', "th");
        MAP.put('ฒ', "th");
        MAP.put('ณ', "n");
        MAP.put('ด', "d");
        MAP.put('ต', "t");
        MAP.put('ถ', "th");
        MAP.put('ท', "th");
        MAP.put('ธ', "th");
        MAP.put('น', "n");
        MAP.put('บ', "b");
        MAP.put('ป', "p");
        MAP.put('ผ', "ph");
        MAP.put('ฝ', "f");
        MAP.put('พ', "ph");
        MAP.put('ฟ', "f");
        MAP.put('ภ', "ph");
        MAP.put('ม', "m");
        MAP.put('ย', "y");
        MAP.put('ร', "r");
        MAP.put('ล', "l");
        MAP.put('ว', "w");
        MAP.put('ศ', "s");
        MAP.put('ษ', "s");
        MAP.put('ส', "s");
        MAP.put('ห', "h");
        MAP.put('ฬ', "l");
        MAP.put('อ', "o");
        MAP.put('ฮ', "h");

        // สระ และ เครื่องหมาย (Vowels & Symbols)
        MAP.put('ะ', "a");
        MAP.put('า', "a");
        MAP.put('ิ', "i");
        MAP.put('ี', "i");
        MAP.put('ึ', "ue");
        MAP.put('ื', "ue");
        MAP.put('ุ', "u");
        MAP.put('ู', "u");
        MAP.put('เ', "e");
        MAP.put('แ', "ae");
        MAP.put('โ', "o");
        MAP.put('ใ', "ai");
        MAP.put('ไ', "ai");
        MAP.put('ำ', "am");
        MAP.put('ั', "a");
        MAP.put('็', "");

        // ตัวเลขไทย (Thai Numerals)
        MAP.put('๐', "0");
        MAP.put('๑', "1");
        MAP.put('๒', "2");
        MAP.put('๓', "3");
        MAP.put('๔', "4");
        MAP.put('๕', "5");
        MAP.put('๖', "6");
        MAP.put('๗', "7");
        MAP.put('๘', "8");
        MAP.put('๙', "9");
    }

    @Override
    public String transliterate(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            
            // ข้ามวรรณยุกต์ไทย (ไม้เอก/ไม้โท/ไม้ตรี/ไม้จัตวา/การันต์)
            if (c >= '\u0E48' && c <= '\u0E4C') {
                continue;
            }

            String replacement = MAP.get(c);
            if (replacement != null) {
                sb.append(replacement);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
