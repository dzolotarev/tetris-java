package tetris;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Игровое поле: цикл, физика, отрисовка и ввод.
 * Правила — Tetris Guideline: SRS с wall kick, 7-bag, hold, призрачная фигура,
 * lock delay и DAS/ARR для движения.
 */
public final class GamePanel extends JPanel implements java.awt.event.ActionListener {

    private static final long serialVersionUID = 1L;

    private static final int CELL = 30;
    private static final int MARGIN = 24;
    private static final int GAP = 26;
    private static final int HUD_WIDTH = 196;

    private static final long DAS_MS = 150;        // задержка автоповтора движения
    private static final long ARR_MS = 40;         // интервал автоповтора
    private static final long SOFT_DROP_MS = 35;
    private static final long LOCK_DELAY_MS = 500;
    private static final int MAX_LOCK_RESETS = 15;
    private static final long CLEAR_ANIM_MS = 240;
    private static final long TOAST_MS = 1500;

    private static final Color BG = new Color(16, 18, 26);
    private static final Color PANEL = new Color(27, 31, 43);
    private static final Color CELL_EMPTY = new Color(23, 26, 36);
    private static final Color GRID = new Color(40, 46, 62);
    private static final Color BORDER = new Color(74, 84, 110);
    private static final Color TEXT = new Color(233, 239, 250);
    private static final Color DIM = new Color(146, 156, 176);
    private static final Color ACCENT = new Color(96, 200, 255);

    private enum State { MENU, PLAYING, PAUSED, GAME_OVER }

    private State state = State.MENU;

    private final Board board = new Board();
    private final Score score = new Score();
    private final Bag bag = new Bag();
    private final HighScore highScore = new HighScore();

    private Tetromino current;
    private Type hold;
    private boolean holdUsed;

    private long gravityAcc;
    private long lockTimer;
    private int lockResets;
    private boolean grounded;
    private boolean lastMoveWasRotation;

    private final List<Integer> clearingRows = new ArrayList<>();
    private long clearStartNs;
    private int pendingSpin;

    private String toast = "";
    private long toastUntil;
    private int toastSeq;
    private int levelShown = 1;
    private int lastPlace;

    private boolean leftHeld;
    private boolean rightHeld;
    private boolean downHeld;
    private int dashDir;
    private long dashTime;

    private long lastTickNs;
    private long nowNs;

    public GamePanel() {
        int width = MARGIN * 2 + Board.COLS * CELL + GAP + HUD_WIDTH;
        int height = MARGIN * 2 + Board.ROWS * CELL;
        setPreferredSize(new Dimension(width, height));
        setBackground(BG);
        setFocusable(true);
        addKeyListener(new Keys());

        Timer timer = new Timer(16, this);
        timer.setCoalesce(true);
        timer.start();
    }

    // ---------------------------------------------------------------- цикл

    @Override
    public void actionPerformed(java.awt.event.ActionEvent event) {
        nowNs = System.nanoTime();
        long dt = Math.min((nowNs - lastTickNs) / 1_000_000L, 100L);
        lastTickNs = nowNs;

        if (state == State.PLAYING) {
            update(dt);
        }
        repaint();
    }

    private void update(long dt) {
        if (!clearingRows.isEmpty()) {
            if (nowNs - clearStartNs >= CLEAR_ANIM_MS * 1_000_000L) {
                commitClear();
            }
            return;
        }
        if (current == null) {
            return;
        }

        handleDas(dt);

        long interval = downHeld ? Math.min(SOFT_DROP_MS, score.gravityMs()) : score.gravityMs();
        gravityAcc += dt;
        while (gravityAcc >= interval) {
            gravityAcc -= interval;
            if (!tryMove(0, 1)) {
                gravityAcc = 0;
                break;
            }
            if (downHeld) {
                score.addDropPoints(1);
            }
        }

        grounded = !canFall();
        if (grounded) {
            lockTimer += dt;
            if (lockTimer >= LOCK_DELAY_MS) {
                lockPiece(false);
            }
        } else {
            lockTimer = 0;
        }
    }

    private void handleDas(long dt) {
        int dir = leftHeld == rightHeld ? 0 : (leftHeld ? -1 : 1);
        if (dir == 0) {
            dashDir = 0;
            dashTime = 0;
            return;
        }
        if (dir != dashDir) {
            dashDir = dir;
            dashTime = 0;
            if (tryMove(dir, 0)) {
                registerManipulation();
                Sound.play(Sound.Effect.MOVE);
            }
            return;
        }
        dashTime += dt;
        while (dashTime >= ARR_MS) {
            dashTime -= ARR_MS;
            if (!tryMove(dir, 0)) {
                break;
            }
            registerManipulation();
        }
    }

    // ------------------------------------------------------------ операции

    private boolean canFall() {
        Tetromino test = current.copy();
        test.shift(0, 1);
        return board.fits(test);
    }

    private boolean tryMove(int dc, int dr) {
        if (current == null) {
            return false;
        }
        Tetromino test = current.copy();
        test.shift(dc, dr);
        if (!board.fits(test)) {
            return false;
        }
        current.shift(dc, dr);
        if (dr == 0) {
            lastMoveWasRotation = false;
        }
        return true;
    }

    /** dir: +1 по часовой, -1 против, 2 — на 180°. */
    private boolean rotate(int dir) {
        if (current == null) {
            return false;
        }
        int from = current.rotation();
        int to = Math.floorMod(from + dir, 4);
        int[][] offsets = dir == 2
            ? KickTable.halfOffsets(current.type())
            : KickTable.offsets(current.type(), from, to);

        for (int[] offset : offsets) {
            Tetromino test = current.copy();
            test.setRotation(to);
            test.shift(offset[0], offset[1]);
            if (board.fits(test)) {
                current = test;
                lastMoveWasRotation = true;
                registerManipulation();
                Sound.play(Sound.Effect.ROTATE);
                return true;
            }
        }
        return false;
    }

    /** Lock delay сбрасывается смещением или поворотом, но не больше 15 раз. */
    private void registerManipulation() {
        if (grounded && lockResets < MAX_LOCK_RESETS) {
            lockResets++;
            lockTimer = 0;
        }
    }

    private void hardDrop() {
        int distance = 0;
        while (tryMove(0, 1)) {
            distance++;
        }
        score.addDropPoints(distance * 2);
        lockPiece(true);
    }

    private void holdSwap() {
        if (current == null || holdUsed) {
            return;
        }
        Type stored = hold;
        hold = current.type();
        holdUsed = true;
        Sound.play(Sound.Effect.HOLD);
        spawn(stored == null ? bag.poll() : stored);
    }

    /** T-spin: последний ход был поворотом и вокруг центра T занято не меньше трёх углов. */
    private int detectSpin() {
        if (!lastMoveWasRotation || current == null || current.type() != Type.T) {
            return Score.SPIN_NONE;
        }
        int col = current.col();
        int row = current.row();
        boolean topLeft = board.solid(col, row);
        boolean topRight = board.solid(col + 2, row);
        boolean bottomLeft = board.solid(col, row + 2);
        boolean bottomRight = board.solid(col + 2, row + 2);

        int occupied = (topLeft ? 1 : 0) + (topRight ? 1 : 0) + (bottomLeft ? 1 : 0) + (bottomRight ? 1 : 0);
        if (occupied < 3) {
            return Score.SPIN_NONE;
        }
        boolean frontA;
        boolean frontB;
        switch (current.rotation()) {
            case 0 -> { frontA = topLeft; frontB = topRight; }
            case 1 -> { frontA = topRight; frontB = bottomRight; }
            case 2 -> { frontA = bottomLeft; frontB = bottomRight; }
            default -> { frontA = topLeft; frontB = bottomLeft; }
        }
        return frontA && frontB ? Score.SPIN_FULL : Score.SPIN_MINI;
    }

    private void lockPiece(boolean hard) {
        int spin = detectSpin();
        if (!board.lock(current)) {
            gameOver(); // lock out: фигура зафиксирована целиком над полем
            return;
        }
        Sound.play(hard ? Sound.Effect.HARD_DROP : Sound.Effect.LOCK);
        current = null;

        clearingRows.clear();
        clearingRows.addAll(board.fullRows());
        if (clearingRows.isEmpty()) {
            score.breakCombo();
            spawnFromQueue();
            return;
        }
        pendingSpin = spin;
        clearStartNs = System.nanoTime();
        Sound.play(clearingRows.size() == 4 ? Sound.Effect.TETRIS : Sound.Effect.CLEAR);
    }

    private void commitClear() {
        int rows = clearingRows.size();
        int spin = pendingSpin;
        board.removeRows(clearingRows);
        clearingRows.clear();

        score.awardClear(rows, spin, board.isEmpty());
        if (score.eventSeq() != toastSeq) {
            toastSeq = score.eventSeq();
            toast = score.lastEvent();
            toastUntil = nowNs + TOAST_MS * 1_000_000L;
        }
        if (score.level() > levelShown) {
            levelShown = score.level();
            Sound.play(Sound.Effect.LEVEL_UP);
        }
        spawnFromQueue();
    }

    private void spawnFromQueue() {
        holdUsed = false;
        spawn(bag.poll());
    }

    private void spawn(Type type) {
        current = new Tetromino(type);
        gravityAcc = 0;
        lockTimer = 0;
        lockResets = 0;
        grounded = false;
        lastMoveWasRotation = false;
        if (!board.fits(current)) {
            gameOver();
        }
    }

    private void startGame() {
        board.clear();
        score.reset();
        clearingRows.clear();
        hold = null;
        holdUsed = false;
        toast = "";
        levelShown = 1;
        lastPlace = 0;
        leftHeld = rightHeld = downHeld = false;
        dashDir = 0;
        state = State.PLAYING;
        spawnFromQueue();
    }

    private void gameOver() {
        state = State.GAME_OVER;
        current = null;
        lastPlace = highScore.submit(score.score());
        Sound.play(Sound.Effect.GAME_OVER);
    }

    // ---------------------------------------------------------------- ввод

    private final class Keys extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent event) {
            int code = event.getKeyCode();
            switch (code) {
                case KeyEvent.VK_LEFT, KeyEvent.VK_H -> leftHeld = true;
                case KeyEvent.VK_RIGHT, KeyEvent.VK_L -> rightHeld = true;
                case KeyEvent.VK_DOWN, KeyEvent.VK_J -> downHeld = true;
                default -> { }
            }

            switch (state) {
                case MENU, GAME_OVER -> {
                    if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_SPACE) {
                        startGame();
                    }
                    return;
                }
                case PAUSED -> {
                    if (code == KeyEvent.VK_P || code == KeyEvent.VK_ESCAPE || code == KeyEvent.VK_ENTER) {
                        state = State.PLAYING;
                    }
                    return;
                }
                default -> { }
            }

            switch (code) {
                case KeyEvent.VK_P, KeyEvent.VK_ESCAPE -> state = State.PAUSED;
                case KeyEvent.VK_R -> startGame();
                case KeyEvent.VK_M -> Sound.toggle();
                case KeyEvent.VK_UP, KeyEvent.VK_X -> rotate(1);
                case KeyEvent.VK_Z, KeyEvent.VK_CONTROL -> rotate(-1);
                case KeyEvent.VK_A -> rotate(2);
                case KeyEvent.VK_SPACE -> hardDrop();
                case KeyEvent.VK_C, KeyEvent.VK_SHIFT -> holdSwap();
                default -> { }
            }
        }

        @Override
        public void keyReleased(KeyEvent event) {
            switch (event.getKeyCode()) {
                case KeyEvent.VK_LEFT, KeyEvent.VK_H -> leftHeld = false;
                case KeyEvent.VK_RIGHT, KeyEvent.VK_L -> rightHeld = false;
                case KeyEvent.VK_DOWN, KeyEvent.VK_J -> downHeld = false;
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------- отрисовка

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawField(g);
        drawStack(g);
        if (state == State.PLAYING && current != null && clearingRows.isEmpty()) {
            drawGhost(g);
            drawPiece(g, current, 1f);
        }
        drawHud(g);
        drawToast(g);

        if (state != State.PLAYING) {
            drawOverlay(g);
        }
    }

    private void drawField(Graphics2D g) {
        g.setColor(CELL_EMPTY);
        g.fillRect(fieldX(), fieldY(), Board.COLS * CELL, Board.ROWS * CELL);
        g.setColor(GRID);
        for (int c = 1; c < Board.COLS; c++) {
            g.drawLine(fieldX() + c * CELL, fieldY(), fieldX() + c * CELL, fieldY() + Board.ROWS * CELL);
        }
        for (int r = 1; r < Board.ROWS; r++) {
            g.drawLine(fieldX(), fieldY() + r * CELL, fieldX() + Board.COLS * CELL, fieldY() + r * CELL);
        }
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(fieldX() - 2, fieldY() - 2, Board.COLS * CELL + 4, Board.ROWS * CELL + 4);
    }

    private void drawStack(Graphics2D g) {
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                int value = board.get(c, r);
                if (value == 0) {
                    continue;
                }
                Type type = Type.values()[value - 1];
                if (clearingRows.contains(r)) {
                    double progress = clamp((nowNs - clearStartNs) / (double) (CLEAR_ANIM_MS * 1_000_000L));
                    Color flash = blend(type.color, Color.WHITE, progress);
                    drawCell(g, c, r, flash, (float) (1.0 - progress * 0.6));
                } else {
                    drawCell(g, c, r, type.color, 1f);
                }
            }
        }
    }

    private void drawGhost(Graphics2D g) {
        Tetromino ghost = current.copy();
        while (true) {
            Tetromino test = ghost.copy();
            test.shift(0, 1);
            if (!board.fits(test)) {
                break;
            }
            ghost = test;
        }
        for (int[] cell : ghost.cells()) {
            if (cell[1] < 0) {
                continue;
            }
            int x = fieldX() + cell[0] * CELL + 2;
            int y = fieldY() + cell[1] * CELL + 2;
            g.setColor(blend(current.type().color, BG, 0.65));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(x, y, CELL - 5, CELL - 5, 5, 5);
        }
    }

    private void drawPiece(Graphics2D g, Tetromino piece, float alpha) {
        for (int[] cell : piece.cells()) {
            if (cell[1] < 0) {
                continue;
            }
            drawCell(g, cell[0], cell[1], piece.type().color, alpha);
        }
    }

    private void drawCell(Graphics2D g, int col, int row, Color color, float alpha) {
        int x = fieldX() + col * CELL + 1;
        int y = fieldY() + row * CELL + 1;
        int size = CELL - 3;
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
        g.setPaint(new java.awt.GradientPaint(x, y, brighten(color, 0.35), x, y + size, darken(color, 0.25)));
        g.fillRoundRect(x, y, size, size, 6, 6);
        g.setColor(blend(color, Color.BLACK, 0.55));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, y, size, size, 6, 6);
        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1f));
    }

    private void drawHud(Graphics2D g) {
        int x = fieldX() + Board.COLS * CELL + GAP;
        int y = fieldY();

        g.setColor(PANEL);
        g.fillRoundRect(x, y, HUD_WIDTH, 96, 10, 10);
        drawLabel(g, "УДЕРЖАНИЕ", x + 12, y + 22);
        if (hold != null) {
            drawPreview(g, hold, x + 12, y + 32, 78, 56);
        }

        y += 110;
        g.setColor(PANEL);
        g.fillRoundRect(x, y, HUD_WIDTH, 236, 10, 10);
        drawLabel(g, "СЛЕДУЮЩИЕ", x + 12, y + 22);
        List<Type> next = bag.preview(5);
        for (int i = 0; i < next.size(); i++) {
            drawPreview(g, next.get(i), x + 12, y + 32 + i * 40, 78, 36);
        }

        y += 250;
        g.setColor(PANEL);
        g.fillRoundRect(x, y, HUD_WIDTH, 154, 10, 10);
        drawLabel(g, "ОЧКИ", x + 12, y + 22);
        drawValue(g, Integer.toString(score.score()), x + 12, y + 48, 24);
        drawLabel(g, "УРОВЕНЬ " + score.level() + "   ЛИНИИ " + score.lines(), x + 12, y + 74);
        String extra = (score.combo() > 1 ? "КОМБО ×" + score.combo() : "")
            + (score.backToBack() ? (score.combo() > 1 ? "   " : "") + "B2B" : "");
        if (!extra.isEmpty()) {
            g.setColor(ACCENT);
            g.setFont(monoFont(14));
            g.drawString(extra, x + 12, y + 98);
        }
        drawLabel(g, "РЕКОРД " + highScore.best(), x + 12, y + 126);
        drawLabel(g, (Sound.enabled() ? "ЗВУК ВКЛ (M)" : "ЗВУК ВЫКЛ (M)"), x + 12, y + 144);
    }

    /** Миниатюра фигуры внутри небольшой рамки. */
    private void drawPreview(Graphics2D g, Type type, int x, int y, int width, int height) {
        int[][] cells = Shapes.cells(type, 0);
        int minCol = Integer.MAX_VALUE;
        int maxCol = Integer.MIN_VALUE;
        int minRow = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        for (int[] cell : cells) {
            minCol = Math.min(minCol, cell[0]);
            maxCol = Math.max(maxCol, cell[0]);
            minRow = Math.min(minRow, cell[1]);
            maxRow = Math.max(maxRow, cell[1]);
        }
        int pieceW = maxCol - minCol + 1;
        int pieceH = maxRow - minRow + 1;
        int unit = Math.min(width / pieceW, height / pieceH);
        int offsetX = x + (width - pieceW * unit) / 2;
        int offsetY = y + (height - pieceH * unit) / 2;

        for (int[] cell : cells) {
            int cx = offsetX + (cell[0] - minCol) * unit;
            int cy = offsetY + (cell[1] - minRow) * unit;
            g.setPaint(new java.awt.GradientPaint(cx, cy, brighten(type.color, 0.35), cx, cy + unit, darken(type.color, 0.25)));
            g.fillRoundRect(cx + 1, cy + 1, unit - 3, unit - 3, 4, 4);
        }
    }

    private void drawToast(Graphics2D g) {
        if (toast.isEmpty() || nowNs > toastUntil) {
            return;
        }
        Font font = boldFont(19);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(toast);
        int boxX = fieldX() + (Board.COLS * CELL - textWidth) / 2 - 12;
        int boxY = fieldY() + Board.ROWS * CELL / 2 - 40;

        g.setColor(new Color(0, 0, 0, 150));
        g.fillRoundRect(boxX, boxY, textWidth + 24, 40, 10, 10);
        g.setColor(ACCENT);
        g.drawString(toast, boxX + 12, boxY + 26);
    }

    private void drawOverlay(Graphics2D g) {
        int width = Board.COLS * CELL;
        int centerX = fieldX() + width / 2;

        g.setColor(new Color(8, 10, 16, 215));
        g.fillRect(fieldX(), fieldY(), width, Board.ROWS * CELL);

        switch (state) {
            case MENU -> {
                drawCentered(g, "ТЕТРИС", centerX, fieldY() + 130, boldFont(46), TEXT);
                drawCentered(g, "SRS · 7-bag · hold · T-спины", centerX, fieldY() + 168, regularFont(15), DIM);
                drawCentered(g, "СТРЕЛКИ — движение, ↓ — мягкое падение", centerX, fieldY() + 234, regularFont(13), TEXT);
                drawCentered(g, "↑ / X — поворот, Z — против, A — 180°", centerX, fieldY() + 257, regularFont(13), TEXT);
                drawCentered(g, "ПРОБЕЛ — сброс, C — удержание", centerX, fieldY() + 280, regularFont(13), TEXT);
                drawCentered(g, "P — пауза, R — заново, M — звук", centerX, fieldY() + 303, regularFont(13), TEXT);
                drawHighScores(g, centerX, fieldY() + 360);
                drawCentered(g, "НАЖМИТЕ ENTER", centerX, fieldY() + 520, boldFont(22), ACCENT);
            }
            case PAUSED -> drawCentered(g, "ПАУЗА", centerX, fieldY() + 300, boldFont(42), TEXT);
            case GAME_OVER -> {
                drawCentered(g, "ИГРА ОКОНЧЕНА", centerX, fieldY() + 180, boldFont(32), TEXT);
                drawCentered(g, "Очки: " + score.score(), centerX, fieldY() + 232, boldFont(24), ACCENT);
                drawCentered(g, "Линии: " + score.lines() + "   Уровень: " + score.level(),
                    centerX, fieldY() + 266, regularFont(16), DIM);
                if (lastPlace > 0) {
                    drawCentered(g, "Место в таблице: " + lastPlace, centerX, fieldY() + 300, boldFont(18), ACCENT);
                }
                drawHighScores(g, centerX, fieldY() + 350);
                drawCentered(g, "ENTER — новая игра", centerX, fieldY() + 520, boldFont(20), TEXT);
            }
            default -> { }
        }
    }

    private void drawHighScores(Graphics2D g, int centerX, int y) {
        List<Integer> scores = highScore.scores();
        drawCentered(g, "ЛУЧШИЕ РЕЗУЛЬТАТЫ", centerX, y, boldFont(15), DIM);
        if (scores.isEmpty()) {
            drawCentered(g, "пока пусто", centerX, y + 26, regularFont(14), DIM);
            return;
        }
        for (int i = 0; i < scores.size(); i++) {
            drawCentered(g, (i + 1) + ".  " + scores.get(i), centerX, y + 26 + i * 22, regularFont(15), TEXT);
        }
    }

    // ----------------------------------------------------------- мелочёвка

    private int fieldX() {
        return MARGIN;
    }

    private int fieldY() {
        return MARGIN;
    }

    private void drawLabel(Graphics2D g, String text, int x, int y) {
        g.setFont(boldFont(12));
        g.setColor(DIM);
        g.drawString(text, x, y);
    }

    private void drawValue(Graphics2D g, String text, int x, int y, int size) {
        g.setFont(monoFont(size));
        g.setColor(TEXT);
        g.drawString(text, x, y);
    }

    private void drawCentered(Graphics2D g, String text, int centerX, int y, Font font, Color color) {
        g.setFont(font);
        g.setColor(color);
        int width = g.getFontMetrics().stringWidth(text);
        g.drawString(text, centerX - width / 2, y);
    }

    private static Font boldFont(int size) {
        return new Font(Font.SANS_SERIF, Font.BOLD, size);
    }

    private static Font regularFont(int size) {
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }

    private static Font monoFont(int size) {
        return new Font(Font.MONOSPACED, Font.BOLD, size);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static Color brighten(Color color, double amount) {
        return blend(color, Color.WHITE, amount);
    }

    private static Color darken(Color color, double amount) {
        return blend(color, Color.BLACK, amount);
    }

    private static Color blend(Color a, Color b, double amount) {
        double t = clamp(amount);
        return new Color(
            (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
            (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
