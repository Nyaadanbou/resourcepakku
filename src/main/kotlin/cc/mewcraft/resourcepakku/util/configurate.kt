@file:JvmName("ConfigurateTools")

package cc.mewcraft.resourcepakku.util

import io.leangen.geantyref.TypeToken
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.RepresentationHint
import org.spongepowered.configurate.serialize.TypeSerializer
import org.spongepowered.configurate.serialize.TypeSerializerCollection
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf

inline fun <reified T> javaTypeOf(): Type =
    typeOf<T>().javaType

inline fun <reified T> typeTokenOf(): TypeToken<T> =
    TypeToken.get(javaTypeOf<T>()) as TypeToken<T>

internal inline fun <reified T> ConfigurationNode.require(): T =
    require(typeOf<T>().javaType) as T

internal inline fun <reified V> representationHint(key: String): RepresentationHint<V> =
    RepresentationHint.of<V>(key, TypeToken.get(typeOf<V>().javaType) as TypeToken<V>)

internal inline fun <reified T> TypeSerializerCollection.Builder.register(serializer: TypeSerializer<T>): TypeSerializerCollection.Builder =
    this.register(typeTokenOf<T>(), serializer)

internal fun <T : Any> TypeSerializerCollection.Builder.register(type: KClass<T>, serializer: TypeSerializer<T>): TypeSerializerCollection.Builder =
    this.register(type.java, serializer)

internal inline fun <reified T> TypeSerializerCollection.Builder.registerExact(serializer: TypeSerializer<T>): TypeSerializerCollection.Builder =
    this.registerExact(typeTokenOf<T>(), serializer)

internal fun <T : Any> TypeSerializerCollection.Builder.registerExact(type: KClass<T>, serializer: TypeSerializer<T>): TypeSerializerCollection.Builder =
    this.registerExact(type.java, serializer)
