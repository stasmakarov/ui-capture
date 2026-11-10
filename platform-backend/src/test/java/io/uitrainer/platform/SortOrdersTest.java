package io.uitrainer.platform;

import io.uitrainer.platform.service.SortOrders;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SortOrdersTest {

    @Test
    void moveAndNormalize() {
        List<Item> items = new ArrayList<>(List.of(new Item(1), new Item(2), new Item(3)));
        assertThat(SortOrders.move(items, items.get(0), 1)).isTrue();
        SortOrders.normalize(items, Item::setOrder);
        assertThat(items.stream().map(Item::getOrder)).containsExactly(1, 2, 3);
        assertThat(items.get(0).seed).isEqualTo(2);
        assertThat(SortOrders.next(items.stream().map(Item::getOrder).toList())).isEqualTo(4);
    }

    private static final class Item {
        private final int seed;
        private int order;

        private Item(int seed) {
            this.seed = seed;
            this.order = seed;
        }

        int getOrder() {
            return order;
        }

        void setOrder(int order) {
            this.order = order;
        }
    }
}
