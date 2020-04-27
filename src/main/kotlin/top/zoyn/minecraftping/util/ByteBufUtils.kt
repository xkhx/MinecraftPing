package top.zoyn.minecraftping.util

import io.netty.buffer.ByteBuf

fun ByteBuf.readVarInt(): Int {
    var i = 0
    var j = 0
    while (true) {
        val k = this.readByte().toInt()
        i = i or (k and 0x7F shl j++ * 7)
        if (j > 5) throw RuntimeException("ValInt too big")
        if ((k and 0x80) != 128) break
    }
    return i
}

fun ByteBuf.writeVarInt(paramInt: Int) {
    var param = paramInt
    while (true) {
        if (param and -0x80 == 0) {
            this.writeByte(param)
            return
        }
        this.writeByte(param and 0x7F or 0x80)
        param = param ushr 7
    }
}