package com.reiko.weather.client.guide;

import com.reiko.weather.ReikosWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Weather Almanac: a two-page book. The home spread lists the chapters; a chapter shows its entries
 * on the left and the selected entry on the right, with recipes drawn as crafting grids. Long entries
 * flow onto extra pages automatically.
 */
public class GuideScreen extends Screen {
    private static final ResourceLocation BOOK = ReikosWeather.id("textures/gui/almanac.png");
    private static final int W = 320, H = 200;
    private static final int PAGE_W = 128, PAGE_TOP = 16, PAGE_BOTTOM = 178;
    private static final int TEXT = 0x3B2A1A, TITLE = 0x6A2E0A, FAINT = 0x8A7458, HOVER = 0xB0501A;
    private static final int ROW = 13;

    private static GuideBook book;
    private static int lastChapter = -1, lastEntry, lastPage;

    private int chapter = -1;
    private int entry;
    private int page;
    private List<View> views = List.of();
    private int x0, y0;
    private ItemStack hovered = ItemStack.EMPTY;

    /** One rendered page of an entry: an optional header (items, recipe) and lines of text. */
    private record View(@Nullable GuideBook.Page source, boolean showTitle, List<FormattedCharSequence> lines) {}

    public GuideScreen() {
        super(Component.translatable("item.reikos_weather.weather_almanac"));
    }

    @Override
    protected void init() {
        // reload every time so resource-pack edits show up without restarting
        book = GuideBook.load();
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;
        if (lastChapter >= 0 && lastChapter < book.chapters().size()) {
            openChapter(lastChapter, lastEntry);
            page = Math.min(lastPage, Math.max(0, views.size() - 1));
        }
    }

    private int leftX() { return x0 + 18; }
    private int rightX() { return x0 + W / 2 + 10; }

    private void openChapter(int index, int entryIndex) {
        chapter = index;
        GuideBook.Chapter c = book.chapters().get(index);
        entry = Math.max(0, Math.min(entryIndex, c.entries().size() - 1));
        page = 0;
        layout();
    }

    /** Splits the selected entry into pages that fit. */
    private void layout() {
        List<View> out = new ArrayList<>();
        if (chapter < 0 || book.chapters().get(chapter).entries().isEmpty()) {
            views = out;
            return;
        }
        GuideBook.Entry e = book.chapters().get(chapter).entries().get(entry);
        boolean first = true;
        for (GuideBook.Page p : e.pages()) {
            int top = PAGE_TOP + (first ? 14 : 0) + header(p);
            int fit = Math.max(1, (PAGE_BOTTOM - 12 - top) / 9);
            List<FormattedCharSequence> lines = font.split(Component.literal(p.text()), PAGE_W);
            List<FormattedCharSequence> chunk = new ArrayList<>(lines.subList(0, Math.min(fit, lines.size())));
            out.add(new View(p, first, chunk));
            int at = chunk.size();
            int more = (PAGE_BOTTOM - 12 - PAGE_TOP) / 9;
            while (at < lines.size()) {
                out.add(new View(null, false, new ArrayList<>(lines.subList(at, Math.min(lines.size(), at + more)))));
                at += more;
            }
            first = false;
        }
        views = out;
    }

    private int header(GuideBook.Page p) {
        int h = 0;
        if (!p.items().isEmpty()) h += 22;
        if (p.recipe() != null && recipe(p.recipe()) != null) h += 62;
        return h;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hovered = ItemStack.EMPTY;
        g.blit(BOOK, x0, y0, 0, 0, W, H, 512, 256);
        if (chapter < 0) {
            renderHome(g, mouseX, mouseY);
        } else {
            renderChapter(g, mouseX, mouseY);
        }
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
    }

    /** Where each chapter's row sits on the contents spread: under the intro on the left, then on down the right page. */
    private List<int[]> chapterSlots() {
        List<int[]> slots = new ArrayList<>();
        int introLines = Math.min(6, font.split(Component.literal(book.intro()), PAGE_W).size());
        int x = leftX(), y = y0 + PAGE_TOP + 14 + introLines * 9 + 8;
        for (int i = 0; i < book.chapters().size(); i++) {
            if (y + ROW > y0 + PAGE_BOTTOM && x == leftX()) {
                x = rightX();
                y = y0 + PAGE_TOP;
            }
            slots.add(new int[]{x, y});
            y += ROW;
        }
        return slots;
    }

    private void renderHome(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftX(), y = y0 + PAGE_TOP;
        g.drawString(font, Component.literal(book.title()).withStyle(s -> s.withBold(true)), x, y, TITLE, false);
        y += 14;
        List<FormattedCharSequence> intro = font.split(Component.literal(book.intro()), PAGE_W);
        for (int i = 0; i < Math.min(6, intro.size()); i++) {
            g.drawString(font, intro.get(i), x, y, TEXT, false);
            y += 9;
        }
        List<int[]> slots = chapterSlots();
        for (int i = 0; i < book.chapters().size(); i++) {
            GuideBook.Chapter c = book.chapters().get(i);
            int cx = slots.get(i)[0], cy = slots.get(i)[1];
            boolean over = mouseX >= cx && mouseX < cx + PAGE_W && mouseY >= cy - 2 && mouseY < cy + ROW - 2;
            smallItem(g, c.icon(), cx, cy - 2);
            g.drawString(font, fit(c.title(), PAGE_W - 4), cx + 14, cy + 1, over ? HOVER : TEXT, false);
        }
    }

    private void renderChapter(GuiGraphics g, int mouseX, int mouseY) {
        GuideBook.Chapter c = book.chapters().get(chapter);
        int x = leftX(), y = y0 + PAGE_TOP;
        smallItem(g, c.icon(), x, y - 2);
        g.drawString(font, Component.literal(fit(c.title(), PAGE_W - 24)).withStyle(s -> s.withBold(true)), x + 14, y + 1, TITLE, false);
        y += 16;
        for (int i = 0; i < c.entries().size(); i++) {
            GuideBook.Entry e = c.entries().get(i);
            boolean over = mouseX >= x && mouseX < x + PAGE_W && mouseY >= y - 2 && mouseY < y + ROW - 2;
            smallItem(g, e.icon(), x, y - 2);
            int color = i == entry ? TITLE : over ? HOVER : TEXT;
            g.drawString(font, fit((i == entry ? "» " : "") + e.title(), PAGE_W - 4), x + 14, y + 1, color, false);
            y += ROW;
        }
        // back to the contents
        boolean overBack = mouseX >= x && mouseX < x + 60 && mouseY >= y0 + PAGE_BOTTOM - 6 && mouseY < y0 + PAGE_BOTTOM + 4;
        g.drawString(font, Component.translatable("screen.reikos_weather.almanac.back"), x, y0 + PAGE_BOTTOM - 5, overBack ? HOVER : FAINT, false);

        if (views.isEmpty()) {
            return;
        }
        page = Math.max(0, Math.min(page, views.size() - 1));
        View view = views.get(page);
        int rx = rightX(), ry = y0 + PAGE_TOP;
        if (view.showTitle()) {
            GuideBook.Entry e = c.entries().get(entry);
            g.drawString(font, Component.literal(e.title()).withStyle(s -> s.withBold(true)), rx, ry, TITLE, false);
            ry += 14;
        }
        if (view.source() != null) {
            GuideBook.Page p = view.source();
            if (!p.items().isEmpty()) {
                int ix = rx;
                for (ItemStack stack : p.items()) {
                    item(g, stack, ix, ry, mouseX, mouseY);
                    ix += 18;
                }
                ry += 22;
            }
            if (p.recipe() != null) {
                RecipeHolder<?> holder = recipe(p.recipe());
                if (holder != null) {
                    drawRecipe(g, holder.value(), rx + 8, ry + 2, mouseX, mouseY);
                    ry += 62;
                }
            }
        }
        for (FormattedCharSequence line : view.lines()) {
            g.drawString(font, line, rx, ry, TEXT, false);
            ry += 9;
        }
        // page arrows
        if (views.size() > 1) {
            String number = (page + 1) + " / " + views.size();
            int ny = y0 + PAGE_BOTTOM - 5;
            g.drawString(font, number, rx + PAGE_W / 2 - font.width(number) / 2, ny, FAINT, false);
            if (page > 0) g.drawString(font, "◀", rx, ny, overPrev(mouseX, mouseY) ? HOVER : TEXT, false);
            if (page < views.size() - 1) g.drawString(font, "▶", rx + PAGE_W - 8, ny, overNext(mouseX, mouseY) ? HOVER : TEXT, false);
        }
    }

    /** Cuts a title down to fit a width, with an ellipsis. */
    private String fit(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }

    private boolean overPrev(double mx, double my) {
        int rx = rightX(), ny = y0 + PAGE_BOTTOM - 5;
        return mx >= rx - 2 && mx < rx + 12 && my >= ny - 3 && my < ny + 10;
    }

    private boolean overNext(double mx, double my) {
        int rx = rightX() + PAGE_W - 8, ny = y0 + PAGE_BOTTOM - 5;
        return mx >= rx - 4 && mx < rx + 10 && my >= ny - 3 && my < ny + 10;
    }

    private void drawRecipe(GuiGraphics g, Recipe<?> recipe, int x, int y, int mouseX, int mouseY) {
        long tick = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        List<Ingredient> ingredients = recipe.getIngredients();
        int w = 3;
        if (recipe instanceof ShapedRecipe shaped) {
            w = shaped.getWidth();
        }
        for (int i = 0; i < 9; i++) {
            g.fill(x + (i % 3) * 18, y + (i / 3) * 18, x + (i % 3) * 18 + 17, y + (i / 3) * 18 + 17, 0x40705030);
        }
        if (recipe instanceof AbstractCookingRecipe) {
            if (!ingredients.isEmpty()) item(g, cycle(ingredients.get(0), tick), x + 18, y + 18, mouseX, mouseY);
            smallItem(g, new ItemStack(net.minecraft.world.item.Items.FURNACE), x + 20, y + 38);
        } else {
            for (int i = 0; i < ingredients.size(); i++) {
                int col = i % w, row = i / w;
                item(g, cycle(ingredients.get(i), tick), x + col * 18, y + row * 18, mouseX, mouseY);
            }
        }
        g.drawString(font, "→", x + 60, y + 22, TEXT, false);
        var level = Minecraft.getInstance().level;
        if (level != null) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            g.fill(x + 74, y + 17, x + 92, y + 35, 0x40705030);
            item(g, result, x + 75, y + 18, mouseX, mouseY);
        }
    }

    private static ItemStack cycle(Ingredient ingredient, long tick) {
        ItemStack[] options = ingredient.getItems();
        return options.length == 0 ? ItemStack.EMPTY : options[(int) (tick / 20 % options.length)];
    }

    private void item(GuiGraphics g, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        if (stack.isEmpty()) return;
        g.renderItem(stack, x, y);
        g.renderItemDecorations(font, stack, x, y);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            hovered = stack;
        }
    }

    private void smallItem(GuiGraphics g, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    @Nullable
    private static RecipeHolder<?> recipe(ResourceLocation id) {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getRecipeManager().byKey(id).orElse(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (chapter < 0) {
                List<int[]> slots = chapterSlots();
                for (int i = 0; i < book.chapters().size(); i++) {
                    int cx = slots.get(i)[0], cy = slots.get(i)[1];
                    if (mouseX >= cx && mouseX < cx + PAGE_W && mouseY >= cy - 2 && mouseY < cy + ROW - 2) {
                        openChapter(i, 0);
                        click();
                        return true;
                    }
                }
            } else {
                GuideBook.Chapter c = book.chapters().get(chapter);
                int x = leftX(), y = y0 + PAGE_TOP + 16;
                for (int i = 0; i < c.entries().size(); i++) {
                    if (mouseX >= x && mouseX < x + PAGE_W && mouseY >= y - 2 && mouseY < y + ROW - 2) {
                        entry = i;
                        page = 0;
                        layout();
                        click();
                        return true;
                    }
                    y += ROW;
                }
                if (mouseX >= x && mouseX < x + 60 && mouseY >= y0 + PAGE_BOTTOM - 6 && mouseY < y0 + PAGE_BOTTOM + 4) {
                    chapter = -1;
                    click();
                    return true;
                }
                if (overPrev(mouseX, mouseY) && page > 0) {
                    page--;
                    click();
                    return true;
                }
                if (overNext(mouseX, mouseY) && page < views.size() - 1) {
                    page++;
                    click();
                    return true;
                }
            }
        } else if (button == 1 && chapter >= 0) {
            chapter = -1;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (chapter >= 0 && !views.isEmpty()) {
            page = Math.max(0, Math.min(views.size() - 1, page + (scrollY < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    public void removed() {
        lastChapter = chapter;
        lastEntry = entry;
        lastPage = page;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
