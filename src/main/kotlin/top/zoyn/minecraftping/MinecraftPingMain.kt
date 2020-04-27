package top.zoyn.minecraftping

import com.google.gson.Gson
import kotlinx.coroutines.launch
import net.mamoe.mirai.console.plugins.PluginBase
import net.mamoe.mirai.console.plugins.withDefaultWriteSave
import net.mamoe.mirai.event.subscribeGroupMessages
import top.zoyn.minecraftping.command.MCPingCommand
import top.zoyn.minecraftping.util.MinecraftQuery
import java.net.InetSocketAddress
import java.util.*
import javax.naming.Context
import javax.naming.directory.InitialDirContext

object MinecraftPingMain : PluginBase() {

    val json = Gson()

    private val minecraftQuery = MinecraftQuery()

    private val config = loadConfig("config.yml")

    private val CommandPrefix by config.withDefaultWriteSave { "#" }
    var Timeout by config.withDefaultWriteSave { 5000 }
    val MessageTemplate by config.withDefaultWriteSave {
        "{favicon}\n" +
                "游戏版本: {version}({protocol})\n" +
                "在线人数: {online}/{max}\n" +
                "Ping: {ping}\n" +
                "{motd}"
    }

    override fun onEnable() {

        logger.info("开始加载MC查询插件")

        MCPingCommand.registerCommand()

        subscribeGroupMessages {
            startsWith(CommandPrefix, removePrefix = true) {
                val command = it.split(" ")
                val root = command[0]
                val args = command.drop(1)
                when (root.toLowerCase()) {
                    "mcping" -> {
                        if (args.isEmpty()) {
                            reply("[MCPing] 请输入#mcping help 查看帮助")
                            return@startsWith
                        }
                        if (args[0].toLowerCase() == "help") {
                            reply(
                                "[MCPing] 命令列表 >\n" +
                                        "#mcping help - 查看帮助\n" +
                                        "#mcping query [host] [netty/socket] - 查询指定mc服务器"
                            )
                            return@startsWith
                        }
                        if (args[0].toLowerCase() == "query") {
                            launch {
                                reply(minecraftQuery.queryServerStatus(subject, args))
                            }
                            return@startsWith
                        }
                        reply("[MCPing] 无效的子命令 请输入#mcping help 查看帮助")
                        return@startsWith
                    }
                }
            }
        }
        logger.info("MC查询插件加载完毕")
}

    override fun onDisable() {
        super.onDisable()
        config["Timeout"] = Timeout
        config.save()
    }

    fun getMinecraftSRVAddress(host: String): InetSocketAddress {
        kotlin.runCatching {
            Class.forName("com.sun.jndi.dns.DnsContextFactory")
            val var2 = Hashtable<String, String>()
            var2[Context.INITIAL_CONTEXT_FACTORY] = "com.sun.jndi.dns.DnsContextFactory"
            var2[Context.PROVIDER_URL] = "dns:"
            var2["com.sun.jndi.dns.timeout.retries"] = "1"
            val var3 = InitialDirContext(var2)
            val var4 = var3.getAttributes("_minecraft._tcp.$host", arrayOf("SRV"))
            val var5 = var4.get("srv").get().toString().split(" ")
            val port = try {
                var5[2].toInt()
            } catch (throwable: Throwable) {
                25565
            }
            val ip = var5[3]
            return InetSocketAddress(ip, port)
        }.onFailure {
            return InetSocketAddress(host, 25565)
        }
        return InetSocketAddress(host, 25565)
    }
}