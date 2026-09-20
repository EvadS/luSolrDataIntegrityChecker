package ua.lz.ep.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MathUtil {

    private MathUtil() {
    }

    public static double calculateProgress(int processed, long total) {
        double progress = 0.0;
        if (total > 0) {
            BigDecimal bd = BigDecimal.valueOf(((double) processed / total) * 100);
            progress = Math.min(bd.setScale(2, RoundingMode.HALF_UP).doubleValue(), 100.0);
        }
        return progress;
    }
}
