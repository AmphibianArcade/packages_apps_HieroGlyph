package org.nukisystems.hieroglyph.View

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class MatrixDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var rows = 16
    var cols = 16
    var cellPadding = 4f
    var onColor = Color.WHITE
    private var cachedValidCount = 0

    // brightness per cell, 0..255
    private var grid: Array<IntArray> = Array(rows) { IntArray(cols) }
    private var validMask: Array<BooleanArray> = Array(rows) { BooleanArray(cols) { true } }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setValidCountPerRow(validCountPerRow: IntArray) {
        rows = validCountPerRow.size
        validMask = Array(rows) { r ->
            val validCount = validCountPerRow[r].coerceIn(0, cols)
            val startCol = (cols - validCount) / 2
            BooleanArray(cols) { c -> c in startCol until (startCol + validCount) }
        }
        grid = Array(rows) { IntArray(cols) }
        invalidate()
        cachedValidCount = validMask.sumOf { row -> row.count { it } }
    }
    
    fun countValidCells(): Int = cachedValidCount

    fun getValidMask(): Array<BooleanArray> = validMask

    fun isValidCell(row: Int, col: Int): Boolean =
        row in 0 until rows && col in 0 until cols && validMask[row][col]

    fun setRowValues(row: Int, values: IntArray) {
        if (row !in 0 until rows) return
        require(values.size == cols) { "Expected $cols values, got ${values.size}" }
        for (c in 0 until cols) {
            grid[row][c] = values[c].coerceIn(0, 255)
        }
        invalidate()
    }

    fun setGrid(newGrid: Array<IntArray>) {
        grid = newGrid
        rows = grid.size
        cols = if (rows > 0) grid[0].size else 0
    }

    fun updateGridContent(newGrid: Array<IntArray>) {
        grid = newGrid
        invalidate()
    }

    fun setCellBrightness(row: Int, col: Int, brightness: Int) {
        if (isValidCell(row, col)) {
            grid[row][col] = brightness.coerceIn(0, 255)
            invalidate()
        }
    }

    fun setFrameFromFlatValues(flatValues: IntArray) {
        var idx = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (validMask[r][c]) {
                    if (idx < flatValues.size) {
                        grid[r][c] = flatValues[idx].coerceIn(0, 255)
                    }
                    idx++
                }
            }
        }
        invalidate()
    }

    fun clear() {
        grid = Array(rows) { IntArray(cols) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cellSize = minOf(width / cols.toFloat(), height / rows.toFloat())

        val gridWidth = cellSize * cols
        val gridHeight = cellSize * rows
        val offsetX = (width - gridWidth) / 2f
        val offsetY = (height - gridHeight) / 2f

        val squareSize = cellSize - cellPadding

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (!validMask[r][c]) continue

                val brightness = grid[r][c]
                val alphaFactor = if (brightness <= 0) 0.15f
                else (0.4f + 0.6f * (brightness / 255f))

                paint.color = onColor
                paint.alpha = (alphaFactor * 255).toInt().coerceIn(0, 255)

                val left = offsetX + c * cellSize + (cellSize - squareSize) / 2f
                val top = offsetY + r * cellSize + (cellSize - squareSize) / 2f
                canvas.drawRect(left, top, left + squareSize, top + squareSize, paint)
            }
        }
    }
}
