package newamazingpvp.nappixelproxy.discord;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.md_5.bungee.api.ChatColor;

import static newamazingpvp.nappixelproxy.NapPixelProxy.proxy;
import static newamazingpvp.nappixelproxy.discord.DiscordUtil.channelId;

public class MessageEvent extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.getChannel().getId().equals(channelId) || event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }


        String message = event.getMessage().getContentRaw();

        Member member = event.getMember();
        String username = member != null ? member.getEffectiveName() : event.getAuthor().getName();

        String highestRole = member != null ? member.getRoles().get(0).getName() : "No Role";
        ChatColor rolecolor;
        if (member.getRoles().get(0).getColor() != null) {
            rolecolor = ChatColor.of("#" + Integer.toHexString(member.getRoles().get(0).getColor().getRGB()).substring(2));
        } else {
            rolecolor = ChatColor.WHITE;
        }

        proxy.getProxy().broadcast(ChatColor.AQUA + "[Discord | " + rolecolor + highestRole + "] " + ChatColor.RESET + username + ": " + message);
    }


}
