package top.zoyn.minecraftping.util

import com.google.gson.internal.LinkedTreeMap
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.uploadImage
import sun.misc.BASE64Decoder
import top.zoyn.minecraftping.MinecraftPingMain
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.ArrayList
import java.util.regex.Pattern

class MinecraftQuery {

    companion object {
        val minecraftQuerySocket = MinecraftQuerySocket()
        val minecraftQueryNetty = MinecraftQueryNetty()
        private val STRIP_COLOR_PATTERN: Pattern = Pattern.compile("(?i)§[0-9A-FK-OR]")
    }

    private suspend fun doTemplateReplacement(subject: Contact, response: StatusResponse): MessageChain {
        val favicon = response.getFavicon()
        val version = response.version.getName()
        val protocol = response.version.protocol
        val online = response.players.online
        val max = response.players.max
        val ping = response.time
        val motd = response.getDescription() ?: "获取Motd失败"
        var template = MinecraftPingMain.MessageTemplate
        template = template.replace("{version}", version)
            .replace("{protocol}", protocol.toString())
            .replace("{online}", online.toString())
            .replace("{max}", max.toString())
            .replace("{motd}", motd)
        template = if (ping == -1) {
            template.replace("{ping}", "超时")
        } else {
            template.replace("{ping}", "${ping}ms")
        }
        val messageChain = MessageChainBuilder()
        if (!template.contains("{favicon}") || favicon == null) {
            template = template.replace("{favicon}\n", "")
            messageChain.add(template)
            return messageChain.build()
        }
        with(template.split("{favicon}")) {
            messageChain.add(this[0].toMessage() + subject.uploadImage(favicon) + this[1])
        }
        return messageChain.build()
    }

    suspend fun queryServerStatus(subject: Contact, args: List<String>): Message {
        if (args.size >= 2) {
            val query = args[1].split(":")
            val host = query[0]
            var port = 25565
            if (query.size > 1) {
                kotlin.runCatching {
                    query[1].toInt()
                }.onSuccess { hostPort ->
                    port = hostPort
                }.onFailure {
                    return PlainText("[MCPing] 请输入正确的端口")
                }
            }
            if (args.size > 2 && args[2].toLowerCase() == "socket") {
                val response =
                    try {
                        minecraftQuerySocket.pingServer(host, port, MinecraftPingMain.Timeout) ?: kotlin.run {
                            return PlainText("[MCPing] 连接超时或无效的主机名")
                        }
                    } catch (throwable: Throwable) {
                        return PlainText("[MCPing] 连接超时或无效的主机名")
                    }
                return doTemplateReplacement(subject, response)
            }
            val data = minecraftQueryNetty
                .address(host, port)
                .connect() ?: kotlin.run {
                return PlainText("[MCPing] 连接目标服务器失败")
            }
            val response = data.getServerInfo() ?: kotlin.run {
                return PlainText("[MCPing] 连接超时或无效的主机名")
            }
            return doTemplateReplacement(subject, response)
        } else {
            return PlainText("[MCPing] 正确的命令#mcping query [ip]")
        }
    }

    data class StatusResponse(
        private val description: Any,
        val players: Players,
        val version: Version,
        private val favicon: String?,
        var time: Int
    ) {
        fun getFavicon(): InputStream? {
            if (favicon == null) return null
            val byteArray = BASE64Decoder()
                .decodeBuffer(favicon.trim().split(",")[1])
            return ByteArrayInputStream(byteArray)
        }

        fun getDescription(): String? {
            if (description is LinkedTreeMap<*, *>) {
                if (!description.contains("extra")) {
                    return STRIP_COLOR_PATTERN.matcher(description["text"].toString())
                        .replaceAll("")
                }
                val extra = description["extra"]
                val desc = StringBuilder()
                if (extra is ArrayList<*>) {
                    for (text in extra) {
                        if (text is LinkedTreeMap<*, *>) {
                            desc.append(text["text"])
                        }
                    }
                }
                return desc.toString()
            }
            return STRIP_COLOR_PATTERN.matcher(description.toString())
                .replaceAll("")
        }
    }

    data class Players(
        val max: Int,
        val online: Int
    )

    data class Version(
        private val name: String,
        val protocol: Int
    ) {
        fun getName(): String {
            return STRIP_COLOR_PATTERN.matcher(name)
                .replaceAll("")
        }
    }
}