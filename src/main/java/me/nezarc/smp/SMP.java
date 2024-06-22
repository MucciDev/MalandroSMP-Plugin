package me.nezarc.smp;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class SMP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private Map<UUID, Integer> playerHearts = new HashMap<>();
    private UUID malandro = null;
    private UUID malandroTarget = null;
    private UUID previousMalandro = null;
    private UUID previousMalandroTarget = null;
    private Random random = new Random();
    private boolean sessionActive = false;
    private boolean firstSession = true;

    @Override
    public void onEnable() {
        getLogger().info("MalandroSMP está ligando.");

        // Register events and commands
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("giveheart").setExecutor(this);
        getCommand("smpadmin").setExecutor(this);
        getCommand("smpadmin").setTabCompleter(this);

        // Load saved player hearts from config (if available)
        loadPlayerHearts();

        // Initialize or enforce whitelist based on session status
        if (sessionActive) {
            Bukkit.getOnlinePlayers().forEach(this::initializePlayerHealth);
        } else {
            Bukkit.setWhitelist(true);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("MalandroSMP está desligando.");

        // Save player hearts to config
        savePlayerHearts();
    }

    private void loadPlayerHearts() {
        FileConfiguration config = getConfig();
        if (config.contains("playerHearts")) {
            for (String key : config.getConfigurationSection("playerHearts").getKeys(false)) {
                UUID playerId = UUID.fromString(key);
                int hearts = config.getInt("playerHearts." + key);
                playerHearts.put(playerId, hearts);
            }
        }
    }

    private void savePlayerHearts() {
        FileConfiguration config = getConfig();
        for (UUID playerId : playerHearts.keySet()) {
            config.set("playerHearts." + playerId.toString(), playerHearts.get(playerId));
        }
        saveConfig();
    }

    private void initializePlayerHealth(Player player) {
        int maxHealth = firstSession ? 40 : playerHearts.getOrDefault(player.getUniqueId(), 20);
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
        player.setHealth(maxHealth);
        playerHearts.put(player.getUniqueId(), maxHealth);
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
            int newKillerHealth = playerHearts.getOrDefault(killer.getUniqueId(), 20) + gainedHearts;
            playerHearts.put(killer.getUniqueId(), newKillerHealth);
            killer.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(newKillerHealth);
        }

        // Check for elimination
        if (newHealth < 10) { // less than 5 hearts
            player.setHealth(0.0); // eliminate player
            Bukkit.broadcastMessage(player.getName() + " foi eliminado.");
            player.kickPlayer("Foste eliminado do MalandroSMP");
            Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), "Foste eliminado seu malandro", null, null);
        }
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        if (!sessionActive && !player.isOp()) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "A sessão ainda não começou. Apenas operadores podem entrar.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Apenas jogadores podem executar este comando.");
            return true;
        }

        Player player = (Player) sender;
        if (command.getName().equalsIgnoreCase("giveheart")) {
            return handleGiveHeartCommand(player, args);
        } else if (command.getName().equalsIgnoreCase("smpadmin")) {
            return handleSmpAdminCommand(player, args);
        }

        return false;
    }

    private boolean handleGiveHeartCommand(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("Uso: /giveheart <player> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("O jogador não está online ou não foi encontrado.");
            return true;
        }

        int heartsToGive;
        try {
            heartsToGive = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("A quantidade de corações deve ser um número inteiro.");
            return true;
        }

        if (heartsToGive <= 0) {
            player.sendMessage("A quantidade de corações deve ser maior que zero.");
            return true;
        }

        int playerHealth = playerHearts.get(player.getUniqueId());

        // Ensure the player has at least 5 hearts remaining after giving
        if (playerHealth - (heartsToGive * 2) < 10) {
            player.sendMessage("Não podes dar tantos corações. Deves manter pelo menos 5 corações.");
            return true;
        }

        int targetHealth = playerHearts.getOrDefault(target.getUniqueId(), 20);

        playerHearts.put(player.getUniqueId(), playerHealth - (heartsToGive * 2));
        playerHearts.put(target.getUniqueId(), targetHealth + (heartsToGive * 2));

        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(playerHealth - (heartsToGive * 2));
        target.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(targetHealth + (heartsToGive * 2));

        player.sendMessage("Deste " + heartsToGive + " corações para " + target.getName());
        target.sendMessage("Recebeste " + heartsToGive + " corações de " + player.getName());

        return true;
    }

    private boolean handleSmpAdminCommand(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage("Uso: /smpadmin <subcomando>");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        Player target;
        int hearts;

        switch (subCommand) {
            case "sethearts":
                if (args.length != 3) {
                    player.sendMessage("Exemplo: /smpadmin sethearts <player> <amount>");
                    return true;
                }
                target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage("Player não está online ou não foi encontrado.");
                    return true;
                }
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

            case "endsession":
                if (!sessionActive) {
                    player.sendMessage("Nenhuma sessão está ativa.");
                    return true;
                }
                endSession();
                player.sendMessage("Sessão terminada.");
                return true;

            case "startsession":
                if (sessionActive) {
                    player.sendMessage("Já existe uma sessão ativa.");
                    return true;
                }
                startSession();
                player.sendMessage("Sessão iniciada.");
                return true;

            case "checkhearts":
                if (args.length == 2) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null || !target.isOnline()) {
                        player.sendMessage("Player não está online ou não foi encontrado.");
                        return true;
                    }
                    player.sendMessage(target.getName() + " tem " + playerHearts.get(target.getUniqueId()) / 2 + " corações.");
                } else {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        player.sendMessage(p.getName() + " tem " + playerHearts.get(p.getUniqueId()) / 2 + " corações.");
                    }
                }
                return true;

            case "checkmalandro":
                Player currentMalandro = Bukkit.getPlayer(malandro);
                Player currentTarget = Bukkit.getPlayer(malandroTarget);
                if (malandro != null) {
                    player.sendMessage("O Malandro atual é " + (currentMalandro != null ? currentMalandro.getName() : "N/A") +
                            " com o alvo " + (currentTarget != null ? currentTarget.getName() : "N/A") + ".");
                } else {
                    player.sendMessage("Não há Malandro atualmente.");
                }
                if (previousMalandro != null) {
                    Player previousMalandroPlayer = Bukkit.getPlayer(previousMalandro);
                    Player previousTargetPlayer = Bukkit.getPlayer(previousMalandroTarget);
                    player.sendMessage("O Malandro anterior era " + (previousMalandroPlayer != null ? previousMalandroPlayer.getName() : "N/A") +
                            " com o alvo " + (previousTargetPlayer != null ? previousTargetPlayer.getName() : "N/A") + ".");
                } else {
                    player.sendMessage("Não há registros do Malandro anterior.");
                }
                return true;

            default:
                player.sendMessage("Subcomando desconhecido.");
                return true;
        }
    }

    private void setPlayerHearts(Player player, int hearts) {
        playerHearts.put(player.getUniqueId(), hearts);
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(hearts);
        player.setHealth(hearts);
    }

    private void setMalandro(Player player) {
        previousMalandro = malandro;
        previousMalandroTarget = malandroTarget;

        malandro = player.getUniqueId();
        do {
            malandroTarget = Bukkit.getOnlinePlayers().toArray(new Player[0])[random.nextInt(Bukkit.getOnlinePlayers().size())].getUniqueId();
        } while (malandro.equals(malandroTarget));

        player.sendMessage("Foste definido como Malandro, o teu objetivo é matar " + Bukkit.getPlayer(malandroTarget).getName());
    }

    private void startSession() {
        sessionActive = true;
        firstSession = false;

        // Allow players to join
        Bukkit.setWhitelist(false);

        // Initialize players' health
        Bukkit.getOnlinePlayers().forEach(this::initializePlayerHealth);

        // Schedule tasks
        scheduleMalandroSelection();
        scheduleSessionEnd();
    }

    private void endSession() {
        sessionActive = false;

        // Disallow new players from joining
        Bukkit.setWhitelist(true);

        // End session tasks
        endAllTasks();

        // Reduce players' max health
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
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
            if (players.length > 0) {
                do {
                    malandro = players[random.nextInt(players.length)].getUniqueId();
                    malandroTarget = players[random.nextInt(players.length)].getUniqueId();
                } while (malandro.equals(malandroTarget));

                Player malandroPlayer = Bukkit.getPlayer(malandro);
                Player targetPlayer = Bukkit.getPlayer(malandroTarget);
                if (malandroPlayer != null && targetPlayer != null) {
                    malandroPlayer.sendMessage("Foste escolhido como Malandro, o teu objetivo é matar " + targetPlayer.getName());
                }
            }
        }, 200L); // Give some time after session starts
    }

    private void scheduleSessionEnd() {
        // Schedule the session to end in 3 hours
        Bukkit.getScheduler().runTaskLater(this, this::endSession, 108000L); // 108000 ticks = 3 hours
    }

    private void endAllTasks() {
        Bukkit.getScheduler().cancelTasks(this);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("smpadmin")) {
            if (args.length == 1) {
                List<String> subcommands = Arrays.asList("sethearts", "setmalandro", "endsession", "startsession", "checkhearts", "checkmalandro");
                return subcommands;
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("sethearts") || args[0].equalsIgnoreCase("setmalandro") || args[0].equalsIgnoreCase("checkhearts"))) {
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
