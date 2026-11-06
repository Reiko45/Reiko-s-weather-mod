package com.reiko.weather.client.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reiko.weather.ReikosWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The almanac's contents, read from {@code assets/reikos_weather/guide/<language>.json} (falling back to
 * English), so resource packs can rewrite or translate it.
 */
public record GuideBook(String title, String intro, List<Chapter> chapters) {
    public record Chapter(String title, ItemStack icon, List<Entry> entries) {}

    public record Entry(String title, ItemStack icon, List<Page> pages) {}

    /** A page: some text, optionally with a row of items and/or a recipe to show above it. */
    public record Page(String text, List<ItemStack> items, @Nullable ResourceLocation recipe) {}

    public static GuideBook load() {
        Minecraft mc = Minecraft.getInstance();
        String language = mc.getLanguageManager().getSelected().toLowerCase(Locale.ROOT);
        for (String lang : new String[]{language, "en_us"}) {
            Optional<Resource> resource = mc.getResourceManager().getResource(ReikosWeather.id("guide/" + lang + ".json"));
            if (resource.isPresent()) {
                try (Reader reader = resource.get().openAsReader()) {
                    return parse(JsonParser.parseReader(reader).getAsJsonObject());
                } catch (Exception e) {
                    ReikosWeather.LOGGER.error("Couldn't read the Weather Almanac ({})", lang, e);
                }
            }
        }
        return new GuideBook("Weather Almanac", "The almanac couldn't be loaded.", List.of());
    }

    private static GuideBook parse(JsonObject json) {
        List<Chapter> chapters = new ArrayList<>();
        for (JsonElement c : GsonHelper.getAsJsonArray(json, "chapters")) {
            JsonObject chapter = c.getAsJsonObject();
            List<Entry> entries = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(chapter, "entries")) {
                JsonObject entry = e.getAsJsonObject();
                List<Page> pages = new ArrayList<>();
                for (JsonElement p : GsonHelper.getAsJsonArray(entry, "pages")) {
                    pages.add(page(p));
                }
                entries.add(new Entry(GsonHelper.getAsString(entry, "title"), item(GsonHelper.getAsString(entry, "icon", "")), pages));
            }
            chapters.add(new Chapter(GsonHelper.getAsString(chapter, "title"), item(GsonHelper.getAsString(chapter, "icon", "")), entries));
        }
        return new GuideBook(GsonHelper.getAsString(json, "title"), GsonHelper.getAsString(json, "intro", ""), chapters);
    }

    private static Page page(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return new Page(element.getAsString(), List.of(), null);
        }
        JsonObject page = element.getAsJsonObject();
        List<ItemStack> items = new ArrayList<>();
        for (JsonElement i : GsonHelper.getAsJsonArray(page, "items", new JsonArray())) {
            items.add(item(i.getAsString()));
        }
        String recipe = GsonHelper.getAsString(page, "recipe", "");
        return new Page(GsonHelper.getAsString(page, "text", ""), items, recipe.isEmpty() ? null : ResourceLocation.parse(recipe));
    }

    private static ItemStack item(String id) {
        if (id.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).map(ItemStack::new).orElse(new ItemStack(Items.BARRIER));
    }
}
