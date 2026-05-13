package smp.cloud.cmdShadeVelocity.resolvers;

import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Decides whether a player should see and run a given command label.
 * <p>
 * Checks, in order:
 *  1. an admin wildcard ('*'),
 *  2. an explicit alias from aliases.properties,
 *  3. common conventional permission patterns
 *     (bukkit.command.X, minecraft.command.X, velocity.command.X, X.use, etc.).
 * <p>
 * A label is allowed only if at least one check returns Tristate.TRUE.
 * Anything UNDEFINED or FALSE means "hide and block" — which is what you want
 * when a permission manager like LuckPerms is the source of truth.
 */
public final class PermissionResolver {

    private final Map<String, String> aliases;

    public PermissionResolver(Map<String, String> aliases) {
        this.aliases = aliases;
    }

    public boolean hasAccess(Player player, String rawLabel) {
        if (rawLabel == null || rawLabel.isEmpty()) return false;
        String label = rawLabel.toLowerCase(Locale.ROOT);

        if (player.getPermissionValue("*") == Tristate.TRUE) return false;

        String configured = aliases.get(label);
        if (configured == null) configured = aliases.get(stripNamespace(label));
        if (configured != null) {
            return !player.hasPermission(configured);
        }

        String name = stripNamespace(label);
        List<String> candidates = getCandidates(label, name);

        for (String perm : candidates) {
            if (player.getPermissionValue(perm) == Tristate.TRUE) return false;
        }
        return true;
    }

    private static @NonNull List<String> getCandidates(String label, String name) {
        String namespace = label.equals(name) ? null : label.substring(0, label.indexOf(':'));

        List<String> candidates = new ArrayList<>(10);
        if (namespace != null) {
            candidates.add(namespace + ".command." + name);
            candidates.add(namespace + "." + name);
        }
        candidates.add("bukkit.command." + name);
        candidates.add("minecraft.command." + name);
        candidates.add("velocity.command." + name);
        candidates.add("command." + name);
        candidates.add(name + ".command");
        candidates.add(name + ".use");
        candidates.add(name);
        return candidates;
    }

    private static String stripNamespace(String label) {
        int idx = label.indexOf(':');
        return idx < 0 ? label : label.substring(idx + 1);
    }
}
