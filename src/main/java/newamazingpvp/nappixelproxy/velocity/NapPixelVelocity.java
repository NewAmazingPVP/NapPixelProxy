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
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
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

import java.io.*;
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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;

import java.lang.reflect.Type;

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
    private final ChannelIdentifier channel = MinecraftChannelIdentifier.create("nappixel", "lifesteal");

    private final Path ipPlayerMappingFile;
    private final Logger logger;
    private Set<String> whitelist;
    private final Map<String, UUID> ipToPlayerMap = new ConcurrentHashMap<>();

    @Inject
    public NapPixelVelocity(ProxyServer proxy, @DataDirectory Path dataDirectory, Logger logger) {
        instance = this;
        this.proxy = proxy;
        this.logger = logger;
        this.whitelist = new HashSet<>();
        this.dataDirectory = dataDirectory;
        ipPlayerMappingFile = dataDirectory.resolve("ip_player_mapping.json");
        config = loadConfig(dataDirectory);
        loadIpPlayerMappings();
        loadWhitelist();
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
        String playerIp = player.getRemoteAddress().getAddress().getHostAddress();
        loadWhitelist();
        if (whitelist.contains(player.getUsername().toLowerCase())) {
            return;
        }

        UUID existingPlayer = ipToPlayerMap.get(playerIp);
        if (existingPlayer != null && !existingPlayer.equals(player.getUniqueId())) {
            player.disconnect(Component.text("Only one account per IP address is allowed.").color(NamedTextColor.RED));
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
        } else {
            ipToPlayerMap.put(playerIp, player.getUniqueId());
            saveIpPlayerMappings();
        }
    }

    private void loadIpPlayerMappings() {
        if (Files.exists(ipPlayerMappingFile)) {
            try (BufferedReader reader = Files.newBufferedReader(ipPlayerMappingFile)) {
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, UUID>>() {}.getType();
                Map<String, UUID> loadedMap = gson.fromJson(reader, type);
                if (loadedMap != null) {
                    ipToPlayerMap.putAll(loadedMap);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveIpPlayerMappings() {
        try (BufferedWriter writer = Files.newBufferedWriter(ipPlayerMappingFile)) {
            Gson gson = new Gson();
            gson.toJson(ipToPlayerMap, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadWhitelist() {
        try {
            Path whitelistPath = new File( dataDirectory+"/whitelist.txt").toPath();
            Files.createDirectories(whitelistPath.getParent());
            if (!Files.exists(whitelistPath)) {
                Files.createFile(whitelistPath);
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(whitelistPath.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    whitelist.add(line.trim().toLowerCase());
                }
            }
        } catch (Exception e) {
            logger.error("Error loading whitelist.txt", e);
        }
    }

    private void saveWhitelist() {
        try {
            Path whitelistPath = new File(dataDirectory+"/whitelist.txt").toPath();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(whitelistPath.toFile()))) {
                for (String username : whitelist) {
                    writer.write(username);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            logger.error("Error saving whitelist.txt", e);
        }
    }

    private class WhitelistCommand implements SimpleCommand {
        @Override
        public boolean hasPermission(final Invocation invocation) {
            return invocation.source().hasPermission("lifesteal.admin");
        }
        @Override
        public void execute(Invocation invocation) {
            String[] args = invocation.arguments();
            if (args.length < 2) {
                invocation.source().sendMessage(Component.text("Usage: /whitelist <add|remove> <username>"));
                return;
            }

            String action = args[0].toLowerCase();
            String username = args[1].toLowerCase();

            if ("add".equals(action)) {
                whitelist.add(username);
                saveWhitelist();
                invocation.source().sendMessage(Component.text("Added " + username + " to the whitelist."));
            } else if ("remove".equals(action)) {
                whitelist.remove(username);
                saveWhitelist();
                invocation.source().sendMessage(Component.text("Removed " + username + " from the whitelist."));
            } else {
                invocation.source().sendMessage(Component.text("Usage: /whitelist <add|remove> <username>"));
            }
        }
    }
}
