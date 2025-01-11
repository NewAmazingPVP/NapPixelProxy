package newamazingpvp.nappixelproxy.velocity;

import com.velocitypowered.api.proxy.ProxyServer;

import java.time.Duration;
import java.util.Calendar;
import java.util.TimeZone;

public class AutoRestart {
    private static final long[] warningTimes = {10, 7, 5, 3, 2, 1};
    private static final String[] warningMessages = {
            "Proxy will restart in 10 minutes!",
            "Proxy will restart in 7 minutes!",
            "Proxy will restart in 5 minutes!",
            "Proxy will restart in 3 minutes!",
            "Proxy will restart in 2 minutes!",
            "Proxy will restart in 1 minute!",
    };

    public static void scheduleRestart(ProxyServer proxy, NapPixelVelocity plugin) {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
        Calendar restartTime = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
        restartTime.set(Calendar.HOUR_OF_DAY, 3);
        restartTime.set(Calendar.MINUTE, 0);
        restartTime.set(Calendar.SECOND, 30);

        if (now.after(restartTime)) {
            restartTime.add(Calendar.DAY_OF_MONTH, 1);
        }

        long initialDelay = restartTime.getTimeInMillis() - now.getTimeInMillis();

        for (int i = 0; i < warningTimes.length; i++) {
            long warningDelay = initialDelay - (warningTimes[i] * 60 * 1000);
            scheduleWarning(proxy, plugin, warningDelay, warningMessages[i]);
        }

        proxy.getScheduler().buildTask(plugin, () -> restartServer(proxy))
                .delay(Duration.ofMillis(initialDelay))
                .schedule();
    }

    private static void scheduleWarning(ProxyServer proxy, NapPixelVelocity plugin, long delay, String message) {
        proxy.getScheduler().buildTask(plugin, () -> broadcastWarning(proxy, message))
                .delay(Duration.ofMillis(delay))
                .schedule();
    }

    private static void broadcastWarning(ProxyServer proxy, String message) {
        proxy.getAllPlayers().forEach(player ->
                player.sendMessage(net.kyori.adventure.text.Component.text(message).color(net.kyori.adventure.text.format.TextColor.color(0xFF0000)))
        );
    }

    private static void restartServer(ProxyServer proxy) {
        proxy.getAllPlayers().forEach(player ->
                player.sendMessage(net.kyori.adventure.text.Component.text("Restarting the server...").color(net.kyori.adventure.text.format.TextColor.color(0x00FFFF)))
        );
        proxy.shutdown();
    }
}
