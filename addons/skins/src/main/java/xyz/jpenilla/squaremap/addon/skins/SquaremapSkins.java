package xyz.jpenilla.squaremap.addon.skins;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import net.skinsrestorer.api.PropertyUtils;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.event.SkinApplyEvent;
import net.skinsrestorer.api.model.MojangProfileResponse;
import net.skinsrestorer.api.model.MojangProfileTexture;
import net.skinsrestorer.api.model.MojangProfileTextureMeta;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

import javax.imageio.ImageIO;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.imgscalr.Scalr;
import org.jetbrains.annotations.Nullable;

import xyz.jpenilla.squaremap.api.SquaremapProvider;

public final class SquaremapSkins extends JavaPlugin {
    private static SquaremapSkins instance;
    private static File skinsDir;
    private static HashMap<String, String> skinHashes = new HashMap<>();

    public SquaremapSkins() {
        instance = this;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        skinsDir = new File(SquaremapProvider.get().webDir().toFile(), "skins");
        if (!skinsDir.exists() && !skinsDir.mkdirs()) {
            getLogger().severe("Could not create skins directory!");
            getLogger().severe("Check your file permissions and try again");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onPlayerJoin(PlayerJoinEvent event) {
                new FetchSkinURL(event.getPlayer()).runTaskLater(instance, 2);
            }
        }, this);

        int interval = getConfig().getInt("update-interval", 60);
        new UpdateTask().runTaskTimer(this, interval, interval);

        SkinsRestorerProvider.get().getEventBus().subscribe(instance, SkinApplyEvent.class, (event) -> {
            new FetchSkinURL(event.getPlayer(Player.class)).runTaskLater(this, 2);
        });
    }

    private static class UpdateTask extends BukkitRunnable {
        @Override
        public void run() {
            Bukkit.getOnlinePlayers().forEach(player -> new FetchSkinURL(player).runTask(instance));
        }
    }

    private static final class FetchSkinURL extends BukkitRunnable {
        private final Player player;

        private FetchSkinURL(Player player) {
            this.player = player;
        }

        @Override
        public void run() {
            String url = getTexture(player);
            if (url == null || url.isEmpty()) {
                return;
            }
            String name = player.getName();
            new SaveSkin(name, url).runTaskAsynchronously(instance);
        }
    }

    private static final class SaveSkin extends BukkitRunnable {
        private final String name;
        private final String url;

        private SaveSkin(String name, String url) {
            this.name = name;
            this.url = url;
        }

        @Override
        public void run() {
            instance.saveTexture(name, url);
        }
    }

    private static String getTexture(Player player) {
        if (!player.isOnline()) {
            return null;
        }
        PlayerProfile profile = player.getPlayerProfile();
        for (ProfileProperty property : profile.getProperties()) {
            if (!property.getName().equals("textures")) {
                continue;
            }
            try {
                MojangProfileTexture texture = PropertyUtils.getSkinProfileData(property.getValue())
                        .getTextures()
                        .getSKIN();
                String oldHash = skinHashes.get(player.getName());
                String newHash = texture.getTextureHash();
                if (newHash.equals(oldHash)) {
                    return null;
                } else {
                    skinHashes.put(player.getName(), newHash);
                }
                return texture.getUrl();
            } catch (Exception e) {
                instance.getSLF4JLogger().error("Invalid profile data (player='{}')", player.getName(), e);
            }
        }
        return null;
    }

    private void saveTexture(String name, String url) {
        BufferedImage skin;
        try {
            skin = ImageIO.read(new URI(url).toURL());
        } catch (IllegalArgumentException | MalformedURLException | URISyntaxException e) {
            instance.getSLF4JLogger()
                    .warn("Invalid skin url (player='{}', url='{}')", name, url, e);
            return;
        } catch (IOException e) {
            instance.getSLF4JLogger().warn("Could not download skin(player='{}', url='{}')", name, url, e);
            return;
        }
        try {
            BufferedImage head = skin.getSubimage(8, 8, 8, 8);
            BufferedImage mask = skin.getSubimage(40, 8, 8, 8);
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int rgb = mask.getRGB(x, y);
                    if ((rgb >>> 24) > 168) {
                        head.setRGB(x, y, rgb);
                    }
                }
            }
            head = Scalr.resize(head, Scalr.Method.SPEED, 16, 16);
            File file = new File(skinsDir, name + ".png");
            File fileLowerCase = new File(skinsDir, name.toLowerCase() + ".png");
            ImageIO.write(head, "png", file);
            ImageIO.write(head, "png", fileLowerCase);
        } catch (IOException e) {
            this.getSLF4JLogger().error("Could not save texture to {}",
                    new File(skinsDir, name + ".png").getAbsolutePath(), e);
        }
    }
}
