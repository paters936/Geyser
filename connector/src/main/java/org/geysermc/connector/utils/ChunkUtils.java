package org.geysermc.connector.utils;

import com.github.steveice10.mc.protocol.data.game.chunk.Chunk;
import com.github.steveice10.mc.protocol.data.game.chunk.Column;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockState;
import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.protocol.bedrock.packet.LevelChunkPacket;
import com.nukkitx.protocol.bedrock.packet.NetworkChunkPublisherUpdatePacket;
import com.nukkitx.protocol.bedrock.packet.UpdateBlockPacket;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.TranslatorsInit;
import org.geysermc.connector.network.translators.block.BlockEntry;
import org.geysermc.connector.world.chunk.ChunkSection;

public class ChunkUtils {
    public static ChunkData translateToBedrock(Column column) {
        ChunkData chunkData = new ChunkData();

        Chunk[] chunks = column.getChunks();
        int chunkSectionCount = chunks.length;
        chunkData.sections = new ChunkSection[chunkSectionCount];

        for (int chunkY = 0; chunkY < chunkSectionCount; chunkY++) {
            chunkData.sections[chunkY] = new ChunkSection();
            Chunk chunk = chunks[chunkY];

            if (chunk == null || chunk.isEmpty())
                continue;

            ChunkSection section = chunkData.sections[chunkY];

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState blockState = chunk.get(x, y, z);
                        BlockEntry block = TranslatorsInit.getBlockTranslator().getBlockEntry(blockState);

                        section.getBlockStorageArray()[0].setFullBlock(ChunkSection.blockPosition(x, y, z),
                                block.getBedrockRuntimeId());

                        if (block.isWaterlogged()) {
                            BlockEntry water = TranslatorsInit.getBlockTranslator().getBlockEntry("minecraft:water[level=0]");
                            section.getBlockStorageArray()[1].setFullBlock(ChunkSection.blockPosition(x, y, z), water.getBedrockRuntimeId());
                        }
                    }
                }
            }
        }
        return chunkData;
    }

    public static void updateBlock(GeyserSession session, BlockState blockState, Position position) {
        BlockEntry blockEntry = TranslatorsInit.getBlockTranslator().getBlockEntry(blockState);
        Vector3i pos = Vector3i.from(position.getX(), position.getY(), position.getZ());

        UpdateBlockPacket updateBlockPacket = new UpdateBlockPacket();
        updateBlockPacket.setDataLayer(0);
        updateBlockPacket.setBlockPosition(pos);
        updateBlockPacket.setRuntimeId(blockEntry.getBedrockRuntimeId());
        updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.NEIGHBORS);
        session.getUpstream().sendPacket(updateBlockPacket);

        UpdateBlockPacket waterPacket = new UpdateBlockPacket();
        waterPacket.setDataLayer(1);
        waterPacket.setBlockPosition(pos);
        if (blockEntry.isWaterlogged()) {
            BlockEntry water = TranslatorsInit.getBlockTranslator().getBlockEntry("minecraft:water[level=0]");
            waterPacket.setRuntimeId(water.getBedrockRuntimeId());
        } else {
            waterPacket.setRuntimeId(0);
        }
        session.getUpstream().sendPacket(waterPacket);
    }

    public static void sendEmptyChunks(GeyserSession session, Vector3i position, int radius, boolean forceUpdate) {
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        NetworkChunkPublisherUpdatePacket chunkPublisherUpdatePacket = new NetworkChunkPublisherUpdatePacket();
        chunkPublisherUpdatePacket.setPosition(position);
        chunkPublisherUpdatePacket.setRadius(radius + 1 << 4);
        session.getUpstream().sendPacket(chunkPublisherUpdatePacket);
        session.setLastChunkPosition(null);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                LevelChunkPacket data = new LevelChunkPacket();
                data.setChunkX(chunkX + x);
                data.setChunkZ(chunkZ + z);
                data.setSubChunksLength(0);
                data.setData(TranslatorsInit.EMPTY_LEVEL_CHUNK_DATA);
                data.setCachingEnabled(false);
                session.getUpstream().sendPacket(data);

                if (forceUpdate) {
                    Vector3i pos = Vector3i.from(chunkX + x << 4, 80, chunkZ + z << 4);
                    UpdateBlockPacket blockPacket = new UpdateBlockPacket();
                    blockPacket.setBlockPosition(pos);
                    blockPacket.setDataLayer(1);
                    blockPacket.setRuntimeId(1);
                    session.getUpstream().sendPacket(blockPacket);
                }
            }
        }
    }

    public static final class ChunkData {
        public ChunkSection[] sections;

        public byte[] biomes = new byte[256];
        public byte[] blockEntities = new byte[0];
    }
}
