package io.github.takusan23.zerowebm.parser

import java.io.File

/*
 * Kotlinのシフト演算子
 * [shl] <<
 * [shr] >>
 */

/** WebMパーサー */

fun main() {
    // 適当にWebMのパスを
    val bytes = File("""""").readBytes()
    val elementList = arrayListOf<MatroskaElement>()
    // トップレベルのパース位置
    // EBML Segment Cluster など
    var topLevelReadPos = 0

    // EBMLを読み出す
    val ebmlElement = parseElement(bytes, 0)
    topLevelReadPos += ebmlElement.elementSize
    elementList.addAll(parseChildElement(ebmlElement.data))

    // Segmentを読み出す
    val segmentElement = parseElement(bytes, topLevelReadPos)
    topLevelReadPos += segmentElement.elementSize
    elementList.addAll(parseChildElement(segmentElement.data))

    // 解析結果を出力
    elementList.forEach {
        println("${it.tag} = ${it.data.take(10).toByteArray().toHexString()}")
    }
}

/**
 * 子要素をパースする
 *
 * @param byteArray バイナリ
 */
fun parseChildElement(byteArray: ByteArray): List<MatroskaElement> {
    val childElementList = arrayListOf<MatroskaElement>()
    var readPos = 0
    while (byteArray.size > readPos) {
        val element = parseElement(byteArray, readPos)
        // 親要素があれば子要素をパースしていく
        when (element.tag) {
            MatroskaTags.SeekHead -> childElementList += parseChildElement(element.data)
            MatroskaTags.Info -> childElementList += parseChildElement(element.data)
            MatroskaTags.Tracks -> childElementList += parseChildElement(element.data)
            MatroskaTags.Track -> childElementList += parseChildElement(element.data)
            MatroskaTags.VideoTrack -> childElementList += parseChildElement(element.data)
            MatroskaTags.AudioTrack -> childElementList += parseChildElement(element.data)
            MatroskaTags.Cues -> childElementList += parseChildElement(element.data)
            MatroskaTags.CuePoint -> childElementList += parseChildElement(element.data)
            MatroskaTags.CueTrackPositions -> childElementList += parseChildElement(element.data)
            MatroskaTags.Cluster -> childElementList += parseChildElement(element.data)
            // 親要素ではなく子要素の場合は配列に入れる
            else -> childElementList += element
        }
        readPos += element.elementSize
    }
    return childElementList
}

/**
 * EBMLをパースする
 *
 * @param byteArray [ByteArray]
 * @param startPos 読み出し開始位置
 */
fun parseElement(byteArray: ByteArray, startPos: Int): MatroskaElement {
    var readPos = startPos
    val idLength = byteArray[readPos].getVIntSize()
    // IDのバイト配列
    val idBytes = byteArray.copyOfRange(readPos, readPos + idLength)
    val idElement = MatroskaTags.find(idBytes)!!
    readPos += idBytes.size
    // DataSize部
    val dataSizeLength = byteArray[readPos].getVIntSize()
    val dataSizeBytes = byteArray.copyOfRange(readPos, readPos + dataSizeLength)
    val dataSize = dataSizeBytes.toDataSize()
    readPos += dataSizeBytes.size
    // Dataを読み出す。
    // 長さが取得できた場合とそうじゃない場合で...
    return if (dataSize != -1) {
        // Data部
        val dataBytes = byteArray.copyOfRange(readPos, readPos + dataSize)
        readPos += dataSize
        MatroskaElement(idElement, dataBytes, readPos - startPos)
    } else {
        // もし -1 (長さ不定)の場合は全部取得するようにする
        // ただし全部取得すると壊れるので、直さないといけない
        val dataBytes = byteArray.copyOfRange(readPos, byteArray.size)
        readPos += dataBytes.size
        MatroskaElement(idElement, dataBytes, readPos - startPos)
    }
}

/** DataSizeの長さが不定の場合 */
private val DATASIZE_UNDEFINED = byteArrayOf(0x1F.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

/**
 * DataSizeを計算する。
 * だたし、長さ不定の場合（[MatroskaTags.Segment]、[MatroskaTags.Cluster]）の場合、[-1]を返す
 *
 * 例
 * 0x82 -> 0x02
 * 0x42 0x10 -> 0x02 0x10
 */
fun ByteArray.toDataSize(): Int {
    var first = first().toInt().andFF()
    // 例外で、 01 FF FF FF FF FF FF FF のときは長さが不定なので...
    // Segment / Cluster の場合は子要素の長さを全部足せば出せると思うので、、、
    if (contentEquals(DATASIZE_UNDEFINED)) {
        return -1
    }
    // 左から数えて最初の1ビット を消す処理
    // 例
    // 0b1000_0000 なら 0b1xxx_xxxx の x の範囲が数値になる
    // break したかったので for
    for (i in 0..8) {
        if ((first and (1 shl (8 - i))) != 0) {
            // 多分
            // 0b1000_1000 XOR 0b0000_1000 みたいなのをやってるはず
            first = first xor (1 shl (8 - i))
            break
        }
    }
    return (byteArrayOf(first.toByte()) + this.drop(1)).toInt()
}

/** ByteArray から Int へ変換する。ByteArray 内にある Byte は符号なしに変換される。 */
fun ByteArray.toInt(): Int {
    // 先頭に 0x00 があれば消す
    val validValuePos = indexOfFirst { it != 0x00.toByte() }
    var result = 0
    // 逆にする
    // これしないと左側にバイトが移動するようなシフト演算？になってしまう
    // for を 多い順 にすればいいけどこっちの方でいいんじゃない
    drop(validValuePos).reversed().also { bytes ->
        for (i in 0 until bytes.count()) {
            result = result or (bytes.get(i).toInt().andFF() shl (8 * i))
        }
    }
    return result
}

/**
 * VIntを出す
 * 後続バイトの長さを返します。失敗したら -1 を返します
 */
fun Byte.getVIntSize(): Int {
    // JavaのByteは符号付きなので、UIntにする必要がある。AND 0xFF すると UInt にできる
    val int = this.toInt().andFF()
    // 以下のように
    // 1000_0000 -> 1xxx_xxxx
    // 0100_0000 -> 01xx_xxxx_xxxx_xxxx
    for (i in 7 downTo 0) {
        if ((int and (1 shl i)) != 0) {
            return 8 - i
        }
    }
    return -1
}

/** ByteをIntに変換した際に、符号付きIntになるので、AND 0xFF するだけの関数 */
fun Int.andFF() = this and 0xFF

/** 16進数に変換するやつ */
private fun ByteArray.toHexString() = this.joinToString(separator = " ") { "%02x".format(it) }

