package tetris;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Звуки синтезируются на лету — внешних файлов и ассетов в проекте нет.
 * Если звуковая подсистема недоступна, звук молча отключается.
 */
public final class Sound {

    public enum Effect {
        MOVE(new double[][]{{220, 28}}),
        ROTATE(new double[][]{{330, 28}}),
        HOLD(new double[][]{{440, 40}, {550, 40}}),
        LOCK(new double[][]{{150, 45}}),
        HARD_DROP(new double[][]{{110, 60}}),
        CLEAR(new double[][]{{523, 70}, {659, 70}, {784, 90}}),
        TETRIS(new double[][]{{523, 70}, {659, 70}, {784, 70}, {1046, 120}}),
        LEVEL_UP(new double[][]{{660, 60}, {880, 60}, {1100, 90}}),
        GAME_OVER(new double[][]{{420, 120}, {320, 120}, {240, 140}, {160, 220}});

        final double[][] notes;

        Effect(double[][] notes) {
            this.notes = notes;
        }
    }

    private static final int RATE = 22050;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);

    private static boolean enabled = true;
    private static boolean unavailable;

    public static boolean enabled() {
        return enabled && !unavailable;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    public static void play(Effect effect) {
        if (!enabled()) {
            return;
        }
        try {
            byte[] pcm = render(effect.notes);
            Clip clip = AudioSystem.getClip();
            clip.open(FORMAT, pcm, 0, pcm.length);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.start();
        } catch (LineUnavailableException | IllegalArgumentException | IllegalStateException e) {
            unavailable = true; // например, в headless-окружении
        }
    }

    /** Собирает последовательность нот в 16-битный PCM с плавными огибающими. */
    private static byte[] render(double[][] notes) {
        int totalFrames = 0;
        for (double[] note : notes) {
            totalFrames += (int) (RATE * note[1] / 1000.0);
        }
        ByteBuffer buffer = ByteBuffer.allocate(totalFrames * 2).order(ByteOrder.LITTLE_ENDIAN);

        for (double[] note : notes) {
            double freq = note[0];
            int frames = (int) (RATE * note[1] / 1000.0);
            for (int i = 0; i < frames; i++) {
                double t = (double) i / RATE;
                double progress = (double) i / frames;
                double envelope = Math.min(1.0, progress * 12) * Math.pow(1.0 - progress, 1.6);
                double sample = Math.sin(2 * Math.PI * freq * t) * envelope * 0.35;
                buffer.putShort((short) (sample * Short.MAX_VALUE));
            }
        }
        return buffer.array();
    }

    private Sound() {
    }
}
