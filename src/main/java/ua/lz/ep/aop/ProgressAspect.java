package ua.lz.ep.aop;



import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.stereotype.Component;
import ua.lz.ep.utils.MathUtil;

import java.util.Arrays;

@Aspect
@Component
public class ProgressAspect {

    private static final Logger logger = LogManager.getLogger(ProgressAspect.class);
    /**
     *  Перехватываем вызов методов, помеченных @ReportProgress,
     *  где ожидаем аргументы (int processed, long total, int pageSize).
     */
    @Before("@annotation(com.se.sample.aop.ReportProgress) && args(processed, total, pageSize)")
    public void beforeReportProgress(int processed, long total, int pageSize) {
        double progress =  MathUtil.calculateProgress(processed, total);
        logger.info("Processed:{} documents, count:{}, progress: {}%", pageSize, total, progress);
    }

    /**     * Защита: если сигнатура вызова другая, попадём сюда — логируем аргументы     */
    @Before("@annotation(com.se.sample.aop.ReportProgress)")
    public void beforeReportProgressFallback(org.aspectj.lang.JoinPoint jp) {
        Object[] args = jp.getArgs();
        logger.debug("ReportProgress invoked with args: {}", Arrays.toString(args));
    }
}