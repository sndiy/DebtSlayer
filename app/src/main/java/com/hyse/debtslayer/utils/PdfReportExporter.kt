package com.hyse.debtslayer.utils

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.hyse.debtslayer.data.entity.Transaction
import com.hyse.debtslayer.viewmodel.DebtState
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfReportExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val LINE_HEIGHT = 22f

    fun export(
        context: Context,
        transactions: List<Transaction>,
        debtState: DebtState
    ): File {
        val document = PdfDocument()
        var pageNumber = 1
        var page = createPage(document, pageNumber)
        var canvas = page.canvas
        var yPos: Float

        // ── Paint styles ──────────────────────────────────────────
        val titlePaint = Paint().apply {
            color = Color.parseColor("#6200EE")
            textSize = 22f
            isFakeBoldText = true
        }
        val subPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
        }
        val headerBgPaint = Paint().apply {
            color = Color.parseColor("#6200EE")
            style = Paint.Style.FILL
        }
        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 11f
            isFakeBoldText = true
        }
        val cellPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
        }
        val grayRowPaint = Paint().apply {
            color = Color.parseColor("#F5F5F5")
            style = Paint.Style.FILL
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 1f
        }
        val summaryLabelPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
        }
        val summaryValuePaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isFakeBoldText = true
        }
        val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id"))
        val sdfDate = SimpleDateFormat("dd MMM yyyy", Locale("id"))

        // ── Header ────────────────────────────────────────────────
        canvas.drawText("Laporan DebtSlayer", MARGIN, 55f, titlePaint)
        canvas.drawText(
            "Dibuat: ${sdf.format(Date())}",
            MARGIN, 72f, subPaint
        )
        canvas.drawLine(MARGIN, 80f, PAGE_WIDTH - MARGIN, 80f, linePaint)

        // ── Summary ───────────────────────────────────────────────
        yPos = 100f
        val summaryItems = listOf(
            "Total Hutang" to CurrencyFormatter.format(debtState.totalDebt),
            "Sudah Dibayar" to CurrencyFormatter.format(debtState.totalPaid),
            "Sisa Hutang" to CurrencyFormatter.format(debtState.remainingDebt),
            "Progress" to "${String.format("%.1f", debtState.progressPercentage)}%",
            "Hari Tersisa" to "${debtState.daysRemaining} hari",
            "Target Harian" to CurrencyFormatter.format(debtState.dailyTarget)
        )
        summaryItems.forEachIndexed { i, (label, value) ->
            val col = i % 3
            val row = i / 3
            val x = MARGIN + col * 170f
            val y = yPos + row * 36f
            canvas.drawText(label, x, y, summaryLabelPaint)
            canvas.drawText(value, x, y + 14f, summaryValuePaint)
        }

        yPos = 185f
        canvas.drawLine(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos, linePaint)
        yPos += 15f

        // ── Table Header ──────────────────────────────────────────
        val colWidths = listOf(35f, 80f, 220f, 120f)
        val colHeaders = listOf("No", "Tanggal", "Sumber", "Jumlah")
        val colX = mutableListOf(MARGIN)
        colWidths.dropLast(1).forEach { colX.add(colX.last() + it) }

        canvas.drawRect(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + 20f, headerBgPaint)
        colHeaders.forEachIndexed { i, h ->
            canvas.drawText(h, colX[i] + 3f, yPos + 14f, headerTextPaint)
        }
        yPos += 20f

        // ── Table Rows ────────────────────────────────────────────
        transactions.sortedByDescending { it.date }.forEachIndexed { idx, tx ->
            if (yPos + LINE_HEIGHT > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber++
                page = createPage(document, pageNumber)
                canvas = page.canvas
                yPos = MARGIN + 20f
                canvas.drawRect(MARGIN, yPos - 20f, PAGE_WIDTH - MARGIN, yPos, headerBgPaint)
                colHeaders.forEachIndexed { i, h ->
                    canvas.drawText(h, colX[i] + 3f, yPos - 6f, headerTextPaint)
                }
            }

            if (idx % 2 == 1) {
                canvas.drawRect(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos + LINE_HEIGHT, grayRowPaint)
            }

            val rowData = listOf(
                "${idx + 1}",
                sdfDate.format(Date(tx.date)),
                tx.source.take(30),
                CurrencyFormatter.format(tx.amount)
            )
            rowData.forEachIndexed { i, text ->
                canvas.drawText(text, colX[i] + 3f, yPos + 15f, cellPaint)
            }
            canvas.drawLine(MARGIN, yPos + LINE_HEIGHT, PAGE_WIDTH - MARGIN, yPos + LINE_HEIGHT, linePaint)
            yPos += LINE_HEIGHT
        }

        // ── Footer ────────────────────────────────────────────────
        yPos += 20f
        if (yPos + 30f < PAGE_HEIGHT - MARGIN) {
            canvas.drawText(
                "Total ${transactions.size} transaksi  •  Total disetor: ${CurrencyFormatter.format(debtState.totalPaid)}",
                MARGIN, yPos, summaryLabelPaint
            )
        }

        document.finishPage(page)

        // ── Simpan file ───────────────────────────────────────────
        val fileName = "DebtSlayer_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun createPage(document: PdfDocument, number: Int): PdfDocument.Page {
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()
        return document.startPage(info)
    }
}