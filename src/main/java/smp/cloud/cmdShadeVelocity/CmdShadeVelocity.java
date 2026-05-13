package smp.cloud.cmdShadeVelocity;

import com.google.inject.Inject;
import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import smp.cloud.cmdShadeVelocity.config.Config;
import smp.cloud.cmdShadeVelocity.resolvers.PermissionResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CmdShadeVelocity {
    private final Logger logger;
    private final Path dataDir;

    private PermissionResolver resolver;
    private Component unknownCommandMessage;
    private boolean hideNamespacedCommands;

    @Inject
    public CmdShadeVelocity(Logger logger, @DataDirectory Path dataDir) {
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        Config config = Config.load(dataDir, logger);
        this.hideNamespacedCommands = config.hideNamespacedCommands();
        this.unknownCommandMessage = parseMessage(config.unknownCommandMessage());

        this.resolver = new PermissionResolver(loadAliases());
        logger.info("\u001B[36mSuccessfully enabled.\u001B[0m");
    }

    private Component parseMessage(String raw) {
        try {
            return MiniMessage.miniMessage().deserialize(raw);
        } catch (Exception e) {
            logger.warn("Invalid MiniMessage in config.toml ({}); falling back to default.", e.getMessage());
            return MiniMessage.miniMessage().deserialize(Config.DEFAULT_UNKNOWN_COMMAND_MESSAGE);
        }
    }

    private Map<String, String> loadAliases() {
        Map<String, String> map = new HashMap<>();
        try {
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
            Path file = dataDir.resolve("aliases.properties");
            if (!Files.exists(file)) {
                Files.writeString(file, """
                        # CommandHider aliases
                        # Map a command label to a custom permission node.
                        # If the player has the permission, the command is visible and usable;
                        # otherwise it is hidden and blocked.
                        #
                        # Format:   <commandLabel>=<permissionNode>
                        # Example:  lp=luckperms.commandregistry
                        #           luckperms=luckperms.commandregistry
                        #           gamemode=essentials.gamemode
                        """);
            }
            Properties props = new Properties();
            try (var in = Files.newInputStream(file)) {
                props.load(in);
            }
            for (String key : props.stringPropertyNames()) {
                map.put(key.toLowerCase(Locale.ROOT), props.getProperty(key));
            }
        } catch (IOException e) {
            logger.warn("Could not load aliases.properties; falling back to built-in patterns only.", e);
        }
        return map;
    }

    /**
     * Fires right before Velocity sends the Brigadier command tree to a client.
     * Anything we remove here disappears from tab-completion and the client's
     * autocomplete UI for that player.
     */
    @Subscribe
    public void onAvailableCommands(PlayerAvailableCommandsEvent event) {
        Player player = event.getPlayer();
        CommandNode<?> root = event.getRootNode();

        List<CommandNode<?>> children = new ArrayList<>(root.getChildren());
        for (CommandNode<?> child : children) {
            String label = child.getName();

            if (hideNamespacedCommands && label.indexOf(':') >= 0) {
                root.removeChildByName(label);
                continue;
            }

            if (resolver.hasAccess(player, label)) {
                root.removeChildByName(label);
            }
        }
    }

    /**
     * Even if a hidden command leaks through (a stubborn client, console alias,
     * macro, etc.), we cancel execution here so the command is genuinely unusable.
     */
    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) return;

        String cmd = event.getCommand();
        int space = cmd.indexOf(' ');
        String label = (space < 0 ? cmd : cmd.substring(0, space)).toLowerCase(Locale.ROOT);

        if (resolver.hasAccess(player, label)) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(unknownCommandMessage);
        }
    }

    /**
     * Defense in depth: filter tab-completion suggestions the backend might send
     * for partial command labels.
     */
    @Subscribe
    public void onTabComplete(TabCompleteEvent event) {
        Player player = event.getPlayer();
        String partial = event.getPartialMessage();
        if (partial.contains(" ")) return;

        event.getSuggestions().removeIf(suggestion -> {
            String s = suggestion.toLowerCase(Locale.ROOT);
            if (s.startsWith("/")) s = s.substring(1);
            if (hideNamespacedCommands && s.indexOf(':') >= 0) return true;
            return resolver.hasAccess(player, s);
        });
    }
}