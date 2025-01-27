package red.jackf.chesttracker.impl.providers;

import com.google.common.collect.Sets;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.jetbrains.annotations.Nullable;
import red.jackf.chesttracker.api.ClientBlockSource;
import red.jackf.chesttracker.api.providers.InteractionTracker;
import red.jackf.chesttracker.api.providers.context.BlockPlacedContext;
import red.jackf.chesttracker.api.providers.ServerProvider;
import red.jackf.chesttracker.impl.events.AfterPlayerPlaceBlock;
import red.jackf.chesttracker.impl.memory.MemoryBankAccessImpl;
import red.jackf.chesttracker.impl.util.CachedClientBlockSource;
import red.jackf.jackfredlib.client.api.gps.Coordinate;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

public class ProviderHandler {
    public static final ProviderHandler INSTANCE = new ProviderHandler();
    private final Set<ServerProvider> REGISTERED_PROVIDERS = Sets.newHashSet();
    private ServerProvider currentProvider = null;

    private @Nullable Coordinate lastCoordinate = null;

    private ProviderHandler() {
    }

    public <T extends ServerProvider> T register(T provider) {
        this.REGISTERED_PROVIDERS.add(provider);
        return provider;
    }

    private void load(Coordinate coordinate) {
        if (this.currentProvider != null) {
            this.unload();
        }

        REGISTERED_PROVIDERS.stream()
                .sorted(Comparator.comparingInt(ServerProvider::getPriority).reversed())
                .filter(provider -> provider.appliesTo(coordinate))
                .findFirst()
                .ifPresent(serverProvider -> {
                    this.currentProvider = serverProvider;
                    serverProvider.onConnect(coordinate);
                });
    }

    private void unload() {
        if (this.currentProvider == null) return;
        this.currentProvider.onDisconnect();
        MemoryBankAccessImpl.INSTANCE.unload();
    }

    public Optional<ServerProvider> getCurrentProvider() {
        return Optional.ofNullable(currentProvider);
    }

    public void setupEvents() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            Optional<Coordinate> coord = Coordinate.getCurrent();
            if (coord.isPresent()) {
                // For servers like hypixel, transferring between sub-servers triggers JOIN each time but not DISCONNECT.
                if (!coord.get().equals(this.lastCoordinate)) {
                    this.lastCoordinate = coord.get();
                    this.load(coord.get());
                }
            } else {
                this.unload();
            }
        }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            this.lastCoordinate = null;
            client.execute(this::unload);
        });

        AfterPlayerPlaceBlock.EVENT.register((clientLevel, pos, state, placementStack) ->
            getCurrentProvider().ifPresent(provider ->
                MemoryBankAccessImpl.INSTANCE.getLoadedInternal().ifPresent(bank -> {
                    // if we don't auto add blocks dont run
                    if (!bank.getMetadata().getFilteringSettings().autoAddPlacedBlocks.blockPredicate.test(state))
                        return;

                    ClientBlockSource cbs = new CachedClientBlockSource(clientLevel, pos, state);

                    provider.onBlockPlaced(BlockPlacedContext.create(cbs, placementStack));
                })));

        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
                getCurrentProvider().ifPresent(provider ->
                        provider.onGameMessageReceived(message, overlay)));

        ClientSendMessageEvents.COMMAND.register(command -> {
            getCurrentProvider().ifPresent(provider -> provider.onCommandSent(command));

            InteractionTracker.INSTANCE.clear();
        });
    }
}
