package fi.dy.masa.litematica.scheduler.tasks;

import java.util.ArrayList;
import java.util.Collection;
import com.google.common.collect.ArrayListMultimap;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import fi.dy.masa.litematica.render.infohud.InfoHud;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.util.SchematicPlacingUtils;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.gui.util.Message.MessageType;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.LayerRange;
import fi.dy.masa.malilib.util.WorldUtils;

public class TaskPasteSchematicPerChunkDirect extends TaskPasteSchematicPerChunkBase
{
    private final ArrayListMultimap<ChunkPos, SchematicPlacement> placementsPerChunk = ArrayListMultimap.create();

    public TaskPasteSchematicPerChunkDirect(Collection<SchematicPlacement> placements, LayerRange range, boolean changedBlocksOnly)
    {
        super(placements, range, changedBlocksOnly);
    }

    @Override
    protected void onChunkAddedForHandling(ChunkPos pos, SchematicPlacement placement)
    {
        super.onChunkAddedForHandling(pos, placement);

        this.placementsPerChunk.put(pos, placement);
    }

    @Override
    public boolean canExecute()
    {
        if (super.canExecute() == false || this.mc.isSingleplayer() == false)
        {
            return false;
        }

        World world = WorldUtils.getBestWorld(this.mc);
        return world != null && world.isRemote == false;
    }

    @Override
    public boolean execute()
    {
        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
        WorldClient worldClient = this.mc.world;
        World world = WorldUtils.getBestWorld(this.mc);
        int processed = 0;

        this.sortChunkList();

        for (int chunkIndex = 0; chunkIndex < this.chunks.size(); ++chunkIndex)
        {
            ChunkPos pos = this.chunks.get(chunkIndex);

            if (this.canProcessChunk(pos, worldSchematic, worldClient))
            {
                // New list to avoid CME
                ArrayList<SchematicPlacement> placements = new ArrayList<>(this.placementsPerChunk.get(pos));

                for (SchematicPlacement placement : placements)
                {
                    if (placement.isInvalidated() ||
                        SchematicPlacingUtils.placeToWorldWithinChunk(placement, pos, world, false))
                    {
                        this.placementsPerChunk.remove(pos, placement);
                        ++processed;
                    }
                }

                if (this.placementsPerChunk.containsKey(pos) == false)
                {
                    this.chunks.remove(chunkIndex);
                    --chunkIndex;
                }
            }
        }

        if (this.chunks.isEmpty())
        {
            this.finished = true;
            return true;
        }

        if (processed > 0)
        {
            this.updateInfoHudLines();
        }

        return false;
    }

    @Override
    public void stop()
    {
        if (this.finished)
        {
            InfoUtils.showGuiOrActionBarMessage(MessageType.SUCCESS, "litematica.message.schematic_placements_pasted");
        }
        else
        {
            InfoUtils.showGuiOrActionBarMessage(MessageType.ERROR, "litematica.message.error.schematic_paste_failed");
        }

        InfoHud.getInstance().removeInfoHudRenderer(this, false);

        super.stop();
    }
}
