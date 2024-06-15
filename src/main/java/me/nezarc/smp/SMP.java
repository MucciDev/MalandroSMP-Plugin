package me.nezarc.smp;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.ArrayList;

public final class SMP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private Logger logger;
    private HashMap<UUID, Integer> playerHearts = new HashMap<>();
    private UUID malandro = null;
    private UUID malandroTarget = null;
    private Random random = new Random();

    @Override
    public void onEnable() {
        // Plugin startup logic
        logger = getLogger();
        logger.info("MalandroSMP a ligar");

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Register commands
        this.getCommand("giveheart").setExecutor(this);
        this.getCommand("smpadmin").setExecutor(this);
        this.getCommand("smpadmin").setTabCompleter(this);

        // Initialize players' max health
        for (Player player : Bukkit.getOnlinePlayers()) {
            initializePlayerHealth(player);
        }

        // Schedule session tasks
        scheduleMalandroSelection();
        scheduleSessionTasks();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        logger.info("MalandroSMP a desligar");
    }

    private void initializePlayerHealth(Player player) {
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(40.0); // 20 hearts
        player.setHealth(40.0);
        playerHearts.put(player.getUniqueId(), 40);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();

        int lostHearts = (killer != null) ? 4 : 2; // 2 hearts if natural, 4 if killed
        int gainedHearts = 2;

        // Update dead player's hearts
        int newHealth = playerHearts.get(player.getUniqueId()) - lostHearts;
        playerHearts.put(player.getUniqueId(), newHealth);
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(newHealth);

        // Update killer's hearts if applicable
        if (killer != null) {
            int newKillerHealth = playerHearts.get(killer.getUniqueId()) + gainedHearts;
            playerHearts.put(killer.getUniqueId(), newKillerHealth);
            killer.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(newKillerHealth);
        }

        // Check for elimination
        if (newHealth < 10) { // less than 5 hearts
            player.setHealth(0.0); // eliminate player
            Bukkit.broadcastMessage(player.getName() + " foi eliminado :(");
            player.kickPlayer("Foste eliminado do MalandroSMP");
            Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), "Foste eliminado seu malandro", null, null);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Apenas players podem executar este comando.");
            return true;
        }

        Player player = (Player) sender;
        if (command.getName().equalsIgnoreCase("giveheart")) {
            if (args.length != 1) {
                player.sendMessage("Exemplo: /giveheart <player>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                player.sendMessage("Player não está online ou não foi encontrado.");
                return true;
            }

            // Transfer heart
            int playerHealth = playerHearts.get(player.getUniqueId());
            if (playerHealth > 2) {
                int targetHealth = playerHearts.get(target.getUniqueId());

                playerHearts.put(player.getUniqueId(), playerHealth - 2);
                playerHearts.put(target.getUniqueId(), targetHealth + 2);

                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(playerHealth - 2);
                target.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(targetHealth + 2);

                player.sendMessage("Tu deste um coração ao " + target.getName());
                target.sendMessage("Tu recebeste um coração de " + player.getName());
            } else {
                player.sendMessage("Não tens corações suficientes.");
            }

            return true;
        } else if (command.getName().equalsIgnoreCase("smpadmin")) {
            if (!player.hasPermission("smp.admin")) {
                player.sendMessage("Não tens permissão para executar este comando.");
                return true;
            }

            if (args.length < 1) {
                player.sendMessage("Exemplo: /smpadmin <subcommand>");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "sethearts":
                    if (args.length != 3) {
                        player.sendMessage("Exemplo: /smpadmin sethearts <player> <amount>");
                        return true;
                    }
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null || !target.isOnline()) {
                        player.sendMessage("Player não está online ou não foi encontrado.");
                        return true;
                    }
                    int hearts;
                    try {
                        hearts = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage("O valor de corações deve ser um número.");
                        return true;
                    }
                    setPlayerHearts(target, hearts);
                    player.sendMessage("Corações de " + target.getName() + " definidos para " + hearts / 2);
                    return true;
                case "setmalandro":
                    if (args.length != 2) {
                        player.sendMessage("Exemplo: /smpadmin setmalandro <player>");
                        return true;
                    }
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null || !target.isOnline()) {
                        player.sendMessage("Player não está online ou não foi encontrado.");
                        return true;
                    }
                    setMalandro(target);
                    player.sendMessage(target.getName() + " foi definido como o Malandro.");
                    return true;
                case "endSession":
                    endSession();
                    player.sendMessage("Sessão terminada e max health de todos os jogadores diminuída.");
                    return true;
                default:
                    player.sendMessage("Subcomando desconhecido.");
                    return true;
            }
        }

        return false;
    }

    private void setPlayerHearts(Player player, int hearts) {
        playerHearts.put(player.getUniqueId(), hearts);
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(hearts);
        player.setHealth(hearts);
    }

    private void setMalandro(Player player) {
        malandro = player.getUniqueId();
        malandroTarget = Bukkit.getOnlinePlayers().toArray(new Player[0])[random.nextInt(Bukkit.getOnlinePlayers().size())].getUniqueId();
        player.sendMessage("Foste definido como Malandro, o teu objetivo é matar " + Bukkit.getPlayer(malandroTarget).getName());
    }

    private void endSession() {
        for (UUID playerId : playerHearts.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                int newHealth = playerHearts.get(playerId) - 2;
                playerHearts.put(playerId, newHealth);
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(newHealth);
                if (newHealth < 10) {
                    player.setHealth(0.0); // eliminate player
                    Bukkit.broadcastMessage(player.getName() + " foi eliminado!");
                    player.kickPlayer("Foste eliminado do MalandroSMP");
                    Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), "Foste eliminado seu malandro", null, null);
                }
            }
        }
    }

    private void scheduleMalandroSelection() {
        // Schedule the selection of a new Malandro at the start of each session
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
            if (players.length > 0) {
                malandro = players[random.nextInt(players.length)].getUniqueId();
                malandroTarget = players[random.nextInt(players.length)].getUniqueId();
                Player malandroPlayer = Bukkit.getPlayer(malandro);
                Player targetPlayer = Bukkit.getPlayer(malandroTarget);
                if (malandroPlayer != null && targetPlayer != null) {
                    malandroPlayer.sendMessage("Foste escolhido como Malandro, o teu objetivo é matar " + targetPlayer.getName());
                }
            }
        }, 0L, 108000L); // 108000 ticks = 3 hours
    }

    private void scheduleSessionTasks() {
        // Decrease max health of all players at the end of each session
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (UUID playerId : playerHearts.keySet()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    int newHealth = playerHearts.get(playerId) - 2;
                    playerHearts.put(playerId, newHealth);
                    player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(newHealth);
                    if (newHealth < 10) {
                        player.setHealth(0.0); // eliminate player
                        Bukkit.broadcastMessage(player.getName() + " foi eliminado!");
                        player.kickPlayer("Foste eliminado do MalandroSMP");
                        Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), "Foste eliminado seu malandro", null, null);
                    }
                }
            }
        }, 108000L, 108000L); // 108000 ticks = 3 hours
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("smpadmin")) {
            if (args.length == 1) {
                List<String> subcommands = new ArrayList<>();
                subcommands.add("sethearts");
                subcommands.add("setmalandro");
                subcommands.add("endsession");
                return subcommands;
            } else if (args.length == 2 && args[0].equalsIgnoreCase("sethearts")) {
                List<String> playerNames = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    playerNames.add(player.getName());
                }
                return playerNames;
            } else if (args.length == 2 && args[0].equalsIgnoreCase("setmalandro")) {
                List<String> playerNames = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    playerNames.add(player.getName());
                }
                return playerNames;
            }
        }
        return null;
    }
}
