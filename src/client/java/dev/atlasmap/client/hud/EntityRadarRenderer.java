package dev.atlasmap.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.List;

public class EntityRadarRenderer {

    public static void render(
            GuiGraphicsExtractor graphics,
            Minecraft client,
            int size,
            int centerX,
            int centerY,
            double sampleSpan,
            float yawDeg,
            boolean rotateWithPlayer
    ) {
        if (client.level == null || client.player == null) {
            return;
        }

        // Radius area yang dicakup minimap
        double radius = sampleSpan / 2.0;

        // Ambil semua LivingEntity di sekitar player
        List<LivingEntity> entities = client.level.getEntitiesOfClass(
                LivingEntity.class,
                client.player.getBoundingBox().inflate(radius)
        );

        // Konversi jarak Minecraft (blok) -> pixel GUI
        double pixelsPerBlock = size / sampleSpan;

        for (LivingEntity entity : entities) {

            // Jangan render player sendiri
            if (entity == client.player) {
                continue;
            }

            // Tentukan warna berdasarkan EntityType
            int color = 0;

// Tarik String ID dari entitas menggunakan Registries Minecraft
            String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();// Cocokkan ID dengan daftar dari website yang Anda temukan
            if (entityId.equals("sheep")) {
                // [Masukkan kode warna/gambar untuk sheep di sini]
            }
            else if (entityId.equals("chicken")) {
                // [Masukkan kode warna/gambar untuk chicken di sini]
            }
            else if (entityId.equals("cow")) {
                // [Masukkan kode warna/gambar untuk cow di sini]
            }
            else if (entityId.equals("zombie")) {
                // [Masukkan kode warna/gambar untuk zombie di sini]
            }
            else if (entityId.equals("creeper")) {
                // [Masukkan kode warna/gambar untuk creeper di sini]
            }
            else if (entityId.equals("skeleton")) {
                // [Masukkan kode warna/gambar untuk skeleton di sini]
            }

            // Entity lain tidak ditampilkan
            if (color == 0) {
                continue;
            }

            // Posisi entity relatif terhadap player
            float dx = (float) (
                    entity.getX() - client.player.getX()
            );

            float dz = (float) (
                    entity.getZ() - client.player.getZ()
            );

            // Rotasi radar mengikuti arah player
            if (rotateWithPlayer) {

                float rad = Mth.DEG_TO_RAD * (180f - yawDeg);

                float cos = Mth.cos(rad);
                float sin = Mth.sin(rad);

                float rotatedX = dx * cos - dz * sin;
                float rotatedZ = dx * sin + dz * cos;

                dx = rotatedX;
                dz = rotatedZ;
            }

            // Konversi koordinat dunia -> koordinat GUI
            int pixelX = (int) (
                    centerX + dx * pixelsPerBlock
            );

            int pixelY = (int) (
                    centerY + dz * pixelsPerBlock
            );

            // Render dot 3x3
            graphics.fill(
                    pixelX - 1,
                    pixelY - 1,
                    pixelX + 2,
                    pixelY + 2,
                    color
            );
        }
    }
}