package newamazingpvp.nappixelproxy.discord;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.awt.*;

import static newamazingpvp.nappixelproxy.discord.DiscordUtil.*;

public class DiscordListeners implements Listener {

    @EventHandler
    public void onSwitch(ServerSwitchEvent event) {
        ProxiedPlayer player = event.getPlayer();
        if(event.getFrom() == null){
            String s = player.getName() + " joined the network at " + event.getPlayer().getServer().getInfo().getName() + " server";
            sendDiscordEmbedPlayer(s, Color.GREEN, channelId, event.getPlayer().getName());
        } else {
            String s = player.getName() + " switched from " + event.getFrom().getName() + " to " + player.getServer().getInfo().getName() + " server";
            sendDiscordEmbedPlayer(s, Color.CYAN, channelId, player.getName());
        }
    }

    @EventHandler
    public void messageSent(ChatEvent event) {
        if(!event.isCancelled()){
            ProxiedPlayer player = (ProxiedPlayer) event.getSender();
            sendWebhook(player, event.getMessage(), player.getServer().getInfo().getName());
        }
    }

    @EventHandler
    public void onLeave(PlayerDisconnectEvent event) {
        String s = event.getPlayer().getName() + " left the network from " + event.getPlayer().getServer();
        sendDiscordEmbedPlayer(s, Color.RED, channelId, event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPluginMessage(net.md_5.bungee.api.event.PluginMessageEvent event) {
        if (event.getTag().equals("BungeeCord")) {
            ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
            String channel = in.readUTF();

            if (channel.equals("Server")) {
                String jsonData = in.readUTF();

                JSONParser parser = new JSONParser();
                try {
                    JSONObject dataObject = (JSONObject) parser.parse(jsonData);

                    String message = (String) dataObject.get("String");
                    String category = (String) dataObject.get("category");
                    String playerName = (String) dataObject.get("player");

                    System.out.println("Received message: " + message);
                    System.out.println("Received intData: " + category);
                    System.out.println("Received doubleData: " + playerName);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
