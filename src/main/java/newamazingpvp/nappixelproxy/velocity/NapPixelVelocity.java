package newamazingpvp.nappixelproxy.velocity;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
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
    public static final MinecraftChannelIdentifier IDENTIFIER = MinecraftChannelIdentifier.from("nappixel:lifesteal");

    private final Path ipPlayerMappingFile;
    private final Logger logger;
    private Set<String> whitelist;
    private Set<String> blacklist;
    private static final String BASE_URL = "https://mcprofile.io/api/v1/";
    private final Map<String, UUID> ipToPlayerMap = new ConcurrentHashMap<>();

    @Inject
    public NapPixelVelocity(ProxyServer proxy, @DataDirectory Path dataDirectory, Logger logger) {
        instance = this;
        this.proxy = proxy;
        this.logger = logger;
        this.whitelist = new HashSet<>();
        this.blacklist = new HashSet<>();
        this.dataDirectory = dataDirectory;
        ipPlayerMappingFile = dataDirectory.resolve("ip_player_mapping.json");
        config = loadConfig(dataDirectory);
        loadIpPlayerMappings();
        loadWhitelist();
        loadBlacklist();
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("whitelist")
                        .aliases("wl")
                        .build(),
                new WhitelistCommand()
        );
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("blacklist")
                        .aliases("bl")
                        .build(),
                new BlacklistCommand()
        );
    }

    private void kickAllPlayers() throws IOException {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("forceRestart");


        ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
        DataOutputStream msgout = new DataOutputStream(msgbytes);
        msgout.writeUTF("forceRestartLOL");
        msgout.writeShort(42);

        out.writeShort(msgbytes.toByteArray().length);
        out.write(msgbytes.toByteArray());

        Optional<RegisteredServer> optionalServer = proxy.getServer("smp");
        if( proxy.getServer("smp").isPresent()){
            RegisteredServer server = optionalServer.get();
            sendPluginMessageToBackend(server, IDENTIFIER, out.toByteArray());
        }


        /*proxy.getServer("smp").ifPresent(serverConnection ->
                serverConnection.sendPluginMessage(IDENTIFIER, out.toByteArray())
        );*/
    }

    public boolean sendPluginMessageToBackend(RegisteredServer server, ChannelIdentifier identifier, byte[] data) {
        // On success, returns true
        return server.sendPluginMessage(identifier, data);
    }

    public static NapPixelVelocity getInstance() {
        return instance;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        config = loadConfig(dataDirectory);
        initializeVelocityBot();
        AutoRestart.scheduleRestart(proxy, this);
        proxy.getChannelRegistrar().register(IDENTIFIER);
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
                        return message.contains("unable to connect");
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
            if(event.getAuthor().isBot()) return;
            if (messageContent.equalsIgnoreCase("shutdown") || messageContent.equalsIgnoreCase("end")) {
                try {
                    kickAllPlayers();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
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
    public void onServerPreConnect(ServerPreConnectEvent event) throws IOException {
        Player player = event.getPlayer();
        String playerIp = player.getRemoteAddress().getAddress().getHostAddress();
        loadWhitelist();
        loadBlacklist();
        if (whitelist.contains(player.getUsername().toLowerCase())) {
            return;
        }
        if (blacklist.contains(player.getUsername().toLowerCase())) {
            player.disconnect(Component.text("You are blacklisted from this server.").color(NamedTextColor.DARK_RED));
            return;
        }
        if (isVpn(playerIp)){
            //proxy btw
            //player.disconnect(Component.text("VPN not allowed!").color(NamedTextColor.DARK_RED));
            sendDiscordMessage(player.getUsername() + " might be using VPN to bypass ban or for alts", "1136353329488875531");
            proxy.sendMessage(Component.text(player.getUsername() + " might be using VPN to bypass ban or for alts. Let admins know if you suspect this").color(NamedTextColor.DARK_RED));
            proxy.getScheduler().buildTask(this, () -> {
                player.sendMessage( Component.text("You are using a VPN, and admins see that and might potentially get you banned if found alting. If you have a valid reason why, make a appeal in #appeals channel on discord to get verified Proxy").color(NamedTextColor.RED));
            }).delay(Duration.ofSeconds(2)).schedule();
        }
        if (isProxy(playerIp)){
            //proxy btw
            //player.disconnect(Component.text("VPN not allowed!").color(NamedTextColor.DARK_RED));
            sendDiscordMessage(player.getUsername() + " might be using Proxy to bypass ban or for alts", "1136353329488875531");
            proxy.sendMessage(Component.text(player.getUsername() + " might be using Proxy to bypass ban or for alts. Let admins know if you suspect this").color(NamedTextColor.DARK_RED));
            proxy.getScheduler().buildTask(this, () -> {
                player.sendMessage( Component.text("You are using a proxy, and admins see that and might potentially get you banned if found alting. If you have a valid reason why, make a appeal in #appeals channel on discord to get verified VPN").color(NamedTextColor.RED));
            }).delay(Duration.ofSeconds(2)).schedule();
        }
        loadIpPlayerMappings();
        UUID existingPlayer = ipToPlayerMap.get(playerIp);
        if (existingPlayer != null && !existingPlayer.equals(player.getUniqueId())) {
            //player.disconnect(Component.text("Only one account per IP address is allowed. If you have siblings, please make an appeal in the #appeals channel in Discord (https://discord.gg/PN8egFY3ap with IGN and a reason to whitelist.").color(NamedTextColor.RED));
            //event.setResult(ServerPreConnectEvent.ServerResult.denied());
            sendDiscordMessage(player.getUsername() + " might possibly be using an alt since they have duplicate same IP accounts with " + getUsernameFromUUID(existingPlayer), "1136353329488875531");
            proxy.sendMessage(Component.text("Player " + player.getUsername() + " might possibly be using an alt since they have duplicate same IP accounts with " + getUsernameFromUUID(existingPlayer) + ". Let admins know if you suspect this").color(NamedTextColor.DARK_RED));
            proxy.getScheduler().buildTask(this, () -> {
                player.sendMessage( Component.text("You are using an alt, and admins see that and might potentially get you banned. If you have a valid reason why, make a appeal in #appeals channel on discord to get verified alt").color(NamedTextColor.RED));
            }).delay(Duration.ofSeconds(2)).schedule();
        } else {
            ipToPlayerMap.put(playerIp, player.getUniqueId());
            saveIpPlayerMappings();
        }
    }

    public void sendDiscordMessage(String msg, String channelID) {
        if (jda == null) return;
        if (channelID.isEmpty()) {
            //channel.sendMessage(msg);
        } else {
            TextChannel tempChannel = jda.getTextChannelById(channelID);
            if (tempChannel != null) {
                tempChannel.sendMessage(msg).queue();
            }
        }
    }
    private void loadIpPlayerMappings() {
        if (Files.exists(ipPlayerMappingFile)) {
            try (BufferedReader reader = Files.newBufferedReader(ipPlayerMappingFile)) {
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, UUID>>() {}.getType();
                Map<String, UUID> loadedMap = gson.fromJson(reader, type);
                if (loadedMap != null) {
                    ipToPlayerMap.clear();
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
            Path whitelistPath = new File(dataDirectory + "/whitelist.txt").toPath();
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

    private void loadBlacklist() {
        try {
            Path blacklistPath = new File(dataDirectory + "/blacklist.txt").toPath();
            Files.createDirectories(blacklistPath.getParent());
            if (!Files.exists(blacklistPath)) {
                Files.createFile(blacklistPath);
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(blacklistPath.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    blacklist.add(line.trim().toLowerCase());
                }
            }
        } catch (Exception e) {
            logger.error("Error loading blacklist.txt", e);
        }
    }

    private void saveWhitelist() {
        try {
            Path whitelistPath = new File(dataDirectory + "/whitelist.txt").toPath();
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

    private void saveBlacklist() {
        try {
            Path blacklistPath = new File(dataDirectory + "/blacklist.txt").toPath();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(blacklistPath.toFile()))) {
                for (String username : blacklist) {
                    writer.write(username);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            logger.error("Error saving blacklist.txt", e);
        }
    }

    private boolean isVpn(String ip) throws IOException {
        String url = "https://proxycheck.io/v2/{IP}?key=PROXYCHECK_KEY&risk=1&vpn=1".replace("{IP}", ip);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(new URL(url).openStream()))) {
            String response = in.lines().collect(Collectors.joining()).toLowerCase();
            //response.contains("yes") ||
            if (response.contains("vpn")) {
                return true;
            }
        }
        return false;
    }

    public boolean isBedrockUUID(UUID uuid) {
        return uuid.toString().startsWith("00000000-0000-0000");
    }

    public String getUsernameFromUUID(UUID uuid) {
        try {
            String urlString;

            if (isBedrockUUID(uuid)) {
                urlString = BASE_URL + "bedrock/fuid/" + uuid;
            } else {
                urlString = BASE_URL + "java/uuid/" + uuid;
            }

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setRequestProperty("x-api-key", config.getString("APIKey"));

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                JsonObject jsonResponse = JsonParser.parseReader(reader).getAsJsonObject();

                if (isBedrockUUID(uuid)) {
                    return jsonResponse.has("gamertag") ? jsonResponse.get("gamertag").getAsString() : null;
                } else {
                    return jsonResponse.has("username") ? jsonResponse.get("username").getAsString() : null;
                }
            } else {
            }
        } catch (Exception e) {
        }
        return uuid.toString();
    }

    private boolean isProxy(String ip) throws IOException {
        String url = "https://proxycheck.io/v2/{IP}?key=PROXYCHECK_KEY&risk=1&vpn=1".replace("{IP}", ip);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(new URL(url).openStream()))) {
            String response = in.lines().collect(Collectors.joining()).toLowerCase();
            //response.contains("vpn") ||
            if (response.contains("yes")) {
                return true;
            }
        }
        return false;
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

    private class BlacklistCommand implements SimpleCommand {
        @Override
        public boolean hasPermission(final Invocation invocation) {
            return invocation.source().hasPermission("lifesteal.admin");
        }
        @Override
        public void execute(Invocation invocation) {
            String[] args = invocation.arguments();
            if (args.length < 2) {
                invocation.source().sendMessage(Component.text("Usage: /blacklist <add|remove> <username>"));
                return;
            }

            String action = args[0].toLowerCase();
            String username = args[1].toLowerCase();

            if ("add".equals(action)) {
                blacklist.add(username);
                saveBlacklist();
                invocation.source().sendMessage(Component.text("Added " + username + " to the blacklist."));
            } else if ("remove".equals(action)) {
                blacklist.remove(username);
                saveBlacklist();
                invocation.source().sendMessage(Component.text("Removed " + username + " from the blacklist."));
            } else {
                invocation.source().sendMessage(Component.text("Usage: /blacklist <add|remove> <username>"));
            }
        }
    }
}
