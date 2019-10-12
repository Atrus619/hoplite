package com.sksamuel.hoplite.decoder

import arrow.data.NonEmptyList
import arrow.data.invalid
import arrow.data.valid
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.ArrayNode
import com.sksamuel.hoplite.TreeNode
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.arrow.flatMap
import com.sksamuel.hoplite.arrow.sequence
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType

class NonEmptyListDecoder : NonNullableDecoder<NonEmptyList<*>> {

  override fun supports(type: KType): Boolean = type.isSubtypeOf(NonEmptyList::class.starProjectedType)

  override fun safeDecode(value: TreeNode,
                          type: KType,
                          registry: DecoderRegistry): ConfigResult<NonEmptyList<*>> {
    require(type.arguments.size == 1)
    val t = type.arguments[0].type!!

    fun <T> decode(node: StringNode, decoder: Decoder<T>): ConfigResult<NonEmptyList<T>> {
      return node.value.split(",").map { it.trim() }
        .map { decoder.decode(StringNode(it, node.pos), type, registry) }.sequence()
        .leftMap { ConfigFailure.CollectionElementErrors(node, it) }
        .map { NonEmptyList.fromListUnsafe(it) }
    }

    fun <T> decode(node: ArrayNode, decoder: Decoder<T>): ConfigResult<NonEmptyList<T>> {
      return node.elements.map { decoder.decode(it, type, registry) }.sequence()
        .leftMap { ConfigFailure.CollectionElementErrors(node, it) }
        .flatMap { ts ->
        NonEmptyList.fromList(ts).fold(
          { ConfigFailure.DecodeError(node, type).invalid() },
          { it.valid() }
        )
      }
    }

    return registry.decoder(t).flatMap { decoder ->
      when (value) {
        is StringNode -> decode(value, decoder)
        is ArrayNode -> decode(value, decoder)
        else -> ConfigFailure.DecodeError(value, type).invalid()
      }
    }
  }
}
