package tetris;

import java.awt.Color;

/**
 * Семь классических тетрамино.
 *
 * @param color цвет фигуры
 * @param box   размер квадратного «ящика» вращения (2 для O, 3 для JLSTZ, 4 для I)
 */
public enum Type {
    I(new Color(0, 199, 222), 4),
    O(new Color(232, 199, 42), 2),
    T(new Color(159, 79, 219), 3),
    S(new Color(61, 197, 110), 3),
    Z(new Color(230, 71, 80), 3),
    J(new Color(57, 116, 225), 3),
    L(new Color(240, 140, 48), 3);

    public final Color color;
    public final int box;

    Type(Color color, int box) {
        this.color = color;
        this.box = box;
    }
}
