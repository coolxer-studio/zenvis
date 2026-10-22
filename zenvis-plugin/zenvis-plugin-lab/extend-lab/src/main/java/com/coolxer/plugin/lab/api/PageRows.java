package com.coolxer.plugin.lab.api;

import java.util.List;

public record PageRows<T>(List<T> rows, long total) {
}
