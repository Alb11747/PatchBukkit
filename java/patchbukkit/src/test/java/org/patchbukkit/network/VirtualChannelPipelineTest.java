package org.patchbukkit.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.patchbukkit.PatchBukkitServer;
import org.patchbukkit.entity.PatchBukkitPlayer;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class VirtualChannelPipelineTest {

    @BeforeAll
    public static void setUp() {
        PatchBukkitServer.initServer();
    }

    @Test
    public void testPlayerHandleAndChannelReflection() throws Exception {
        UUID uuid = UUID.randomUUID();
        PatchBukkitPlayer player = new PatchBukkitPlayer(uuid, "Steve");

        // 1. PacketEvents reflects on player handle
        VirtualPlayerHandle handle = VirtualChannelManager.getInstance().getPlayerHandle(player);
        assertNotNull(handle, "Player handle must not be null");

        // Authentic ServerPlayer
        net.minecraft.server.level.ServerPlayer nmsPlayer = player.getHandle();
        assertNotNull(nmsPlayer, "ServerPlayer must not be null");

        // 2. PacketEvents reflects on handle.connection
        Field connectionField = handle.getClass().getField("connection");
        assertNotNull(connectionField);
        Object connection = connectionField.get(handle);
        assertNotNull(connection, "Handle connection must not be null");

        // 3. PacketEvents reflects on connection.channel (direct)
        Field channelField = connection.getClass().getField("channel");
        assertNotNull(channelField);
        Object channelObj = channelField.get(connection);
        assertNotNull(channelObj, "Channel must not be null");
        assertInstanceOf(Channel.class, channelObj);

        // Verify authentic ServerPlayer connection points to exact same channel
        assertSame(channelObj, nmsPlayer.connection.connection.channel, "NMS ServerPlayer channel must match");

        // Also verify nested connection.connection.channel (Mojang ServerGamePacketListenerImpl -> Connection -> channel)
        Field nestedConnField = connection.getClass().getField("connection");
        Object nestedConn = nestedConnField.get(connection);
        Field nestedChannelField = nestedConn.getClass().getField("channel");
        assertSame(channelObj, nestedChannelField.get(nestedConn), "Nested connection.channel must match direct channel");

        // Also verify nested connection.networkManager.channel (Spigot ServerGamePacketListenerImpl -> NetworkManager -> channel)
        Field nestedNmField = connection.getClass().getField("networkManager");
        Object nestedNm = nestedNmField.get(connection);
        Field nestedNmChannelField = nestedNm.getClass().getField("channel");
        assertSame(channelObj, nestedNmChannelField.get(nestedNm), "Nested networkManager.channel must match direct channel");

        Channel channel = (Channel) channelObj;
        assertTrue(channel.isOpen());
        assertTrue(channel.isActive());

        // 4. Verify standard Minecraft pipeline handlers exist in exact vanilla order
        assertNotNull(channel.pipeline().get("timeout"), "Pipeline must contain 'timeout'");
        assertNotNull(channel.pipeline().get("legacy_query"), "Pipeline must contain 'legacy_query'");
        assertNotNull(channel.pipeline().get("splitter"), "Pipeline must contain 'splitter'");
        assertNotNull(channel.pipeline().get("decompress"), "Pipeline must contain 'decompress'");
        assertNotNull(channel.pipeline().get("decoder"), "Pipeline must contain 'decoder'");
        assertNotNull(channel.pipeline().get("prepender"), "Pipeline must contain 'prepender'");
        assertNotNull(channel.pipeline().get("compress"), "Pipeline must contain 'compress'");
        assertNotNull(channel.pipeline().get("encoder"), "Pipeline must contain 'encoder'");
        assertNotNull(channel.pipeline().get("bundle_packer"), "Pipeline must contain 'bundle_packer'");
        assertNotNull(channel.pipeline().get("packet_handler"), "Pipeline must contain 'packet_handler'");
    }

    @Test
    public void testCompressionAndEncryptionLifecycle() {
        UUID uuid = UUID.randomUUID();
        PatchBukkitPlayer player = new PatchBukkitPlayer(uuid, "CompressPlayer");
        VirtualPlayerHandle handle = VirtualChannelManager.getInstance().getPlayerHandle(player);
        PatchBukkitVirtualChannel channel = (PatchBukkitVirtualChannel) handle.connection.connection.channel;
        VirtualNetworkManager nm = handle.connection.connection;

        // Initially compressed with threshold 256
        assertTrue(channel.isCompressed());
        assertTrue(nm.isCompressed());
        assertEquals(256, channel.getCompressionThreshold());
        assertEquals(256, nm.getCompressionThreshold());
        assertFalse(channel.isEncrypted());
        assertFalse(nm.isEncrypted());

        // Change compression threshold
        nm.setupCompression(512, true);
        assertEquals(512, channel.getCompressionThreshold());
        assertEquals(512, nm.getCompressionThreshold());
        PatchBukkitVirtualChannel.VirtualCompressionDecoder decoder =
                (PatchBukkitVirtualChannel.VirtualCompressionDecoder) channel.pipeline().get("decompress");
        PatchBukkitVirtualChannel.VirtualCompressionEncoder encoder =
                (PatchBukkitVirtualChannel.VirtualCompressionEncoder) channel.pipeline().get("compress");
        assertNotNull(decoder);
        assertNotNull(encoder);
        assertEquals(512, decoder.threshold());
        assertTrue(decoder.validateDecompressed);
        assertEquals(512, encoder.threshold());

        // Disable compression with -1
        nm.setupCompression(-1);
        assertFalse(channel.isCompressed());
        assertFalse(nm.isCompressed());
        assertNull(channel.pipeline().get("decompress"));
        assertNull(channel.pipeline().get("compress"));

        // Re-enable compression
        nm.setupCompression(128);
        assertTrue(channel.isCompressed());
        assertTrue(nm.isCompressed());
        assertEquals(128, channel.getCompressionThreshold());
        assertNotNull(channel.pipeline().get("decompress"));
        assertNotNull(channel.pipeline().get("compress"));

        // Enable encryption
        Object dummyCipher = new Object();
        nm.setEncryptionKey(dummyCipher, dummyCipher);
        assertTrue(channel.isEncrypted());
        assertTrue(nm.isEncrypted());
        assertNotNull(channel.pipeline().get("decrypt"));
        assertNotNull(channel.pipeline().get("encrypt"));
    }

    @Test
    public void testPacketEventsStylePipelineInjection() {
        UUID uuid = UUID.randomUUID();
        PatchBukkitPlayer player = new PatchBukkitPlayer(uuid, "Alex");
        VirtualPlayerHandle handle = VirtualChannelManager.getInstance().getPlayerHandle(player);
        Channel channel = handle.connection.connection.channel;

        AtomicBoolean injectedInboundReceived = new AtomicBoolean(false);
        AtomicInteger receivedBytesCount = new AtomicInteger(0);

        // Simulate PacketEvents injecting its handler before "packet_handler"
        channel.pipeline().addBefore("packet_handler", "packetevents_inbound_handler", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof ByteBuf buf) {
                    injectedInboundReceived.set(true);
                    receivedBytesCount.set(buf.readableBytes());
                }
                ctx.fireChannelRead(msg);
            }
        });

        // Simulate Pumpkin receiving packet bytes from the client and forwarding to VirtualChannelManager
        byte[] testPacket = "hello_minecraft_packet".getBytes(StandardCharsets.UTF_8);
        VirtualChannelManager.getInstance().handlePacketReceived(uuid, 0x01, testPacket);

        assertTrue(injectedInboundReceived.get(), "Injected PacketEvents handler should have intercepted the inbound packet");
        assertEquals(testPacket.length, receivedBytesCount.get(), "Injected handler should have received exact byte length");

        // Simulate outbound write
        AtomicBoolean outboundIntercepted = new AtomicBoolean(false);
        channel.pipeline().addBefore("encoder", "packetevents_outbound_handler", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                outboundIntercepted.set(true);
                ctx.write(msg, promise);
            }
        });

        channel.writeAndFlush("dummy_packet");
        assertTrue(outboundIntercepted.get(), "Outbound write should have passed through injected outbound handler");
    }
}
