package newamazingpvp.nappixelproxy.discord;

import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.concurrent.TimeUnit;

import static newamazingpvp.nappixelproxy.NapPixelProxy.proxy;
import static newamazingpvp.nappixelproxy.discord.DiscordUtil.channelId;
import static newamazingpvp.nappixelproxy.discord.DiscordUtil.jda;

public class Status extends ListenerAdapter {
    @Override
    public void onReady(ReadyEvent event) {
        super.onReady(event);

        proxy.getProxy().getScheduler().schedule(proxy, () -> {
            proxy.getProxy().getScheduler().runAsync(proxy, () -> {
                jda.getPresence().setActivity(Activity.playing("minecraft"));
                TextChannel channel = event.getJDA().getTextChannelById(channelId);

                if (channel != null) {
                    int num = proxy.getProxy().getPlayers().size();
                    channel.getManager().setTopic("Total Players online: " + num).queue();
                }
            });
        }, 1, 5, TimeUnit.MINUTES);

    }
}
