package inventoryreader.ir;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;

public class SecurityWarningScreen extends Screen {
    private final Screen parent;
    
    public SecurityWarningScreen(Screen parent) {
        super(Text.of("Security Notice"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        
        this.addDrawableChild(ButtonWidget.builder(Text.of("I Accept & Continue"), button -> {
            MinecraftClient.getInstance().setScreen(new ExeDownloadScreen());
        }).dimensions(this.width / 2 - 100, this.height - 80, 200, 20).build());
        
        this.addDrawableChild(ButtonWidget.builder(Text.of("Cancel"), button -> {
            MinecraftClient.getInstance().setScreen(parent);
        }).dimensions(this.width / 2 - 100, this.height - 50, 200, 20).build());
        
        this.addDrawableChild(ButtonWidget.builder(Text.of("View Source Code"), button -> {
            Util.getOperatingSystem().open(URI.create("https://github.com/Scholiboi/hypixel-forge"));
        }).dimensions(this.width / 2 - 100, this.height - 110, 200, 20).build());
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        
        context.drawCenteredTextWithShadow(this.textRenderer, "Important Security Notice", this.width / 2, 15, 0xFF5555);

        String[] info = {
            "This mod requires a native executable to function properly.",
            "The executable will be downloaded from GitHub and run on your computer.",
            "This executable is necessary to provide real-time inventory tracking",
            "between Minecraft and external applications.",
            "",
            "⚠️ NOTE: THIS MOD CURRENTLY WORKS ON WINDOWS ONLY ⚠️",
            "",
            "Security measures in place:",
            "• SHA-256 checksum verification",
            "• Quarantine system for failed verifications",
            "• All files stored in dedicated mod directory",
            "• Full source code available on GitHub"
        };
        
        int y = 40;
        for (String line : info) {
            context.drawCenteredTextWithShadow(this.textRenderer, line, this.width / 2, y, 0xFFFFFF);
            y += 12;
        }
    }
}