package com.stockmarket.app.ui.components.charts

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.CandleStickChart
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.stockmarket.app.domain.models.CandleData
import java.text.SimpleDateFormat
import java.util.*

/**
 * Candlestick Chart Composable using MPAndroidChart
 */
@Composable
fun CandlestickChartView(
    candles: List<CandleData>,
    modifier: Modifier = Modifier,
    showVolume: Boolean = true,
    showMA: Boolean = false,
    maPeriods: List<Int> = listOf(5, 10, 20)
) {
    val context = LocalContext.current
    
    AndroidView(
        factory = { ctx ->
            createCandleStickChart(ctx)
        },
        update = { chart ->
            updateCandleStickChart(chart, candles, showVolume, showMA, maPeriods)
        },
        modifier = modifier
    )
}

private fun createCandleStickChart(context: Context): CandleStickChart {
    return CandleStickChart(context).apply {
        // Chart description
        description.isEnabled = false
        
        // Background
        setBackgroundColor(Color.TRANSPARENT)
        
        // Grid
        setDrawGridBackground(false)
        
        // Legend
        legend.isEnabled = false
        
        // X Axis
        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(true)
            gridColor = Color.parseColor("#2A3A54")
            textColor = Color.parseColor("#78909C")
            setDrawAxisLine(false)
            granularity = 1f
            labelRotationAngle = -45f
        }
        
        // Left Y Axis (prices)
        axisLeft.apply {
            setDrawGridLines(true)
            gridColor = Color.parseColor("#2A3A54")
            textColor = Color.parseColor("#78909C")
            setDrawAxisLine(false)
            setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
        }
        
        // Right Y Axis - disabled
        axisRight.isEnabled = false
        
        // Touch interactions
        setTouchEnabled(true)
        isDragEnabled = true
        setScaleEnabled(true)
        setPinchZoom(true)
        isDoubleTapToZoomEnabled = true
        
        // Highlight
        isHighlightPerDragEnabled = true
        isHighlightPerTapEnabled = true
        
        // Animation
        animateX(500)
        
        // Extra offsets for better view
        setExtraOffsets(0f, 10f, 0f, 10f)
    }
}

private fun updateCandleStickChart(
    chart: CandleStickChart,
    candles: List<CandleData>,
    showVolume: Boolean,
    showMA: Boolean,
    maPeriods: List<Int>
) {
    if (candles.isEmpty()) {
        chart.clear()
        return
    }
    
    // Create candlestick entries
    val entries = candles.mapIndexed { index, candle ->
        CandleEntry(
            index.toFloat(),
            candle.high,
            candle.low,
            candle.open,
            candle.close
        )
    }
    
    // Create dataset
    val dataSet = CandleDataSet(entries, "Price").apply {
        // Colors
        decreasingColor = Color.parseColor("#EF5350") // Red for bearish
        decreasingPaintStyle = Paint.Style.FILL
        increasingColor = Color.parseColor("#26A69A") // Green for bullish
        increasingPaintStyle = Paint.Style.FILL
        neutralColor = Color.parseColor("#78909C") // Gray for unchanged
        shadowColorSameAsCandle = true
        
        // Style
        shadowWidth = 1f
        barSpace = 0.1f
        
        // Draw values
        setDrawValues(false)
        
        // Highlight
        highLightColor = Color.parseColor("#FFFFFF")
        highlightLineWidth = 1f
    }
    
    // Set X axis formatter for dates
    chart.xAxis.valueFormatter = object : ValueFormatter() {
        private val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
        
        override fun getFormattedValue(value: Float): String {
            val index = value.toInt()
            return if (index >= 0 && index < candles.size) {
                dateFormat.format(Date(candles[index].timestamp))
            } else ""
        }
    }
    
    // Zoom to show last 30 candles by default
    chart.data = CandleData(dataSet)
    
    if (candles.size > 30) {
        chart.moveViewToX((candles.size - 30).toFloat())
        chart.setVisibleXRangeMaximum(30f)
    }
    
    chart.invalidate()
}

/**
 * Combined Chart with Candlesticks, Volume, and Moving Averages
 */
@Composable
fun AdvancedChartView(
    candles: List<CandleData>,
    modifier: Modifier = Modifier,
    showVolume: Boolean = true,
    showMA5: Boolean = false,
    showMA10: Boolean = false,
    showMA20: Boolean = false
) {
    val context = LocalContext.current
    
    AndroidView(
        factory = { ctx ->
            createCombinedChart(ctx)
        },
        update = { chart ->
            updateCombinedChart(chart, candles, showVolume, showMA5, showMA10, showMA20)
        },
        modifier = modifier
    )
}

private fun createCombinedChart(context: Context): CombinedChart {
    return CombinedChart(context).apply {
        description.isEnabled = false
        setBackgroundColor(Color.TRANSPARENT)
        setDrawGridBackground(false)
        legend.isEnabled = true
        legend.textColor = Color.parseColor("#78909C")
        
        // Draw order - candles on top
        drawOrder = arrayOf(
            CombinedChart.DrawOrder.BAR,
            CombinedChart.DrawOrder.CANDLE,
            CombinedChart.DrawOrder.LINE
        )
        
        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(true)
            gridColor = Color.parseColor("#2A3A54")
            textColor = Color.parseColor("#78909C")
            setDrawAxisLine(false)
            granularity = 1f
        }
        
        axisLeft.apply {
            setDrawGridLines(true)
            gridColor = Color.parseColor("#2A3A54")
            textColor = Color.parseColor("#78909C")
            setDrawAxisLine(false)
            setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
        }
        
        axisRight.apply {
            setDrawGridLines(false)
            textColor = Color.parseColor("#78909C")
            setDrawAxisLine(false)
            setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
        }
        
        setTouchEnabled(true)
        isDragEnabled = true
        setScaleEnabled(true)
        setPinchZoom(true)
        animateX(500)
    }
}

private fun updateCombinedChart(
    chart: CombinedChart,
    candles: List<CandleData>,
    showVolume: Boolean,
    showMA5: Boolean,
    showMA10: Boolean,
    showMA20: Boolean
) {
    if (candles.isEmpty()) {
        chart.clear()
        return
    }
    
    val combinedData = CombinedData()
    
    // Candlestick data
    val candleEntries = candles.mapIndexed { index, candle ->
        CandleEntry(index.toFloat(), candle.high, candle.low, candle.open, candle.close)
    }
    
    val candleDataSet = CandleDataSet(candleEntries, "Price").apply {
        decreasingColor = Color.parseColor("#EF5350")
        decreasingPaintStyle = Paint.Style.FILL
        increasingColor = Color.parseColor("#26A69A")
        increasingPaintStyle = Paint.Style.FILL
        shadowColorSameAsCandle = true
        shadowWidth = 1f
        setDrawValues(false)
        axisDependency = YAxis.AxisDependency.LEFT
    }
    
    combinedData.setData(CandleData(candleDataSet))
    
    // Volume bars
    if (showVolume) {
        val volumeEntries = candles.mapIndexed { index, candle ->
            BarEntry(
                index.toFloat(),
                candle.volume.toFloat(),
                if (candle.isBullish) 1 else 0
            )
        }
        
        val volumeDataSet = BarDataSet(volumeEntries, "Volume").apply {
            colors = volumeEntries.map { entry ->
                if (entry.data == 1) Color.parseColor("#4026A69A")
                else Color.parseColor("#40EF5350")
            }
            setDrawValues(false)
            axisDependency = YAxis.AxisDependency.RIGHT
        }
        
        combinedData.setData(BarData(volumeDataSet))
    }
    
    // Moving Averages
    val lineDataSets = mutableListOf<LineDataSet>()
    
    if (showMA5) {
        val ma5 = calculateMA(candles, 5)
        lineDataSets.add(createMALineDataSet(ma5, "MA5", Color.parseColor("#FF9800")))
    }
    
    if (showMA10) {
        val ma10 = calculateMA(candles, 10)
        lineDataSets.add(createMALineDataSet(ma10, "MA10", Color.parseColor("#E91E63")))
    }
    
    if (showMA20) {
        val ma20 = calculateMA(candles, 20)
        lineDataSets.add(createMALineDataSet(ma20, "MA20", Color.parseColor("#9C27B0")))
    }
    
    if (lineDataSets.isNotEmpty()) {
        combinedData.setData(LineData(lineDataSets.toList()))
    }
    
    // X axis formatter
    chart.xAxis.valueFormatter = object : ValueFormatter() {
        private val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
        override fun getFormattedValue(value: Float): String {
            val index = value.toInt()
            return if (index >= 0 && index < candles.size) {
                dateFormat.format(Date(candles[index].timestamp))
            } else ""
        }
    }
    
    chart.data = combinedData
    
    if (candles.size > 30) {
        chart.moveViewToX((candles.size - 30).toFloat())
        chart.setVisibleXRangeMaximum(30f)
    }
    
    chart.invalidate()
}

private fun calculateMA(candles: List<CandleData>, period: Int): List<Entry> {
    return candles.mapIndexedNotNull { index, _ ->
        if (index >= period - 1) {
            val sum = (0 until period).sumOf { candles[index - it].close.toDouble() }
            Entry(index.toFloat(), (sum / period).toFloat())
        } else null
    }
}

private fun createMALineDataSet(entries: List<Entry>, label: String, color: Int): LineDataSet {
    return LineDataSet(entries, label).apply {
        this.color = color
        lineWidth = 1.5f
        setDrawCircles(false)
        setDrawValues(false)
        mode = LineDataSet.Mode.CUBIC_BEZIER
        axisDependency = YAxis.AxisDependency.LEFT
    }
}
