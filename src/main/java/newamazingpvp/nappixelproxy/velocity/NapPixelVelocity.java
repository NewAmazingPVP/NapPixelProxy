package newamazingpvp.nappixelproxy.velocity;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
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
import net.kyori.adventure.title.Title;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(id = "nappixelproxy", name = "NapPixelProxy", authors = "NewAmazingPVP")
public class NapPixelVelocity extends ListenerAdapter {

    private static NapPixelVelocity instance;

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

    @Inject
    public NapPixelVelocity(ProxyServer proxy, @DataDirectory Path dataDirectory) {
        instance = this;
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        config = loadConfig(dataDirectory);
    }

    public static NapPixelVelocity getInstance() {
        return instance;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        config = loadConfig(dataDirectory);
        initializeVelocityBot();
        AutoRestart.scheduleRestart(proxy, this);
        createLimboServer();
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
            proxy.getCommandManager().executeImmediatelyAsync(proxy.getConsoleCommandSource(), messageContent);
        }
    }

    private void createLimboServer() {
        ServerInfo limboServerInfo = new ServerInfo("limbo", new InetSocketAddress("0.0.0.0", 14144));
        proxy.registerServer(limboServerInfo);
    }

    @Subscribe
    public void onPlayerKicked(KickedFromServerEvent event) {
        Player player = event.getPlayer();
        RegisteredServer originalServer = event.getServer();

        if (!originalServer.getServerInfo().getName().contains("limbo")) {
            Optional<RegisteredServer> limboServer = proxy.getServer("limbo");
            if (limboServer.isPresent()) {
                event.setResult(KickedFromServerEvent.RedirectPlayer.create(limboServer.get(), Component.text("Server is restarting. Please wait...")));

                if (!limboCooldown.contains(player.getUniqueId())) {
                    keepPlayerInLimbo(player, limboServer.get());
                    limboCooldown.add(player.getUniqueId());
                    proxy.getScheduler().buildTask(this, () -> limboCooldown.remove(player.getUniqueId()))
                            .delay(limboCooldownDuration.toMillis(), TimeUnit.MILLISECONDS)
                            .schedule();
                }

                proxy.getScheduler().buildTask(this, () -> {
                    if (isServerAvailable(originalServer)) {
                        player.createConnectionRequest(originalServer).connect();
                    }
                }).delay(30, TimeUnit.SECONDS).schedule();
            }
        }
    }

    private boolean isServerAvailable(RegisteredServer server) {
        return proxy.getServer(server.getServerInfo().getName()).isPresent();
    }



    private void keepPlayerInLimbo(Player player, RegisteredServer limboServer) {
        proxy.getScheduler().buildTask(this, () -> {
            player.showTitle(Title.title(
                    Component.text("Server Background Restarting")
                            .color(NamedTextColor.RED),
                    Component.text("Please stay connected...")
                            .color(NamedTextColor.YELLOW)
            ));

            player.sendActionBar(Component.text("Server background restarting. Please stay connected...")
                    .color(NamedTextColor.YELLOW));

            player.sendMessage(Component.text("Server background restarting. Please stay connected...")
                    .color(NamedTextColor.YELLOW));

        }).repeat(10, TimeUnit.SECONDS).schedule();
    }
}
