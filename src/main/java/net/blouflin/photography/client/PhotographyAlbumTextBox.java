package net.blouflin.photography.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PhotographyAlbumTextBox extends AbstractWidget {
    private final Font font;
    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private final boolean multiline;
    private final TextFieldHelper helper;
    private int textColor = 0xffb59774;
    private int selectionColor = 0xff8888ff;
    private int selectionUnfocusedColor = 0xffbbbbff;
    private int frameTick;
    private String text;
    private DisplayCache cache = DisplayCache.EMPTY;
    private boolean cacheDirty = true;
    private long lastClickTime;
    private int lastClickIndex = -1;

    public PhotographyAlbumTextBox(Font font, int x, int y, int width, int height,
                                   Supplier<String> getter, Consumer<String> setter, boolean multiline) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.getter = getter;
        this.setter = setter;
        this.multiline = multiline;
        this.text = getter.get();
        this.helper = new TextFieldHelper(this::getText, this::setText,
                TextFieldHelper.createClipboardGetter(Minecraft.getInstance()),
                TextFieldHelper.createClipboardSetter(Minecraft.getInstance()),
                this::fits);
        this.helper.setCursorToEnd();
    }

    public PhotographyAlbumTextBox setTextColor(int color) {
        this.textColor = color;
        this.cacheDirty = true;
        return this;
    }

    public PhotographyAlbumTextBox setSelectionColor(int focused, int unfocused) {
        this.selectionColor = focused;
        this.selectionUnfocusedColor = unfocused;
        return this;
    }

    public void tick() {
        frameTick++;
    }

    public String getText() {
        return text;
    }

    public void setText(String value) {
        String safe = value == null ? "" : value;
        if (!multiline) {
            safe = safe.replace("\n", "");
        }
        this.text = safe;
        this.setter.accept(this.text);
        this.cacheDirty = true;
    }

    public void setCursorToEnd() {
        helper.setCursorToEnd();
        cacheDirty = true;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        String latest = getter.get();
        if (!latest.equals(text)) {
            text = latest;
            helper.setCursorPos(Math.min(helper.getCursorPos(), text.length()), false);
            cacheDirty = true;
        }
        DisplayCache display = displayCache();
        for (LineInfo line : display.lines()) {
            context.text(font, line.text(), getX() + line.x(), getY() + line.y(), textColor, false);
        }
        renderSelection(context, display);
        if (isFocused() && frameTick / 6 % 2 == 0) {
            renderCursor(context, display);
        }
    }

    private void renderSelection(GuiGraphicsExtractor context, DisplayCache display) {
        if (helper.getCursorPos() == helper.getSelectionPos()) {
            return;
        }
        int color = isFocused() ? selectionColor : selectionUnfocusedColor;
        int start = Math.min(helper.getCursorPos(), helper.getSelectionPos());
        int end = Math.max(helper.getCursorPos(), helper.getSelectionPos());
        for (LineInfo line : display.lines()) {
            int selectionStart = Math.max(start, line.start());
            int selectionEnd = Math.min(end, line.end());
            if (selectionStart >= selectionEnd) {
                continue;
            }
            int x0 = getX() + line.x() + font.width(text.substring(line.start(), selectionStart));
            int x1 = getX() + line.x() + font.width(text.substring(line.start(), selectionEnd));
            int y = getY() + line.y();
            context.fill(x0, y - 1, x1, y + font.lineHeight, color);
        }
    }

    private void renderCursor(GuiGraphicsExtractor context, DisplayCache display) {
        Cursor cursor = display.cursor();
        int cursorX = getX() + cursor.x();
        int cursorY = getY() + cursor.y();
        if (cursor.atEnd()) {
            context.text(font, "_", cursorX, cursorY, textColor, false);
        } else {
            context.fill(cursorX, cursorY - 1, cursorX + 1, cursorY + font.lineHeight, textColor);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (visible && active && isMouseOver(event.x(), event.y()) && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            setFocused(true);
            int index = displayCache().indexAt((int) event.x() - getX(), (int) event.y() - getY(), font);
            long now = System.currentTimeMillis();
            if (index == lastClickIndex && now - lastClickTime < 250L) {
                selectWord(index);
            } else {
                helper.setCursorPos(index, hasShiftDown());
            }
            lastClickIndex = index;
            lastClickTime = now;
            cacheDirty = true;
            net.blouflin.photography.Photography.LOGGER.debug("[album-title-edit] phase=focus focused=true text={}", text);
            return true;
        }
        setFocused(false);
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT && isFocused()) {
            helper.setCursorPos(displayCache().indexAt((int) mouseX - getX(), (int) mouseY - getY(), font), true);
            cacheDirty = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isFocused()) {
            return false;
        }
        int key = event.key();
        TextFieldHelper.CursorStep step = hasControlDown() ? TextFieldHelper.CursorStep.WORD : TextFieldHelper.CursorStep.CHARACTER;
        boolean handled = true;
        if (key == InputConstants.KEY_UP) {
            changeLine(-1);
        } else if (key == InputConstants.KEY_DOWN) {
            changeLine(1);
        } else if (key == InputConstants.KEY_HOME) {
            keyHome();
        } else if (key == InputConstants.KEY_END) {
            keyEnd();
        } else if (key == InputConstants.KEY_BACKSPACE) {
            helper.removeFromCursor(-1, step);
        } else if (key == InputConstants.KEY_DELETE) {
            helper.removeFromCursor(1, step);
        } else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            if (multiline) {
                helper.insertText(CommonComponents.NEW_LINE.getString());
            } else {
                setFocused(false);
            }
        } else {
            handled = helper.keyPressed(event);
        }
        if (handled) {
            cacheDirty = true;
        }
        return handled;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!isFocused() || !event.isAllowedChatCharacter()) {
            return false;
        }
        boolean typed = helper.charTyped(event);
        if (typed) {
            cacheDirty = true;
        }
        return typed;
    }

    private boolean fits(String value) {
        if (!multiline && value.contains("\n")) {
            return false;
        }
        return buildCache(value, helper.getCursorPos(), helper.getSelectionPos()).lines().size() <= maxLines();
    }

    private DisplayCache displayCache() {
        if (cacheDirty) {
            cache = buildCache(text, helper.getCursorPos(), helper.getSelectionPos());
            cacheDirty = false;
        }
        return cache;
    }

    private DisplayCache buildCache(String value, int cursor, int selection) {
        List<LineInfo> lines = wrap(value);
        Cursor cursorPos = cursorFor(lines, value, Math.max(0, Math.min(cursor, value.length())));
        return new DisplayCache(value, lines, cursorPos, selection);
    }

    private List<LineInfo> wrap(String value) {
        ArrayList<LineInfo> lines = new ArrayList<>();
        if (value.isEmpty()) {
            lines.add(new LineInfo("", 0, 0, centeredLineX("_"), 0));
            return lines;
        }
        int index = 0;
        int lineY = 0;
        while (index <= value.length()) {
            int newline = value.indexOf('\n', index);
            int logicalEnd = newline >= 0 ? newline : value.length();
            if (logicalEnd == index) {
                lines.add(new LineInfo("", index, index, centeredLineX(""), lineY));
                lineY += font.lineHeight;
            } else {
                int lineStart = index;
                while (lineStart < logicalEnd) {
                    String remaining = value.substring(lineStart, logicalEnd);
                    String slice = font.plainSubstrByWidth(remaining, getWidth());
                    if (slice.isEmpty()) {
                        slice = remaining.substring(0, remaining.offsetByCodePoints(0, 1));
                    }
                    int end = lineStart + slice.length();
                    if (end < logicalEnd) {
                        int breakAt = Math.max(slice.lastIndexOf(' '), slice.lastIndexOf('\t'));
                        if (breakAt > 0) {
                            end = lineStart + breakAt;
                            slice = value.substring(lineStart, end);
                        }
                    }
                    lines.add(new LineInfo(slice, lineStart, end, centeredLineX(slice), lineY));
                    lineY += font.lineHeight;
                    lineStart = end;
                    while (lineStart < logicalEnd && Character.isWhitespace(value.charAt(lineStart)) && value.charAt(lineStart) != '\n') {
                        lineStart++;
                    }
                }
            }
            if (newline < 0) {
                break;
            }
            index = newline + 1;
            if (index == value.length()) {
                lines.add(new LineInfo("", index, index, centeredLineX(""), lineY));
                break;
            }
        }
        return lines;
    }

    private Cursor cursorFor(List<LineInfo> lines, String value, int cursorIndex) {
        if (lines.isEmpty()) {
            return new Cursor(centeredLineX("_"), 0, true);
        }
        for (LineInfo line : lines) {
            if (cursorIndex >= line.start() && cursorIndex <= line.end()) {
                int x = line.x() + font.width(value.substring(line.start(), cursorIndex));
                return new Cursor(x, line.y(), cursorIndex == value.length());
            }
        }
        LineInfo last = lines.get(lines.size() - 1);
        return new Cursor(last.x() + font.width(last.text()), last.y(), true);
    }

    private void selectWord(int index) {
        helper.setSelectionRange(StringSplitter.getWordPosition(text, -1, index, false),
                StringSplitter.getWordPosition(text, 1, index, false));
    }

    private void changeLine(int delta) {
        helper.setCursorPos(displayCache().changeLine(helper.getCursorPos(), delta), hasShiftDown());
    }

    private void keyHome() {
        if (hasControlDown()) {
            helper.setCursorToStart(hasShiftDown());
        } else {
            helper.setCursorPos(displayCache().lineStart(helper.getCursorPos()), hasShiftDown());
        }
    }

    private void keyEnd() {
        if (hasControlDown()) {
            helper.setCursorToEnd(hasShiftDown());
        } else {
            helper.setCursorPos(displayCache().lineEnd(helper.getCursorPos()), hasShiftDown());
        }
    }

    private static boolean hasShiftDown() {
        Minecraft client = Minecraft.getInstance();
        return InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_RSHIFT);
    }

    private static boolean hasControlDown() {
        Minecraft client = Minecraft.getInstance();
        return InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_RCONTROL);
    }

    private int maxLines() {
        return Math.max(1, getHeight() / font.lineHeight);
    }

    private int centeredLineX(String line) {
        return (getWidth() - font.width(line)) / 2;
    }

    private record Cursor(int x, int y, boolean atEnd) {
    }

    private record LineInfo(String text, int start, int end, int x, int y) {
    }

    private record DisplayCache(String text, List<LineInfo> lines, Cursor cursor, int selection) {
        private static final DisplayCache EMPTY = new DisplayCache("", List.of(new LineInfo("", 0, 0, 0, 0)), new Cursor(0, 0, true), 0);

        private int indexAt(int x, int y, Font font) {
            int lineIndex = Math.max(0, Math.min(lines.size() - 1, y / font.lineHeight));
            LineInfo line = lines.get(lineIndex);
            int localX = x - line.x();
            int relative = font.getSplitter().plainIndexAtWidth(line.text(), localX, net.minecraft.network.chat.Style.EMPTY);
            return Math.max(0, Math.min(text.length(), line.start() + relative));
        }

        private int changeLine(int cursorIndex, int delta) {
            int lineIndex = lineFor(cursorIndex);
            int target = lineIndex + delta;
            if (target < 0 || target >= lines.size()) {
                return cursorIndex;
            }
            LineInfo current = lines.get(lineIndex);
            LineInfo next = lines.get(target);
            int column = Math.max(0, cursorIndex - current.start());
            return Math.min(next.end(), next.start() + column);
        }

        private int lineStart(int cursorIndex) {
            return lines.get(lineFor(cursorIndex)).start();
        }

        private int lineEnd(int cursorIndex) {
            return lines.get(lineFor(cursorIndex)).end();
        }

        private int lineFor(int cursorIndex) {
            for (int i = 0; i < lines.size(); i++) {
                LineInfo line = lines.get(i);
                if (cursorIndex >= line.start() && cursorIndex <= line.end()) {
                    return i;
                }
            }
            return Math.max(0, lines.size() - 1);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
