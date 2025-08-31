package cc.mewcraft.resourcepakku.serializer

import io.leangen.geantyref.TypeToken
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.spongepowered.configurate.serialize.ScalarSerializer
import java.lang.reflect.Type
import java.util.function.Predicate
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf

object MiniMessageSerializer : ScalarSerializer<Component>(TypeToken.get(typeOf<Component>().javaType) as TypeToken<Component>) {

    override fun deserialize(type: Type, obj: Any): Component {
        return MiniMessage.miniMessage().deserialize(obj.toString().replace("§", ""))
    }

    override fun serialize(item: Component, typeSupported: Predicate<Class<*>>?): Any {
        return MiniMessage.miniMessage().serialize(item)
    }
}