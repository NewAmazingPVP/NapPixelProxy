package newamazingpvp.nappixelproxy;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.Collection;

import static newamazingpvp.nappixelproxy.NapPixelProxy.sendDiscordMessage;


public class PluginCommand extends Command {

    private final Plugin plugin;

    public PluginCommand(Plugin plugin) {
        super("getplugins");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if ( !(sender instanceof ProxiedPlayer)) {
            Collection<Plugin> plugins = plugin.getProxy().getPluginManager().getPlugins();
            sender.sendMessage("Loaded plugins:");
            for (Plugin loadedPlugin : plugins) {
                sender.sendMessage("- " + loadedPlugin.getDescription().getName() + " v" + loadedPlugin.getDescription().getVersion());
            }
            boolean isFloodgateLoaded = false;
            boolean isGeyserLoaded = false;

            for (Plugin loadedPlugin : plugins) {
                if (loadedPlugin.getDescription().getName().toLowerCase().contains("floodgate")) {
                    isFloodgateLoaded = true;
                } else if (loadedPlugin.getDescription().getName().toLowerCase().contains("geyser")) {
                    isGeyserLoaded = true;
                }
            }

            if (isFloodgateLoaded && isGeyserLoaded) {
                sendDiscordMessage("Geyser and floogate are LOADED", "1135323447522771114");
            }
        } else {
            sender.sendMessage("This command can only be executed from the console.");
        }
    }


}
