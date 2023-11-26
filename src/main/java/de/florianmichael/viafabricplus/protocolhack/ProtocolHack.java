/*
 * This file is part of ViaFabricPlus - https://github.com/FlorianMichael/ViaFabricPlus
 * Copyright (C) 2021-2023 FlorianMichael/EnZaXD
 * Copyright (C) 2023      RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.florianmichael.viafabricplus.protocolhack;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.ProtocolInfo;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.ProtocolPathEntry;
import com.viaversion.viaversion.api.protocol.ProtocolPipeline;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl;
import de.florianmichael.viafabricplus.event.ChangeProtocolVersionCallback;
import de.florianmichael.viafabricplus.event.PostViaVersionLoadCallback;
import de.florianmichael.viafabricplus.injection.access.IClientConnection;
import de.florianmichael.viafabricplus.protocolhack.command.ViaFabricPlusVLCommandHandler;
import de.florianmichael.viafabricplus.protocolhack.impl.ViaFabricPlusVLInjector;
import de.florianmichael.viafabricplus.protocolhack.impl.ViaFabricPlusVLLoader;
import de.florianmichael.viafabricplus.protocolhack.impl.platform.ViaFabricPlusViaLegacyPlatformImpl;
import de.florianmichael.viafabricplus.protocolhack.netty.ViaFabricPlusVLLegacyPipeline;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.raphimc.vialoader.ViaLoader;
import net.raphimc.vialoader.impl.platform.ViaAprilFoolsPlatformImpl;
import net.raphimc.vialoader.impl.platform.ViaBackwardsPlatformImpl;
import net.raphimc.vialoader.impl.platform.ViaBedrockPlatformImpl;
import net.raphimc.vialoader.impl.platform.ViaVersionPlatformImpl;
import net.raphimc.vialoader.util.VersionEnum;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * This class represents the whole Protocol Translator, here all important variables are stored
 */
public class ProtocolHack {
    /**
     * These attribute keys are used to track the main connections of Minecraft and ViaVersion, so that they can be used later during the connection to send packets.
     */
    public static final AttributeKey<ClientConnection> CLIENT_CONNECTION_ATTRIBUTE_KEY = AttributeKey.newInstance("viafabricplus-clientconnection");

    /**
     * This attribute stores the forced version for the current connection (if you set a specific version in the Edit Server screen)
     */
    public static final AttributeKey<VersionEnum> TARGET_VERSION_ATTRIBUTE_KEY = AttributeKey.newInstance("viafabricplus-targetversion");

    /**
     * The native version of the client
     */
    public static final VersionEnum NATIVE_VERSION = VersionEnum.r1_20_2;

    /**
     * This field stores the target version that you set in the GUI
     */
    private static VersionEnum targetVersion = NATIVE_VERSION;

    /**
     * Injects the ViaFabricPlus pipeline with all ViaVersion elements into a Minecraft pipeline
     *
     * @param connection the Minecraft connection
     */
    public static void injectViaPipeline(final ClientConnection connection, final Channel channel) {
        final IClientConnection mixinClientConnection = (IClientConnection) connection;
        final VersionEnum serverVersion = mixinClientConnection.viaFabricPlus$getTargetVersion();

        if (serverVersion != ProtocolHack.NATIVE_VERSION) {
            channel.attr(ProtocolHack.CLIENT_CONNECTION_ATTRIBUTE_KEY).set(connection);
            channel.attr(ProtocolHack.TARGET_VERSION_ATTRIBUTE_KEY).set(serverVersion);

            if (VersionEnum.bedrockLatest.equals(serverVersion)) {
                channel.config().setOption(RakChannelOption.RAK_PROTOCOL_VERSION, 11);
                channel.config().setOption(RakChannelOption.RAK_CONNECT_TIMEOUT, 4_000L);
                channel.config().setOption(RakChannelOption.RAK_SESSION_TIMEOUT, 30_000L);
                channel.config().setOption(RakChannelOption.RAK_GUID, ThreadLocalRandom.current().nextLong());
            }

            final UserConnection user = new UserConnectionImpl(channel, true);
            new ProtocolPipelineImpl(user);
            mixinClientConnection.viaFabricPlus$setUserConnection(user);

            channel.pipeline().addLast(new ViaFabricPlusVLLegacyPipeline(user, serverVersion));
        }
    }

    /**
     * This method is used when you need the target version after connecting to the server.
     *
     * @return the target version
     */
    public static VersionEnum getTargetVersion() {
        return targetVersion;
    }

    /**
     * Sets the target version
     *
     * @param newVersion the target version
     */
    public static void setTargetVersion(VersionEnum newVersion) {
        if (newVersion == null) return;

        final VersionEnum oldVersion = targetVersion;
        targetVersion = newVersion;
        if (oldVersion != newVersion) {
            ChangeProtocolVersionCallback.EVENT.invoker().onChangeProtocolVersion(oldVersion, targetVersion);
        }
    }

    /**
     * @param clientVersion The client version
     * @param serverVersion The server version
     * @return Creates a dummy UserConnection class with a valid protocol pipeline to emulate packets
     */
    public static UserConnection createDummyUserConnection(final VersionEnum clientVersion, final VersionEnum serverVersion) {
        final UserConnection user = new UserConnectionImpl(null, true);
        final ProtocolPipeline pipeline = new ProtocolPipelineImpl(user);
        final List<ProtocolPathEntry> path = Via.getManager().getProtocolManager().getProtocolPath(clientVersion.getVersion(), serverVersion.getVersion());
        for (ProtocolPathEntry pair : path) {
            pipeline.add(pair.protocol());
            pair.protocol().init(user);
        }

        final MinecraftClient mc = MinecraftClient.getInstance();
        final ProtocolInfo info = user.getProtocolInfo();
        info.setState(State.PLAY);
        info.setProtocolVersion(clientVersion.getVersion());
        info.setServerProtocolVersion(serverVersion.getVersion());
        if (mc.player != null) {
            info.setUsername(MinecraftClient.getInstance().player.getGameProfile().getName());
            info.setUuid(MinecraftClient.getInstance().player.getGameProfile().getId());
        }

        return user;
    }

    /**
     * @return Returns the current UserConnection of the connection to the server, if the player isn't connected to a server it will return null
     */
    public static UserConnection getPlayNetworkUserConnection() {
        final ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler != null) {
            return ((IClientConnection) handler.getConnection()).viaFabricPlus$getUserConnection();
        }

        throw new IllegalStateException("The player is not connected to a server");
    }

    public static CompletableFuture<Void> init(final File directory) {
        // Register command callback for /viaversion and /viafabricplus
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            final var commandHandler = (ViaFabricPlusVLCommandHandler) Via.getManager().getCommandHandler();
            final var executor = RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("args", StringArgumentType.greedyString()).
                    executes(commandHandler::execute).suggests(commandHandler::suggestion);

            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("viaversion").then(executor).executes(commandHandler::execute));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("viafabricplus").then(executor).executes(commandHandler::execute));
        });

        return CompletableFuture.runAsync(() -> {
            // Load ViaVersion and register all platforms and their components
            ViaLoader.init(
                    new ViaVersionPlatformImpl(directory),
                    new ViaFabricPlusVLLoader(),
                    new ViaFabricPlusVLInjector(),
                    new ViaFabricPlusVLCommandHandler(),
                    ViaBackwardsPlatformImpl::new,
                    ViaFabricPlusViaLegacyPlatformImpl::new,
                    ViaAprilFoolsPlatformImpl::new,
                    ViaBedrockPlatformImpl::new
            );
            PostViaVersionLoadCallback.EVENT.invoker().onPostViaVersionLoad();
        });
    }
}
