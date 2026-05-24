package de.maxhenkel.voicechat.net;

import de.maxhenkel.voicechat.BukkitUtils;
import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.VoicechatPaperPlugin;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;

import java.util.HashSet;
import java.util.Set;
import de.maxhenkel.voicechat.net.PlayerStatesPacket;
import de.maxhenkel.voicechat.net.PlayerStatePacket;
import de.maxhenkel.voicechat.net.RemovePlayerStatePacket;

public class PaperNetManager extends NetManager {

    private final Set<Identifier> packets;

    public PaperNetManager() {
        packets = new HashSet<>();
        Bukkit.getMessenger().registerOutgoingPluginChannel(VoicechatPaperPlugin.INSTANCE, "voicechat:player_states");
        Bukkit.getMessenger().registerOutgoingPluginChannel(VoicechatPaperPlugin.INSTANCE, "voicechat:player_state");
    }

    public Set<Identifier> getPackets() {
        return packets;
    }

    @Override
    public <T extends Packet<T>> Channel<T> registerReceiver(Class<T> packetType, boolean toClient, boolean toServer) {
        Channel<T> c = new Channel<>();
        try {
            T dummyPacket = packetType.getDeclaredConstructor().newInstance();
            CustomPacketPayload.Type<T> type = dummyPacket.type();
            String packetId = type.id().toString();
            if (toServer) {
                Bukkit.getMessenger().registerIncomingPluginChannel(VoicechatPaperPlugin.INSTANCE, packetId, (s, player, bytes) -> {
                    try {
                        T packet = packetType.getDeclaredConstructor().newInstance();
                        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));

                        packet.fromBytes(buffer);
                        ServerPlayer serverPlayer = BukkitUtils.getPlayer(player);
                        if (!Voicechat.SERVER.isCompatible(serverPlayer) && !packetType.equals(RequestSecretPacket.class)) {
                            return;
                        }
                        c.onServerPacket(serverPlayer, packet);
                    } catch (Exception e) {
                        Voicechat.LOGGER.error("Failed to read packet", e);
                    }
                });
            }
            if (toClient) {
                Bukkit.getMessenger().registerOutgoingPluginChannel(VoicechatPaperPlugin.INSTANCE, packetId);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return c;
    }

    @Override
    protected void sendToServerInternal(Packet<?> packet) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sendToClient(Packet<?> packet, ServerPlayer player) {
        Bukkit.getGlobalRegionScheduler().run(VoicechatPaperPlugin.INSTANCE, (t) -> {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            Packet<?> packetToSend = packet;
            Identifier id = packet.type().id();
            int compatibilityVersion = Voicechat.SERVER.getCompatibilityVersion(player.getUUID());
            if (compatibilityVersion <= 18) {
                if (packet instanceof PlayerStatesPacket) {
                    id = Identifier.fromNamespaceAndPath(Voicechat.MODID, "player_states");
                } else if (packet instanceof PlayerStatePacket) {
                    id = Identifier.fromNamespaceAndPath(Voicechat.MODID, "player_state");
                } else if (packet instanceof RemovePlayerStatePacket) {
                    RemovePlayerStatePacket removePacket = (RemovePlayerStatePacket) packet;
                    de.maxhenkel.voicechat.voice.common.PlayerState dummyState = new de.maxhenkel.voicechat.voice.common.PlayerState(removePacket.getId(), "", false, true);
                    packetToSend = new PlayerStatePacket(dummyState);
                    id = Identifier.fromNamespaceAndPath(Voicechat.MODID, "player_state");
                }
            }
            packetToSend.toBytes(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            player.connection.send(new ClientboundCustomPayloadPacket(new DiscardedPayload(id, bytes)));
        });
    }

    public void close() {
        Set<String> incomingChannels = Bukkit.getMessenger().getIncomingChannels(VoicechatPaperPlugin.INSTANCE);
        Set<String> outgoingChannels = Bukkit.getMessenger().getOutgoingChannels(VoicechatPaperPlugin.INSTANCE);
        for (String channel : incomingChannels) {
            Bukkit.getMessenger().unregisterIncomingPluginChannel(VoicechatPaperPlugin.INSTANCE, channel);
        }
        for (String channel : outgoingChannels) {
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(VoicechatPaperPlugin.INSTANCE, channel);
        }
        packets.clear();
    }

}
