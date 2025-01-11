package newamazingpvp.nappixelproxy.discord;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

public class ConsoleCommand extends ListenerAdapter {

    private final Plugin plugin;

    public ConsoleCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {

        String bungeeChannelId = "1135323447522771114";
        if (event.getMessage().getChannelId().equals(bungeeChannelId)) {

            String messageContent = event.getMessage().getContentRaw();
            ProxyServer.getInstance().getPluginManager().dispatchCommand(ProxyServer.getInstance().getConsole(), messageContent);
        }
    }
}
