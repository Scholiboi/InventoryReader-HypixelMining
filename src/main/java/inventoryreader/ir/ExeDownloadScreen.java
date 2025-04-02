package inventoryreader.ir;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class ExeDownloadScreen extends Screen {
    private enum State {
        INITIAL, DOWNLOADING, DOWNLOAD_COMPLETE, VERIFICATION_FAILED, COMPLETE
    }
    
    private State currentState = State.INITIAL;
    private final AtomicInteger progress = new AtomicInteger(0);
    private CompletableFuture<Void> downloadFuture;
    private ButtonWidget okButton;
    private boolean needsDownload = false;
    private String cachedFileSize = "Calculating...";

    public ExeDownloadScreen() {
        super(Text.of("IR Mod Setup"));
        InventoryReader.LOGGER.info("ExeDownloadScreen constructor called");
        checkExeStatus();
        
        CompletableFuture.runAsync(() -> {
            try {
                URI downloadUri = new URI("https://github.com/Scholiboi/hypixel-forge/releases/download/v1.1.2/" + ChecksumVerifier.EXE_FILENAME);
                URL downloadUrl = downloadUri.toURL();
                long bytes = downloadUrl.openConnection().getContentLengthLong();
                if (bytes <= 0) {
                    cachedFileSize = "Unknown";
                } else {
                    cachedFileSize = String.format("%.1f", bytes / (1024.0 * 1024.0));
                }
            } catch (Exception e) {
                cachedFileSize = "Unknown";
            }
        });
    }

    private void checkExeStatus() {
        File exeFile = FilePathManager.getExecutablePath();
        boolean fileExists = exeFile.exists();
        needsDownload = true;
        
        if (fileExists) {
            InventoryReader.LOGGER.info("Found existing executable. Verifying integrity...");
            if (ChecksumVerifier.verify(exeFile)) {
                InventoryReader.LOGGER.info("Existing executable passed verification. Using it.");
                needsDownload = false;
                currentState = State.COMPLETE;
            } else {
                InventoryReader.LOGGER.warn("Existing executable failed verification. Will download fresh copy.");
            }
        } else {
            InventoryReader.LOGGER.info("Executable not found. Will download it.");
        }
    }

    @Override
    protected void init() {
        super.init();
        InventoryReader.LOGGER.info("ExeDownloadScreen init called, needsDownload=" + needsDownload);
        
        if (!needsDownload) {
            this.close();
            return;
        }
        
        okButton = ButtonWidget.builder(Text.of("OK"), button -> {
            if (currentState == State.INITIAL) {
                startDownload();
            } else if (currentState == State.DOWNLOAD_COMPLETE) {
                InventoryReader.launchOrFetchExe();
                this.close();
            } else if (currentState == State.VERIFICATION_FAILED ||
                      currentState == State.COMPLETE) {
                this.close();
            }
        }).dimensions(this.width / 2 - 100, this.height - 40, 200, 20).build();
        
        this.addDrawableChild(okButton);
    }

    private void startDownload() {
        currentState = State.DOWNLOADING;
        okButton.active = false;
        
        downloadFuture = CompletableFuture.runAsync(() -> {
            try {
                InventoryReader.LOGGER.info("Downloading executable...");
                URI downloadUri = new URI("https://github.com/Scholiboi/hypixel-forge/releases/download/v1.1.2/" + ChecksumVerifier.EXE_FILENAME);
                URL downloadUrl = downloadUri.toURL();

                File tempFile = new File(FilePathManager.MOD_DIR, ChecksumVerifier.EXE_FILENAME);
                long totalBytes = downloadUrl.openConnection().getContentLengthLong();
                long downloadedBytes = 0;
                
                try (java.io.InputStream in = downloadUrl.openStream();
                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;
                        progress.set((int)((downloadedBytes * 100) / totalBytes));
                    }
                }
                
                InventoryReader.LOGGER.info("Download complete. Verifying integrity...");
                
                if (!ChecksumVerifier.verify(tempFile)) {
                    InventoryReader.LOGGER.error("Downloaded file failed checksum verification.");
                    currentState = State.VERIFICATION_FAILED;
                    return;
                }

                File exeFile = FilePathManager.getExecutablePath();
                if (exeFile.exists() && !exeFile.equals(tempFile)) {
                    exeFile.delete();
                }
                
                Files.move(tempFile.toPath(), exeFile.toPath(),
                           StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                
                currentState = State.DOWNLOAD_COMPLETE;
            } catch (Exception e) {
                InventoryReader.LOGGER.error("Download failed", e);
                currentState = State.VERIFICATION_FAILED;
            }
        });
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        switch (currentState) {
            case INITIAL:
                context.drawCenteredTextWithShadow(this.textRenderer, "IR Mod Setup - Required Files", this.width / 2, 20, 0xFFFFFF);
                context.drawCenteredTextWithShadow(this.textRenderer, "The application for IR Mod needs to be downloaded:", this.width / 2, 50, 0xFFFFFF);
                context.drawCenteredTextWithShadow(this.textRenderer, ChecksumVerifier.EXE_FILENAME, this.width / 2, 70, 0x00FFFF);
                context.drawCenteredTextWithShadow(this.textRenderer, "File size: ~" + estimateFileSize() + " MB", this.width / 2, 90, 0xDDDDDD);
                context.drawCenteredTextWithShadow(this.textRenderer, "Press OK to continue.", this.width / 2, 120, 0xFFFFFF);
                break;
                
            case DOWNLOADING:
                context.drawCenteredTextWithShadow(this.textRenderer, "Downloading... " + progress.get() + "%", this.width / 2, 50, 0xFFFFFF);
                context.fill(this.width / 2 - 100, 70, this.width / 2 - 100 + 200 * progress.get() / 100, 90, 0xFF00FF00);
                context.fill(this.width / 2 - 100, 70, this.width / 2 + 100, 90, 0x50FFFFFF);

                if (downloadFuture.isDone()) {
                    okButton.active = true;
                    okButton.setMessage(Text.of("Continue"));
                }
                break;
                
            case DOWNLOAD_COMPLETE:
                context.drawCenteredTextWithShadow(this.textRenderer, "Download Complete!", this.width / 2, 50, 0x00FF00);
                context.drawCenteredTextWithShadow(this.textRenderer, "Files verified successfully.", this.width / 2, 70, 0x00FF00);
                okButton.active = true;
                okButton.setMessage(Text.of("Continue"));
                break;
                
            case VERIFICATION_FAILED:
                context.drawCenteredTextWithShadow(this.textRenderer, "Download Failed!", this.width / 2, 50, 0xFF0000);
                context.drawCenteredTextWithShadow(this.textRenderer, "File verification failed. Please try again later.", this.width / 2, 70, 0xFF0000);
                okButton.active = true;
                okButton.setMessage(Text.of("Close"));
                break;
                
            case COMPLETE:
                context.drawCenteredTextWithShadow(this.textRenderer, "Setup Complete", this.width / 2, 50, 0x00FF00);
                context.drawCenteredTextWithShadow(this.textRenderer, "All required files are ready.", this.width / 2, 70, 0x00FF00);
                break;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return currentState != State.DOWNLOADING;
    }

    @Override
    public void tick() {
        super.tick();
        if (currentState == State.DOWNLOADING && downloadFuture != null && downloadFuture.isDone()) {
            okButton.active = true;
            okButton.setMessage(Text.of("Continue"));
        }
    }
    
    private String estimateFileSize() {
        return cachedFileSize;
    }
}