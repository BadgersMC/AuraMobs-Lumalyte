package dev.aurelium.auramobs.util;

import java.util.Map;

public final class SmallCapsUtil {

	private static final Map<Character, String> SMALL_CAPS_MAP = Map.ofEntries(
			Map.entry('a', "ᴀ"),
			Map.entry('b', "ʙ"),
			Map.entry('c', "ᴄ"),
			Map.entry('d', "ᴅ"),
			Map.entry('e', "ᴇ"),
			Map.entry('f', "ꜰ"),
			Map.entry('g', "ɢ"),
			Map.entry('h', "ʜ"),
			Map.entry('i', "ɪ"),
			Map.entry('j', "ᴊ"),
			Map.entry('k', "ᴋ"),
			Map.entry('l', "ʟ"),
			Map.entry('m', "ᴍ"),
			Map.entry('n', "ɴ"),
			Map.entry('o', "ᴏ"),
			Map.entry('p', "ᴘ"),
			Map.entry('q', "q"),      // fallback
			Map.entry('r', "ʀ"),
			Map.entry('s', "ꜱ"),
			Map.entry('t', "ᴛ"),
			Map.entry('u', "ᴜ"),
			Map.entry('v', "ᴠ"),
			Map.entry('w', "ᴡ"),
			Map.entry('x', "ˣ"),
			Map.entry('y', "ʏ"),
			Map.entry('z', "ᴢ"),
			// Numbers (superscript)
			Map.entry('0', "⁰"),
			Map.entry('1', "¹"),
			Map.entry('2', "²"),
			Map.entry('3', "³"),
			Map.entry('4', "⁴"),
			Map.entry('5', "⁵"),
			Map.entry('6', "⁶"),
			Map.entry('7', "⁷"),
			Map.entry('8', "⁸"),
			Map.entry('9', "⁹")
	);

	private SmallCapsUtil() {
		// Utility class; prevent instantiation
	}

	public static String toSmallCaps(String input) {
		StringBuilder sb = new StringBuilder(input.length());
		for (char c : input.toCharArray()) {
			char lower = Character.toLowerCase(c);
			if (SMALL_CAPS_MAP.containsKey(lower)) {
				sb.append(SMALL_CAPS_MAP.get(lower));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}