package newamazingpvp.nappixelproxy;

import net.md_5.bungee.api.ProxyServer;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static newamazingpvp.nappixelproxy.NapPixelProxy.proxy;


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

    public static void scheduleRestart() {
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
            scheduleWarning(warningDelay, warningMessages[i]);
        }

        ProxyServer.getInstance().getScheduler().schedule(proxy, () -> restartServer(), initialDelay, TimeUnit.MILLISECONDS);
    }

    private static void scheduleWarning(long delay, String message) {
        ProxyServer.getInstance().getScheduler().schedule(proxy, () -> broadcastWarning(message), delay, TimeUnit.MILLISECONDS);
    }

    private static void broadcastWarning(String message) {
        ProxyServer.getInstance().broadcast(net.md_5.bungee.api.ChatColor.RED + message);
    }

    private static void restartServer() {
        ProxyServer.getInstance().broadcast(net.md_5.bungee.api.ChatColor.AQUA + "Restarting the server...");
        ProxyServer.getInstance().getPluginManager().dispatchCommand(ProxyServer.getInstance().getConsole(), "end"); // Use the appropriate command to restart BungeeCord
    }
}
