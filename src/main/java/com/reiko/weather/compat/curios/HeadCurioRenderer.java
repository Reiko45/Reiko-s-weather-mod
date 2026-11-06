package com.reiko.weather.compat.curios;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reiko.weather.ReikosWeather;
import com.reiko.weather.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/** Draws a hat worn in a Curios slot just like the helmet version. */
public class HeadCurioRenderer implements ICurioRenderer {
    private final ResourceLocation texture;
    private HumanoidModel<LivingEntity> model;

    public HeadCurioRenderer(ResourceLocation texture) {
        this.texture = texture;
    }

    public static void register() {
        CuriosRendererRegistry.register(ModItems.SUN_HAT.get(), () -> new HeadCurioRenderer(ReikosWeather.id("textures/models/armor/straw_layer_1.png")));
        CuriosRendererRegistry.register(ModItems.GOGGLES.get(), () -> new HeadCurioRenderer(ReikosWeather.id("textures/models/armor/goggles_layer_1.png")));
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext, PoseStack poseStack,
            RenderLayerParent<T, M> renderLayerParent, MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (model == null) {
            model = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        }
        LivingEntity entity = slotContext.entity();
        // a fresh model defaults to a baby's proportions (small head at chest height)
        model.young = entity.isBaby();
        model.crouching = entity.isCrouching();
        ICurioRenderer.followHeadRotations(entity, model.head, model.hat);
        model.setAllVisible(false);
        model.head.visible = true;
        model.hat.visible = true;
        VertexConsumer consumer = ItemRenderer.getArmorFoilBuffer(buffers, RenderType.armorCutoutNoCull(texture), stack.hasFoil());
        model.renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY);
    }
}
