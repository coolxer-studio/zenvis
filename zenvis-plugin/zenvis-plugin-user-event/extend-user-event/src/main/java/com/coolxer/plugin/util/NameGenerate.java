package com.coolxer.plugin.util;

import java.util.Random;

public class NameGenerate {

    public String name() {
        return "name"+ new Random().nextInt(10);
    }
}
