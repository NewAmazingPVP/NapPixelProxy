package newamazingpvp.nappixelproxy;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.awt.*;
import java.util.EnumSet;

import static newamazingpvp.nappixelproxy.NapPixelProxy.config;
import static newamazingpvp.nappixelproxy.NapPixelProxy.jda;

public class DiscordUtil {
    public static TextChannel channel;
    public static WebhookClient client;
    public static String channelId;

    public static void sendDiscordMessage(String msg, String channelID) {
        if(jda == null)return;
        if (channelID.isEmpty()) {
            channel.sendMessage(msg);
        } else {
            TextChannel tempChannel = jda.getTextChannelById(channelID);
            if (tempChannel != null) {
                tempChannel.sendMessage(msg).queue();
            }
        }
    }

    public static void sendDiscordEmbedTitle(String msg, Color c, String channelID) {
        if(jda == null)return;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(msg);
        eb.setColor(c);
        if (channelID.isEmpty()) {
            channel.sendMessageEmbeds(eb.build()).queue();
        } else {
            TextChannel tempChannel = jda.getTextChannelById(channelID);
            if (tempChannel != null) {
                tempChannel.sendMessageEmbeds(eb.build()).queue();
            }
        }
    }

    public static void sendDiscordEmbedStats(String msg, Color c, String channelID, String name) {
        if(jda == null)return;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(msg);
        eb.setColor(c);
        eb.setThumbnail("https://minotar.net/armor/body/" + name + "/100.png");
        if (channelID.isEmpty()) {
            channel.sendMessageEmbeds(eb.build()).queue();
        } else {
            TextChannel tempChannel = jda.getTextChannelById(channelID);
            if (tempChannel != null) {
                tempChannel.sendMessageEmbeds(eb.build()).queue();
            }
        }
    }

    public static void sendDiscordEmbedPlayer(String msg, Color c, String channelID, String p) {
        if(jda == null)return;
        p = "https://minotar.net/helm/" + p;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(msg, "https://shop.nappixel.tk/", p);
        eb.setColor(c);
        if (channelID.isEmpty()) {
            channel.sendMessageEmbeds(eb.build()).queue();
        } else {
            TextChannel tempChannel = jda.getTextChannelById(channelID);
            if (tempChannel != null) {
                tempChannel.sendMessageEmbeds(eb.build()).queue();
            }
        }
    }
    public static void webHookClient(){
        WebhookClientBuilder builder = new WebhookClientBuilder(config.getString("Discord.Webhook"));
        builder.setThreadFactory((job) -> {
            Thread thread = new Thread(job);
            thread.setName("E");
            thread.setDaemon(true);
            return thread;
        });
        builder.setWait(true);
        client = builder.build();
    }

    public static void sendWebhook(ProxiedPlayer p, String msg, String server){
        String avatar = "https://minotar.net/helm/" + p.getName();
        WebhookMessageBuilder builder = new WebhookMessageBuilder();
        builder.setUsername("[" + server + "] " +p.getName());
        builder.setAvatarUrl(avatar);
        builder.setContent(msg);
        client.send(builder.build());
    }


}
