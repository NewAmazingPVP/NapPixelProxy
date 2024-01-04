package newamazingpvp.nappixelproxy.discord;

import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.concurrent.TimeUnit;

import static newamazingpvp.nappixelproxy.NapPixelProxy.bg;
import static newamazingpvp.nappixelproxy.NapPixelProxy.jda;
import static newamazingpvp.nappixelproxy.discord.DiscordUtil.channelId;

public class Status extends ListenerAdapter {
    @Override
    public void onReady(ReadyEvent event) {
        super.onReady(event);

        bg.getProxy().getScheduler().schedule(bg, () -> {
            bg.getProxy().getScheduler().runAsync(bg, () -> {
                jda.getPresence().setActivity(Activity.playing("minecraft"));
                TextChannel channel = event.getJDA().getTextChannelById(channelId);

                if (channel != null) {
                    int num = bg.getProxy().getPlayers().size();
                    channel.getManager().setTopic("Total Players online: " + num).queue();
                }
            });
        }, 0, 5, TimeUnit.MINUTES);

    }
}
