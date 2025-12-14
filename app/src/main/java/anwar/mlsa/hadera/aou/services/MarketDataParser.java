package anwar.mlsa.hadera.aou.services;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.HashMap;

import anwar.mlsa.hadera.aou.models.MarketData;

public class MarketDataParser {
    private static final String TAG = "MarketDataParser";

    public static MarketData parseCoinGeckoResponse(String response, String fiatCurrency) {
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(response, JsonObject.class);
            
            if (root.has("hedera-hashgraph")) {
                JsonObject hbarData = root.getAsJsonObject("hedera-hashgraph");
                
                double price = hbarData.has(fiatCurrency) ? hbarData.get(fiatCurrency).getAsDouble() : 0.0;
                double changePercent24h = hbarData.has(fiatCurrency + "_24h_change") ? 
                    hbarData.get(fiatCurrency + "_24h_change").getAsDouble() : 0.0;
                
                // Calculate absolute change from percentage
                double change24h = price * (changePercent24h / 100.0);
                
                return new MarketData(price, change24h, changePercent24h, fiatCurrency.toUpperCase());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing CoinGecko response", e);
        }
        
        return null;
    }

    public static HashMap<String, Double> parseHistoricalPrice(String response, String fiatCurrency) {
        HashMap<String, Double> prices = new HashMap<>();
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(response, JsonObject.class);
            
            if (root.has("prices")) {
                // CoinGecko returns prices as [[timestamp, price], ...]
                // TODO: Implement full historical price parsing
                // For now, this is a placeholder for future enhancement
                // Full implementation would:
                // 1. Parse the prices array
                // 2. Map timestamps to prices
                // 3. Return a map of timestamp -> price
                // Example: prices.put("1234567890", 0.05);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing historical price response", e);
        }
        
        return prices;
    }
}
