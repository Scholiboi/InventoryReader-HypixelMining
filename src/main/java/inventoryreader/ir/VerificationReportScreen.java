package inventoryreader.ir;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.File;

public class VerificationReportScreen extends Screen {
    private final File verifiedFile;
    private final String calculatedChecksum;
    private final String expectedChecksum;
    private final boolean checksumMatch;
    
    public VerificationReportScreen(File file, String calculatedChecksum, String expectedChecksum) {
        super(Text.of("Security Verification Report"));
        this.verifiedFile = file;
        this.calculatedChecksum = calculatedChecksum;
        this.expectedChecksum = expectedChecksum;
        this.checksumMatch = calculatedChecksum.equalsIgnoreCase(expectedChecksum);
    }
    
    @Override
    protected void init() {
        super.init();
        
        this.addDrawableChild(ButtonWidget.builder(Text.of("Continue"), button -> {
            MinecraftClient.getInstance().setScreen(null);
        }).dimensions(this.width / 2 - 100, this.height - 40, 200, 20).build());
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
                
        super.render(context, mouseX, mouseY, delta);
        
        context.drawCenteredTextWithShadow(this.textRenderer, "File Verification Report", this.width / 2, 20, 0xFFFFFF);
        
        int y = 50;
        context.drawTextWithShadow(this.textRenderer, "File: " + verifiedFile.getName(), 20, y, 0xFFFFFF);
        y += 20;
        
        String statusText = checksumMatch ? "§aVERIFIED SECURE" : "§cFAILED VERIFICATION";
        context.drawTextWithShadow(this.textRenderer, "Status: " + statusText, 20, y, 0xFFFFFF);
        y += 30;
        
        context.drawTextWithShadow(this.textRenderer, "Expected checksum:", 20, y, 0xFFFFFF);
        y += 15;
        context.drawTextWithShadow(this.textRenderer, expectedChecksum, 20, y, checksumMatch ? 0x00FF00 : 0xFFFFFF);
        y += 25;
        
        context.drawTextWithShadow(this.textRenderer, "Actual checksum:", 20, y, 0xFFFFFF);
        y += 15;
        context.drawTextWithShadow(this.textRenderer, calculatedChecksum, 20, y, checksumMatch ? 0x00FF00 : 0xFF0000);
    }
}