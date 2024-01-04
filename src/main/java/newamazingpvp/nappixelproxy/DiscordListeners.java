package newamazingpvp.nappixelproxy;

import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.awt.*;

import static newamazingpvp.nappixelproxy.DiscordUtil.*;

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

}
