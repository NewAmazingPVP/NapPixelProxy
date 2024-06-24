package newamazingpvp.nappixelproxy.velocity;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import me.scarsz.jdaappender.ChannelLoggingHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Plugin(id = "nappixelproxy", name = "NapPixelProxy", authors = "NewAmazingPVP")
public class NapPixelVelocity extends ListenerAdapter {

    private static NapPixelVelocity instance;
    private static final ZonedDateTime SEASON_START_TIME = ZonedDateTime.of(
            2024, 6, 23, 12, 0, 0, 0, ZoneId.of("America/New_York")
    );

    private Toml config;
    private final ProxyServer proxy;
    private final Path dataDirectory;
    private final Set<UUID> messageToggles = new HashSet<>();
    private Set<String> disabledServers;
    private Set<String> privateServers;
    private final ConcurrentHashMap<UUID, String> playerLastServer = new ConcurrentHashMap<>();
    private final Map<String, String> serverNames = new HashMap<>();
    public JDA jda;
    public String consoleChannelId = "1135323447522771114";
    public ScheduledTask task;
    public MessageChannel consoleChannel;
    private final Set<UUID> limboCooldown = ConcurrentHashMap.newKeySet();
    private final Duration limboCooldownDuration = Duration.ofSeconds(5);
    private final Map<UUID, ScheduledTask> actionBarTasks = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> ipPlayerMap = new ConcurrentHashMap<>();
    private final Set<String> whitelistedPlayers = new HashSet<>();
    private final ChannelIdentifier channel = MinecraftChannelIdentifier.create("nappixel", "lifesteal");

    @Inject
    public NapPixelVelocity(ProxyServer proxy, @DataDirectory Path dataDirectory) {
        instance = this;
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        config = loadConfig(dataDirectory);
        loadWhitelist();
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("kickall")
                        .aliases("ka")
                        .build(),
                (SimpleCommand) invocation -> kickAllPlayers()
        );
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("whitelist")
                        .aliases("wl")
                        .build(),
                new WhitelistCommand()
        );
    }

    private void kickAllPlayers() {
        String s = ";p";
        proxy.getServer("smp").ifPresent(serverConnection ->
                serverConnection.sendPluginMessage(channel, s.getBytes())
        );
    }

    public static NapPixelVelocity getInstance() {
        return instance;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        config = loadConfig(dataDirectory);
        initializeVelocityBot();
        loadIpPlayerMap();
        AutoRestart.scheduleRestart(proxy, this);
    }

    private Toml loadConfig(Path path) {
        File folder = path.toFile();
        File file = new File(folder, "config.toml");

        if (!file.exists()) {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (InputStream input = getClass().getResourceAsStream("/" + file.getName())) {
                if (input != null) {
                    Files.copy(input, file.toPath());
                } else {
                    file.createNewFile();
                }
            } catch (IOException exception) {
                exception.printStackTrace();
                return null;
            }
        }
        return new Toml().read(file);
    }

    public void initializeVelocityBot() {
        String token = config.getString("BotToken");
        EnumSet<GatewayIntent> allIntents = EnumSet.allOf(GatewayIntent.class);

        JDABuilder jdaBuilder = JDABuilder.createDefault(token);
        jdaBuilder.enableIntents(allIntents);
        jda = jdaBuilder.build();
        jda.addEventListener(this);
        task = proxy.getScheduler().buildTask(this, () -> {
            consoleChannel = jda.getTextChannelById(consoleChannelId);
            if (consoleChannel != null) {
                ChannelLoggingHandler handler1 = new ChannelLoggingHandler(() -> consoleChannel, config -> {
                    config.setColored(true);
                    config.setSplitCodeBlockForLinks(false);
                    config.setAllowLinkEmbeds(true);
                    config.addFilter(logItem -> {
                        String message = logItem.getMessage();
                        return !message.contains("unable to connect");
                    });
                    config.mapLoggerName("net.dv8tion.jda", "JDA");
                    config.mapLoggerName("net.minecraft.server.MinecraftServer", "Server");
                    config.mapLoggerNameFriendly("net.minecraft.server", s -> "Server/" + s);
                    config.mapLoggerNameFriendly("net.minecraft", s -> "Minecraft/" + s);
                    config.mapLoggerName("github.scarsz.discordsrv.dependencies.jda", s -> "DiscordSRV/JDA/" + s);
                }).attach();
                handler1.schedule();
                task.cancel();
            }
        }).delay(Duration.ofSeconds(1)).repeat(Duration.ofSeconds(1)).schedule();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String bungeeChannelId = "1135323447522771114";
        if (event.getMessage().getChannelId().equals(bungeeChannelId)) {
            String messageContent = event.getMessage().getContentRaw();
            if (messageContent.contains("shutdown") || messageContent.contains("end")) {
                kickAllPlayers();
            }
            proxy.getScheduler().buildTask(this, () -> {
                proxy.getCommandManager().executeImmediatelyAsync(proxy.getConsoleCommandSource(), messageContent);
            }).delay(1, TimeUnit.SECONDS).schedule();
        }
    }

    private boolean isServerAvailable(RegisteredServer server) {
        return proxy.getServer(server.getServerInfo().getName()).isPresent();
    }

    private void keepPlayerInLimbo(Player player, RegisteredServer server) {
        UUID playerId = player.getUniqueId();
        if (actionBarTasks.containsKey(playerId)) {
            return;
        }
        AtomicReference<ScheduledTask> taskReference = new AtomicReference<>();

        ScheduledTask task = proxy.getScheduler().buildTask(this, () -> {
            player.sendActionBar(Component.text("Server background restarting. Please stay connected...")
                    .color(NamedTextColor.YELLOW));
            if (player.getCurrentServer().isPresent() && player.getCurrentServer().get().getServer().equals(server)) {
                proxy.getScheduler().buildTask(this, () -> {
                    ScheduledTask scheduledTask = taskReference.get();
                    if (scheduledTask != null) {
                        scheduledTask.cancel();
                        actionBarTasks.remove(playerId);
                    }
                }).delay(Duration.ofSeconds(1)).schedule();
            }
        }).delay(Duration.ofSeconds(1)).repeat(Duration.ofSeconds(1)).schedule();

        taskReference.set(task);
        actionBarTasks.put(playerId, task);
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/New_York"));
        if (whitelistedPlayers.contains(player.getUsername())) {
            return;
        }
        if (now.isBefore(SEASON_START_TIME)) {
            Duration duration = Duration.between(now, SEASON_START_TIME);
            String timeRemaining = formatDuration(duration);
            player.disconnect(Component.text("New Lifesteal season is starting in " + timeRemaining).color(NamedTextColor.GREEN)
                    .append(Component.text(" Join discord https://discord.gg/PN8egFY3ap for more info!").color(NamedTextColor.AQUA)));
            return;
        }

        String playerIp = player.getRemoteAddress().getAddress().getHostAddress();
        ipPlayerMap.computeIfAbsent(playerIp, k -> new HashSet<>());

        if (ipPlayerMap.get(playerIp).size() >= 1) {
            player.disconnect(Component.text("Only one account per IP address is allowed.").color(NamedTextColor.RED));
        } else {
            ipPlayerMap.get(playerIp).add(player.getUniqueId());
            saveIpPlayerMap();
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        String playerIp = player.getRemoteAddress().getAddress().getHostAddress();
        Set<UUID> players = ipPlayerMap.getOrDefault(playerIp, new HashSet<>());
        players.remove(player.getUniqueId());
        if (players.isEmpty()) {
            ipPlayerMap.remove(playerIp);
        }
        saveIpPlayerMap();
    }

    private void loadIpPlayerMap() {
        File file = new File(dataDirectory.toFile(), "ipPlayerMap.toml");
        if (file.exists()) {
            Toml toml = new Toml().read(file);
            toml.toMap().forEach((ip, uuids) -> {
                Set<UUID> uuidSet = ((List<String>) uuids).stream().map(UUID::fromString).collect(Collectors.toSet());
                ipPlayerMap.put(ip, uuidSet);
            });
        }
    }

    private void saveIpPlayerMap() {
        File file = new File(dataDirectory.toFile(), "ipPlayerMap.toml");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("[ipPlayerMap]\n");
            for (Map.Entry<String, Set<UUID>> entry : ipPlayerMap.entrySet()) {
                writer.write(entry.getKey() + " = [" + entry.getValue().stream().map(UUID::toString).collect(Collectors.joining(", ")) + "]\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadWhitelist() {
        File file = new File(dataDirectory.toFile(), "whitelist.txt");
        if (file.exists()) {
            try {
                List<String> lines = Files.readAllLines(file.toPath());
                whitelistedPlayers.addAll(lines);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveWhitelist() {
        File file = new File(dataDirectory.toFile(), "whitelist.txt");
        try (FileWriter writer = new FileWriter(file)) {
            for (String username : whitelistedPlayers) {
                writer.write(username + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class WhitelistCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (args.length == 0) {
                source.sendMessage(Component.text("Usage: /whitelist <add|remove|list> <username>").color(NamedTextColor.RED));
                return;
            }

            String action = args[0];
            if (action.equalsIgnoreCase("list")) {
                source.sendMessage(Component.text("Whitelisted players: " + String.join(", ", whitelistedPlayers)).color(NamedTextColor.GREEN));
                return;
            }

            if (args.length < 2) {
                source.sendMessage(Component.text("Usage: /whitelist <add|remove> <username>").color(NamedTextColor.RED));
                return;
            }

            String username = args[1];
            if (action.equalsIgnoreCase("add")) {
                whitelistedPlayers.add(username);
                saveWhitelist();
                source.sendMessage(Component.text("Added " + username + " to the whitelist.").color(NamedTextColor.GREEN));
            } else if (action.equalsIgnoreCase("remove")) {
                whitelistedPlayers.remove(username);
                saveWhitelist();
                source.sendMessage(Component.text("Removed " + username + " from the whitelist.").color(NamedTextColor.GREEN));
            } else {
                source.sendMessage(Component.text("Unknown command. Usage: /whitelist <add|remove|list> <username>").color(NamedTextColor.RED));
            }
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            String[] args = invocation.arguments();
            if (args.length == 1) {
                return Arrays.asList("add", "remove", "list");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
                return new ArrayList<>(whitelistedPlayers);
            }
            return Collections.emptyList();
        }
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        return String.format("%d days, %d hours, %d minutes, %d seconds", days, hours, minutes, seconds);
    }
}
