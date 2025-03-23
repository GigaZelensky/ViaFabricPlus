// First, let's create a class to store favorite versions
// File: /src/main/java/com/viaversion/viafabricplus/save/impl/FavoriteVersionsSave.java

package com.viaversion.viafabricplus.save.impl;

import com.viaversion.viafabricplus.save.BaseSave;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FavoriteVersionsSave extends BaseSave {
    
    private final Set<String> favoriteVersions = new HashSet<>();
    
    public boolean isFavorite(ProtocolVersion version) {
        return favoriteVersions.contains(version.getName());
    }
    
    public void toggleFavorite(ProtocolVersion version) {
        if (isFavorite(version)) {
            favoriteVersions.remove(version.getName());
        } else {
            favoriteVersions.add(version.getName());
        }
        save();
    }
    
    public List<ProtocolVersion> getFavoriteVersions() {
        List<ProtocolVersion> favorites = new ArrayList<>();
        for (ProtocolVersion version : com.viaversion.vialoader.util.ProtocolVersionList.getProtocolsNewToOld()) {
            if (isFavorite(version)) {
                favorites.add(version);
            }
        }
        return favorites;
    }
}

// Now, let's modify the SaveManager to include our new save
// File: /src/main/java/com/viaversion/viafabricplus/save/SaveManager.java
// We need to add the following field and getter:

/*
private final FavoriteVersionsSave favoriteVersionsSave = new FavoriteVersionsSave();

public FavoriteVersionsSave getFavoriteVersionsSave() {
    return favoriteVersionsSave;
}
*/

// Now, let's modify the ProtocolSelectionScreen to show favorites
// File: /src/main/java/com/viaversion/viafabricplus/screen/impl/ProtocolSelectionScreen.java

package com.viaversion.viafabricplus.screen.impl;

import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslator;
import com.viaversion.viafabricplus.save.SaveManager;
import com.viaversion.viafabricplus.screen.VFPList;
import com.viaversion.viafabricplus.screen.VFPListEntry;
import com.viaversion.viafabricplus.screen.VFPScreen;
import com.viaversion.viafabricplus.screen.impl.settings.SettingsScreen;
import com.viaversion.vialoader.util.ProtocolVersionList;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.awt.*;
import java.util.List;

public final class ProtocolSelectionScreen extends VFPScreen {

    public static final ProtocolSelectionScreen INSTANCE = new ProtocolSelectionScreen();
    private SlotList mainList;
    private FavoritesList favoritesList;
    private boolean showFavorites = true;

    private ProtocolSelectionScreen() {
        super("ViaFabricPlus", true);
    }

    @Override
    protected void init() {
        // List and Settings
        this.setupDefaultSubtitle();
        
        // Create the main version list (narrower to make room for favorites)
        int mainListWidth = width * 3 / 4;
        this.mainList = new SlotList(this.client, mainListWidth, height, 3 + 3 /* start offset */ + (textRenderer.fontHeight + 2) * 3 /* title is 2 */, 30, textRenderer.fontHeight + 4);
        this.addDrawableChild(mainList);
        
        // Create the favorites list
        int favListWidth = width - mainListWidth;
        this.favoritesList = new FavoritesList(this.client, favListWidth, height, 3 + 3 + (textRenderer.fontHeight + 2) * 3, 30, textRenderer.fontHeight + 4);
        this.favoritesList.setX(mainListWidth);
        this.addDrawableChild(favoritesList);
        
        // Toggle favorites visibility button
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("favorites.viafabricplus.toggle"), button -> {
            showFavorites = !showFavorites;
            favoritesList.visible = showFavorites;
            button.setMessage(Text.translatable(showFavorites ? "favorites.viafabricplus.hide" : "favorites.viafabricplus.show"));
        }).position(width - 98 - 105, 5).size(98, 20).build());
        
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("base.viafabricplus.settings"), button -> SettingsScreen.INSTANCE.open(this)).position(width - 98 - 5, 5).size(98, 20).build());

        this.addDrawableChild(ButtonWidget.builder(ServerListScreen.INSTANCE.getTitle(), button -> ServerListScreen.INSTANCE.open(this))
                .position(5, height - 25).size(98, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("report.viafabricplus.button"), button -> ReportIssuesScreen.INSTANCE.open(this))
                .position(width - 98 - 5, height - 25).size(98, 20).build());

        super.init();
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        // Draw a separator line between main list and favorites
        if (showFavorites) {
            int mainListWidth = width * 3 / 4;
            context.fill(mainListWidth, 3 + 3 + (textRenderer.fontHeight + 2) * 3, 
                    mainListWidth + 1, height - 30, 0x99FFFFFF);
            
            // Draw "Favorites" header
            context.drawTextWithShadow(textRenderer, Text.translatable("favorites.viafabricplus.title"),
                    mainListWidth + 5, 3 + 3 + (textRenderer.fontHeight + 2) * 2, Color.YELLOW.getRGB());
        }
    }

    public static class SlotList extends VFPList {
        private static double scrollAmount;

        public SlotList(MinecraftClient minecraftClient, int width, int height, int top, int bottom, int entryHeight) {
            super(minecraftClient, width, height, top, bottom, entryHeight);

            ProtocolVersionList.getProtocolsNewToOld().stream().map(ProtocolSlot::new).forEach(this::addEntry);
            initScrollY(scrollAmount);
        }

        @Override
        protected void updateSlotAmount(double amount) {
            scrollAmount = amount;
        }
        
        @Override
        public int getRowWidth() {
            return width - 20; // Make rows a bit narrower to accommodate the star
        }
    }
    
    public static class FavoritesList extends VFPList {
        private static double favScrollAmount;

        public FavoritesList(MinecraftClient minecraftClient, int width, int height, int top, int bottom, int entryHeight) {
            super(minecraftClient, width, height, top, bottom, entryHeight);
            
            // Add favorite versions to the list
            List<ProtocolVersion> favorites = SaveManager.INSTANCE.getFavoriteVersionsSave().getFavoriteVersions();
            favorites.stream().map(FavoriteSlot::new).forEach(this::addEntry);
            
            initScrollY(favScrollAmount);
        }

        @Override
        protected void updateSlotAmount(double amount) {
            favScrollAmount = amount;
        }
        
        @Override
        public int getRowWidth() {
            return width - 10;
        }
        
        public void refreshFavorites() {
            clearEntries();
            SaveManager.INSTANCE.getFavoriteVersionsSave().getFavoriteVersions()
                .stream().map(FavoriteSlot::new).forEach(this::addEntry);
        }
    }

    public static class ProtocolSlot extends VFPListEntry {
        private final ProtocolVersion protocolVersion;

        public ProtocolSlot(final ProtocolVersion protocolVersion) {
            this.protocolVersion = protocolVersion;
        }

        @Override
        public Text getNarration() {
            return Text.of(this.protocolVersion.getName());
        }

        @Override
        public void mappedMouseClicked(double mouseX, double mouseY, int button) {
            int entryWidth = this.getWidth();
            
            // Check if star area was clicked (right side)
            if (mouseX > entryWidth - 20 && mouseX < entryWidth - 5) {
                // Toggle favorite status
                SaveManager.INSTANCE.getFavoriteVersionsSave().toggleFavorite(protocolVersion);
                
                // Refresh favorites list
                if (INSTANCE.favoritesList != null) {
                    INSTANCE.favoritesList.refreshFavorites();
                }
            } else {
                // Normal version selection
                ProtocolTranslator.setTargetVersion(this.protocolVersion);
            }
        }

        @Override
        public void mappedRender(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            final boolean isSelected = ProtocolTranslator.getTargetVersion().equals(protocolVersion);
            final boolean isFavorite = SaveManager.INSTANCE.getFavoriteVersionsSave().isFavorite(protocolVersion);
            
            final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            context.drawCenteredTextWithShadow(textRenderer, this.protocolVersion.getName(), 
                    entryWidth / 2 - 10, // Shift text slightly to make room for star
                    entryHeight / 2 - textRenderer.fontHeight / 2, 
                    isSelected ? Color.GREEN.getRGB() : Color.RED.getRGB());
            
            // Draw star icon (filled if favorited, outline if not)
            String starSymbol = isFavorite ? "★" : "☆";
            int starColor = isFavorite ? Color.YELLOW.getRGB() : Color.GRAY.getRGB();
            context.drawTextWithShadow(textRenderer, Text.of(starSymbol), 
                    entryWidth - 15, 
                    entryHeight / 2 - textRenderer.fontHeight / 2,
                    starColor);
        }
        
        private int getWidth() {
            if (this.getParent() != null) {
                return this.getParent().getRowWidth();
            }
            return 100; // Fallback value
        }
    }
    
    public static class FavoriteSlot extends VFPListEntry {
        private final ProtocolVersion protocolVersion;

        public FavoriteSlot(final ProtocolVersion protocolVersion) {
            this.protocolVersion = protocolVersion;
        }

        @Override
        public Text getNarration() {
            return Text.of(this.protocolVersion.getName());
        }

        @Override
        public void mappedMouseClicked(double mouseX, double mouseY, int button) {
            // Set as active version
            ProtocolTranslator.setTargetVersion(this.protocolVersion);
        }

        @Override
        public void mappedRender(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            final boolean isSelected = ProtocolTranslator.getTargetVersion().equals(protocolVersion);
            
            final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            context.drawCenteredTextWithShadow(textRenderer, this.protocolVersion.getName(), 
                    entryWidth / 2, 
                    entryHeight / 2 - textRenderer.fontHeight / 2, 
                    isSelected ? Color.GREEN.getRGB() : Color.YELLOW.getRGB());
        }
    }
}

// We need to add translations to the language file
// Add to en_us.json:
/*
{
  "favorites.viafabricplus.title": "Favorites",
  "favorites.viafabricplus.toggle": "Toggle Favorites",
  "favorites.viafabricplus.show": "Show Favorites",
  "favorites.viafabricplus.hide": "Hide Favorites"
}
*/
