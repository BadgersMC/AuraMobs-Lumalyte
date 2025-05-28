package dev.aurelium.auramobs.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Subcommand;
import dev.aurelium.auramobs.AuraMobs;
import dev.aurelium.auramobs.util.ColorUtils;
import org.bukkit.command.CommandSender;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import java.util.Locale;

@CommandAlias("auramobs")
public class AuraMobsCommand extends BaseCommand {

    private final AuraMobs plugin;

    public AuraMobsCommand(AuraMobs plugin) {
        this.plugin = plugin;
    }

    @Subcommand("reload")
    @CommandPermission("auramobs.reload")
    public void onReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getConfigManager().loadConfig();
        plugin.getPolyglot().getMessageManager().loadMessages();
        plugin.setLanguage(new Locale(plugin.optionString("language")));
        plugin.getScaleManager().loadConfiguration();
        sender.sendMessage(ColorUtils.colorMessage(plugin.getMsg("commands.reload")));
    }

    @Subcommand("applybloodmoonpack")
    public void onApplyBloodMoonPack(org.bukkit.entity.Player player) {
        String resourcePackUrl = plugin.getBloodMoonManager().getResourcePackUrl();
        if (resourcePackUrl != null && !resourcePackUrl.isEmpty()) {
            try {
                // Generate a consistent UUID for the resource pack (you might want a more robust solution)
                UUID packUuid = UUID.nameUUIDFromBytes(resourcePackUrl.getBytes());

                // Create ResourcePackRequest using Adventure API builder
                ResourcePackRequest packRequest = ResourcePackRequest.resourcePackRequest()
                        .packs(ResourcePackInfo.resourcePackInfo()
                                .id(packUuid)
                                .uri(new URI(resourcePackUrl))
                                .build())
                        .build();

                // Send the resource pack request using Adventure API
                player.sendResourcePacks(packRequest);

                player.sendMessage(dev.aurelium.auramobs.util.ColorUtils.colorMessage("&aApplying Blood Moon resource pack...")); // You might want to add this to your messages.yml
            } catch (URISyntaxException e) {
                plugin.getLogger().severe("Invalid resource pack URL: " + resourcePackUrl);
                player.sendMessage(dev.aurelium.auramobs.util.ColorUtils.colorMessage("&cFailed to apply resource pack due to an invalid URL."));
            }
        } else {
            player.sendMessage(dev.aurelium.auramobs.util.ColorUtils.colorMessage("&cBlood Moon resource pack URL is not configured.")); // You might want to add this to your messages.yml
        }
    }
}
