package com.example.cuffedaddon;

import java.util.Random;

public class Muffler {
    private static final String[] SOUNDS = {"mph", "mhm", "hmm", "fmp", "mpr", "mrp"};
    private static final Random RANDOM = new Random();

    public static String muffle(String message) {
        StringBuilder out = new StringBuilder();
        for (String word : message.split(" ", -1)) {
            if (word.isEmpty()) {
                out.append(word);
            } else {
                out.append(SOUNDS[RANDOM.nextInt(SOUNDS.length)]);
            }
            out.append(' ');
        }
        return out.toString().stripTrailing();
    }
}
