package newamazingpvp.nappixelproxy;

import me.scarsz.jdaappender.ChannelLoggingHandler;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import newamazingpvp.nappixelproxy.discord.ConsoleCommand;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static newamazingpvp.nappixelproxy.AutoRestart.scheduleRestart;


public class NapPixelProxy extends Plugin {

    public static Configuration config;
    public static JDA jda;
    public static TextChannel channel;
    private static Map<String, String> discordMessageIds = new HashMap<>();
    public static NapPixelProxy bg;
    private boolean intialized = false;

    @Override
    public void onEnable() {
        bg = this;
        saveDefaultConfig();
        loadConfiguration();
        String token = config.getString("Discord.BotToken");
        String channelId = config.getString("Discord.Channel");
        EnumSet<GatewayIntent> allIntents = EnumSet.allOf(GatewayIntent.class);

        JDABuilder jdaBuilder = JDABuilder.createDefault(token);
        jdaBuilder.enableIntents(allIntents);
        jda = jdaBuilder.build();
        getProxy().getScheduler().schedule(this, () -> {
            channel = jda.getTextChannelById("1170146550274600960");
            jda = jdaBuilder.addEventListeners(new ConsoleCommand(this)).build();
            intialized = true;
        }, 5000, -1, TimeUnit.MILLISECONDS);
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new PluginCommand(this));
        scheduleRestart();
        ChannelLoggingHandler handler1 = new ChannelLoggingHandler(() -> jda.getTextChannelById("1135323447522771114"), config -> {
            config.setColored(true);
            config.setSplitCodeBlockForLinks(false);
            config.setAllowLinkEmbeds(true);
            config.mapLoggerName("net.dv8tion.jda", "JDA");
            config.mapLoggerName("net.minecraft.server.MinecraftServer", "Server");
            config.mapLoggerNameFriendly("net.minecraft.server", s -> "Server/" + s);
            config.mapLoggerNameFriendly("net.minecraft", s -> "Minecraft/" + s);
            config.mapLoggerName("github.scarsz.discordsrv.dependencies.jda", s -> "DiscordSRV/JDA/" + s);
        }).attach().schedule();
        handler1.schedule();
    }

    @Override
    public void onDisable() {
    }

    private void saveDefaultConfig() {
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadConfiguration() {
        File file = new File(getDataFolder(), "config.yml");
        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void sendDiscordMessage(String msg, String channelID) {
        if (channelID.isEmpty()) {
            // channel.sendMessage(msg);
        } else {
            TextChannel tempChannel = jda.getTextChannelById(channelID);
            if (tempChannel != null) {

                if (discordMessageIds.containsKey(channelID)) {
                    String oldMessageId = discordMessageIds.get(channelID);

                    String oldMessageContent = tempChannel.retrieveMessageById(oldMessageId).complete().getContentRaw();

                    oldMessageContent = oldMessageContent.replaceAll("```", "");

                    if (oldMessageContent.length() >= 2000 || oldMessageContent.length()+msg.length() >= 2000) {
                        tempChannel.sendMessage("```\n" + msg + "\n```").queue(message -> {
                            discordMessageIds.put(channelID, message.getId());
                        });
                    } else {
                        String newMessage = "```\n" + oldMessageContent + "\n" + msg + "\n```";

                        tempChannel.editMessageById(oldMessageId, newMessage).queue();
                    }
                } else {
                    tempChannel.sendMessage("```\n" + msg + "\n```").queue(message -> {
                        discordMessageIds.put(channelID, message.getId());
                    });
                }
            }
        }
    }

}
