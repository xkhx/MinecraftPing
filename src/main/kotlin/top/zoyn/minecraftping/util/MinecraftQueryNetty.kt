package top.zoyn.minecraftping.util

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.timeout.ReadTimeoutHandler
import kotlinx.coroutines.CompletableDeferred
import top.zoyn.minecraftping.MinecraftPingMain
import top.zoyn.minecraftping.MinecraftPingMain.getMinecraftSRVAddress
import top.zoyn.minecraftping.packet.MinecraftPacketMessageDecoder
import top.zoyn.minecraftping.packet.MinecraftPacketMessageEncoder
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class MinecraftQueryNetty {
    private var ip = "localhost"
    private var port = 25565

    companion object {
        private val bootstrap = Bootstrap()

        init {
            val info = CompletableDeferred<String>()
            val ping = CompletableDeferred<Int>()
            bootstrap
                .group(NioEventLoopGroup())
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<Channel>() {
                    override fun initChannel(channel: Channel) {
                        channel.pipeline()
                            .addFirst("decoder", MinecraftPacketMessageDecoder())
                            .addFirst("encoder", MinecraftPacketMessageEncoder())
                            .addFirst(ReadTimeoutHandler(5000L, TimeUnit.MILLISECONDS))
                    }
                })
        }
    }

    fun address(ip: String, port: Int): MinecraftQueryNetty {
        this.ip = ip
        this.port = port
        return this
    }

    suspend fun connect(): ServerData? {
        val info = CompletableDeferred<String>()
        val ping = CompletableDeferred<Int>()
        val data = CompletableDeferred<ServerData?>()
        val address = if (port == 25565) getMinecraftSRVAddress(ip) else InetSocketAddress(ip, port)
        bootstrap
            .connect(address)
            .addListener { future ->
                if (future.isSuccess) {
                    data.complete(ServerData(info, ping))
                    val channel = (future as ChannelFuture).channel()
                    channel.pipeline()
                        .addLast("main-handler", ChannelHandler(info, ping))
                } else {
                    data.complete(null)
                }
            }
        return data.await()
    }

    class ServerData(
        private val info: CompletableDeferred<String>,
        private val ping: CompletableDeferred<Int>
    ) {
        suspend fun getServerInfo(): MinecraftQuery.StatusResponse? {
            kotlin.runCatching {
                val response = MinecraftPingMain.json.fromJson(info.await(), MinecraftQuery.StatusResponse::class.java)
                response.time = getPing()
                return response
            }
            return null
        }

        private suspend fun getPing(): Int {
            kotlin.runCatching {
                return ping.await()
            }
            return -1
        }
    }

    class ChannelHandler(
        private val info: CompletableDeferred<String>,
        private val ping: CompletableDeferred<Int>
    ) : ChannelInboundHandlerAdapter() {

        override fun channelActive(ctx: ChannelHandlerContext) {
            //握手
            handshake(ctx)
            //motd
            motd(ctx)
        }

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            if (msg is ByteBuf) {
                when (msg.readVarInt()) {
                    0 -> {
                        val length = msg.readVarInt()
                        val inByte = ByteArray(length)
                        msg.readBytes(inByte)
                        val text = String(inByte)
                        info.complete(text)
                        //motd后ping
                        ping(ctx)
                    }
                    1 -> {
                        val now = msg.readLong()
                        ping.complete((System.currentTimeMillis() - now).toInt())
                        ctx.channel().close()
                    }
                }
            }
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            info.completeExceptionally(cause)
            ping.completeExceptionally(cause)
            ctx.channel().close()
        }

        private fun handshake(ctx: ChannelHandlerContext) {
            val address = ctx.channel().remoteAddress() as InetSocketAddress
            val handshake = Unpooled.buffer()
            handshake.writeByte(0x00)
            handshake.writeVarInt(578)
            handshake.writeVarInt(address.address.hostAddress.length)
            handshake.writeBytes(address.address.hostAddress.toByteArray())
            handshake.writeShort(address.port)
            handshake.writeVarInt(1)
            ctx.writeAndFlush(handshake)
        }

        private fun motd(ctx: ChannelHandlerContext) {
            val motd = Unpooled.buffer()
            motd.writeByte(0x00)
            ctx.writeAndFlush(motd)
        }

        private fun ping(ctx: ChannelHandlerContext) {
            val ping = Unpooled.buffer()
            ping.writeByte(0x01)
            ping.writeLong(System.currentTimeMillis())
            ctx.writeAndFlush(ping)
        }
    }
}