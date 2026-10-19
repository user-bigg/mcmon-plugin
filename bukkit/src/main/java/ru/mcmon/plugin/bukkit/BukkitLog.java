package ru.mcmon.plugin.bukkit;

import java.util.logging.Logger;
import ru.mcmon.plugin.core.Log;

final class BukkitLog implements Log {

    private final Logger logger;

    BukkitLog(Logger logger) {
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
