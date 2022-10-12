package io.github.takusan23.zerowebm.writer

import io.github.takusan23.zerowebm.MatroskaTags

/**
 * EBML要素を作成する
 *
 * @param tagId タグ
 * @param byteArray 実際のデータ
 * @param dataSize DataSize。エンコード済み
 */
data class MatroskaBuildElement(
    val tagId: MatroskaTags,
    val byteArray: ByteArray,
    val dataSize: ByteArray = byteArray.calcDataSize(),
) {

    /** [tagId] + [dataSize] + [byteArray] を繋げたバイト配列を返す */
    fun concat() = tagId.byteArray + dataSize + byteArray

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MatroskaBuildElement

        if (tagId != other.tagId) return false
        if (!byteArray.contentEquals(other.byteArray)) return false
        if (!dataSize.contentEquals(other.dataSize)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tagId.hashCode()
        result = 31 * result + byteArray.contentHashCode()
        result = 31 * result + dataSize.contentHashCode()
        return result
    }

}

/** [ByteArray]の長さを求めて、DataSizeを作成する */
private fun ByteArray.calcDataSize(): ByteArray {
    // IntをByteArrayにする
    // TODO これだと 1 でも 0x00 0x00 0x00 0x01 と無駄なパディングが入ってしまう
    val dataSizeByteArray = this.size.toByteArray()
    val first = dataSizeByteArray.first()
    // データサイズ自体も可変長なので、何バイト分がデータサイズなのか記述する
    // V_INT とかいうやつで、1が先頭から何番目に立ってるかで残りのバイト数が分かるようになってる
    // 1000 0000 -> 7 ビット ( 1xxx xxxx )
    // 0100 0000 -> 14 ビット ( 01xx xxxx xxxx xxxx )
    val dataSizeBytesSize = when (dataSizeByteArray.size) {
        1 -> 0b1000_0000
        2 -> 0b0100_0000
        3 -> 0b0010_0000
        4 -> 0b0001_0000
        5 -> 0b0000_1000
        6 -> 0b0000_0100
        7 -> 0b0000_0010
        else -> 0b0000_0001
    }
    // データサイズのバイトの先頭に V_INT のやつを OR する
    val dataSize = dataSizeByteArray.apply {
        this[0] = (dataSizeBytesSize or first.toInt()).toByte()
    }
    return dataSize
}

/** [Int]を[ByteArray]に変換する */
private fun Int.toByteArray() = byteArrayOf(
    (this shr 24).toByte(),
    (this shr 16).toByte(),
    (this shr 8).toByte(),
    this.toByte(),
)
