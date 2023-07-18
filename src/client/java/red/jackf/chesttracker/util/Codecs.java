package red.jackf.chesttracker.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import red.jackf.chesttracker.memory.ItemMemory;
import red.jackf.chesttracker.memory.LocationData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class Codecs {
    public static final Codec<BlockPos> BLOCK_POS_STRING = Codec.STRING.comapFlatMap(
            s -> {
                String[] split = s.split(",");
                if (split.length == 3) {
                    try {
                        int x = Integer.parseInt(split[0]);
                        int y = Integer.parseInt(split[1]);
                        int z = Integer.parseInt(split[2]);

                        return DataResult.success(new BlockPos(x, y, z));
                    } catch (NumberFormatException ex) {
                        return DataResult.error(() -> "Invalid integer in key");
                    }
                } else {
                    return DataResult.error(() -> "Unknown number of coordinates: " + split.length);
                }
            }, pos -> "%d,%d,%d".formatted(pos.getX(), pos.getY(), pos.getZ())
    );

    public static final Codec<LocationData> LOCATION_DATA = RecordCodecBuilder.create(instance ->
            instance.group(makeMutableList(ItemStack.CODEC.listOf()).fieldOf("items").forGetter(LocationData::items)).apply(instance, LocationData::new));

    public static final Codec<ItemMemory> ITEM_MEMORY = RecordCodecBuilder.create(instance ->
            instance.group(makeMutableMap(Codec.unboundedMap(
                    ResourceLocation.CODEC,
                    makeMutableMap(Codec.unboundedMap(
                            BLOCK_POS_STRING,
                            LOCATION_DATA
                    ))
            )).fieldOf("memories").forGetter(ItemMemory::getMemories)).apply(instance, ItemMemory::new));


    /**
     * Makes a list codec return a mutable list instance of the default immutable one.
     * @param codec Codec that returns an immutable list
     * @return Codec that provides a mutable list on deserialization ({@link ArrayList})
     * @param <T> Type contained in lists in said codecs
     */
    public static <T> Codec<List<T>> makeMutableList(Codec<List<T>> codec) {
        return codec.xmap(ArrayList::new, Function.identity());
    }

    /**
     * Makes a map codec return a mutable map instead of the default immutable one.
     * @param codec Codec that returns an immutable map
     * @return Codec that provides a mutable map on deserialization ({@link HashMap})
     * @param <K> Type of key in said maps
     * @param <V> Type of value in said maps
     */
    public static <K, V> Codec<Map<K, V>> makeMutableMap(Codec<Map<K, V>> codec) {
        return codec.xmap(HashMap::new, Function.identity());
    }
}