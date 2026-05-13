package smp.cloud.cmdShadeVelocity.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plugin configuration backed by a TOML file (config.toml) in the plugin's data directory.
 * <p>
 * The file is created with sensible defaults the first time the plugin starts;
 * existing files are read as-is. Missing keys fall back to the defaults declared here,
 * so adding a new option in a future version will not break old configs.
 */
public final class Config {

    /** Default MiniMessage-formatted message shown when a player tries a hidden command. */
    public static final String DEFAULT_UNKNOWN_COMMAND_MESSAGE =
            "<dark_red>[\uD83D\uDD0E] Unknown command. Type \"/help\" for help.</dark_red>";

    /** Default behaviour: hide namespaced commands (e.g. "minecraft:tp", "bukkit:plugins"). */
    public static final boolean DEFAULT_HIDE_NAMESPACED_COMMANDS = true;

    private static final String PATH_UNKNOWN_COMMAND = "messages.unknown_command";
    private static final String PATH_HIDE_NAMESPACED = "commands.hide_namespaced";

    private final String unknownCommandMessage;
    private final boolean hideNamespacedCommands;

    private Config(String unknownCommandMessage, boolean hideNamespacedCommands) {
        this.unknownCommandMessage = unknownCommandMessage;
        this.hideNamespacedCommands = hideNamespacedCommands;
    }

    /** The MiniMessage-formatted message shown when a player runs a hidden command. */
    public String unknownCommandMessage() {
        return unknownCommandMessage;
    }

    /**
     * Whether commands containing a namespace prefix (anything matching {@code <ns>:<name>},
     * e.g. {@code minecraft:tp}, {@code bukkit:plugins}) should be stripped from the
     * client command tree and tab-completion for every player.
     */
    public boolean hideNamespacedCommands() {
        return hideNamespacedCommands;
    }

    /**
     * Loads the configuration from {@code <dataDir>/config.toml}, creating the file with
     * defaults if it does not exist. Any I/O or parsing failure is logged and the defaults
     * are returned instead, so the plugin remains functional.
     */
    public static Config load(Path dataDir, Logger logger) {
        try {
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
            Path file = dataDir.resolve("config.toml");
            if (!Files.exists(file)) {
                writeDefault(file);
            }

            try (CommentedFileConfig config = CommentedFileConfig.builder(file)
                    .preserveInsertionOrder()
                    .build()) {
                config.load();

                String message = config.getOrElse(PATH_UNKNOWN_COMMAND, DEFAULT_UNKNOWN_COMMAND_MESSAGE);
                boolean hide = config.getOrElse(PATH_HIDE_NAMESPACED, DEFAULT_HIDE_NAMESPACED_COMMANDS);
                return new Config(message, hide);
            }
        } catch (Exception e) {
            logger.warn("Could not load config.toml; using built-in defaults.", e);
            return new Config(DEFAULT_UNKNOWN_COMMAND_MESSAGE, DEFAULT_HIDE_NAMESPACED_COMMANDS);
        }
    }

    private static void writeDefault(Path file) throws IOException {
        String content = """
                # cmd-shade-velocity configuration
                # All keys are optional — anything you remove falls back to the built-in default.
 
                [messages]
                # Message sent to a player who tries to execute a command they cannot use.
                # Parsed as MiniMessage: https://docs.advntr.dev/minimessage/format.html
                #
                # Examples:
                #   '<red>No such command.</red>'
                #   '<gradient:red:gold><bold>Forbidden!</bold></gradient>'
                #   '<#A33333>[🔎] Unknown command. Type "/help" for help.</#A33333>'
                unknown_command = '<dark_red>[🔎] Unknown command. Type "/help" for help.</dark_red>'
 
                [commands]
                # If true, commands with a namespace prefix (e.g. "minecraft:tp", "bukkit:plugins")
                # are stripped from the client command tree and tab-completion for every player,
                # regardless of permissions.
                #
                # If false, namespaced commands are kept and only filtered through the normal
                # per-player permission check, the same as any other command.
                hide_namespaced = true
                """;
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
