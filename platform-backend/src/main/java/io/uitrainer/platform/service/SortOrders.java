package io.uitrainer.platform.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class SortOrders {

    private SortOrders() {
    }

    public static <T> List<T> sorted(List<T> items, Function<T, Integer> order) {
        List<T> copy = new ArrayList<>(items);
        copy.sort(Comparator.comparing(order, Comparator.nullsLast(Integer::compareTo)));
        return copy;
    }

    public static <T> boolean move(List<T> items, T selected, int delta) {
        int index = items.indexOf(selected);
        int other = index + delta;
        if (index < 0 || other < 0 || other >= items.size()) {
            return false;
        }
        Collections.swap(items, index, other);
        return true;
    }

    public static <T> void normalize(List<T> items, BiConsumer<T, Integer> setter) {
        for (int i = 0; i < items.size(); i++) {
            setter.accept(items.get(i), i + 1);
        }
    }

    public static int next(Iterable<Integer> orders) {
        int max = 0;
        for (Integer order : orders) {
            if (order != null) {
                max = Math.max(max, order);
            }
        }
        return max + 1;
    }
}
