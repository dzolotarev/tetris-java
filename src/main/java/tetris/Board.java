package tetris;

import java.util.ArrayList;
import java.util.List;

/**
 * Игровое поле 10x20. Значение 0 — пусто, иначе ordinal типа + 1.
 * Ряды с отрицательными индексами считаются пустыми — там спавнятся фигуры.
 */
public final class Board {

    public static final int COLS = 10;
    public static final int ROWS = 20;

    private final int[][] grid = new int[ROWS][COLS];

    public int get(int col, int row) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) {
            return 0;
        }
        return grid[row][col];
    }

    /** Клетка занята стеной, полом или фигурой (клетки над полем — свободны). */
    public boolean solid(int col, int row) {
        if (row < 0) {
            return false;
        }
        if (col < 0 || col >= COLS || row >= ROWS) {
            return true;
        }
        return grid[row][col] != 0;
    }

    public boolean fits(Tetromino piece) {
        for (int[] cell : piece.cells()) {
            if (solid(cell[0], cell[1])) {
                return false;
            }
        }
        return true;
    }

    /** Фиксирует фигуру; возвращает false, если она целиком осталась над полем (lock out). */
    public boolean lock(Tetromino piece) {
        boolean visible = false;
        for (int[] cell : piece.cells()) {
            if (cell[1] >= 0 && cell[1] < ROWS) {
                grid[cell[1]][cell[0]] = piece.type().ordinal() + 1;
                visible = true;
            }
        }
        return visible;
    }

    public List<Integer> fullRows() {
        List<Integer> rows = new ArrayList<>();
        for (int r = 0; r < ROWS; r++) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) {
                if (grid[r][c] == 0) {
                    full = false;
                    break;
                }
            }
            if (full) {
                rows.add(r);
            }
        }
        return rows;
    }

    /** Удаляет строки (сдвигает всё сверху вниз) — вызывается после анимации. */
    public void removeRows(List<Integer> rows) {
        boolean[] drop = new boolean[ROWS];
        for (int r : rows) {
            drop[r] = true;
        }
        int write = ROWS - 1;
        for (int r = ROWS - 1; r >= 0; r--) {
            if (drop[r]) {
                continue;
            }
            for (int c = 0; c < COLS; c++) {
                grid[write][c] = grid[r][c];
            }
            write--;
        }
        for (int r = write; r >= 0; r--) {
            java.util.Arrays.fill(grid[r], 0);
        }
    }

    public boolean isEmpty() {
        for (int[] row : grid) {
            for (int cell : row) {
                if (cell != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    public void clear() {
        for (int[] row : grid) {
            java.util.Arrays.fill(row, 0);
        }
    }
}
