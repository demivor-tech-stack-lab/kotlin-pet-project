package com.vehiclerental.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * kotlinx.serialization không biết sẵn cách đọc/ghi LocalDateTime và BigDecimal
 * (chúng là lớp của Java, không có annotation @Serializable), nên ta tự dạy nó.
 *
 * Cách dùng: đánh dấu trên thuộc tính trong DTO
 *     @Serializable(with = LocalDateTimeSerializer::class)
 *     val startAt: LocalDateTime
 */
object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME  // 2026-08-21T09:30:00

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) =
        encoder.encodeString(value.format(formatter))

    override fun deserialize(decoder: Decoder): LocalDateTime =
        LocalDateTime.parse(decoder.decodeString(), formatter)
}

/**
 * Tiền được truyền dưới dạng CHUỖI trong JSON ("450000.00").
 * Lý do: JSON number là double ở nhiều ngôn ngữ -> mất chính xác khi tính tiền.
 */
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.math.BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) =
        encoder.encodeString(value.toPlainString())

    override fun deserialize(decoder: Decoder): BigDecimal =
        BigDecimal(decoder.decodeString())
}
