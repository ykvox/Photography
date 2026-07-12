package net.blouflin.photography.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.blouflin.photography.Photography;
import net.blouflin.photography.PhotographyAlbum;
import net.blouflin.photography.PhotographyAlbumItem;
import net.blouflin.photography.PhotographyPhoto;
import net.blouflin.photography.networking.PhotographyAlbumActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class PhotographyAlbumScreen extends Screen {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("photography", "textures/gui/album.png");
    static final WidgetSprites SIGN_BUTTON_SPRITES = sprites("album/sign");
    static final WidgetSprites CANCEL_BUTTON_SPRITES = sprites("album/cancel");
    private static final WidgetSprites PREVIOUS_PAGE = sprites("album/previous_page");
    private static final WidgetSprites NEXT_PAGE = sprites("album/next_page");
    private static final WidgetSprites PHOTO_SLOT = sprites("album/photograph_slot");
    private static final WidgetSprites EMPTY_PHOTO_SLOT = sprites("album/photograph_slot_empty");
    private static final int IMAGE_WIDTH = 298;
    private static final int IMAGE_HEIGHT = 188;
    private static final int TEXTURE_SIZE = 512;
    private static final int PHOTO_SIZE = 108;
    private static final int LEFT_PHOTO_X = 25;
    private static final int RIGHT_PHOTO_X = 165;
    private static final int PHOTO_Y = 21;
    private static final int NOTE_X = 22;
    private static final int NOTE_Y = 133;
    private static final int NOTE_WIDTH = 114;
    private static final int NOTE_HEIGHT = 27;
    private static final int LEFT_PAGE_NUMBER_X = 71;
    private static final int RIGHT_PAGE_NUMBER_X = 212;
    private static final int PAGE_NUMBER_Y = 167;
    private static final double FOCUS_ZOOM_PER_STEP = 1.4d;
    private static final double FOCUS_SCROLL_STEP = 0.5d;
    private static final double FOCUS_ZOOM_EASING = 0.32d;

    private final InteractionHand hand;
    private int leftPos;
    private int topPos;
    private int spreadIndex = 0;
    private ImageButton previousPageButton;
    private ImageButton nextPageButton;
    private ImageButton signButton;
    private PhotographyAlbumTextBox titleBox;
    private PhotographyAlbumTextBox leftNoteBox;
    private PhotographyAlbumTextBox rightNoteBox;
    private boolean syncingFields;
    private int addingPage = -1;
    private int focusedPage = -1;
    private ItemStack focusedPhoto = ItemStack.EMPTY;
    private PhotographyPhoto.ImageData focusedImage = PhotographyPhoto.ImageData.empty();
    private double focusedZoom = focusMinZoom();
    private double focusedZoomTarget = 1.0d;
    private PhotographyAlbum.Page leftPageCache = PhotographyAlbum.Page.EMPTY;
    private PhotographyAlbum.Page rightPageCache = PhotographyAlbum.Page.EMPTY;
    private PhotographyPhoto.ImageData leftPhotoImage = PhotographyPhoto.ImageData.empty();
    private PhotographyPhoto.ImageData rightPhotoImage = PhotographyPhoto.ImageData.empty();
    private boolean albumHasContent;
    private int leftTooltipHash = Integer.MIN_VALUE;
    private int rightTooltipHash = Integer.MIN_VALUE;
    private java.util.List<Component> leftTooltipCache = java.util.List.of();
    private java.util.List<Component> rightTooltipCache = java.util.List.of();
    private String committedTitle = "";
    private String committedLeftNote = "";
    private String committedRightNote = "";
    private String savedTitle = "";
    private String savedLeftNote = "";
    private String savedRightNote = "";

    public PhotographyAlbumScreen(String handName) {
        super(Component.translatableWithFallback("item.photography.album", "Photo Album"));
        this.hand = parseHand(handName);
        this.spreadIndex = LastAlbumState.spreadIndex(handName);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        leftPos = (width - IMAGE_WIDTH) / 2;
        topPos = (height - IMAGE_HEIGHT) / 2;
        rebuildAlbumWidgets();
        syncTextFields();
    }

    @Override
    public void tick() {
        super.tick();
        if (titleBox != null) {
            titleBox.tick();
        }
        if (leftNoteBox != null) {
            leftNoteBox.tick();
        }
        if (rightNoteBox != null) {
            rightNoteBox.tick();
        }
        if (!focusedPhoto.isEmpty()) {
            focusedZoom += (focusedZoomTarget - focusedZoom) * FOCUS_ZOOM_EASING;
            if (focusedZoomTarget <= 0.0d && focusedZoom <= focusMinZoom() + 0.1d) {
                focusedPhoto = ItemStack.EMPTY;
                focusedPage = -1;
                focusedImage = PhotographyPhoto.ImageData.empty();
                focusedZoom = focusMinZoom();
                focusedZoomTarget = 1.0d;
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(context);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        context.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0f, 0.0f,
                IMAGE_WIDTH, IMAGE_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        if (shouldShowSignButton()) {
            context.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos - 27, topPos + 14, 447.0f, 0.0f,
                    27, 28, TEXTURE_SIZE, TEXTURE_SIZE);
        }
        renderPage(context, mouseX, mouseY, 0, leftPos + LEFT_PHOTO_X, topPos + PHOTO_Y);
        renderPage(context, mouseX, mouseY, 1, leftPos + RIGHT_PHOTO_X, topPos + PHOTO_Y);
        if (addingPage >= 0) {
            renderAddingOverlay(context, mouseX, mouseY);
        } else {
            int leftPage = spreadIndex * 2 + 1;
            int rightPage = Math.min(PhotographyAlbum.MAX_PAGES, spreadIndex * 2 + 2);
            drawPageNumber(context, leftPage, leftPos + LEFT_PAGE_NUMBER_X, topPos + PAGE_NUMBER_Y);
            drawPageNumber(context, rightPage, leftPos + RIGHT_PAGE_NUMBER_X, topPos + PAGE_NUMBER_Y);
        }
        updateNavigationButtons();
        super.extractRenderState(context, mouseX, mouseY, partialTick);
        if (!focusedPhoto.isEmpty()) {
            renderFocusedPhoto(context, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!focusedPhoto.isEmpty()) {
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                closeFocusedPhoto();
            }
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            int button = event.button();
            double mouseX = event.x();
            double mouseY = event.y();
            if (addingPage >= 0) {
                if (button == InputConstants.MOUSE_BUTTON_LEFT && handleInventoryPhotoClick(mouseX, mouseY)) {
                    return true;
                }
                addingPage = -1;
                return true;
            }
            if (handlePhotoClick(mouseX, mouseY, button, 0, leftPos + LEFT_PHOTO_X, topPos + PHOTO_Y)) {
                return true;
            }
            if (handlePhotoClick(mouseX, mouseY, button, 1, leftPos + RIGHT_PHOTO_X, topPos + PHOTO_Y)) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!focusedPhoto.isEmpty()) {
            if (scrollY > 0.0) {
                focusedZoomTarget = Math.min(focusMaxZoom(), focusedZoomTarget * Math.pow(FOCUS_ZOOM_PER_STEP, scrollY * FOCUS_SCROLL_STEP));
            } else {
                double nextTarget = focusedZoomTarget / Math.pow(FOCUS_ZOOM_PER_STEP, Math.abs(scrollY) * FOCUS_SCROLL_STEP);
                if (nextTarget <= focusMinZoom() + 0.001d && focusedZoom <= focusMinZoom() + 0.08d) {
                    closeFocusedPhoto();
                } else {
                    focusedZoomTarget = Math.max(focusMinZoom(), nextTarget);
                }
            }
            return true;
        }
        if (spreadIndex >= 0) {
            if (handlePhotoScroll(mouseX, mouseY, scrollY, 0, leftPos + LEFT_PHOTO_X, topPos + PHOTO_Y)) {
                return true;
            }
            if (handlePhotoScroll(mouseX, mouseY, scrollY, 1, leftPos + RIGHT_PHOTO_X, topPos + PHOTO_Y)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!focusedPhoto.isEmpty() && event.key() == InputConstants.KEY_ESCAPE) {
            closeFocusedPhoto();
            return true;
        }
        if (addingPage >= 0 && event.key() == InputConstants.KEY_ESCAPE) {
            addingPage = -1;
            return true;
        }
        return super.keyPressed(event);
    }

    private void rebuildAlbumWidgets() {
        clearWidgets();
        previousPageButton = addRenderableWidget(new SilentImageButton(leftPos + 12, topPos + 164, 13, 15, PREVIOUS_PAGE, button -> previousSpread(), Component.empty()));
        nextPageButton = addRenderableWidget(new SilentImageButton(leftPos + 273, topPos + 164, 13, 15, NEXT_PAGE, button -> nextSpread(), Component.empty()));
        signButton = addRenderableWidget(new ImageButton(leftPos - 23, topPos + 17, 22, 22,
                SIGN_BUTTON_SPRITES, button -> openSigningScreen(), Component.translatableWithFallback("gui.photography.album.sign", "Sign Album")));
        int coverLeft = leftPos + (IMAGE_WIDTH - 149) / 2;
        titleBox = addRenderableWidget(new PhotographyAlbumTextBox(font, coverLeft + 21, topPos + 73, 108, 9,
                () -> committedTitle,
                value -> {
                    committedTitle = value;
                    if (!syncingFields) {
                        net.blouflin.photography.Photography.LOGGER.debug("[album-title-edit] phase=type focused=true text={}", value);
                        net.blouflin.photography.Photography.LOGGER.debug("[album-edit] field=title phase=local_change chars={} stackReplaced=false", value.length());
                    }
                }, false).setTextColor(0xffb59774).setSelectionColor(0xff8888ff, 0xffbbbbff));

        leftNoteBox = createNoteBox(0);
        rightNoteBox = createNoteBox(1);
    }

    private void previousSpread() {
        commitEdits();
        int old = spreadIndex;
        spreadIndex = Math.max(0, spreadIndex - 1);
        LastAlbumState.setSpreadIndex(hand.name(), spreadIndex);
        addingPage = -1;
        if (old != spreadIndex) {
            setFocused(null);
            playUi(SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.0f);
            syncTextFields();
        }
    }

    private void nextSpread() {
        commitEdits();
        int old = spreadIndex;
        spreadIndex = Math.min((PhotographyAlbum.MAX_PAGES - 1) / 2, spreadIndex + 1);
        LastAlbumState.setSpreadIndex(hand.name(), spreadIndex);
        addingPage = -1;
        if (old != spreadIndex) {
            setFocused(null);
            playUi(SoundEvents.BOOK_PAGE_TURN, 1.0f, 1.0f);
            syncTextFields();
        }
    }

    private void renderPage(GuiGraphicsExtractor context, int mouseX, int mouseY, int side, int x, int y) {
        int page = spreadIndex * 2 + side;
        ItemStack photo = getPagePhoto(page);
        PhotographyPhoto.ImageData image = getPageImage(page);
        boolean hasPhoto = !image.isEmpty();
        boolean focused = page == focusedPage && !focusedPhoto.isEmpty();
        WidgetSprites sprites = hasPhoto ? PHOTO_SLOT : EMPTY_PHOTO_SLOT;
        Identifier slotSprite = sprites.get(true, isInside(mouseX, mouseY, x, y, PHOTO_SIZE, PHOTO_SIZE));
        if (hasPhoto && !focused) {
            PhotographyPhotoRenderCache.drawAlbumPhoto(context, image, x, y, PHOTO_SIZE);
        }
        context.blitSprite(RenderPipelines.GUI_TEXTURED, slotSprite, x, y, PHOTO_SIZE, PHOTO_SIZE);
        if (addingPage < 0 && !focused && hasPhoto && isInside(mouseX, mouseY, x, y, PHOTO_SIZE, PHOTO_SIZE)) {
            context.setComponentTooltipForNextFrame(font, getPhotoTooltip(side, photo, image.hash()), mouseX, mouseY);
        } else if (addingPage < 0 && !hasPhoto && isInside(mouseX, mouseY, x, y, PHOTO_SIZE, PHOTO_SIZE)) {
            context.setTooltipForNextFrame(font, Component.translatableWithFallback("gui.photography.album.add_photograph", "Add Photograph"), mouseX, mouseY);
        }
    }

    private void updateNavigationButtons() {
        if (previousPageButton == null || nextPageButton == null) {
            return;
        }
        previousPageButton.visible = spreadIndex > 0 && addingPage < 0;
        previousPageButton.active = previousPageButton.visible;
        previousPageButton.setX(leftPos + 12);
        previousPageButton.setY(topPos + 164);
        nextPageButton.visible = spreadIndex < (PhotographyAlbum.MAX_PAGES - 1) / 2 && addingPage < 0;
        nextPageButton.active = nextPageButton.visible;
        nextPageButton.setX(leftPos + 273);
        nextPageButton.setY(topPos + 164);
        if (signButton != null) {
            signButton.visible = shouldShowSignButton() && addingPage < 0;
            signButton.active = signButton.visible;
        }
        if (titleBox != null) {
            titleBox.visible = false;
        }
        if (leftNoteBox != null && rightNoteBox != null) {
            leftNoteBox.visible = addingPage < 0;
            rightNoteBox.visible = addingPage < 0;
        }
        net.blouflin.photography.Photography.LOGGER.debug("[album-nav] pageType=spread pageIndex={} prevVisible={} nextVisible={} prev=<{},{}> next=<{},{}>",
                spreadIndex, previousPageButton.visible, nextPageButton.visible,
                previousPageButton.getX(), previousPageButton.getY(), nextPageButton.getX(), nextPageButton.getY());
    }

    private void drawPageNumber(GuiGraphicsExtractor context, int page, int x, int y) {
        String text = Integer.toString(page);
        int drawX = x + 8 - font.width(text) / 2;
        context.text(font, text, drawX, y, 0xffefe4ca, false);
        net.blouflin.photography.Photography.LOGGER.debug("[album-page-number] text={} width={} centerX={} drawX={}",
                text, font.width(text), x + 8, drawX);
    }

    private boolean handlePhotoClick(double mouseX, double mouseY, int button, int side, int x, int y) {
        if (!isInside(mouseX, mouseY, x, y, PHOTO_SIZE, PHOTO_SIZE)) {
            return false;
        }
        int page = spreadIndex * 2 + side;
        ItemStack photo = getPagePhoto(page);
        PhotographyPhoto.ImageData image = getPageImage(page);
        boolean hasPhoto = !image.isEmpty();
        String action;
        if (button == InputConstants.MOUSE_BUTTON_RIGHT && hasPhoto) {
            action = "remove";
            playUi(Photography.PHOTOGRAPH_PLACE, 1.0f, 1.0f);
            setCachedPage(page, cachedPage(page).setPhotograph(ItemStack.EMPTY));
            commitEdits();
        } else if (button == InputConstants.MOUSE_BUTTON_LEFT && !hasPhoto) {
            commitEdits();
            addingPage = page;
            playUi(SoundEvents.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return true;
        } else if (button == InputConstants.MOUSE_BUTTON_LEFT && hasPhoto) {
            openFocusedPhoto(page, photo, image);
            return true;
        } else {
            return true;
        }
        sendAction(page, action);
        return true;
    }

    private boolean handlePhotoScroll(double mouseX, double mouseY, double scrollY, int side, int x, int y) {
        if (scrollY <= 0.0 || !isInside(mouseX, mouseY, x, y, PHOTO_SIZE, PHOTO_SIZE)) {
            return false;
        }
        ItemStack photo = getPagePhoto(spreadIndex * 2 + side);
        PhotographyPhoto.ImageData image = getPageImage(spreadIndex * 2 + side);
        if (image.isEmpty()) {
            return false;
        }
        openFocusedPhoto(spreadIndex * 2 + side, photo, image);
        return true;
    }

    private void renderAddingOverlay(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int overlayX = leftPos + 70;
        int overlayY = topPos + 115;
        net.blouflin.photography.Photography.LOGGER.debug("[album-picker-layout] screen=<{},{}> albumOrigin=<{},{}> tray=<{},{},176,100> clipped={}",
                width, height, leftPos, topPos, overlayX - 8, overlayY - 18, overlayY - 18 + 100 > height);
        context.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, overlayX - 8, overlayY - 18, 0.0f, 188.0f,
                176, 100, TEXTURE_SIZE, TEXTURE_SIZE);
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            SlotRect rect = inventorySlotRect(slot, overlayX, overlayY);
            if (rect == null) {
                continue;
            }
            ItemStack stack = client.player.getInventory().getItem(slot);
            context.item(stack, rect.x(), rect.y());
            context.itemDecorations(font, stack, rect.x(), rect.y());
            if (!stack.isEmpty() && !PhotographyPhoto.isPhotographyPhoto(stack)) {
                context.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, rect.x() - 1, rect.y() - 1, 176.0f, 188.0f,
                        18, 18, TEXTURE_SIZE, TEXTURE_SIZE);
            }
            if (!stack.isEmpty() && isInside(mouseX, mouseY, rect.x(), rect.y(), 16, 16)) {
                context.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }
        }
    }

    private boolean handleInventoryPhotoClick(double mouseX, double mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return false;
        }
        int overlayX = leftPos + 70;
        int overlayY = topPos + 115;
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            SlotRect rect = inventorySlotRect(slot, overlayX, overlayY);
            if (rect == null || !isInside(mouseX, mouseY, rect.x(), rect.y(), 16, 16)) {
                continue;
            }
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (PhotographyPhoto.isPhotographyPhoto(stack)) {
                commitEdits();
                PhotographyAlbum.Page existing = cachedPage(addingPage);
                String note = existing.note().isBlank() ? defaultCaption(stack) : existing.note();
                setCachedPage(addingPage, existing.setPhotograph(stack).setNote(note));
                sendAction(addingPage, "insertSlot:" + slot);
                playUi(Photography.PHOTOGRAPH_PLACE, 0.7f, 1.1f);
                addingPage = -1;
            }
            return true;
        }
        return false;
    }

    private PhotographyAlbumTextBox createNoteBox(int side) {
        int pageX = side == 0 ? 0 : 140;
        final int boxSide = side;
        PhotographyAlbumTextBox box = addRenderableWidget(new PhotographyAlbumTextBox(font,
                leftPos + pageX + NOTE_X, topPos + NOTE_Y, NOTE_WIDTH, NOTE_HEIGHT,
                () -> boxSide == 0 ? committedLeftNote : committedRightNote,
                value -> {
                    if (boxSide == 0) {
                        committedLeftNote = value;
                    } else {
                        committedRightNote = value;
                    }
                    if (!syncingFields && spreadIndex >= 0) {
                        net.blouflin.photography.Photography.LOGGER.debug("[album-caption-layout] page={} box={},{},{},{} lines=pending maxLines={} chars={}",
                                spreadIndex * 2 + boxSide, leftPos + pageX + NOTE_X, topPos + NOTE_Y,
                                NOTE_WIDTH, NOTE_HEIGHT, NOTE_HEIGHT / font.lineHeight, value.length());
                        net.blouflin.photography.Photography.LOGGER.debug("[album-edit] field=caption phase=local_change chars={} stackReplaced=false", value.length());
                    }
                }, true).setTextColor(0xffb59774).setSelectionColor(0xff8888ff, 0xffbbbbff));
        return box;
    }

    private void syncTextFields() {
        if (titleBox == null || leftNoteBox == null || rightNoteBox == null) {
            return;
        }
        syncingFields = true;
        ItemStack album = getAlbumStack();
        titleBox.visible = false;
        committedTitle = PhotographyAlbum.getTitle(album);
        savedTitle = committedTitle;
        titleBox.setText(committedTitle);
        leftNoteBox.visible = addingPage < 0;
        rightNoteBox.visible = addingPage < 0;
        leftPageCache = PhotographyAlbum.getPage(album, spreadIndex * 2).orElse(PhotographyAlbum.Page.EMPTY);
        rightPageCache = PhotographyAlbum.getPage(album, spreadIndex * 2 + 1).orElse(PhotographyAlbum.Page.EMPTY);
        committedLeftNote = leftPageCache.note();
        committedRightNote = rightPageCache.note();
        leftPhotoImage = PhotographyPhoto.getImage(leftPageCache.photograph());
        rightPhotoImage = PhotographyPhoto.getImage(rightPageCache.photograph());
        albumHasContent = computeVisibleContentState(album);
        invalidateTooltipCaches();
        savedLeftNote = committedLeftNote;
        savedRightNote = committedRightNote;
        leftNoteBox.setText(committedLeftNote);
        rightNoteBox.setText(committedRightNote);
        syncingFields = false;
    }

    @Override
    public void onClose() {
        commitEdits();
        super.onClose();
    }

    private void commitEdits() {
        if (leftNoteBox != null && rightNoteBox != null && spreadIndex >= 0) {
            String left = leftNoteBox.getText();
            if (!left.equals(savedLeftNote)) {
                leftPageCache = leftPageCache.setNote(left);
                albumHasContent = computeCachedContentState();
                sendAction(spreadIndex * 2, "note:" + left);
                net.blouflin.photography.Photography.LOGGER.debug("[album-edit] field=caption phase=commit chars={} stackReplaced=false", left.length());
                savedLeftNote = left;
            }
            String right = rightNoteBox.getText();
            if (!right.equals(savedRightNote)) {
                rightPageCache = rightPageCache.setNote(right);
                albumHasContent = computeCachedContentState();
                sendAction(spreadIndex * 2 + 1, "note:" + right);
                net.blouflin.photography.Photography.LOGGER.debug("[album-edit] field=caption phase=commit chars={} stackReplaced=false", right.length());
                savedRightNote = right;
            }
        }
    }

    private PhotographyAlbum.Page cachedPage(int page) {
        if (page == spreadIndex * 2) {
            return leftPageCache;
        }
        if (page == spreadIndex * 2 + 1) {
            return rightPageCache;
        }
        return PhotographyAlbum.Page.EMPTY;
    }

    private void setCachedPage(int page, PhotographyAlbum.Page pageData) {
        if (page == spreadIndex * 2) {
            leftPageCache = pageData;
            leftPhotoImage = PhotographyPhoto.getImage(pageData.photograph());
            committedLeftNote = pageData.note();
            savedLeftNote = pageData.note();
            if (leftNoteBox != null) {
                leftNoteBox.setText(pageData.note());
            }
        } else if (page == spreadIndex * 2 + 1) {
            rightPageCache = pageData;
            rightPhotoImage = PhotographyPhoto.getImage(pageData.photograph());
            committedRightNote = pageData.note();
            savedRightNote = pageData.note();
            if (rightNoteBox != null) {
                rightNoteBox.setText(pageData.note());
            }
        }
        albumHasContent = computeCachedContentState();
        invalidateTooltipCaches();
    }

    private static String defaultCaption(ItemStack photograph) {
        net.minecraft.world.item.component.CustomData customData = photograph.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return "";
        }
        net.minecraft.nbt.CompoundTag metadata = customData.copyTag().getCompoundOrEmpty(PhotographyPhoto.SHOT_METADATA_TAG);
        String taken = metadata.getStringOr("takenTime", "");
        if (taken.isBlank()) {
            return "";
        }
        String author = metadata.getStringOr("visiblePhotographerName", "");
        if (metadata.getBooleanOr("anonymousPhotographer", false) || author.isBlank()) {
            return "Taken " + taken;
        }
        return "Taken " + taken + "\nby " + author;
    }

    private void sendAction(int page, String action) {
        ClientPlayNetworking.send(new PhotographyAlbumActionPayload(hand.name(), page, action));
    }

    void commitTitleFromSigning(String value) {
        PhotographyAlbum.setTitle(getAlbumStack(), value);
        sendAction(0, "title:" + value);
        savedTitle = value;
        committedTitle = value;
        LastAlbumState.setDraftTitle(hand.name(), value);
        net.blouflin.photography.Photography.LOGGER.debug("[album-edit] field=title phase=commit chars={} stackReplaced=false", value.length());
    }

    void rememberTitleDraft(String value) {
        LastAlbumState.setDraftTitle(hand.name(), value);
    }

    private void openSigningScreen() {
        String title = LastAlbumState.draftTitle(hand.name(), PhotographyAlbum.getTitle(getAlbumStack()));
        Minecraft.getInstance().setScreenAndShow(new PhotographyAlbumSigningScreen(this, title));
    }

    private boolean shouldShowSignButton() {
        return albumHasContent;
    }

    private void renderFocusedPhoto(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        context.fill(0, 0, width, height, 0x99000000);
        int defaultSize = Math.max(1, (int) (height * 0.8d));
        int size = Math.max(1, (int) (defaultSize * focusedZoom));
        int x = (width - size) / 2;
        int y = (height - size) / 2;
        PhotographyPhotoRenderCache.drawAlbumPhoto(context, focusedImage, x, y, size);
    }

    private void openFocusedPhoto(int page, ItemStack photo, PhotographyPhoto.ImageData image) {
        playUi(Photography.PHOTOGRAPH_RUSTLE, 1.0f, 1.0f);
        focusedPage = page;
        focusedPhoto = photo.copy();
        focusedImage = image;
        focusedZoom = focusMinZoom();
        focusedZoomTarget = 1.0d;
    }

    private void closeFocusedPhoto() {
        focusedZoomTarget = 0.0d;
    }

    private static double focusMinZoom() {
        return 1.0d / Math.pow(FOCUS_ZOOM_PER_STEP, 4);
    }

    private static double focusMaxZoom() {
        return Math.pow(FOCUS_ZOOM_PER_STEP, 4);
    }

    private void playUi(SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private void playUi(Holder<SoundEvent> sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }

    private void drawCenteredText(GuiGraphicsExtractor context, Component component, int centerX, int y, int color) {
        context.text(font, component, centerX - font.width(component) / 2, y, color, false);
    }

    private SlotRect inventorySlotRect(int slot, int overlayX, int overlayY) {
        if (slot >= 9 && slot < 36) {
            int index = slot - 9;
            return new SlotRect(overlayX + (index % 9) * 18, overlayY + (index / 9) * 18, slot);
        }
        if (slot >= 0 && slot < 9) {
            return new SlotRect(overlayX + slot * 18, overlayY + 58, slot);
        }
        return null;
    }

    private ItemStack getPagePhoto(int page) {
        if (page == spreadIndex * 2) {
            return leftPageCache.photograph();
        }
        if (page == spreadIndex * 2 + 1) {
            return rightPageCache.photograph();
        }
        return PhotographyAlbum.getPage(getAlbumStack(), page).map(PhotographyAlbum.Page::photograph).orElse(ItemStack.EMPTY);
    }

    private PhotographyPhoto.ImageData getPageImage(int page) {
        if (page == spreadIndex * 2) {
            return leftPhotoImage;
        }
        if (page == spreadIndex * 2 + 1) {
            return rightPhotoImage;
        }
        return PhotographyPhoto.getImage(getPagePhoto(page));
    }

    private ItemStack getAlbumStack() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = client.player.getItemInHand(hand);
        if (PhotographyAlbum.isAlbum(stack)) {
            return stack;
        }
        return ItemStack.EMPTY;
    }

    private java.util.List<Component> getPhotoTooltip(int side, ItemStack photo, int hash) {
        if (side == 0) {
            if (leftTooltipHash != hash) {
                leftTooltipHash = hash;
                leftTooltipCache = buildPhotoTooltip(photo);
            }
            return leftTooltipCache;
        }
        if (rightTooltipHash != hash) {
            rightTooltipHash = hash;
            rightTooltipCache = buildPhotoTooltip(photo);
        }
        return rightTooltipCache;
    }

    private java.util.List<Component> buildPhotoTooltip(ItemStack photo) {
        java.util.List<Component> tooltip = new java.util.ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), photo));
        tooltip.add(Component.translatableWithFallback("gui.photography.album.left_click_or_scroll_up_to_view", "Left Click or Scroll Up to View"));
        tooltip.add(Component.translatableWithFallback("gui.photography.album.right_click_to_remove", "Right Click to Remove"));
        return java.util.List.copyOf(tooltip);
    }

    private void invalidateTooltipCaches() {
        leftTooltipHash = Integer.MIN_VALUE;
        rightTooltipHash = Integer.MIN_VALUE;
        leftTooltipCache = java.util.List.of();
        rightTooltipCache = java.util.List.of();
    }

    private boolean computeVisibleContentState(ItemStack album) {
        return !committedLeftNote.isBlank()
                || !committedRightNote.isBlank()
                || !leftPhotoImage.isEmpty()
                || !rightPhotoImage.isEmpty()
                || PhotographyAlbum.hasContent(album);
    }

    private boolean computeCachedContentState() {
        return !leftPageCache.isEmpty() || !rightPageCache.isEmpty() || PhotographyAlbum.hasContent(getAlbumStack());
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private static InteractionHand parseHand(String handName) {
        try {
            return InteractionHand.valueOf(handName);
        } catch (IllegalArgumentException ignored) {
            return InteractionHand.MAIN_HAND;
        }
    }

    private static WidgetSprites sprites(String path) {
        Identifier normal = Identifier.fromNamespaceAndPath("photography", path);
        return new WidgetSprites(normal, Identifier.fromNamespaceAndPath("photography", path + "_highlighted"));
    }

    private static class SilentImageButton extends ImageButton {
        public SilentImageButton(int x, int y, int width, int height, WidgetSprites sprites, OnPress onPress, Component message) {
            super(x, y, width, height, sprites, onPress, message);
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
        }
    }

    private record SlotRect(int x, int y, int slot) {
    }

    private static final class LastAlbumState {
        private static final java.util.Map<String, Integer> SPREADS = new java.util.HashMap<>();
        private static final java.util.Map<String, String> DRAFT_TITLES = new java.util.HashMap<>();

        private static int spreadIndex(String hand) {
            return SPREADS.getOrDefault(hand, 0);
        }

        private static void setSpreadIndex(String hand, int spreadIndex) {
            SPREADS.put(hand, spreadIndex);
        }

        private static String draftTitle(String hand, String fallback) {
            return DRAFT_TITLES.getOrDefault(hand, fallback);
        }

        private static void setDraftTitle(String hand, String title) {
            DRAFT_TITLES.put(hand, title);
        }
    }
}
