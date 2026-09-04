package de.secoolio.bankranking;

import java.util.List;
import java.util.UUID;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Baut die Deko- und Knopf-Gegenstaende des Bank-Fensters.
 *
 * <p>Benutzt die Datenkomponenten von 26.2: {@code ITEM_NAME} (wird nicht kursiv dargestellt),
 * {@code LORE} und {@code TOOLTIP_DISPLAY}, mit dem Fuellsteine ganz ohne Beschriftung auskommen.
 */
public final class GuiItems {

    /** Spielerkopf mit gruenem Haken (Textur von textures.minecraft.net, fest hinterlegt). */
    private static final String CHECK_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDMx"
                    + "MmNhNDYzMmRlZjVmZmFmMmViMGQ5ZDdjYzdiNTVhNTBjNGUzOTIwZDkwMzcyYWFiMTQwNzgxZjVkZmJjNCJ9fX0=";
    /** Fester Zufallswert, damit alle Haken-Koepfe dasselbe Profil teilen. */
    private static final UUID CHECK_PROFILE_ID = UUID.fromString("7a5f1e2c-9d34-4b8a-91c6-2f0e8d4b6a13");

    private GuiItems() {
    }

    /** Grauer Fuellstein ohne Beschriftung fuer den Rahmen. */
    public static ItemStack filler() {
        ItemStack stack = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return stack;
    }

    /** Beschrifteter Gegenstand fuer Knoepfe und Anzeigen. */
    public static ItemStack labelled(Material material, String name, List<String> lore) {
        ItemStack stack = ItemStack.of(material);
        apply(stack, name, lore);
        return stack;
    }

    /**
     * Der Bestaetigen-Knopf: ein Spielerkopf mit gruenem Haken. Laesst sich das Profil nicht setzen,
     * wird auf einen gruenen Farbstoff zurueckgefallen, damit der Knopf nie fehlt.
     */
    public static ItemStack checkButton(String name, List<String> lore, boolean useHead) {
        if (useHead) {
            try {
                ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
                head.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile()
                        .uuid(CHECK_PROFILE_ID)
                        .addProperty(new ProfileProperty("textures", CHECK_TEXTURE))
                        .build());
                apply(head, name, lore);
                return head;
            } catch (RuntimeException ex) {
                // Fällt unten auf den Farbstoff zurück.
            }
        }
        ItemStack fallback = ItemStack.of(Material.LIME_DYE);
        apply(fallback, name, lore);
        return fallback;
    }

    private static void apply(ItemStack stack, String name, List<String> lore) {
        // ITEM_NAME statt CUSTOM_NAME: wird vom Client nicht kursiv gesetzt.
        stack.setData(DataComponentTypes.ITEM_NAME, Messages.mm(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> lines = lore.stream().map(Messages::mm).map(Component.class::cast).toList();
            stack.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
        }
    }
}
