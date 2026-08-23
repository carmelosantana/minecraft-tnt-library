/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xpfarm.tntlibrary.TntLibraryPlugin;
import org.xpfarm.tntlibrary.block.BombBlocks;
import org.xpfarm.tntlibrary.core.CustomTnt;
import org.xpfarm.tntlibrary.core.TntRegistry;
import org.xpfarm.tntlibrary.smartbomb.ParamCodec;
import org.xpfarm.tntlibrary.smartbomb.SmartBomb;
import org.xpfarm.tntlibrary.smartbomb.SmartBombFeature;
import org.xpfarm.tntlibrary.smartbomb.SmartBombParams;

/**
 * The {@code /tntlibrary} (alias {@code /tntlib}) command: {@code give}, {@code list}, {@code
 * reload}.
 *
 * <p>Argument parsing that is genuinely server-free — subcommand routing ({@link Subcommand}) and
 * give-amount validation ({@link CommandArgs}) — lives in unit-tested helpers; this class does the
 * server-touching work (resolving players and bomb definitions, granting items, replying) that only
 * runs on a live server and is verified at the runtime gate.
 *
 * <p>Every permission node it checks ({@link Permissions#GIVE}, {@link Permissions#RELOAD}) is
 * declared in {@code plugin.yml}; {@code PluginDescriptorTest} pins that agreement.
 */
public final class TntCommand implements CommandExecutor, TabCompleter {

    private final TntLibraryPlugin plugin;

    public TntCommand(TntLibraryPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        Optional<Subcommand> sub = args.length == 0 ? Optional.empty() : Subcommand.fromArg(args[0]);
        if (sub.isEmpty()) {
            usage(sender);
            return true;
        }
        return switch (sub.get()) {
            case GIVE -> give(sender, args);
            case LIST -> list(sender);
            case SMART -> smart(sender, args);
            case RELOAD -> reload(sender);
        };
    }

    // ---------------------------------------------------------------------------------------------
    // give
    // ---------------------------------------------------------------------------------------------

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.GIVE)) {
            denied(sender);
            return true;
        }
        if (args.length < 2) {
            reply(sender, Component.text("Usage: /tntlibrary give <bomb> [player] [amount]",
                    NamedTextColor.RED));
            return true;
        }

        String bombId = args[1];
        Optional<CustomTnt> bomb = plugin.registry().get(bombId);
        if (bomb.isEmpty()) {
            reply(sender, Component.text("Unknown or disabled bomb: '" + bombId + "'. Try /tntlibrary "
                    + "list.", NamedTextColor.RED));
            return true;
        }

        Player target = resolveTarget(sender, args);
        if (target == null) {
            return true; // resolveTarget already messaged the sender
        }

        int amount = 1;
        if (args.length >= 4) {
            OptionalInt parsed = CommandArgs.parseAmount(args[3]);
            if (parsed.isEmpty()) {
                reply(sender, Component.text("Amount must be a whole number between "
                        + CommandArgs.MIN_AMOUNT + " and " + CommandArgs.MAX_AMOUNT + ".",
                        NamedTextColor.RED));
                return true;
            }
            amount = parsed.getAsInt();
        }

        ItemStack stack = bomb.get().createItem();
        stack.setAmount(amount);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(stack);
        for (ItemStack leftover : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }

        Component name = bomb.get().displayName();
        reply(sender, Component.text("Gave ", NamedTextColor.GREEN)
                .append(Component.text(amount + "x ", NamedTextColor.GREEN))
                .append(name)
                .append(Component.text(" to " + target.getName() + ".", NamedTextColor.GREEN)));
        return true;
    }

    /**
     * Resolves the give target from {@code args[2]} (an online player name) or, if absent, the
     * sender when the sender is a player. Messages the sender and returns {@code null} on failure
     * (console with no name, or an offline/unknown name).
     */
    @Nullable
    private Player resolveTarget(CommandSender sender, String[] args) {
        if (args.length >= 3) {
            Player named = plugin.getServer().getPlayerExact(args[2]);
            if (named == null) {
                reply(sender, Component.text("Player not found or offline: '" + args[2] + "'.",
                        NamedTextColor.RED));
            }
            return named;
        }
        if (sender instanceof Player self) {
            return self;
        }
        reply(sender, Component.text("From the console you must name a player: /tntlibrary give <bomb> "
                + "<player> [amount]", NamedTextColor.RED));
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // list
    // ---------------------------------------------------------------------------------------------

    private boolean list(CommandSender sender) {
        // list is op-ish: reuse the give gate (parented by tntlibrary.admin in plugin.yml).
        if (!sender.hasPermission(Permissions.GIVE)) {
            denied(sender);
            return true;
        }
        TntRegistry registry = plugin.registry();
        if (registry.size() == 0) {
            reply(sender, Component.text("No bombs are registered.", NamedTextColor.YELLOW));
            return true;
        }
        reply(sender, Component.text("Registered bombs (" + registry.size() + "):",
                NamedTextColor.AQUA));
        for (String id : registry.ids()) {
            reply(sender, Component.text(" - ", NamedTextColor.GRAY)
                    .append(Component.text(id, NamedTextColor.WHITE))
                    .append(Component.text(" [enabled]", NamedTextColor.GREEN)));
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // reload
    // ---------------------------------------------------------------------------------------------

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission(Permissions.RELOAD)) {
            denied(sender);
            return true;
        }
        String summary = plugin.reloadPlugin();
        reply(sender, Component.text("TNTLibrary " + summary, NamedTextColor.GREEN));
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // smart
    // ---------------------------------------------------------------------------------------------

    /**
     * Programs the placed Smart Bomb the player is looking at: {@code get} prints its current params,
     * {@code set <key> <value>} edits one field. Reuses the unit-tested {@link ParamCodec} for parsing
     * and rendering, so this method only does the server-touching work (target resolution, replies).
     */
    private boolean smart(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.SMART)) {
            denied(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            reply(sender, red("Only a player can program a Smart Bomb."));
            return true;
        }
        SmartBombFeature feature = plugin.smartBomb();
        if (feature == null || plugin.registry().get(SmartBomb.ID).isEmpty()) {
            reply(sender, red("The Smart Bomb is disabled on this server."));
            return true;
        }
        Block target = player.getTargetBlockExact(6);
        if (target == null || !BombBlocks.bombIdOf(target).map(SmartBomb.ID::equals).orElse(false)) {
            reply(sender, red("Look at a placed Smart Bomb within 6 blocks."));
            return true;
        }
        if (args.length < 2) {
            reply(sender, yellow("Usage: /tntlibrary smart <get | set <key> <value>>"));
            return true;
        }
        SmartBombParams current = feature.programmer().current(target, feature.seedParams());
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("get")) {
            reply(sender, aqua("Smart Bomb: " + ParamCodec.describe(current)));
            return true;
        }
        if (action.equals("set")) {
            if (args.length < 4) {
                reply(sender, red("Usage: /tntlibrary smart set <key> <value>"));
                return true;
            }
            ParamCodec.Edit edit = ParamCodec.applyKeyValue(current, args[2], args[3]);
            if (edit.error() != null) {
                reply(sender, red(edit.error()));
                return true;
            }
            feature.programmer().apply(target, edit.params());
            String note = feature.watcher().isArmed(target) ? " (re-arm to apply while armed)" : "";
            reply(sender, green("Set " + args[2].toLowerCase(Locale.ROOT) + ". Now: "
                    + ParamCodec.describe(edit.params()) + note));
            return true;
        }
        reply(sender, yellow("Usage: /tntlibrary smart <get | set <key> <value>>"));
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Tab completion
    // ---------------------------------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission(Permissions.GIVE)) {
                subs.add(Subcommand.GIVE.label());
                subs.add(Subcommand.LIST.label());
            }
            if (sender.hasPermission(Permissions.SMART)) {
                subs.add(Subcommand.SMART.label());
            }
            if (sender.hasPermission(Permissions.RELOAD)) {
                subs.add(Subcommand.RELOAD.label());
            }
            return prefixed(subs, args[0]);
        }

        Optional<Subcommand> sub = Subcommand.fromArg(args[0]);
        if (sub.isPresent() && sub.get() == Subcommand.SMART && sender.hasPermission(Permissions.SMART)) {
            return switch (args.length) {
                case 2 -> prefixed(List.of("get", "set"), args[1]);
                case 3 -> args[1].equalsIgnoreCase("set")
                        ? prefixed(List.of(ParamCodec.KEY_RADIUS, ParamCodec.KEY_DELAY,
                                ParamCodec.KEY_TIME, ParamCodec.KEY_PROXIMITY,
                                ParamCodec.KEY_PROXIMITY_RADIUS), args[2])
                        : List.of();
                default -> List.of();
            };
        }
        if (sub.isEmpty() || sub.get() != Subcommand.GIVE || !sender.hasPermission(Permissions.GIVE)) {
            return List.of();
        }
        return switch (args.length) {
            case 2 -> prefixed(new ArrayList<>(plugin.registry().ids()), args[1]);
            case 3 -> prefixed(onlinePlayerNames(), args[2]);
            case 4 -> prefixed(List.of("1", "16", "32", "64"), args[3]);
            default -> List.of();
        };
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            names.add(online.getName());
        }
        return names;
    }

    private static List<String> prefixed(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }

    // ---------------------------------------------------------------------------------------------
    // Shared replies
    // ---------------------------------------------------------------------------------------------

    private void usage(CommandSender sender) {
        reply(sender, Component.text("Usage: /tntlibrary <give <bomb> [player] [amount] | list | "
                + "reload>", NamedTextColor.YELLOW));
    }

    private void denied(CommandSender sender) {
        reply(sender, Component.text("You don't have permission to do that.", NamedTextColor.RED));
    }

    private static void reply(CommandSender sender, Component message) {
        sender.sendMessage(message);
    }

    private static Component red(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    private static Component yellow(String message) {
        return Component.text(message, NamedTextColor.YELLOW);
    }

    private static Component aqua(String message) {
        return Component.text(message, NamedTextColor.AQUA);
    }

    private static Component green(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }
}
