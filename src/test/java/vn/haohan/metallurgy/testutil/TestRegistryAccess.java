package vn.haohan.metallurgy.testutil;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class TestRegistryAccess implements RegistryAccess {

    private static final Map<RegistryKey<?>, Class<?>> KEY_TYPES = Map.ofEntries(
            Map.entry(RegistryKey.SOUND_EVENT, org.bukkit.Sound.class),
            Map.entry(RegistryKey.GAME_EVENT, org.bukkit.GameEvent.class),
            Map.entry(RegistryKey.BLOCK, org.bukkit.block.BlockType.class),
            Map.entry(RegistryKey.ITEM, org.bukkit.inventory.ItemType.class),
            Map.entry(RegistryKey.MOB_EFFECT, org.bukkit.potion.PotionEffectType.class),
            Map.entry(RegistryKey.PARTICLE_TYPE, org.bukkit.Particle.class),
            Map.entry(RegistryKey.POTION, org.bukkit.potion.PotionType.class),
            Map.entry(RegistryKey.ATTRIBUTE, org.bukkit.attribute.Attribute.class),
            Map.entry(RegistryKey.FLUID, org.bukkit.Fluid.class),
            Map.entry(RegistryKey.STRUCTURE_TYPE, org.bukkit.generator.structure.StructureType.class),
            Map.entry(RegistryKey.DAMAGE_TYPE, org.bukkit.damage.DamageType.class),
            Map.entry(RegistryKey.ENCHANTMENT, org.bukkit.enchantments.Enchantment.class),
            Map.entry(RegistryKey.ENTITY_TYPE, org.bukkit.entity.EntityType.class),
            Map.entry(RegistryKey.MENU, org.bukkit.inventory.MenuType.class)
    );

    private final Map<Object, Registry<?>> registries = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings({"unchecked", "removal"})
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        return (Registry<T>) registries.computeIfAbsent(type, k -> createDummyRegistry(type));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
        return (Registry<T>) registries.computeIfAbsent(key, k -> {
            Class<?> targetClass = KEY_TYPES.getOrDefault(key, Keyed.class);
            return createDummyRegistry((Class<T>) targetClass);
        });
    }

    private <T extends Keyed> Registry<T> createDummyRegistry(Class<T> type) {
        return new DummyRegistry<>(type);
    }

    private static final class DummyRegistry<T extends Keyed> implements Registry<T> {
        private final Class<T> type;
        private final Map<NamespacedKey, T> cache = new ConcurrentHashMap<>();

        DummyRegistry(Class<T> type) {
            this.type = type;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T get(NamespacedKey key) {
            if (key == null) return null;
            return cache.computeIfAbsent(key, k -> {
                if (type == org.bukkit.GameEvent.class) {
                    return (T) new org.bukkit.GameEvent() {
                        @Override
                        public NamespacedKey getKey() {
                            return k;
                        }

                        @Override
                        public int getRange() {
                            return 16;
                        }

                        @Override
                        public int getVibrationLevel() {
                            return 0;
                        }

                        @Override
                        public String toString() {
                            return k.toString();
                        }

                        @Override
                        public boolean equals(Object obj) {
                            return obj instanceof org.bukkit.GameEvent ge && k.equals(ge.getKey());
                        }

                        @Override
                        public int hashCode() {
                            return k.hashCode();
                        }
                    };
                }
                Class<?>[] interfaces = type.isInterface()
                        ? new Class<?>[]{type}
                        : new Class<?>[]{Keyed.class};
                return (T) Proxy.newProxyInstance(
                        type.getClassLoader() != null ? type.getClassLoader() : getClass().getClassLoader(),
                        interfaces,
                        (proxy, method, args) -> {
                            String mName = method.getName();
                            if ("getKey".equals(mName)) return k;
                            if ("key".equals(mName)) return k;
                            if ("name".equals(mName)) return k.getKey().toUpperCase(Locale.ROOT);
                            if ("toString".equals(mName)) return k.toString();
                            if ("equals".equals(mName)) return proxy == (args != null && args.length > 0 ? args[0] : null);
                            if ("hashCode".equals(mName)) return k.hashCode();
                            if ("asString".equals(mName)) return k.asString();
                            Class<?> ret = method.getReturnType();
                            if (ret == boolean.class) return false;
                            if (ret == int.class) return 0;
                            if (ret == long.class) return 0L;
                            if (ret == float.class) return 0f;
                            if (ret == double.class) return 0d;
                            return null;
                        }
                );
            });
        }

        @Override
        public NamespacedKey getKey(T value) {
            return value != null ? value.getKey() : null;
        }

        @Override
        public boolean hasTag(io.papermc.paper.registry.tag.TagKey<T> tagKey) { return false; }

        @Override
        public io.papermc.paper.registry.tag.Tag<T> getTag(io.papermc.paper.registry.tag.TagKey<T> tagKey) { return null; }

        @Override
        public Collection<io.papermc.paper.registry.tag.Tag<T>> getTags() { return Collections.emptyList(); }

        @Override
        public Stream<T> stream() { return cache.values().stream(); }

        @Override
        public Stream<NamespacedKey> keyStream() { return cache.keySet().stream(); }

        @Override
        public int size() { return cache.size(); }

        @Override
        public Iterator<T> iterator() { return cache.values().iterator(); }
    }
}
