package top.zoyn.minecraftping.packet

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import top.zoyn.minecraftping.util.writeVarInt

/**
 * Packet encoder for Minecraft.
 *
 * @since 2.9
 */
class MinecraftPacketMessageEncoder : MessageToByteEncoder<ByteBuf>() {
    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        val bytes = msg.readableBytes()
        val size: Int = `$unknown_a`(bytes)
        require(size <= 3) { "unable to fit $size into 3" }
        out.ensureWritable(bytes + size)
        out.writeVarInt(bytes)
        out.writeBytes(msg, msg.readerIndex(), bytes)
    }

    private fun `$unknown_a`(i: Int): Int {
        for (j in 1..4) {
            if (i and (-1 shl (j * 7)) == 0) {
                return j
            }
        }
        return 5
    }
}