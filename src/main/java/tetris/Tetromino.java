package tetris;

/**
 * Активная фигура: тип, поворот и положение верхнего левого угла ящика на поле.
 */
public final class Tetromino {

    private final Type type;
    private int rotation;
    private int col;
    private int row;

    public Tetromino(Type type) {
        this.type = type;
        this.rotation = 0;
        this.col = spawnCol(type);
        this.row = -1; // ящик наполовину над видимой частью поля
    }

    public Type type() {
        return type;
    }

    public int rotation() {
        return rotation;
    }

    public int col() {
        return col;
    }

    public int row() {
        return row;
    }

    /** Клетки фигуры в координатах поля. */
    public int[][] cells() {
        int[][] local = Shapes.cells(type, rotation);
        int[][] abs = new int[local.length][2];
        for (int i = 0; i < local.length; i++) {
            abs[i][0] = col + local[i][0];
            abs[i][1] = row + local[i][1];
        }
        return abs;
    }

    public void shift(int dc, int dr) {
        col += dc;
        row += dr;
    }

    public void setRotation(int rotation) {
        this.rotation = Math.floorMod(rotation, 4);
    }

    public Tetromino copy() {
        Tetromino copy = new Tetromino(type);
        copy.rotation = rotation;
        copy.col = col;
        copy.row = row;
        return copy;
    }

    /** Позиция спавна по горизонтали: фигура появляется в колонках 3..5 (I — 3..6). */
    private static int spawnCol(Type type) {
        return switch (type) {
            case I -> 2;
            case O -> 4;
            default -> 3;
        };
    }
}
