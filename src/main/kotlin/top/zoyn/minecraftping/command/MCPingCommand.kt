package top.zoyn.minecraftping.command

import net.mamoe.mirai.console.command.registerCommand
import top.zoyn.minecraftping.MinecraftPingMain

object MCPingCommand {
    fun registerCommand() {
        MinecraftPingMain.registerCommand {
            name = "mcping"
            alias = listOf("minecraftping")
            description = "我的世界服务器查询命令"
            usage = "我的世界服务器查询管理命令\n" +
                    "/mcping timeout [time] - 设置查询超时(单位毫秒)"
            onCommand {
                if (it.isEmpty()) {
                    return@onCommand false
                }
                when (it[0].toLowerCase()) {
                    "timeout" -> {
                        if (it.size < 2) {
                            this.sendMessage("[MCPing] 正确的指令 /mcping timeout [time]")
                            return@onCommand true
                        }
                        kotlin.runCatching {
                            it[0].toInt()
                        }.onSuccess { time ->
                            MinecraftPingMain.Timeout = time
                        }.onFailure {
                            this.sendMessage("[MCPing] 请输入正确的时间")
                            return@onCommand true
                        }
                        this.sendMessage("[MCPing] 成功设置查询超时为${it[1]}ms")
                    }
                    else -> {
                        return@onCommand false
                    }
                }
                return@onCommand true
            }
        }
    }
}