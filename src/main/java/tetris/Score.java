package tetris;

/**
 * Очки, уровни и бонусы по правилам Tetris Guideline:
 * линейные очки, T-спины, бонус за комбо, back-to-back и perfect clear.
 */
public final class Score {

    public static final int MAX_LEVEL = 20;

    /** tspin: 0 — нет, 1 — mini, 2 — полноценный. */
    public static final int SPIN_NONE = 0;
    public static final int SPIN_MINI = 1;
    public static final int SPIN_FULL = 2;

    private static final int[] LINE_POINTS = {0, 100, 300, 500, 800};
    private static final int[] SPIN_POINTS = {0, 100, 400};     // спин без очистки линий: mini / полный
    private static final int[] SPIN_LINE_POINTS = {0, 800, 1200, 1600}; // полные спины по 1..3 линии
    private static final int[] MINI_LINE_POINTS = {0, 200, 400, 400};
    private static final int[] PERFECT_POINTS = {0, 2000, 3000, 4000, 5000};

    private int score;
    private int lines;
    private int level = 1;
    private int combo = -1;
    private boolean backToBack;
    private String lastEvent = "";
    private int eventSeq;

    public int score() {
        return score;
    }

    public int lines() {
        return lines;
    }

    public int level() {
        return level;
    }

    public int combo() {
        return Math.max(combo, 0);
    }

    public boolean backToBack() {
        return backToBack;
    }

    /** Всплывающая подпись последнего начисления (для HUD). */
    public String lastEvent() {
        return lastEvent;
    }

    /** Счётчик событий: меняется при каждом начислении за очистку. */
    public int eventSeq() {
        return eventSeq;
    }

    public void addDropPoints(int points) {
        score += points;
    }

    /** Комбо прерывается, если фигура зафиксирована без очистки линий. */
    public void breakCombo() {
        combo = -1;
    }

    public void awardClear(int rows, int spin, boolean perfect) {
        int base;
        boolean difficult;

        if (spin != SPIN_NONE) {
            int[] table = spin == SPIN_FULL ? SPIN_LINE_POINTS : MINI_LINE_POINTS;
            base = rows > 0 ? table[Math.min(rows, 3)] : SPIN_POINTS[spin];
            difficult = rows > 0;
        } else {
            base = LINE_POINTS[Math.min(rows, 4)];
            difficult = rows == 4;
        }

        int points = base * level;

        boolean b2bApplied = difficult && backToBack;
        if (b2bApplied) {
            points += points / 2;
        }
        if (difficult) {
            backToBack = true;
        } else if (rows > 0) {
            backToBack = false;
        }

        combo++;
        if (combo > 0) {
            points += 50 * combo * level;
        }
        if (perfect) {
            points += PERFECT_POINTS[Math.min(rows, 4)] * level;
        }

        score += points;
        lines += rows;
        level = Math.min(lines / 10 + 1, MAX_LEVEL);

        lastEvent = describe(rows, spin, perfect, b2bApplied, points);
        eventSeq++;
    }

    public void reset() {
        score = 0;
        lines = 0;
        level = 1;
        combo = -1;
        backToBack = false;
        lastEvent = "";
        eventSeq = 0;
    }

    /** Интервал гравитации в миллисекундах для текущего уровня. */
    public long gravityMs() {
        double seconds = Math.pow(0.8 - ((level - 1) * 0.007), level - 1);
        return Math.max(Math.round(seconds * 1000), 16);
    }

    private String describe(int rows, int spin, boolean perfect, boolean b2b, int points) {
        String suffix = " +" + points;
        if (perfect) {
            return "ИДЕАЛЬНАЯ ОЧИСТКА" + suffix;
        }
        String name = switch (rows) {
            case 1 -> "ОДНА ЛИНИЯ";
            case 2 -> "ДВЕ ЛИНИИ";
            case 3 -> "ТРИ ЛИНИИ";
            case 4 -> "ТЕТРИС";
            default -> spin == SPIN_FULL ? "T-СПИН" : "T-СПИН MINI";
        };
        if (spin != SPIN_NONE && rows > 0) {
            name = (spin == SPIN_FULL ? "T-СПИН " : "T-СПИН MINI ") + name;
        }
        if (b2b) {
            name += " × B2B";
        }
        return name + suffix;
    }
}
