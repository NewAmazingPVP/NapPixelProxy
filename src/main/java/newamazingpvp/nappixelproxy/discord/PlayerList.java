package newamazingpvp.nappixelproxy.discord;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.ArrayList;
import java.util.List;

import static newamazingpvp.nappixelproxy.NapPixelProxy.bg;

public class PlayerList extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        String messageContent = event.getMessage().getContentRaw();
        if (messageContent.equalsIgnoreCase("playerlist")) {
            List<String> playerNames = new ArrayList<>();
            int size = 0;

            for (ProxiedPlayer p : bg.getProxy().getPlayers()) {
                playerNames.add(p.getName());
                size++;
            }

            if (size > 0) {
                String playerList = String.join(", ", playerNames);
                String message = String.format("**Online players: %d**\n```\n%s\n```", size, playerList);
                event.getChannel().sendMessage(message).queue();
            } else {
                event.getChannel().sendMessage("No players online.").queue();
            }
        }
    }
}
