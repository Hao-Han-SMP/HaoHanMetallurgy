package vn.haohan.metallurgy;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URI;
import java.util.Objects;

/** Registers the plugin-bundled native advancement datapack before server data loads. */
public final class HaoHanMetallurgyBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
                LifecycleEvents.DATAPACK_DISCOVERY,
                event -> {
                    try {
                        URI uri = Objects.requireNonNull(
                                HaoHanMetallurgyBootstrap.class
                                        .getResource("/haohan_advancements"))
                                .toURI();
                        event.registrar().discoverPack(
                                uri,
                                "advancements",
                                configurer -> configurer.autoEnableOnServerStart(false));
                    } catch (URISyntaxException | IOException exception) {
                        throw new IllegalStateException(
                                "Could not discover bundled HaoHan advancement pack", exception);
                    }
                });
    }
}
