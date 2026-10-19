package ru.mcmon.plugin.bungee;

import java.util.logging.Logger;
import ru.mcmon.plugin.core.Log;

final class BungeeLog implements Log {

    private final Logger logger;

    BungeeLog(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warning(message);
    }
}
