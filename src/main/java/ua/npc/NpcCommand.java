package ua.npc;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class NpcCommand implements CommandExecutor, TabCompleter {
    private final Main plugin;
    private final NpcManager manager;

    public NpcCommand(Main plugin, NpcManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("npcs.admin")) { s.sendMessage("§cНемає прав."); return true; }
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) { help(s); return true; }

        String sub = a[0].toLowerCase();
        if (sub.equals("list")) {
            s.sendMessage("§eNPC: §f" + manager.all().stream().map(n -> n.id).collect(Collectors.joining(", ")));
            return true;
        }
        if (sub.equals("reload")) {
            for (Npc n : manager.all()) for (UUID u : new ArrayList<>(n.viewers)) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) manager.hide(n, p);
            }
            manager.load();
            s.sendMessage("§aПерезавантажено: " + manager.all().size());
            return true;
        }
        if (a.length < 2) { help(s); return true; }

        if (sub.equals("create")) {
            if (!(s instanceof Player p)) return true;
            if (manager.get(a[1]) != null) { s.sendMessage("§cТакий NPC вже є."); return true; }
            Npc n = manager.create(a[1], p.getLocation());
            s.sendMessage("§aNPC створено: " + n.id + " (відкрий меню: /npc edit " + n.id + ")");
            return true;
        }

        Npc npc = manager.get(a[1]);
        if (npc == null) { s.sendMessage("§cNPC не знайдено."); return true; }

        switch (sub) {
            case "edit" -> { if (s instanceof Player p) NpcMenu.open(p, npc); }
            case "remove" -> { manager.remove(npc); s.sendMessage("§aВидалено."); }
            case "tp" -> {
                if (s instanceof Player p) {
                    npc.loc = p.getLocation().clone();
                    manager.refresh(npc);
                    s.sendMessage("§aNPC переміщено до тебе.");
                }
            }
            case "lookclose" -> {
                npc.lookClose = !npc.lookClose;
                manager.save();
                s.sendMessage("§eLookClose: " + npc.lookClose);
            }
            case "skin" -> {
                if (a.length < 3) { help(s); return true; }
                s.sendMessage("§7Завантажую скін...");
                SkinFetcher.byName(a[2]).thenAccept(skin -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (skin == null) s.sendMessage("§cСкін не знайдено.");
                    else { manager.setSkin(npc, a[2], skin); s.sendMessage("§aСкін змінено."); }
                }));
            }
            case "action" -> {
                // /npc action <id> <right|left> <дія...>
                if (a.length < 4) { help(s); return true; }
                String text = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
                (a[2].equalsIgnoreCase("left") ? npc.leftActions : npc.rightActions).add(text);
                manager.save();
                s.sendMessage("§aДію додано.");
            }
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage("""
                §e/npc create <id> §7- створити тут
                §e/npc edit <id> §7- GUI редактор
                §e/npc skin <id> <нік> §7- скін
                §e/npc tp <id> §7- перенести до себе
                §e/npc lookclose <id> §7- повертати голову
                §e/npc action <id> <right|left> <[console]/[player]/[message] ...>
                §e/npc remove <id> | list | reload""");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return List.of("create", "edit", "skin", "tp", "lookclose", "action", "remove", "list", "reload");
        if (a.length == 2 && !a[0].equalsIgnoreCase("create"))
            return manager.all().stream().map(n -> n.id).collect(Collectors.toList());
        if (a.length == 3 && a[0].equalsIgnoreCase("action")) return List.of("right", "left");
        return List.of();
    }
}
