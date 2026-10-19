package ru.mcmon.plugin.velocity;

import org.slf4j.Logger;
import ru.mcmon.plugin.core.Log;

final class VelocityLog implements Log {

    private final Logger logger;

    VelocityLog(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }
}
