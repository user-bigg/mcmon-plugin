package ru.mcmon.plugin.client.fabric;

import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.mcmon.plugin.client.Answer;
import ru.mcmon.plugin.client.LaunchCode;
import ru.mcmon.plugin.client.ServerTarget;

public final class LaunchCodeAnswers {

    private static final Logger LOG = LogManager.getLogger("MC Monitor");

    private LaunchCodeAnswers() {}

    public static Answer answer(String sessionNick, ServerTarget server) {
        Answer answer = LaunchCode.answer(FabricLoader.getInstance().getGameDir(), sessionNick, server);
        if (answer.isSent()) {
            LOG.info("The server asked for the launch code: sent");
        } else {
            LOG.info(
                    "The server asked for the launch code: not sent ({})",
                    answer.refusal().reason());
        }
        return answer;
    }

    public static void failed(Throwable error) {
        LOG.warn(
                "The mod failed on a login query and left it to the game: {}",
                error.getClass().getName());
    }
}
