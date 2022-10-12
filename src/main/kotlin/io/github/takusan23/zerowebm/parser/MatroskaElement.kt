package io.github.takusan23.zerowebm.parser

import io.github.takusan23.zerowebm.MatroskaTags

/**
 * EBMLの要素を表すデータクラス
 *
 * @param tag [MatroskaTags]
 * @param elementSize 要素の合計サイズ
 * @param data 実際のデータ
 */
data class MatroskaElement(
    val tag: MatroskaTags,
    val data: ByteArray,
    val elementSize: Int,
)