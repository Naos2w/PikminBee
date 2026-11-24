package com.naos.pikminbee.data

import android.content.Context
import org.json.JSONArray
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory

/** One shared Bright paint-only palette. Sources, filters, layout, zooms and
 * route/current-position overlays are never altered by appearance changes. */
object BrightMapPalette {
    fun applyDark(context: Context, style: Style) {
        val groups = JSONArray(context.assets.open("lan/bright-dark-palette.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() })
        for (i in 0 until groups.length()) {
            val group = groups.getJSONObject(i)
            val value = group.get("value")
            val color = if (value is JSONArray) Expression.raw(value.toString())
                else Expression.color(android.graphics.Color.parseColor(
                    if (value is String && value.startsWith("rgba(")) {
                        // Parse CSS rgba without losing alpha when applying to native.
                        val parts = value.removePrefix("rgba(").removeSuffix(")").split(",")
                        val rgb = parts.take(3).map { it.trim().toInt() }
                        val alpha = (parts[3].trim().toDouble() * 255).toInt().coerceIn(0,255)
                        "#%02x%02x%02x%02x".format(alpha,rgb[0],rgb[1],rgb[2])
                    } else value.toString()
                ))
            val layers = group.getJSONArray("layers")
            for (j in 0 until layers.length()) {
                val layer = style.getLayer(layers.getString(j)) ?: continue
                when (group.getString("property")) {
                    "background-color" -> (layer as? BackgroundLayer)?.setProperties(PropertyFactory.backgroundColor(color))
                    "fill-color" -> (layer as? FillLayer)?.setProperties(PropertyFactory.fillColor(color))
                    "fill-outline-color" -> (layer as? FillLayer)?.setProperties(PropertyFactory.fillOutlineColor(color))
                    "line-color" -> (layer as? LineLayer)?.setProperties(PropertyFactory.lineColor(color))
                    "text-color" -> (layer as? SymbolLayer)?.setProperties(PropertyFactory.textColor(color))
                    "text-halo-color" -> (layer as? SymbolLayer)?.setProperties(PropertyFactory.textHaloColor(color))
                }
            }
        }
    }
}
