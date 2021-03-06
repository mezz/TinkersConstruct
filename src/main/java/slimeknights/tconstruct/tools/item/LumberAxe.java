package slimeknights.tconstruct.tools.item;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import gnu.trove.set.hash.THashSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.materials.ToolMaterialStats;
import slimeknights.tconstruct.library.tinkering.Category;
import slimeknights.tconstruct.library.tinkering.PartMaterialType;
import slimeknights.tconstruct.library.tools.ToolNBT;
import slimeknights.tconstruct.library.utils.ToolHelper;
import slimeknights.tconstruct.tools.TinkerTools;
import slimeknights.tconstruct.tools.events.TinkerToolEvent;

public class LumberAxe extends Hatchet {

  public LumberAxe() {
    super(new PartMaterialType.ToolPartType(TinkerTools.toughToolRod),
          new PartMaterialType.ToolPartType(TinkerTools.broadAxeHead),
          new PartMaterialType.ToolPartType(TinkerTools.largePlate),
          new PartMaterialType.ToolPartType(TinkerTools.toughBinding));

    // lumberaxe is not a weapon. it's for lumberjacks. Lumberjacks are manly, they're weapons themselves.
    categories.remove(Category.WEAPON);
  }

  @Override
  public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, EntityPlayer player) {
    if(detectTree(player.worldObj, pos)) {
      return fellTree(itemstack, pos, player);
    }
    return super.onBlockStartBreak(itemstack, pos, player);
  }

  @Override
  public ImmutableList<BlockPos> getAOEBlocks(ItemStack stack, World world, EntityPlayer player, BlockPos origin) {
    return ToolHelper.calcAOEBlocks(stack, world, player, origin, 3, 3, 3);
  }

  @Override
  public NBTTagCompound buildTag(List<Material> materials) {
    ToolMaterialStats handle = materials.get(0).getStats(ToolMaterialStats.TYPE);
    ToolMaterialStats head = materials.get(1).getStats(ToolMaterialStats.TYPE);
    ToolMaterialStats plate = materials.get(2).getStats(ToolMaterialStats.TYPE);
    ToolMaterialStats binding = materials.get(3).getStats(ToolMaterialStats.TYPE);

    ToolNBT data = new ToolNBT(head);
    data.handle(handle).extra(binding, plate);


    data.durability *= 1f + 0.15f * (binding.extraQuality - 0.5f);
    data.speed *= 1f + 0.1f * (handle.handleQuality * handle.miningspeed);
    data.speed *= 0.3f; // slower because AOE
    data.attack = head.attack*2f + plate.attack/3f;
    data.attack *= 1f + 0.1f * handle.handleQuality * binding.extraQuality;

    /*
    // as with the hatchet, the binding is very important. Except this time the plate also factors in

    data.durability *= 0.9f;
    data.durability += plate.durability * binding.extraQuality;
    data.durability *= 0.8f + 0.2f * handle.handleQuality;
    data.durability += 0.03f * handle.durability + 0.28 * binding.durability;

    // since it's a big axe.. we calculate the coefficient the same way as with the hatchet :D
    float coeff = (0.5f + handle.handleQuality / 2) * (0.5f + binding.extraQuality / 2);

    data.speed += 0.11f * plate.miningspeed;
    data.speed *= 0.6f + 0.4f * coeff;
    data.speed *= 0.6f;

    data.attack *= 0.3f + (0.4f + 0.1f * plate.extraQuality) * coeff;
*/
    // 3 free modifiers
    data.modifiers = DEFAULT_MODIFIERS;

    return data.get();
  }

  public static boolean detectTree(World world, BlockPos origin) {
    BlockPos pos = null;
    Stack<BlockPos> candidates = new Stack<BlockPos>();
    candidates.add(origin);

    while(!candidates.isEmpty()) {
      BlockPos candidate = candidates.pop();
      if((pos == null || candidate.getY() > pos.getY()) && isLog(world, candidate)) {
        pos = candidate.up();
        // go up
        while(isLog(world, pos)) {
          pos = pos.up();
        }
        // check if we still have a way diagonally up
        candidates.add(pos.north());
        candidates.add(pos.east());
        candidates.add(pos.south());
        candidates.add(pos.west());
      }
    }

    // not even one match, so there were no logs.
    if(pos == null) {
      return false;
    }

    // check if there were enough leaves around the last position
    // pos now contains the block above the topmost log
    // we want at least 5 leaves in the surrounding 26 blocks
    int d = 3;
    int o = -1; // -(d-1)/2
    int leaves = 0;
    for(int x = 0; x < d; x++) {
      for(int y = 0; y < d; y++) {
        for(int z = 0; z < d; z++) {
          BlockPos leaf = pos.add(o + x, o + y, o + z);
          if(world.getBlockState(leaf).getBlock().isLeaves(world, leaf)) {
            if(++leaves >= 5) {
              return true;
            }
          }
        }
      }
    }

    // not enough leaves. sorreh
    return false;
  }

  private static boolean isLog(World world, BlockPos pos) {
    return world.getBlockState(pos).getBlock().isWood(world, pos);
  }

  public static boolean fellTree(ItemStack itemstack, BlockPos start, EntityPlayer player) {
    if(player.worldObj.isRemote) {
      return true;
    }
    TinkerToolEvent.ExtraBlockBreak event = TinkerToolEvent.ExtraBlockBreak.fireEvent(itemstack, player, 3, 3, 3, -1);
    int speed = Math.round((event.width * event.height * event.depth)/27f);
    if(event.distance > 0) {
      speed = event.distance + 1;
    }

    MinecraftForge.EVENT_BUS.register(new TreeChopTask(itemstack, start, player, speed));
    return true;
  }

  public static class TreeChopTask {

    public final World world;
    public final EntityPlayer player;
    public final ItemStack tool;
    public final int blocksPerTick;

    public Queue<BlockPos> blocks = Lists.newLinkedList();
    public Set<BlockPos> visited = new THashSet<BlockPos>();


    public TreeChopTask(ItemStack tool, BlockPos start, EntityPlayer player, int blocksPerTick) {
      this.world = player.getEntityWorld();
      this.player = player;
      this.tool = tool;
      this.blocksPerTick = blocksPerTick;

      this.blocks.add(start);
    }

    @SubscribeEvent
    public void chopChop(TickEvent.WorldTickEvent event) {
      if(event.side.isClient()) {
        finish();
        return;
      }

      // setup
      int left = blocksPerTick;

      // continue running
      BlockPos pos;
      while(left > 0) {
        // completely done or can't do our job anymore?!
        if(blocks.isEmpty() || ToolHelper.isBroken(tool)) {
          finish();
          return;
        }

        pos = blocks.remove();
        if(!visited.add(pos)) {
          continue;
        }

        // can we harvest the block and is effective?
        if(!isLog(world, pos) || !ToolHelper.isToolEffective2(tool, world.getBlockState(pos))) {
          continue;
        }

        // save its neighbours
        for(EnumFacing facing : new EnumFacing[]{EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST}) {
          BlockPos pos2 = pos.offset(facing);
          if(!visited.contains(pos2)) {
            blocks.add(pos2);
          }
        }

        // also add the layer above.. stupid acacia trees
        for(int x = 0; x < 3; x++) {
          for(int z = 0; z < 3; z++) {
            BlockPos pos2 = pos.add(-1 + x, 1, -1 + z);
            if(!visited.contains(pos2)) {
              blocks.add(pos2);
            }
          }
        }

        // break it, wooo!
        ToolHelper.breakExtraBlock(tool, world, player, pos, pos);
        left--;
      }
    }

    private void finish() {
      // goodbye cruel world
      MinecraftForge.EVENT_BUS.unregister(this);
    }
  }
}
