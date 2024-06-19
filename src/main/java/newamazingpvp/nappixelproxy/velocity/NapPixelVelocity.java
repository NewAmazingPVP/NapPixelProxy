package newamazingpvp.nappixelproxy.velocity;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
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
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
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
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;

import java.awt.*;
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
import java.util.concurrent.atomic.AtomicReference;

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
    private final Map<UUID, ScheduledTask> actionBarTasks = new ConcurrentHashMap<>();
    private final ChannelIdentifier channel = MinecraftChannelIdentifier.create("nappixel", "lifesteal");

    @Inject
    public NapPixelVelocity(ProxyServer proxy, @DataDirectory Path dataDirectory) {
        instance = this;
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
        config = loadConfig(dataDirectory);
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("kickall")
                        .aliases("ka")
                        .build(),
                (SimpleCommand) invocation -> kickAllPlayers()
        );
    }
    private void kickAllPlayers() {
        /*for (Player player : proxy.getAllPlayers()) {
            player.disconnect(Component.text("The server is restarting. Please rejoin later.")
                    .color(NamedTextColor.RED));
        }*/
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
        //createLimboServer();
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
            if(messageContent.contains("shutdown") || messageContent.contains("end")){
                kickAllPlayers();
            }
            proxy.getScheduler().buildTask(this, () -> {
                proxy.getCommandManager().executeImmediatelyAsync(proxy.getConsoleCommandSource(), messageContent);
            }).delay(1, TimeUnit.SECONDS).schedule();

        }
    }


    /*@Subscribe
    public void onPlayerKicked(KickedFromServerEvent event) {
        Player player = event.getPlayer();
        if(event.getServerKickReason().isPresent()){
            if(event.getServerKickReason().get().toString().contains("ban") || event.getServerKickReason().get().toString().contains("hack")){
                return;
            }
        }
        //Optional<RegisteredServer> limboServer = proxy.getServer("limbo");
        //if (limboServer.isPresent()) {
        event.setResult(KickedFromServerEvent.RedirectPlayer.create(event.getServer(), Component.text("Server has successfully restarted. Join https://discord.gg/ckmNKnMMaX for help!").color(NamedTextColor.GREEN)));
        keepPlayerInLimbo(player, event.getServer());
        //}
    }*/




    private boolean isServerAvailable(RegisteredServer server) {
        return proxy.getServer(server.getServerInfo().getName()).isPresent();
    }


    private void keepPlayerInLimbo(Player player, RegisteredServer server) {
        UUID playerId = player.getUniqueId();
            /*player.showTitle(Title.title(
                    Component.text("Server Background Restarting")
                            .color(NamedTextColor.RED),
                    Component.text("Please stay connected...")
                            .color(NamedTextColor.YELLOW)
            ));*/
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

           /* player.sendMessage(Component.text("Server background restarting. Please stay connected...")
                    .color(NamedTextColor.YELLOW));
*/

    }
}
