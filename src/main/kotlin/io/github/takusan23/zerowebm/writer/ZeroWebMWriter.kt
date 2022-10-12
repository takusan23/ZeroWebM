package io.github.takusan23.zerowebm.writer

import io.github.takusan23.zerowebm.MatroskaTags
import java.io.File

/*
 * Kotlinのシフト演算子
 * [shl] <<
 * [shr] >>
 */

/** サイズが不明 */
private val UNKNOWN_SIZE = byteArrayOf(0x01, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

/** TrackType が Video */
private const val VIDEO_TRACK_TYPE = 1

/** TrackType が Audio */
private const val AUDIO_TRACK_TYPE = 2

/** キーフレームなら */
private const val SIMPLE_BLOCK_FLAGS_KEYFRAME = 0x80

/** キーフレームじゃない */
private const val SIMPLE_BLOCK_FLAGS = 0x00

/** WebMライター */

fun main() {
    val ebmlHeader = createEbmlHeader()

    val info = createInfo()
    val tracks = createTracks()
    val cluster = createStreamingCluster()
    val segment = MatroskaBuildElement(MatroskaTags.Segment, info.concat() + tracks.concat() + cluster.concat())

    // EBML Header + Segment 書き込み
    File("empty.webm").apply {
        appendBytes(ebmlHeader.concat())
        appendBytes(segment.concat())
    }
}

/** EBMLヘッダーを作成する */
private fun createEbmlHeader(): MatroskaBuildElement {
    // WebMファイルの先頭にある EBML Header を作る
    // 子要素を作成する
    val ebmlVersion = MatroskaBuildElement(MatroskaTags.EBMLVersion, byteArrayOf(0x01))
    val readVersion = MatroskaBuildElement(MatroskaTags.EBMLReadVersion, byteArrayOf(0x01))
    val maxIdLength = MatroskaBuildElement(MatroskaTags.EBMLMaxIDLength, byteArrayOf(0x04))
    val maxSizeLength = MatroskaBuildElement(MatroskaTags.EBMLMaxSizeLength, byteArrayOf(0x08))
    val docType = MatroskaBuildElement(MatroskaTags.DocType, "webm".toAscii())
    val docTypeVersion = MatroskaBuildElement(MatroskaTags.DocTypeVersion, byteArrayOf(0x02))
    val docTypeReadVersion = MatroskaBuildElement(MatroskaTags.DocTypeReadVersion, byteArrayOf(0x02))

    // EBML Header 要素
    val children = ebmlVersion.concat() + readVersion.concat() + maxIdLength.concat() + maxSizeLength.concat() + docType.concat() + docTypeVersion.concat() + docTypeReadVersion.concat()
    return MatroskaBuildElement(MatroskaTags.EBML, children)
}

/** Infoを作成する */
private fun createInfo(): MatroskaBuildElement {
    val timestampScale = MatroskaBuildElement(MatroskaTags.TimestampScale, 1_000_000.to4ByteArray())
    val multiplexingAppName = MatroskaBuildElement(MatroskaTags.MuxingApp, "ZeroWebM".toAscii())
    val writingAppName = MatroskaBuildElement(MatroskaTags.WritingApp, "ZeroWebM".toAscii())
    val children = timestampScale.concat() + multiplexingAppName.concat() + writingAppName.concat()
    return MatroskaBuildElement(MatroskaTags.Info, children)
}

/** Track要素を作成する */
private fun createTracks(
    videoTrackId: Int = 1,
    videoCodec: String = "V_VP9",
    videoWidth: Int = 1280,
    videoHeight: Int = 720,
    audioTrackId: Int = 2,
    audioCodec: String = "O_OPUS",
    audioSamplingRate: Float = 48_000.0f, // Floatなの！？
    audioChannelCount: Int = 2,
): MatroskaBuildElement {

    // 動画トラック情報
    val videoTrackNumber = MatroskaBuildElement(MatroskaTags.TrackNumber, videoTrackId.toByteArray())
    val videoTrackUid = MatroskaBuildElement(MatroskaTags.TrackUID, videoTrackId.toByteArray())
    val videoCodecId = MatroskaBuildElement(MatroskaTags.CodecID, videoCodec.toAscii())
    val videoTrackType = MatroskaBuildElement(MatroskaTags.TrackType, VIDEO_TRACK_TYPE.toByteArray())
    val pixelWidth = MatroskaBuildElement(MatroskaTags.PixelWidth, videoWidth.toByteArray())
    val pixelHeight = MatroskaBuildElement(MatroskaTags.PixelHeight, videoHeight.toByteArray())
    val videoTrack = MatroskaBuildElement(MatroskaTags.VideoTrack, pixelWidth.concat() + pixelHeight.concat())
    val videoTrackEntryChildren = videoTrackNumber.concat() + videoTrackUid.concat() + videoCodecId.concat() + videoTrackType.concat() + videoTrack.concat()
    val videoTrackEntry = MatroskaBuildElement(MatroskaTags.Track, videoTrackEntryChildren)

    // 音声トラック情報
    val audioTrackNumber = MatroskaBuildElement(MatroskaTags.TrackNumber, audioTrackId.toByteArray())
    val audioTrackUid = MatroskaBuildElement(MatroskaTags.TrackUID, audioTrackId.toByteArray())
    val audioCodecId = MatroskaBuildElement(MatroskaTags.CodecID, audioCodec.toAscii())
    val audioTrackType = MatroskaBuildElement(MatroskaTags.TrackType, AUDIO_TRACK_TYPE.toByteArray())
    // Segment > Tracks > Audio の CodecPrivate に入れる中身
    // OpusHeaderをつくる
    // https://www.rfc-editor.org/rfc/rfc7845
    // Version = 0x01
    // Channel Count = 0x02
    // Pre-Skip = 0x00 0x00
    // Input Sample Rate ( little endian ) 0x80 0xBB 0x00 0x00 . Kotlin は Big endian なので反転する
    // Output Gain 0x00 0x00
    // Mapping Family 0x00
    // ??? 0x00 0x00
    val opusHeader = "OpusHead".toAscii() + byteArrayOf(1.toByte()) + byteArrayOf(audioChannelCount.toByte()) + byteArrayOf(0x00.toByte(), 0x00.toByte()) + audioSamplingRate.toInt().toByteArray().reversed() + byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())
    val codecPrivate = MatroskaBuildElement(MatroskaTags.CodecPrivate, opusHeader)
    // Float を ByteArray にするにはひと手間必要
    val sampleFrequency = MatroskaBuildElement(MatroskaTags.SamplingFrequency, audioSamplingRate.toBits().to4ByteArray())
    val channels = MatroskaBuildElement(MatroskaTags.Channels, audioChannelCount.toByteArray())
    val audioTrack = MatroskaBuildElement(MatroskaTags.AudioTrack, channels.concat() + sampleFrequency.concat())
    val audioTrackEntryValue = audioTrackNumber.concat() + audioTrackUid.concat() + audioCodecId.concat() + audioTrackType.concat() + codecPrivate.concat() + audioTrack.concat()
    val audioTrackEntry = MatroskaBuildElement(MatroskaTags.Track, audioTrackEntryValue)

    // Tracks を作る
    return MatroskaBuildElement(MatroskaTags.Tracks, videoTrackEntry.concat() + audioTrackEntry.concat())
}

/**
 * Clusterの中に入れるSimpleBlockを作る
 *
 * @param trackNumber トラック番号、映像なのか音声なのか
 * @param simpleBlockTimescale エンコードしたデータの時間
 * @param byteArray エンコードされたデータ
 * @param isKeyFrame キーフレームの場合は true
 */
private fun createSimpleBlock(
    trackNumber: Int,
    simpleBlockTimescale: Int,
    byteArray: ByteArray,
    isKeyFrame: Boolean,
): MatroskaBuildElement {
    val vIntTrackNumberBytes = trackNumber.toVInt()
    val simpleBlockBytes = simpleBlockTimescale.toByteArray()
    // flags。キーフレームかどうかぐらいしか入れることなさそう
    val flagsBytes = byteArrayOf((if (isKeyFrame) SIMPLE_BLOCK_FLAGS_KEYFRAME else SIMPLE_BLOCK_FLAGS).toByte())
    // エンコードしたデータの先頭に、
    // トラック番号、時間、キーフレームかどうか を付け加える
    val simpleBlockValue = vIntTrackNumberBytes + simpleBlockBytes + flagsBytes + byteArray

    return MatroskaBuildElement(MatroskaTags.SimpleBlock, simpleBlockValue)
}

/**
 * ストリーミング可能な Cluster を作成する。
 * データサイズが不定になっている。
 *
 * @param timescaleMs 開始時間。ミリ秒
 */
private fun createStreamingCluster(timescaleMs: Int = 0): MatroskaBuildElement {
    val timescaleBytes = timescaleMs.to4ByteArray()
    val timescale = MatroskaBuildElement(MatroskaTags.Timestamp, timescaleBytes)
    val clusterValue = timescale.concat()

    return MatroskaBuildElement(MatroskaTags.Cluster, clusterValue, UNKNOWN_SIZE)
}

/** 数値を V_INT でエンコードする */
private fun Int.toVInt(): ByteArray {
    val valueByteArray = this.toByteArray()
    val valueSize = when (valueByteArray.size) {
        1 -> 0b1000_0000
        2 -> 0b0100_0000
        3 -> 0b0010_0000
        4 -> 0b0001_0000
        5 -> 0b0000_1000
        6 -> 0b0000_0100
        7 -> 0b0000_0010
        else -> 0b0000_0001
    }
    return valueByteArray.apply {
        // TODO これだと多分よくない（立てたい位置にすでに 1 が立っている場合に数値がおかしくなる）
        this[0] = (valueSize or this[0].toInt()).toByte()
    }
}

/** 文字列を ASCII のバイト配列に変換する */
private fun String.toAscii() = this.toByteArray(charset = Charsets.US_ASCII)

/** [Int]を[ByteArray]に変換する。2バイト */
private fun Int.toByteArray() = byteArrayOf(
    (this shr 8).toByte(),
    this.toByte(),
)

/** [Int]を[ByteArray]に変換する。4バイト */
private fun Int.to4ByteArray() = byteArrayOf(
    (this shr 24).toByte(),
    (this shr 16).toByte(),
    (this shr 8).toByte(),
    this.toByte(),
)
