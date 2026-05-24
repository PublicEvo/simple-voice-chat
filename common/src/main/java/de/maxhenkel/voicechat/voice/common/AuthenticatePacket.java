package de.maxhenkel.voicechat.voice.common;

import de.maxhenkel.voicechat.Voicechat;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public class AuthenticatePacket implements Packet<AuthenticatePacket> {

    private UUID playerUUID;
    private Secret secret;

    public AuthenticatePacket(UUID playerUUID, Secret secret) {
        this.playerUUID = playerUUID;
        this.secret = secret;
    }

    public AuthenticatePacket() {

    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public Secret getSecret() {
        return secret;
    }

    @Override
    public AuthenticatePacket fromBytes(FriendlyByteBuf buf) {
        AuthenticatePacket packet = new AuthenticatePacket();
        packet.playerUUID = buf.readUUID();
        int version = Voicechat.SERVER != null ? Voicechat.SERVER.getCompatibilityVersion(packet.playerUUID) : -1;
        int size = Secret.getSecretSize(version);
        packet.secret = Secret.fromBytes(buf, size, version);
        return packet;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(playerUUID);
        secret.toBytes(buf);
    }
}
