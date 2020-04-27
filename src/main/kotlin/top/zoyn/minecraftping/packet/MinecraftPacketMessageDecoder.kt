package top.zoyn.minecraftping.packet

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.CorruptedFrameException
import top.zoyn.minecraftping.util.readVarInt

/**
 * Packet encoder for Minecraft.
 *
 * @since 2.9
 */
class MinecraftPacketMessageDecoder : ByteToMessageDecoder() {
    override fun decode(
        ctx: ChannelHandlerContext,
        msg: ByteBuf,
        out: MutableList<Any>
    ) {
        root@ while (msg.isReadable) {
            msg.markReaderIndex()
            val b = ByteArray(3)
            for (i in 0..2) {
                if (!msg.isReadable) {
                    msg.resetReaderIndex()
                    return
                }
                b[i] = msg.readByte()
                val w = b[i]
                if (w > 0) {
                    val size: Int = Unpooled.wrappedBuffer(b).readVarInt()
                    if (size > msg.readableBytes()) {
                        msg.resetReaderIndex()
                        return
                    }
                    out.add(msg.readBytes(size))
                    msg.markReaderIndex()
                    continue@root
                }
            }
            throw CorruptedFrameException("length wider than 21-bit")
        }
    }
}