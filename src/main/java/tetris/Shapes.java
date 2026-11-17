package tetris;

import java.util.ArrayList;
import java.util.List;

/**
 * Формы фигур. Задаётся только состояние спавна, остальные повороты
 * вычисляются поворотом матрицы по часовой стрелке — это ровно то,
 * что делает SRS для квадратных ящиков (3x3 у JLSTZ, 4x4 у I).
 */
public final class Shapes {

    /** Матрицы спавна: '#' — заполненная клетка, строки сверху вниз. */
    private static final String[] SPAWN = {
        "....",   // I
        "XXXX",
        "....",
        "....",

        "XX",     // O
        "XX",

        ".X.",     // T
        "XXX",
        "...",

        ".XX",    // S
        "XX.",
        "...",

        "XX.",    // Z
        ".XX",
        "...",

        "X..",    // J
        "XXX",
        "...",

        "..X",    // L
        "XXX",
        "..."
    };

    /** CELLS[type.ordinal()][rotation] — массив пар {col, row} внутри ящика. */
    private static final int[][][][] CELLS = new int[Type.values().length][4][][];

    static {
        int offset = 0;
        for (Type type : Type.values()) {
            char[][] grid = readGrid(SPAWN, offset, type.box);
            offset += type.box;
            for (int rot = 0; rot < 4; rot++) {
                CELLS[type.ordinal()][rot] = cellsOf(grid);
                grid = rotateCW(grid);
            }
        }
    }

    /** Клетки фигуры данного типа в данном повороте (координаты внутри ящика). */
    public static int[][] cells(Type type, int rotation) {
        return CELLS[type.ordinal()][Math.floorMod(rotation, 4)];
    }

    private static char[][] readGrid(String[] rows, int from, int size) {
        char[][] grid = new char[size][size];
        for (int r = 0; r < size; r++) {
            String row = rows[from + r];
            for (int c = 0; c < size; c++) {
                grid[r][c] = row.charAt(c);
            }
        }
        return grid;
    }

    /** Поворот квадратной матрицы на 90° по часовой стрелке. */
    private static char[][] rotateCW(char[][] src) {
        int n = src.length;
        char[][] dst = new char[n][n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                dst[r][c] = src[n - 1 - c][r];
            }
        }
        return dst;
    }

    private static int[][] cellsOf(char[][] grid) {
        List<int[]> list = new ArrayList<>();
        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid.length; c++) {
                if (grid[r][c] == 'X') {
                    list.add(new int[]{c, r});
                }
            }
        }
        return list.toArray(new int[0][]);
    }

    private Shapes() {
    }
}
