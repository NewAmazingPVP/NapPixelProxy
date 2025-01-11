package newamazingpvp.nappixelproxy;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import newamazingpvp.nappixelproxy.discord.DiscordListeners;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import static newamazingpvp.nappixelproxy.AutoRestart.scheduleRestart;
import static newamazingpvp.nappixelproxy.discord.DiscordUtil.intializeBot;
import static newamazingpvp.nappixelproxy.discord.DiscordUtil.webHookClient;


public class NapPixelProxy extends Plugin {

    public static Configuration config;
    public static NapPixelProxy proxy;

    @Override
    public void onEnable() {
        //getProxy().registerChannel("BungeeCord");
        proxy = this;
        saveDefaultConfig();
        loadConfiguration();
        intializeBot();
        webHookClient();
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new PluginCommand(this));
        ProxyServer.getInstance().getPluginManager().registerListener(this, new DiscordListeners());
        ProxyServer.getInstance().registerChannel("BungeeCord");
        scheduleRestart();
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

}
