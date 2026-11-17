package tetris;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * «Мешок» 7-bag: все семь фигур выдаются по одному разу, затем мешок перемешивается.
 * Гарантирует, что длинных простоев одной фигуры не будет.
 */
public final class Bag {

    private final Random random = new Random();
    private final Deque<Type> queue = new ArrayDeque<>();

    public Type poll() {
        ensure(1);
        return queue.pollFirst();
    }

    /** Следующие n фигур без извлечения. */
    public List<Type> preview(int n) {
        ensure(n);
        return new ArrayList<>(queue.stream().limit(n).toList());
    }

    private void ensure(int n) {
        while (queue.size() < n) {
            List<Type> batch = new ArrayList<>(List.of(Type.values()));
            Collections.shuffle(batch, random);
            queue.addAll(batch);
        }
    }
}
