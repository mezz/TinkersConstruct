package slimeknights.tconstruct.tools.item;

import com.google.common.collect.ImmutableSet;

import net.minecraft.block.Block;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.materials.ToolMaterialStats;
import slimeknights.tconstruct.library.tinkering.Category;
import slimeknights.tconstruct.library.tinkering.PartMaterialType;
import slimeknights.tconstruct.library.tools.AoeToolCore;
import slimeknights.tconstruct.library.tools.ToolNBT;
import slimeknights.tconstruct.tools.TinkerTools;

public class Pickaxe extends AoeToolCore {

  public static final ImmutableSet<net.minecraft.block.material.Material> effective_materials =
      ImmutableSet.of(net.minecraft.block.material.Material.iron,
                      net.minecraft.block.material.Material.anvil,
                      net.minecraft.block.material.Material.rock,
                      net.minecraft.block.material.Material.ice,
                      net.minecraft.block.material.Material.glass,
                      net.minecraft.block.material.Material.packedIce,
                      net.minecraft.block.material.Material.piston);

  // Pick-head, binding, tool-rod
  public Pickaxe() {
    this(new PartMaterialType.ToolPartType(TinkerTools.toolRod),
         new PartMaterialType.ToolPartType(TinkerTools.pickHead),
         new PartMaterialType.ToolPartType(TinkerTools.binding));
  }

  public Pickaxe(PartMaterialType... requiredComponents) {
    super(requiredComponents);

    addCategory(Category.HARVEST);

    // set the toolclass, actual harvestlevel is done by the overridden callback
    this.setHarvestLevel("pickaxe", 0);
  }

  @Override
  public boolean isEffective(Block block) {
    return effective_materials.contains(block.getMaterial()) || ItemPickaxe.EFFECTIVE_ON.contains(block);
  }

  @Override
  public float damagePotential() {
    return 0.3f;
  }

  @Override
  public NBTTagCompound buildTag(List<Material> materials) {
    ToolMaterialStats handle = materials.get(0).getStats(ToolMaterialStats.TYPE);
    ToolMaterialStats head = materials.get(1).getStats(ToolMaterialStats.TYPE);
    ToolMaterialStats binding = materials.get(2).getStats(ToolMaterialStats.TYPE);

    ToolNBT data = new ToolNBT(head);
    data.handle(handle).extra(binding);

    data.durability *= 1f + 0.1f * (binding.extraQuality - 0.5f);
    data.speed *= 1f + 0.2f * (handle.handleQuality * handle.miningspeed);
    data.attack *= 1f + 0.1f * handle.handleQuality * binding.extraQuality;

    // handle influences durability
    // binding quality influences how well the handle interacts with the head
    //data.durability *= 0.33f + 0.77f*(handle.handleQuality * (1 + binding.extraQuality)/2);
    // flat durability from other parts
    //data.durability += 0.15f * handle.durability + 0.10f * binding.durability;
    // handle also influences mining speed a bit (0-20% change)
    // binding adds a bit to the speed
    //data.speed += (binding.miningspeed * binding.extraQuality)*0.14f;

    // 3 free modifiers
    data.modifiers = DEFAULT_MODIFIERS;

    return data.get();
  }
}
