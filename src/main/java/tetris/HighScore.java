package tetris;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Таблица рекордов: пять лучших результатов в ~/.tetris-java/scores.txt.
 * Файл локальный, никаких сетевых вызовов.
 */
public final class HighScore {

    private static final int LIMIT = 5;

    private final Path file;
    private final List<Integer> scores = new ArrayList<>();

    public HighScore() {
        this(Path.of(System.getProperty("user.home"), ".tetris-java", "scores.txt"));
    }

    public HighScore(Path file) {
        this.file = file;
        load();
    }

    public List<Integer> scores() {
        return List.copyOf(scores);
    }

    public int best() {
        return scores.isEmpty() ? 0 : scores.get(0);
    }

    /** Добавляет результат, возвращает место (1..5) или 0, если в топ не попал. */
    public int submit(int score) {
        if (score <= 0) {
            return 0;
        }
        scores.add(score);
        scores.sort((a, b) -> Integer.compare(b, a));
        if (scores.size() > LIMIT) {
            scores.remove(scores.size() - 1);
        }
        int place = scores.indexOf(score) + 1;
        save();
        return place;
    }

    private void load() {
        try {
            if (!Files.exists(file)) {
                return;
            }
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    scores.add(Integer.parseInt(trimmed));
                }
            }
        } catch (IOException | NumberFormatException e) {
            // повреждённый файл — начинаем с чистой таблицы
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            List<String> lines = scores.stream().map(String::valueOf).toList();
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // не удалось записать — рекорды останутся только в памяти
        }
    }
}
