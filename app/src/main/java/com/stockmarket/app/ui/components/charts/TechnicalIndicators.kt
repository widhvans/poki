package com.stockmarket.app.ui.components.charts

import com.stockmarket.app.domain.models.CandleData

/**
 * Technical Indicators Calculator
 */
object TechnicalIndicators {
    
    /**
     * Simple Moving Average (SMA)
     */
    fun calculateSMA(data: List<CandleData>, period: Int): List<Float?> {
        return data.mapIndexed { index, _ ->
            if (index >= period - 1) {
                val sum = (0 until period).sumOf { data[index - it].close.toDouble() }
                (sum / period).toFloat()
            } else null
        }
    }
    
    /**
     * Exponential Moving Average (EMA)
     */
    fun calculateEMA(data: List<CandleData>, period: Int): List<Float?> {
        if (data.size < period) return List(data.size) { null }
        
        val multiplier = 2.0 / (period + 1)
        val result = mutableListOf<Float?>()
        
        // Fill nulls for initial entries
        repeat(period - 1) { result.add(null) }
        
        // First EMA is SMA
        var ema = data.take(period).map { it.close }.average()
        result.add(ema.toFloat())
        
        // Calculate subsequent EMAs
        for (i in period until data.size) {
            ema = (data[i].close - ema) * multiplier + ema
            result.add(ema.toFloat())
        }
        
        return result
    }
    
    /**
     * Relative Strength Index (RSI)
     */
    fun calculateRSI(data: List<CandleData>, period: Int = 14): List<Float?> {
        if (data.size < period + 1) return List(data.size) { null }
        
        val result = mutableListOf<Float?>()
        result.add(null) // First value has no change
        
        // Calculate price changes
        val changes = data.zipWithNext { a, b -> b.close - a.close }
        
        // Fill nulls for initial period
        repeat(period - 1) { result.add(null) }
        
        // Calculate initial average gain/loss
        var avgGain = changes.take(period).filter { it > 0 }.map { it.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0
        var avgLoss = changes.take(period).filter { it < 0 }.map { -it.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0
        
        // First RSI
        val firstRsi = if (avgLoss != 0.0) 100 - (100 / (1 + avgGain / avgLoss)) else 100.0
        result.add(firstRsi.toFloat())
        
        // Calculate subsequent RSIs using smoothed averages
        for (i in period until changes.size) {
            val change = changes[i]
            val gain = if (change > 0) change.toDouble() else 0.0
            val loss = if (change < 0) -change.toDouble() else 0.0
            
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            
            val rsi = if (avgLoss != 0.0) 100 - (100 / (1 + avgGain / avgLoss)) else 100.0
            result.add(rsi.toFloat())
        }
        
        return result
    }
    
    /**
     * MACD (Moving Average Convergence Divergence)
     */
    data class MACDResult(
        val macdLine: List<Float?>,
        val signalLine: List<Float?>,
        val histogram: List<Float?>
    )
    
    fun calculateMACD(
        data: List<CandleData>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): MACDResult {
        val fastEMA = calculateEMA(data, fastPeriod)
        val slowEMA = calculateEMA(data, slowPeriod)
        
        // MACD Line = Fast EMA - Slow EMA
        val macdLine = fastEMA.zip(slowEMA) { fast, slow ->
            if (fast != null && slow != null) fast - slow else null
        }
        
        // Signal Line = EMA of MACD Line
        val nonNullMacd = macdLine.filterNotNull()
        val signalEMA = if (nonNullMacd.size >= signalPeriod) {
            val multiplier = 2.0 / (signalPeriod + 1)
            val signalResult = mutableListOf<Float>()
            
            var ema = nonNullMacd.take(signalPeriod).average()
            signalResult.add(ema.toFloat())
            
            for (i in signalPeriod until nonNullMacd.size) {
                ema = (nonNullMacd[i] - ema) * multiplier + ema
                signalResult.add(ema.toFloat())
            }
            
            signalResult
        } else emptyList()
        
        // Map signal back to full size
        val signalLine = mutableListOf<Float?>()
        var signalIndex = 0
        for (i in macdLine.indices) {
            if (macdLine[i] != null && signalIndex < signalEMA.size && 
                i >= slowPeriod - 1 + signalPeriod - 1) {
                signalLine.add(signalEMA[signalIndex++])
            } else {
                signalLine.add(null)
            }
        }
        
        // Histogram = MACD Line - Signal Line
        val histogram = macdLine.zip(signalLine) { macd, signal ->
            if (macd != null && signal != null) macd - signal else null
        }
        
        return MACDResult(macdLine, signalLine, histogram)
    }
    
    /**
     * Bollinger Bands
     */
    data class BollingerBandsResult(
        val upper: List<Float?>,
        val middle: List<Float?>,
        val lower: List<Float?>
    )
    
    fun calculateBollingerBands(
        data: List<CandleData>,
        period: Int = 20,
        stdDevMultiplier: Float = 2f
    ): BollingerBandsResult {
        val sma = calculateSMA(data, period)
        
        val upper = mutableListOf<Float?>()
        val lower = mutableListOf<Float?>()
        
        for (i in data.indices) {
            val ma = sma[i]
            if (ma != null) {
                // Calculate standard deviation
                val prices = (0 until period).map { data[i - it].close }
                val variance = prices.map { (it - ma) * (it - ma) }.average()
                val stdDev = kotlin.math.sqrt(variance).toFloat()
                
                upper.add(ma + stdDevMultiplier * stdDev)
                lower.add(ma - stdDevMultiplier * stdDev)
            } else {
                upper.add(null)
                lower.add(null)
            }
        }
        
        return BollingerBandsResult(upper, sma, lower)
    }
    
    /**
     * Support and Resistance Levels
     */
    fun findSupportResistance(data: List<CandleData>, lookback: Int = 20): Pair<Float, Float> {
        if (data.size < lookback) {
            return Pair(
                data.minOfOrNull { it.low } ?: 0f,
                data.maxOfOrNull { it.high } ?: 0f
            )
        }
        
        val recentData = data.takeLast(lookback)
        val support = recentData.minOf { it.low }
        val resistance = recentData.maxOf { it.high }
        
        return Pair(support, resistance)
    }
    
    /**
     * Volume Weighted Average Price (VWAP)
     */
    fun calculateVWAP(data: List<CandleData>): List<Float> {
        var cumulativeTPV = 0.0 // Typical Price * Volume
        var cumulativeVolume = 0L
        
        return data.map { candle ->
            val typicalPrice = (candle.high + candle.low + candle.close) / 3
            cumulativeTPV += typicalPrice * candle.volume
            cumulativeVolume += candle.volume
            
            if (cumulativeVolume > 0) {
                (cumulativeTPV / cumulativeVolume).toFloat()
            } else 0f
        }
    }
}
