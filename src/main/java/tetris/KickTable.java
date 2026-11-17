package tetris;

/**
 * Таблицы wall kick из SRS.
 * <p>
 * В оригинальных таблицах ось Y направлена вверх, здесь все смещения
 * уже пересчитаны под экранные координаты (Y растёт вниз).
 * Смещение задаётся парой {col, row}.
 */
public final class KickTable {

    private static final int[][][][] JLSTZ = new int[4][4][][];
    private static final int[][][][] I = new int[4][4][][];

    /** Смещения для поворота на 180° (упрощённый вариант, которого хватает на практике). */
    private static final int[][] HALF_JLSTZ = {
        {0, 0}, {0, 1}, {0, -1}, {1, 0}, {-1, 0}
    };
    private static final int[][] HALF_I = {
        {0, 0}, {-2, 0}, {2, 0}, {0, 1}, {0, -1}, {1, 0}, {-1, 0}
    };

    static {
        put(JLSTZ, 0, 1, 0, 0, -1, 0, -1, -1, 0, 2, -1, 2);
        put(JLSTZ, 1, 0, 0, 0, 1, 0, 1, 1, 0, -2, 1, -2);
        put(JLSTZ, 1, 2, 0, 0, 1, 0, 1, 1, 0, -2, 1, -2);
        put(JLSTZ, 2, 1, 0, 0, -1, 0, -1, -1, 0, 2, -1, 2);
        put(JLSTZ, 2, 3, 0, 0, 1, 0, 1, -1, 0, 2, 1, 2);
        put(JLSTZ, 3, 2, 0, 0, -1, 0, -1, 1, 0, -2, -1, -2);
        put(JLSTZ, 3, 0, 0, 0, -1, 0, -1, 1, 0, -2, -1, -2);
        put(JLSTZ, 0, 3, 0, 0, 1, 0, 1, -1, 0, 2, 1, 2);

        put(I, 0, 1, 0, 0, -2, 0, 1, 0, -2, 1, 1, -2);
        put(I, 1, 0, 0, 0, 2, 0, -1, 0, 2, -1, -1, 2);
        put(I, 1, 2, 0, 0, -1, 0, 2, 0, -1, -2, 2, 1);
        put(I, 2, 1, 0, 0, 1, 0, -2, 0, 1, 2, -2, -1);
        put(I, 2, 3, 0, 0, 2, 0, -1, 0, 2, -1, -1, 2);
        put(I, 3, 2, 0, 0, -2, 0, 1, 0, -2, 1, 1, -2);
        put(I, 3, 0, 0, 0, 1, 0, -2, 0, 1, 2, -2, -1);
        put(I, 0, 3, 0, 0, -1, 0, 2, 0, -1, -2, 2, 1);
    }

    /** Набор смещений, которые по очереди пробуем при переходе from -> to. */
    public static int[][] offsets(Type type, int from, int to) {
        if (type == Type.O) {
            return new int[][]{{0, 0}};
        }
        if (type == Type.I) {
            return I[from][to];
        }
        return JLSTZ[from][to];
    }

    /** Набор смещений для поворота на 180°. */
    public static int[][] halfOffsets(Type type) {
        if (type == Type.O) {
            return new int[][]{{0, 0}};
        }
        return type == Type.I ? HALF_I : HALF_JLSTZ;
    }

    private static void put(int[][][][] table, int from, int to, int... flat) {
        int[][] pairs = new int[flat.length / 2][2];
        for (int i = 0; i < pairs.length; i++) {
            pairs[i][0] = flat[i * 2];
            pairs[i][1] = flat[i * 2 + 1];
        }
        table[from][to] = pairs;
    }

    private KickTable() {
    }
}
