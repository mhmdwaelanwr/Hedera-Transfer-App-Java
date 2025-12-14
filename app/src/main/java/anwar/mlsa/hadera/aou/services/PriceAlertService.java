package anwar.mlsa.hadera.aou.services;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import anwar.mlsa.hadera.aou.models.PriceAlert;

public class PriceAlertService {
    private static final String PREFS_NAME = "PriceAlertPrefs";
    private static final String KEY_PRICE_ALERTS = "PRICE_ALERTS";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static List<PriceAlert> getPriceAlerts(Context context) {
        String json = getPrefs(context).getString(KEY_PRICE_ALERTS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<PriceAlert>>() {}.getType();
        List<PriceAlert> alerts = new Gson().fromJson(json, type);
        return alerts != null ? alerts : new ArrayList<>();
    }

    public static void savePriceAlerts(Context context, List<PriceAlert> alerts) {
        getPrefs(context).edit()
                .putString(KEY_PRICE_ALERTS, new Gson().toJson(alerts))
                .apply();
    }

    public static boolean addAlert(Context context, double targetPrice, String direction, String fiatCurrency) {
        List<PriceAlert> alerts = getPriceAlerts(context);
        String id = UUID.randomUUID().toString();
        alerts.add(new PriceAlert(id, targetPrice, direction, true, fiatCurrency));
        savePriceAlerts(context, alerts);
        return true;
    }

    public static boolean deleteAlert(Context context, String id) {
        List<PriceAlert> alerts = getPriceAlerts(context);
        for (int i = 0; i < alerts.size(); i++) {
            if (alerts.get(i).getId().equals(id)) {
                alerts.remove(i);
                savePriceAlerts(context, alerts);
                return true;
            }
        }
        return false;
    }

    public static boolean toggleAlert(Context context, String id) {
        List<PriceAlert> alerts = getPriceAlerts(context);
        for (int i = 0; i < alerts.size(); i++) {
            if (alerts.get(i).getId().equals(id)) {
                PriceAlert alert = alerts.get(i);
                alert.setActive(!alert.isActive());
                savePriceAlerts(context, alerts);
                return true;
            }
        }
        return false;
    }

    public static List<PriceAlert> evaluateAlerts(Context context, double currentPrice, String fiatCurrency) {
        List<PriceAlert> alerts = getPriceAlerts(context);
        List<PriceAlert> triggeredAlerts = new ArrayList<>();
        
        if (fiatCurrency == null) {
            return triggeredAlerts;
        }
        
        for (PriceAlert alert : alerts) {
            if (alert.isActive() && alert.getFiatCurrency() != null && 
                alert.getFiatCurrency().equalsIgnoreCase(fiatCurrency)) {
                boolean triggered = false;
                if ("ABOVE".equals(alert.getDirection()) && currentPrice >= alert.getTargetPrice()) {
                    triggered = true;
                } else if ("BELOW".equals(alert.getDirection()) && currentPrice <= alert.getTargetPrice()) {
                    triggered = true;
                }
                
                if (triggered) {
                    triggeredAlerts.add(alert);
                    // Deactivate the alert so it doesn't trigger again
                    alert.setActive(false);
                }
            }
        }
        
        if (!triggeredAlerts.isEmpty()) {
            savePriceAlerts(context, alerts);
        }
        
        return triggeredAlerts;
    }
}
