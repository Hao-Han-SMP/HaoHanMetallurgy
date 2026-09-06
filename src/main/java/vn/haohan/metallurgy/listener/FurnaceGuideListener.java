package vn.haohan.metallurgy.listener;

import vn.haohan.displayui.api.DisplayUiService;
import vn.haohan.displayui.api.UiDocument;
import vn.haohan.displayui.api.UiHandle;
import vn.haohan.displayui.api.UiOptions;
import vn.haohan.displayui.api.interaction.UiButton;
import vn.haohan.displayui.api.layout.UiAnchor;
import vn.haohan.displayui.api.layout.UiCameraTransform;
import vn.haohan.displayui.api.layout.UiRect;
import vn.haohan.displayui.api.node.AlignedTextNode;
import vn.haohan.displayui.api.node.BlockNode;
import vn.haohan.displayui.api.node.TextNode;
import vn.haohan.displayui.api.node.UiIconNode;
import vn.haohan.displayui.api.text.UiText;
import vn.haohan.displayui.api.text.UiTextAlignment;
import vn.haohan.displayui.api.text.UiVerticalAlignment;
import vn.haohan.metallurgy.HaoHanMetallurgy;
import vn.haohan.metallurgy.guide.FuelCombination;
import vn.haohan.metallurgy.guide.GuideIcon;
import vn.haohan.metallurgy.guide.MetallurgyCategory;
import vn.haohan.metallurgy.guide.MetallurgyGuideCatalog;
import vn.haohan.metallurgy.guide.OutputChance;
import vn.haohan.metallurgy.guide.RecipeNote;
import vn.haohan.metallurgy.item.CustomItem;
import vn.haohan.metallurgy.machine.Machine;
import vn.haohan.metallurgy.machine.forge.AncientForge;
import vn.haohan.metallurgy.recipe.MetallurgyRecipe;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Data-driven, category-based Ancient Forge guide. */
public final class FurnaceGuideListener implements Listener {
    private static final String OWNER_KEY = "haohanmetallurgy:forge_guide";
    private static final Key GUIDE_FONT = Key.key("haohan:furnace_guide");
    private static final String PANEL_GLYPH = "\uE200";
    private static final double DISPLAY_DISTANCE = 5.5;
    private static final float PIXELS_PER_BLOCK = 40.0f;
    private static final float TEXT_WIDTH_REFERENCE_PIXELS_PER_BLOCK = 80.0f;
    private static final float PANEL_SCALE = 0.75f;
    private static final float BACKGROUND_DEPTH = 0.0f;
    private static final float TEXT_DEPTH = 0.0010f;
    private static final float ICON_DEPTH = 0.0015f;
    private static final int NAV_VISIBLE = 7;

    private static final TextColor MAGENTA = UiText.hex("#d43bb5");
    private static final TextColor PURPLE = UiText.hex("#7b248f");
    private static final TextColor ORANGE = UiText.hex("#ff9d00");
    private static final TextColor SOFT_WHITE = UiText.hex("#ddd4df");
    private static final TextColor MUTED = UiText.hex("#9b8d9f");
    private static final TextColor GREEN = UiText.hex("#55ee55");
    private static final TextColor RED = UiText.hex("#ff553f");

    private static final UiRect PANEL = UiRect.centered(
            0.0f, 0.0f, 192.0f * PANEL_SCALE, 128.0f * PANEL_SCALE);
    private static final UiRect RECIPE_PANEL = new UiRect(-64.0f, -24.0f, 80.0f, 50.0f);
    private static final UiRect FLUX_PANEL = new UiRect(19.0f, -24.0f, 45.0f, 50.0f);
    private static final UiRect INFO_LEFT_PANEL = new UiRect(-64.0f, -24.0f, 80.0f, 50.0f);
    private static final UiRect INFO_RIGHT_PANEL = new UiRect(19.0f, -24.0f, 45.0f, 50.0f);
    private static final UiRect NAV_PANEL = new UiRect(-64.0f, 28.0f, 128.0f, 15.0f);
    private static final UiRect CLOSE_BUTTON = new UiRect(57.0f, -43.0f, 7.0f, 7.0f);

    private final HaoHanMetallurgy plugin;
    private final DisplayUiService displayUi;
    private final NamespacedKey legacyDisplayKey;
    private final Map<ForgeKey, UiHandle> activeDisplays = new HashMap<>();
    private final Map<ForgeKey, GuideState> states = new HashMap<>();

    public FurnaceGuideListener(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
        this.displayUi = Bukkit.getServicesManager().load(DisplayUiService.class);
        if (displayUi == null) throw new IllegalStateException("HaoHanDisplayUI service is unavailable");
        this.legacyDisplayKey = new NamespacedKey(plugin, "furnace_guide_display");
        displayUi.removeOwnedBy(OWNER_KEY);
        plugin.getServer().getScheduler().runTask(plugin, this::removeOrphanedDisplays);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::renderNearbyGuides, 2L, 2L);
    }

    private void renderNearbyGuides() {
        MetallurgyGuideCatalog catalog = MetallurgyGuideCatalog.from(plugin);
        if (catalog.categories().isEmpty()) return;

        Set<ForgeKey> liveForges = new HashSet<>();
        for (Machine machine : plugin.getMachineManager().getAll()) {
            if (!(machine instanceof AncientForge forge)) continue;
            ForgeKey key = ForgeKey.from(forge.getLocation());
            liveForges.add(key);

            if (!hasNearbyViewer(forge)) {
                removeDisplay(key);
                continue;
            }

            GuideState state = states.computeIfAbsent(key, ignored -> new GuideState());
            state.normalize(catalog.categories());
            UiHandle display = ensureDisplay(forge, catalog, state);
            display.update(document(forge, catalog, state));
        }

        for (ForgeKey key : new HashSet<>(activeDisplays.keySet())) {
            if (!liveForges.contains(key)) {
                removeDisplay(key);
                states.remove(key);
            }
        }
    }

    private boolean hasNearbyViewer(AncientForge forge) {
        Location center = guideLocation(forge);
        World world = center.getWorld();
        if (world == null) return false;
        for (Player player : world.getNearbyPlayers(center, DISPLAY_DISTANCE + 0.5)) {
            if (isInFront(forge, player)
                    && player.getEyeLocation().distanceSquared(center) < DISPLAY_DISTANCE * DISPLAY_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    private UiHandle ensureDisplay(AncientForge forge, MetallurgyGuideCatalog catalog,
                                   GuideState state) {
        ForgeKey key = ForgeKey.from(forge.getLocation());
        UiHandle cached = activeDisplays.get(key);
        if (cached != null && cached.isValid()) return cached;
        if (cached != null) cached.remove();

        UiOptions options = new UiOptions(
                PIXELS_PER_BLOCK, DISPLAY_DISTANCE, true, 0.11f,
                ManagedDisplayProtectionListener.MANAGED_TAG, UiCameraTransform.fixed());
        UiHandle result = displayUi.create(
                OWNER_KEY, guideLocation(forge), document(forge, catalog, state), options,
                player -> player.hasPermission("haohansmp.metallurgy.use"));
        result.onClick(click -> handleButton(key, click.button().id()));
        activeDisplays.put(key, result);
        return result;
    }

    private UiDocument document(AncientForge forge, MetallurgyGuideCatalog catalog,
                                GuideState state) {
        if (!state.expanded) return collapsedDocument();
        List<MetallurgyCategory> categories = catalog.categories();
        MetallurgyCategory category = categories.get(state.categoryIndex);

        UiDocument.Builder builder = UiDocument.builder()
                .add(new TextNode(
                        Component.text(PANEL_GLYPH).font(GUIDE_FONT).color(NamedTextColor.WHITE),
                        PANEL.centerX(), PANEL.centerY(), BACKGROUND_DEPTH,
                        2000, PANEL_SCALE, TextDisplay.TextAlignment.CENTER, false, false));

        renderHeader(builder, forge, category);
        switch (category.kind()) {
            case FUEL -> renderFuelReference(builder, category);
            case INFO -> renderInfoReference(builder, catalog.outputChance(), catalog.operatingNotes());
            case METAL, SLAG -> renderRecipeReference(builder, category);
        }
        renderCategoryNavigation(builder, categories, state);
        addIconButton(builder, "close_guide", new ItemStack(Material.BARRIER), CLOSE_BUTTON,
                Component.text(tr("guide.button.close-guide"), RED));
        return builder.build();
    }

    private void renderHeader(UiDocument.Builder builder, AncientForge forge,
                              MetallurgyCategory category) {
        addText(builder, Component.text(tr("guide.title"), ORANGE).decorate(TextDecoration.BOLD),
                new UiRect(-64.0f, -44.0f, 78.0f, 9.0f), 9.0f, UiTextAlignment.LEFT);
        addText(builder, Component.text(tr("guide.subtitle-prefix") + " ", MAGENTA)
                        .append(Component.text(tr("guide.subtitle-system"), GREEN)),
                new UiRect(-64.0f, -34.0f, 72.0f, 5.0f), 5.4f, UiTextAlignment.LEFT);
        addText(builder, Component.text(category.name().toUpperCase(Locale.ROOT), MAGENTA)
                        .decorate(TextDecoration.BOLD),
                new UiRect(14.0f, -44.0f, 40.0f, 7.0f), 5.6f, UiTextAlignment.RIGHT);
        addText(builder, Component.text(forge.getTemperature() + " C", ORANGE),
                new UiRect(25.0f, -35.0f, 29.0f, 5.0f), 5.4f, UiTextAlignment.RIGHT);
    }

    private void renderRecipeReference(UiDocument.Builder builder, MetallurgyCategory category) {
        addHeading(builder, tr("guide.headings.recipes"), RECIPE_PANEL);
        List<MetallurgyRecipe> recipes = category.recipes();
        if (recipes.isEmpty()) {
            addText(builder, Component.text(tr("guide.text.no-recipe"), MUTED),
                    row(RECIPE_PANEL, 8.0f, 6.0f), 4.8f, UiTextAlignment.LEFT);
        } else if (recipes.size() <= 2) {
            float rowHeight = (RECIPE_PANEL.height() - 6.0f) / recipes.size();
            for (int index = 0; index < recipes.size(); index++) {
                renderDetailedRecipe(builder, recipes.get(index),
                        category.fuelCombinations(),
                        RECIPE_PANEL.top() + 6.0f + index * rowHeight, rowHeight);
            }
        } else {
            float rowHeight = (RECIPE_PANEL.height() - 6.0f) / recipes.size();
            for (int index = 0; index < recipes.size(); index++) {
                renderCompactRecipe(builder, recipes.get(index),
                        RECIPE_PANEL.top() + 6.0f + index * rowHeight, rowHeight);
            }
        }
        renderFluxPanel(builder, recipes);
    }

    private void renderDetailedRecipe(UiDocument.Builder builder, MetallurgyRecipe recipe,
                                      List<FuelCombination> fuels,
                                      float top, float height) {
        float inputWidth = recipe.getInputs().size() == 1 ? 34.0f : 28.0f;
        float x = RECIPE_PANEL.left();
        for (int index = 0; index < recipe.getInputs().size(); index++) {
            MetallurgyRecipe.Ingredient input = recipe.getInputs().get(index);
            addItemRow(builder, ingredientItem(input), ingredientLabel(input),
                    new UiRect(x, top, inputWidth, 6.0f), SOFT_WHITE);
            x += inputWidth;
            if (index + 1 < recipe.getInputs().size()) {
                addText(builder, Component.text("+", MAGENTA),
                        new UiRect(x, top, 4.0f, 6.0f), 5.0f, UiTextAlignment.CENTER);
                x += 4.0f;
            }
        }
        addText(builder, Component.text("->", MAGENTA),
                new UiRect(x, top, 5.0f, 6.0f), 5.0f, UiTextAlignment.CENTER);
        addItemRow(builder, outputItem(recipe.getOutput()), outputLabel(recipe.getOutput()),
                new UiRect(x + 5.0f, top, Math.max(8.0f, RECIPE_PANEL.right() - x - 5.0f), 6.0f),
                ORANGE);
        String environment = recipe.requiresColdQuench() ? "  |  " + tr("guide.text.cold-quench")
                : recipe.requiresSoulFire() ? "  |  " + tr("guide.text.soul-fire") : "";
        addText(builder, Component.text(recipe.getMinTemperature() + "-" + recipe.getMaxTemperature()
                        + " C  |  " + recipe.getTimeSeconds() + "s  |  "
                        + tr("guide.text.refine") + " " + recipe.getPurificationTemperature()
                        + " C" + environment, MUTED),
                new UiRect(RECIPE_PANEL.left(), top + 6.5f, RECIPE_PANEL.width(), 5.0f),
                4.8f, UiTextAlignment.LEFT);
        List<FuelCombination> suitable = suitableFuels(fuels, recipe);
        if (!suitable.isEmpty() && height >= 17.0f) {
            addText(builder, Component.text(tr("guide.text.fuel"), MAGENTA),
                    new UiRect(RECIPE_PANEL.left(), top + 12.0f, 8.0f, 5.0f),
                    4.5f, UiTextAlignment.LEFT);
            addFuelRow(builder, suitable.getFirst(),
                    new UiRect(RECIPE_PANEL.left() + 8.0f, top + 12.0f, 34.0f, 5.0f));
        }
    }

    private void renderCompactRecipe(UiDocument.Builder builder, MetallurgyRecipe recipe,
                                     float top, float height) {
        MetallurgyRecipe.Ingredient input = recipe.getInputs().getFirst();
        addItemRow(builder, ingredientItem(input), ingredientLabel(input),
                new UiRect(RECIPE_PANEL.left(), top, 28.0f, height), SOFT_WHITE);
        addText(builder, Component.text("->", MAGENTA),
                new UiRect(RECIPE_PANEL.left() + 28.0f, top, 5.0f, height),
                4.6f, UiTextAlignment.CENTER);
        addItemRow(builder, outputItem(recipe.getOutput()), outputLabel(recipe.getOutput()),
                new UiRect(RECIPE_PANEL.left() + 33.0f, top, 27.0f, height), ORANGE);
        String condition = recipe.requiresColdQuench() ? " | " + tr("guide.text.ice")
                : recipe.requiresSoulFire() ? " | " + tr("guide.text.soul") : "";
        addText(builder, Component.text(recipe.getMinTemperature() + "-" + recipe.getMaxTemperature()
                        + " C | " + recipe.getTimeSeconds() + "s" + condition, MUTED),
                new UiRect(RECIPE_PANEL.left() + 60.0f, top, 20.0f, height),
                4.3f, UiTextAlignment.RIGHT);
    }

    private void renderFluxPanel(UiDocument.Builder builder, List<MetallurgyRecipe> recipes) {
        addHeading(builder, tr("guide.headings.fluxes"), FLUX_PANEL);
        if (recipes.isEmpty()) return;
        float rowHeight = (FLUX_PANEL.height() - 6.0f) / recipes.size();
        for (int recipeIndex = 0; recipeIndex < recipes.size(); recipeIndex++) {
            MetallurgyRecipe recipe = recipes.get(recipeIndex);
            float top = FLUX_PANEL.top() + 6.0f + recipeIndex * rowHeight;
            addText(builder, Component.text(shortRecipeName(recipe), MAGENTA),
                    new UiRect(FLUX_PANEL.left(), top, FLUX_PANEL.width(), 4.5f),
                    4.3f, UiTextAlignment.LEFT);
            if (recipe.getFluxes().isEmpty()) {
                addText(builder, Component.text(tr("guide.text.no-flux"), MUTED),
                        new UiRect(FLUX_PANEL.left(), top + 4.0f, FLUX_PANEL.width(),
                                Math.max(4.0f, rowHeight - 4.0f)),
                        4.5f, UiTextAlignment.LEFT);
                continue;
            }
            int shown = Math.min(recipe.getFluxes().size(), rowHeight >= 16.0f ? 2 : 1);
            for (int fluxIndex = 0; fluxIndex < shown; fluxIndex++) {
                MetallurgyRecipe.Flux flux = recipe.getFluxes().get(fluxIndex);
                String bonus = flux.cleanOutputBonus() >= 0.0
                        ? "  +" + Math.round(flux.cleanOutputBonus() * 100.0) + "%"
                        : "";
                addItemRow(builder, fluxItem(flux),
                        Component.text(flux.amount() + "x " + fluxName(flux) + bonus, GREEN),
                        new UiRect(FLUX_PANEL.left(), top + 4.5f + fluxIndex * 6.0f,
                                FLUX_PANEL.width(), 5.5f), GREEN);
            }
        }
    }

    private List<FuelCombination> suitableFuels(List<FuelCombination> fuels,
                                                MetallurgyRecipe recipe) {
        return fuels.stream().filter(row -> row.canStart(recipe)).toList();
    }

    private void renderFuelReference(UiDocument.Builder builder, MetallurgyCategory category) {
        UiRect list = new UiRect(-64.0f, -24.0f, 128.0f, 50.0f);
        addHeading(builder, tr("guide.headings.fuel-combinations"), list);
        List<FuelCombination> fuels = category.fuelCombinations();
        for (int index = 0; index < Math.min(10, fuels.size()); index++) {
            int column = index / 6;
            int row = index % 6;
            addFuelRow(builder, fuels.get(index), new UiRect(
                    list.left() + column * 64.0f, list.top() + 7.0f + row * 6.0f,
                    60.0f, 5.5f));
        }
        addText(builder, Component.text(tr("guide.text.fuel-description"), MUTED),
                new UiRect(list.left(), list.bottom() - 5.0f, list.width(), 5.0f),
                4.8f, UiTextAlignment.LEFT);
    }

    private void renderInfoReference(UiDocument.Builder builder, OutputChance chance,
                                     List<RecipeNote> notes) {
        addHeading(builder, tr("guide.headings.output-chances"), INFO_LEFT_PANEL);
        addText(builder, Component.text(tr("guide.text.without-flux"), MAGENTA),
                row(INFO_LEFT_PANEL, 8.0f, 5.0f), 5.2f, UiTextAlignment.LEFT);
        addItemRow(builder, new ItemStack(Material.IRON_INGOT),
                Component.text(tr("guide.text.clean-metal") + "  "
                        + chance.cleanWithoutBoraxPercent() + "%", GREEN),
                row(INFO_LEFT_PANEL, 14.0f, 6.0f), GREEN);
        addItemRow(builder, plugin.getItemManager().createItem(CustomItem.IRON_SLAG, 1),
                Component.text(tr("guide.text.slag") + "  "
                        + chance.slagWithoutBoraxPercent() + "%", RED),
                row(INFO_LEFT_PANEL, 20.0f, 6.0f), RED);
        addText(builder, Component.text(tr("guide.text.with-flux"), MAGENTA),
                row(INFO_LEFT_PANEL, 29.0f, 5.0f), 5.2f, UiTextAlignment.LEFT);
        addItemRow(builder, new ItemStack(Material.IRON_INGOT),
                Component.text(tr("guide.text.clean-metal") + "  " + tr("guide.text.up-to")
                        + " " + chance.cleanWithBoraxPercent() + "%", GREEN),
                row(INFO_LEFT_PANEL, 35.0f, 6.0f), GREEN);
        addItemRow(builder, plugin.getItemManager().createItem(CustomItem.IRON_SLAG, 1),
                Component.text(tr("guide.text.slag") + "  " + tr("guide.text.down-to")
                        + " " + chance.slagWithBoraxPercent() + "%", RED),
                row(INFO_LEFT_PANEL, 41.0f, 6.0f), RED);

        addHeading(builder, tr("guide.headings.operating-info"), INFO_RIGHT_PANEL);
        for (int index = 0; index < Math.min(6, notes.size()); index++) {
            RecipeNote note = notes.get(index);
            TextColor color = switch (note.tone()) {
                case INFO -> SOFT_WHITE;
                case GOOD -> GREEN;
                case WARNING -> ORANGE;
                case DANGER -> RED;
            };
            String prefix = switch (note.tone()) {
                case INFO -> "i";
                case GOOD -> "+";
                case WARNING -> "!";
                case DANGER -> "x";
            };
            addText(builder, Component.text(prefix + "  " + note.text(), color),
                    new UiRect(INFO_RIGHT_PANEL.left(), INFO_RIGHT_PANEL.top() + 7.0f + index * 7.0f,
                            INFO_RIGHT_PANEL.width(), 6.0f), 4.9f, UiTextAlignment.LEFT);
        }
    }

    private void renderCategoryNavigation(UiDocument.Builder builder,
                                          List<MetallurgyCategory> categories,
                                          GuideState state) {
        int pageCount = Math.max(1, (categories.size() + NAV_VISIBLE - 1) / NAV_VISIBLE);
        state.navPage = Math.floorMod(state.navPage, pageCount);
        int start = state.navPage * NAV_VISIBLE;
        int end = Math.min(categories.size(), start + NAV_VISIBLE);

        addTextButton(builder, "previous_categories", "<",
                new UiRect(NAV_PANEL.left(), NAV_PANEL.top() + 2.0f, 5.0f, 10.0f),
                Component.text(tr("guide.button.previous-categories"), MAGENTA));
        addTextButton(builder, "next_categories", ">",
                new UiRect(NAV_PANEL.right() - 5.0f, NAV_PANEL.top() + 2.0f, 5.0f, 10.0f),
                Component.text(tr("guide.button.next-categories"), MAGENTA));

        float slotsLeft = NAV_PANEL.left() + 7.0f;
        float slotWidth = (NAV_PANEL.width() - 14.0f) / NAV_VISIBLE;
        for (int index = start; index < end; index++) {
            int visibleIndex = index - start;
            MetallurgyCategory category = categories.get(index);
            UiRect slot = new UiRect(slotsLeft + visibleIndex * slotWidth,
                    NAV_PANEL.top(), slotWidth, NAV_PANEL.height());
            boolean selected = index == state.categoryIndex;
            UiRect iconBounds = slot.place(UiAnchor.TOP_CENTER, UiAnchor.TOP_CENTER,
                    selected ? 8.0f : 7.0f, selected ? 8.0f : 7.0f, 0.0f, 0.5f);
            if (category.kind() == MetallurgyCategory.Kind.INFO) {
                AlignedTextNode infoIcon = new AlignedTextNode(
                        Component.text("i", selected ? ORANGE : MAGENTA)
                                .decorate(TextDecoration.BOLD),
                        iconBounds, UiTextAlignment.CENTER)
                        .fontSize(selected ? 8.0f : 7.0f)
                        .align(UiVerticalAlignment.CENTER)
                        .verticalOffset(-1.0f)
                        .shadowed(true)
                        .atDepth(ICON_DEPTH);
                builder.add(infoIcon).button(UiButton.forText("category_" + index, infoIcon)
                        .describedBy(Component.text(tr("guide.button.open-info"), ORANGE)).hitSlop(4.0f));
            } else {
                UiIconNode icon = addIcon(builder, iconItem(category.icon()), iconBounds);
                builder.button(UiButton.forIcon("category_" + index, icon)
                        .describedBy(Component.text(tr("guide.button.open-category",
                                Map.of("category", category.name())), ORANGE))
                        .hitSlop(4.0f));
            }
            addText(builder, Component.text(selected
                                    ? shortCategoryName(category.name()).toUpperCase(Locale.ROOT)
                                    : shortCategoryName(category.name()),
                            selected ? ORANGE : SOFT_WHITE),
                    new UiRect(slot.left(), slot.top() + 8.5f, slot.width(), 5.0f),
                    4.3f, UiTextAlignment.CENTER);
        }
        addText(builder, Component.text((state.navPage + 1) + "/" + pageCount, PURPLE),
                new UiRect(-8.0f, 43.0f, 16.0f, 4.0f), 4.2f, UiTextAlignment.CENTER);
    }

    private void addFuelRow(UiDocument.Builder builder, FuelCombination fuel, UiRect bounds) {
        float iconSize = Math.min(4.5f, bounds.height());
        UiRect first = new UiRect(bounds.left(), bounds.top() - 1.7f, iconSize, iconSize);
        addIcon(builder, new ItemStack(fuel.first()), first);
        addText(builder, Component.text("+", MAGENTA),
                new UiRect(first.right() + 0.5f, bounds.top(), 3.0f, bounds.height()),
                4.2f, UiTextAlignment.CENTER);
        UiRect second = new UiRect(first.right() + 4.0f, bounds.top() - 1.7f, iconSize, iconSize);
        addIcon(builder, new ItemStack(fuel.second()), second);
        addText(builder, Component.text("-> " + fuel.maxTemperature() + " C", ORANGE),
                new UiRect(second.right() + 1.0f, bounds.top(),
                        Math.max(1.0f, bounds.right() - second.right() - 1.0f), bounds.height()),
                4.8f, UiTextAlignment.LEFT);
    }

    private void addItemRow(UiDocument.Builder builder, ItemStack item, Component label,
                            UiRect bounds, TextColor color) {
        float iconSize = Math.min(bounds.height(), 6.0f);
        UiRect iconBounds = new UiRect(bounds.left(), bounds.centerY() - iconSize * 0.5f - 1.8f,
                iconSize, iconSize);
        addIcon(builder, item, iconBounds);
        addText(builder, label.color(color),
                new UiRect(iconBounds.right() + 1.5f, bounds.top(),
                        Math.max(1.0f, bounds.right() - iconBounds.right() - 1.5f), bounds.height()),
                5.2f, UiTextAlignment.LEFT);
    }

    private void addHeading(UiDocument.Builder builder, String text, UiRect panel) {
        addText(builder, Component.text(text, MAGENTA).decorate(TextDecoration.BOLD),
                new UiRect(panel.left(), panel.top(), panel.width(), 5.0f),
                5.8f, UiTextAlignment.LEFT);
    }

    private void addText(UiDocument.Builder builder, Component text, UiRect bounds,
                         float fontSize, UiTextAlignment alignment) {
        float measured = measureTextWidth(text, fontSize);
        float fittedSize = measured > bounds.width()
                ? Math.max(3.0f, fontSize * bounds.width() / measured)
                : fontSize;
        measured = Math.min(bounds.width(), measureTextWidth(text, fittedSize));
        builder.add(new AlignedTextNode(text, bounds, alignment)
                .fontSize(fittedSize)
                .align(UiVerticalAlignment.CENTER)
                .verticalOffset(-1.0f)
                .shadowed(true)
                .atDepth(TEXT_DEPTH));
    }

    private UiIconNode addIcon(UiDocument.Builder builder, ItemStack item, UiRect bounds) {
        UiIconNode icon = new UiIconNode(item, bounds, ICON_DEPTH,
                16.0f, 16.0f, ItemDisplay.ItemDisplayTransform.FIXED);
        builder.add(icon);
        return icon;
    }

    private void addIconButton(UiDocument.Builder builder, String id, ItemStack item,
                               UiRect bounds, Component description) {
        UiIconNode icon = addIcon(builder, item, bounds);
        builder.button(UiButton.forIcon(id, icon).describedBy(description).hitSlop(2.0f));
    }

    private void addTextButton(UiDocument.Builder builder, String id, String label,
                               UiRect bounds, Component description) {
        AlignedTextNode text = new AlignedTextNode(
                Component.text(label, MAGENTA).decorate(TextDecoration.BOLD),
                bounds, UiTextAlignment.CENTER)
                .fontSize(6.0f)
                .verticalOffset(-1.0f)
                .shadowed(true)
                .atDepth(ICON_DEPTH);
        builder.add(text).button(UiButton.forText(id, text).describedBy(description).hitSlop(2.0f));
    }

    /**
     * Converts DisplayUI's width estimate, calibrated at 80 logical pixels per
     * block, into this guide's 40-pixel coordinate space. TextDisplay keeps its
     * native scale while scene positions are divided by pixelsPerBlock, so the
     * conversion is required to keep left and right edges visually anchored.
     */
    private float measureTextWidth(Component text, float fontSize) {
        return UiText.estimateWidth(text, fontSize)
                * PIXELS_PER_BLOCK / TEXT_WIDTH_REFERENCE_PIXELS_PER_BLOCK;
    }

    private UiDocument collapsedDocument() {
        UiRect collapsed = UiRect.centered(0.0f, 0.0f, 20.0f, 20.0f);
        UiRect iconBounds = collapsed.place(
                UiAnchor.CENTER, UiAnchor.CENTER, 14.0f, 14.0f, 0.0f, 0.0f);
        UiIconNode icon = new UiIconNode(new ItemStack(Material.KNOWLEDGE_BOOK),
                iconBounds, ICON_DEPTH, 16.0f, 16.0f, ItemDisplay.ItemDisplayTransform.FIXED);
        return UiDocument.builder()
                .add(new BlockNode(Material.BLACK_CONCRETE.createBlockData(),
                        collapsed, BACKGROUND_DEPTH, 1.0f))
                .add(icon)
                .button(UiButton.forIcon("open_guide", icon)
                        .describedBy(Component.text(tr("guide.button.open-guide"), GREEN))
                        .hitSlop(3.0f))
                .build();
    }

    private ItemStack ingredientItem(MetallurgyRecipe.Ingredient ingredient) {
        if (ingredient.customItemId() != null) {
            CustomItem item = CustomItem.getById(ingredient.customItemId()).orElse(null);
            if (item != null) return plugin.getItemManager().createItem(item, 1);
        }
        return new ItemStack(ingredient.material());
    }

    private ItemStack outputItem(MetallurgyRecipe.OutputItem output) {
        if (output.customItemId() != null) {
            CustomItem item = CustomItem.getById(output.customItemId()).orElse(null);
            if (item != null) return plugin.getItemManager().createItem(item, 1);
        }
        return new ItemStack(output.material());
    }

    private ItemStack fluxItem(MetallurgyRecipe.Flux flux) {
        if (flux.customItemId() != null) {
            CustomItem item = CustomItem.getById(flux.customItemId()).orElse(null);
            if (item != null) return plugin.getItemManager().createItem(item, 1);
        }
        return new ItemStack(flux.material());
    }

    private ItemStack iconItem(GuideIcon icon) {
        if (icon.customItemId() != null) {
            CustomItem item = CustomItem.getById(icon.customItemId()).orElse(null);
            if (item != null) return plugin.getItemManager().createItem(item, 1);
        }
        return new ItemStack(icon.material());
    }

    private Component ingredientLabel(MetallurgyRecipe.Ingredient input) {
        String label = input.amount() + "x " + itemName(input.material(), input.customItemId());
        if (!input.alternativeMaterials().isEmpty()) label += " / " + title(input.alternativeMaterials().getFirst().name());
        return Component.text(label, SOFT_WHITE);
    }

    private Component outputLabel(MetallurgyRecipe.OutputItem output) {
        return Component.text(output.amount() + "x " + itemName(output.material(), output.customItemId()), ORANGE);
    }

    private String fluxName(MetallurgyRecipe.Flux flux) {
        return itemName(flux.material(), flux.customItemId());
    }

    private String shortRecipeName(MetallurgyRecipe recipe) {
        if (recipe.getId().contains("slag_recycling") && !recipe.getInputs().isEmpty()) {
            MetallurgyRecipe.Ingredient input = recipe.getInputs().getFirst();
            return tr("guide.text.slag-recycle", Map.of(
                    "metal", itemName(input.material(), input.customItemId())));
        }
        return itemName(recipe.getOutput().material(), recipe.getOutput().customItemId());
    }

    private String itemName(Material material, String customItemId) {
        if (customItemId != null) {
            CustomItem item = CustomItem.getById(customItemId).orElse(null);
            if (item != null) return plugin.getLanguageManager().itemName(item);
            return title(customItemId);
        }
        if (material == null) return "?";
        return plugin.getLanguageManager().plainOr(
                "guide.material." + material.name().toLowerCase(Locale.ROOT), title(material.name()));
    }

    private String shortCategoryName(String value) {
        return value.length() <= 10 ? value : value.substring(0, 9) + ".";
    }

    private String title(String id) {
        StringBuilder result = new StringBuilder();
        for (String word : id.toLowerCase(Locale.ROOT).split("_")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private String tr(String key) {
        return plugin.getLanguageManager().plain(key);
    }

    private String tr(String key, Map<String, ?> values) {
        return plugin.getLanguageManager().plain(key, values);
    }

    private UiRect row(UiRect panel, float topOffset, float height) {
        return new UiRect(panel.left(), panel.top() + topOffset, panel.width(), height);
    }

    private void handleButton(ForgeKey key, String id) {
        GuideState state = states.computeIfAbsent(key, ignored -> new GuideState());
        if (id.startsWith("category_")) {
            try {
                state.selectCategory(Integer.parseInt(id.substring("category_".length())));
            } catch (NumberFormatException ignored) {
                // Ignore malformed button IDs supplied by stale display entities.
            }
            return;
        }
        switch (id) {
            case "open_guide" -> state.expanded = true;
            case "close_guide" -> state.expanded = false;
            case "previous_categories" -> state.navPage--;
            case "next_categories" -> state.navPage++;
            default -> { }
        }
    }

    private Location guideLocation(AncientForge forge) {
        org.bukkit.util.Vector front = forgeFrontDirection(forge);
        Location location = forge.getLocation().add(
                0.5 + front.getX() * 1.72, 1.55, 0.5 + front.getZ() * 1.72);
        location.setYaw((float) forge.getRotation());
        location.setPitch(0.0f);
        return location;
    }

    private org.bukkit.util.Vector forgeFrontDirection(AncientForge forge) {
        return switch (forge.getRotation()) {
            case 90 -> new org.bukkit.util.Vector(-1, 0, 0);
            case 180 -> new org.bukkit.util.Vector(0, 0, -1);
            case 270 -> new org.bukkit.util.Vector(1, 0, 0);
            default -> new org.bukkit.util.Vector(0, 0, 1);
        };
    }

    private boolean isInFront(AncientForge forge, Player player) {
        org.bukkit.util.Vector front = forgeFrontDirection(forge);
        org.bukkit.util.Vector toPlayer = player.getEyeLocation().toVector()
                .subtract(forge.getLocation().add(0.5, 1.5, 0.5).toVector());
        toPlayer.setY(0.0);
        return toPlayer.lengthSquared() > 0.0001 && front.dot(toPlayer.normalize()) > 0.12;
    }

    private void removeDisplay(ForgeKey key) {
        UiHandle display = activeDisplays.remove(key);
        if (display != null && display.isValid()) display.remove();
    }

    private void removeOrphanedDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getPersistentDataContainer().has(legacyDisplayKey, PersistentDataType.STRING)) {
                    display.remove();
                }
            }
        }
        activeDisplays.clear();
        states.clear();
    }

    public void shutdown() {
        displayUi.removeOwnedBy(OWNER_KEY);
        activeDisplays.clear();
        states.clear();
    }

    private static final class GuideState {
        private boolean expanded = true;
        private int categoryIndex;
        private int navPage;

        private void selectCategory(int index) {
            categoryIndex = index;
        }

        private void normalize(List<MetallurgyCategory> categories) {
            categoryIndex = Math.floorMod(categoryIndex, categories.size());
            int pages = Math.max(1, (categories.size() + NAV_VISIBLE - 1) / NAV_VISIBLE);
            navPage = Math.floorMod(navPage, pages);
        }
    }

    private record ForgeKey(UUID worldId, int x, int y, int z) {
        static ForgeKey from(Location location) {
            return new ForgeKey(location.getWorld().getUID(), location.getBlockX(),
                    location.getBlockY(), location.getBlockZ());
        }
    }
}
