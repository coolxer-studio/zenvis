package com.coolxer.plugin.util;

import java.util.Random;

public class CardGenerate {

    public static String card() {
        return "card"+new Random().nextInt(10);
    }
}
