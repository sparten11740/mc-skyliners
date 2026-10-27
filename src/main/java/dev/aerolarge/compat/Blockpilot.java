package dev.aerolarge.compat;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Blockpilot, the client mod an agent play-tests Aerolarge with (github.com/sparten11740/blockpilot-mcp): when it is
 * installed, Aerolarge gives it commands of its own for its airframes (see
 * {@link dev.aerolarge.compat.blockpilot.BlockpilotCommands}). They are looked up by name, because a build made without
 * Blockpilot's API at hand leaves them out (see the build script), and only touched when Blockpilot is there.
 */
public final class Blockpilot {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String COMMANDS = "dev.aerolarge.compat.blockpilot.BlockpilotCommands";

    private Blockpilot() {
    }

    /** Registers the commands with Blockpilot, during mod loading, if it is installed and they were built. */
    public static void register() {
        if (!ModList.get().isLoaded("blockpilot")) {
            return;
        }
        try {
            Class.forName(COMMANDS).getMethod("register").invoke(null);
        } catch (ClassNotFoundException e) {
            LOGGER.info("Aerolarge was built without Blockpilot's API, so it gives Blockpilot no commands of its own");
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Blockpilot did not take Aerolarge's commands", e);
        }
    }
}
