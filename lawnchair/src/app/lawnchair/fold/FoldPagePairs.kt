package app.lawnchair.fold

import kotlin.math.max

/**
 * How the home pages pair up on the two panels of a foldable's large screen ("Two pages"), for
 * each [FoldPageOrder]. Pure functions of page indices, for `Workspace`.
 *
 * Each pair is laid out in page order, its first page on the side where the order starts. Pairs
 * are set from the anchor, the page the user was on, so that it sits on the fixed half:
 * - [FoldPageOrder.OPENING]: pages run from the fixed half towards the moving one, so the anchor N
 *   starts its pair, (N, N + 1).
 * - [FoldPageOrder.READING]: pages run in the UI language's direction. When the fixed half is on
 *   the start side this is the same as [FoldPageOrder.OPENING]. When it is on the end side (English
 *   with the fixed half on the right), the anchor ends its pair, (N - 1, N). The first page can't
 *   end a pair: it starts (0, 1), page 1 then being on the fixed half.
 *
 * Pairs are described by a parity: 0 for (0, 1), (2, 3)..., 1 for (1, 2), (3, 4)... where the
 * first page is alone and shown next to page 1.
 */
object FoldPagePairs {

    /**
     * Whether pages run right to left on the two panels. [FoldPageOrder.OPENING] runs from the
     * fixed half towards the moving one; [FoldPageOrder.READING], or a hinge side that isn't
     * known, follows the UI language.
     *
     * @param isRtlLanguage whether the UI language is right to left.
     * @param movingHalfLeft whether the moving half is the left one, null if unknown (see
     * [FoldPosture.isMovingHalfLeft]).
     */
    @JvmStatic
    fun isPageOrderRtl(order: FoldPageOrder, isRtlLanguage: Boolean, movingHalfLeft: Boolean?): Boolean = if (order == FoldPageOrder.OPENING && movingHalfLeft != null) movingHalfLeft else isRtlLanguage

    /**
     * Whether the fixed half shows the second page of each pair, i.e. the page order runs towards
     * it. Never with [FoldPageOrder.OPENING].
     */
    @JvmStatic
    fun isFixedHalfSecond(order: FoldPageOrder, isRtlLanguage: Boolean, movingHalfLeft: Boolean?): Boolean {
        if (movingHalfLeft == null) return false
        // Pages run right to left: the first page of a pair is on the right half, which is the
        // fixed half when the moving half is the left one.
        return isPageOrderRtl(order, isRtlLanguage, movingHalfLeft) != movingHalfLeft
    }

    /**
     * The parity of the pairs that put the page at [anchorIndex] (-1: none) on the fixed half:
     * first in its pair, or second when [fixedHalfSecond], except the first page which is always
     * first.
     */
    @JvmStatic
    fun pairParity(anchorIndex: Int, fixedHalfSecond: Boolean): Int = when {
        anchorIndex <= 0 -> 0
        fixedHalfSecond -> (anchorIndex + 1) % 2
        else -> anchorIndex % 2
    }

    /** The first page of the pair holding [pageIndex] with [parity]; the first page alone with parity 1. */
    @JvmStatic
    fun pairStart(pageIndex: Int, parity: Int): Int = max(0, pageIndex - Math.floorMod(pageIndex - parity, 2))

    /**
     * The page shown beside the anchor at [anchorIndex] once pairs are set from it (see
     * [pairParity]): the page after it, or the one before when [fixedHalfSecond] and there is one.
     */
    @JvmStatic
    fun pairedPage(anchorIndex: Int, fixedHalfSecond: Boolean): Int = if (fixedHalfSecond && anchorIndex > 0) anchorIndex - 1 else anchorIndex + 1

    /**
     * The page on the fixed half while the pair starting at [pairStart] is shown, among
     * [pageCount] pages: [pairStart] itself, or with [fixedHalfSecond] the page after it (the second
     * of the pair, or page 1 beside the first page alone) unless [pairStart] is the last page.
     */
    @JvmStatic
    fun fixedHalfPage(pairStart: Int, pageCount: Int, fixedHalfSecond: Boolean): Int = if (fixedHalfSecond && pairStart + 1 < pageCount) pairStart + 1 else pairStart
}
