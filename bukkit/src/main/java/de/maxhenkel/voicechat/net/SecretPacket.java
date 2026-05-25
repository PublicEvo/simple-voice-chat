package de.maxhenkel.voicechat.net;

import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.config.ServerConfig;
import de.maxhenkel.voicechat.plugins.PluginManager;
import de.maxhenkel.voicechat.util.FriendlyByteBuf;
import de.maxhenkel.voicechat.util.Key;
import de.maxhenkel.voicechat.voice.common.Secret;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SecretPacket implements Packet<SecretPacket> {

    public static final Key SECRET = Voicechat.compatibility.createNamespacedKey("secret");

    private Secret secret;
    private int serverPort;
    private UUID playerUUID;
    private ServerConfig.Codec codec;
    private int mtuSize;
    private double voiceChatDistance;
    private int keepAlive;
    private boolean groupsEnabled;
    private String voiceHost;
    private boolean allowRecording;

    public SecretPacket() {

    }

    public SecretPacket(Player player, Secret secret, int port, ServerConfig serverConfig) {
        this.secret = secret;
        this.serverPort = port;
        this.playerUUID = player.getUniqueId();
        this.codec = serverConfig.voiceChatCodec.get();
        this.mtuSize = serverConfig.voiceChatMtuSize.get();
        this.voiceChatDistance = serverConfig.voiceChatDistance.get();
        this.keepAlive = serverConfig.keepAlive.get();
        this.groupsEnabled = serverConfig.groupsEnabled.get();
        
        String resolvedVoiceHost = serverConfig.voiceHost.get();
        String connectedHost = "unknown";
        try {
            java.lang.reflect.Method getVirtualHostMethod = player.getClass().getMethod("getVirtualHost");
            java.net.InetSocketAddress virtualHost = (java.net.InetSocketAddress) getVirtualHostMethod.invoke(player);
            if (virtualHost != null) {
                connectedHost = virtualHost.getHostString().toLowerCase().split("\u0000")[0].split("\0")[0].split(":")[0].trim();
                String forcedHostsVal = serverConfig.forcedHosts.get();
                if (forcedHostsVal != null && !forcedHostsVal.isEmpty()) {
                    forcedHostsVal = forcedHostsVal.replace("\"", "").replace("'", "").trim();
                    String[] entries = forcedHostsVal.split(",");
                    for (String entry : entries) {
                        String[] parts = entry.split("=", 2);
                        if (parts.length == 2) {
                            String mcHost = parts[0].replace("\"", "").replace("'", "").trim().toLowerCase();
                            String voiceHostVal = parts[1].replace("\"", "").replace("'", "").trim();
                            if (connectedHost.equals(mcHost) || connectedHost.endsWith("." + mcHost)) {
                                resolvedVoiceHost = voiceHostVal;
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore / fallback to default voice_host
        }
        
        Voicechat.LOGGER.info("[voicechat] Player {} connected via host: '{}', resolved voice host: '{}'", player.getName(), connectedHost, resolvedVoiceHost);
        this.voiceHost = PluginManager.instance().getVoiceHost(player, resolvedVoiceHost);
        this.allowRecording = serverConfig.allowRecording.get();
    }

    public Secret getSecret() {
        return secret;
    }

    public int getServerPort() {
        return serverPort;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public ServerConfig.Codec getCodec() {
        return codec;
    }

    public int getMtuSize() {
        return mtuSize;
    }

    public double getVoiceChatDistance() {
        return voiceChatDistance;
    }

    public int getKeepAlive() {
        return keepAlive;
    }

    public boolean groupsEnabled() {
        return groupsEnabled;
    }

    public String getVoiceHost() {
        return voiceHost;
    }

    @Override
    public Key getID() {
        return SECRET;
    }

    public boolean allowRecording() {
        return allowRecording;
    }

    @Override
    public SecretPacket fromBytes(FriendlyByteBuf buf) {
        secret = Secret.fromBytes(buf);
        serverPort = buf.readInt();
        playerUUID = buf.readUUID();
        codec = ServerConfig.Codec.values()[buf.readByte()];
        mtuSize = buf.readInt();
        voiceChatDistance = buf.readDouble();
        keepAlive = buf.readInt();
        groupsEnabled = buf.readBoolean();
        voiceHost = buf.readUtf();
        allowRecording = buf.readBoolean();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        secret.toBytes(buf);
        buf.writeInt(serverPort);
        buf.writeUUID(playerUUID);
        buf.writeByte(codec.ordinal());
        buf.writeInt(mtuSize);
        buf.writeDouble(voiceChatDistance);
        buf.writeInt(keepAlive);
        buf.writeBoolean(groupsEnabled);
        buf.writeUtf(voiceHost);
        buf.writeBoolean(allowRecording);
    }

}
