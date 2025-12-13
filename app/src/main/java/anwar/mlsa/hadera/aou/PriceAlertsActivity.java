package anwar.mlsa.hadera.aou;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Locale;

import anwar.mlsa.hadera.aou.models.PriceAlert;
import anwar.mlsa.hadera.aou.services.CurrencyPreferenceService;
import anwar.mlsa.hadera.aou.services.PriceAlertService;

public class PriceAlertsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private PriceAlertAdapter adapter;
    private TextView emptyView;
    private FloatingActionButton fabAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_price_alerts);

        setupToolbar();
        initializeViews();
        loadPriceAlerts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPriceAlerts();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.price_alerts_recyclerview);
        emptyView = findViewById(R.id.empty_view);
        fabAdd = findViewById(R.id.fab_add_alert);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PriceAlertAdapter();
        recyclerView.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> showAddAlertDialog());
    }

    private void loadPriceAlerts() {
        List<PriceAlert> alerts = PriceAlertService.getPriceAlerts(this);
        adapter.updateData(alerts);

        if (alerts.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    private void showAddAlertDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_price_alert, null);
        TextInputEditText priceInput = dialogView.findViewById(R.id.target_price_input);
        RadioGroup directionGroup = dialogView.findViewById(R.id.direction_group);
        Spinner currencySpinner = dialogView.findViewById(R.id.currency_spinner);

        // Setup currency spinner
        ArrayAdapter<String> currencyAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                CurrencyPreferenceService.CURRENCY_NAMES
        );
        currencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        currencySpinner.setAdapter(currencyAdapter);

        // Set default to current currency
        String currentCurrency = CurrencyPreferenceService.getSelectedCurrency(this);
        for (int i = 0; i < CurrencyPreferenceService.SUPPORTED_CURRENCIES.length; i++) {
            if (CurrencyPreferenceService.SUPPORTED_CURRENCIES[i].equals(currentCurrency)) {
                currencySpinner.setSelection(i);
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Add Price Alert")
                .setView(dialogView)
                .setPositiveButton("Create", (dialog, which) -> {
                    String priceStr = priceInput.getText().toString().trim();
                    if (priceStr.isEmpty()) {
                        Toast.makeText(this, "Please enter a target price", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        double targetPrice = Double.parseDouble(priceStr);
                        int selectedId = directionGroup.getCheckedRadioButtonId();
                        String direction = selectedId == R.id.radio_above ? "ABOVE" : "BELOW";
                        int currencyIndex = currencySpinner.getSelectedItemPosition();
                        String currency = CurrencyPreferenceService.SUPPORTED_CURRENCIES[currencyIndex];

                        boolean success = PriceAlertService.addAlert(this, targetPrice, direction, currency.toUpperCase());
                        if (success) {
                            Toast.makeText(this, "Price alert created", Toast.LENGTH_SHORT).show();
                            loadPriceAlerts();
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid price format", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class PriceAlertAdapter extends RecyclerView.Adapter<PriceAlertAdapter.ViewHolder> {
        private List<PriceAlert> alerts;

        public PriceAlertAdapter() {
            this.alerts = PriceAlertService.getPriceAlerts(PriceAlertsActivity.this);
        }

        public void updateData(List<PriceAlert> newAlerts) {
            this.alerts = newAlerts;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_price_alert, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(alerts.get(position));
        }

        @Override
        public int getItemCount() {
            return alerts.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView alertText, statusText;
            SwitchMaterial activeSwitch;
            Button deleteButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                alertText = itemView.findViewById(R.id.alert_text);
                statusText = itemView.findViewById(R.id.status_text);
                activeSwitch = itemView.findViewById(R.id.active_switch);
                deleteButton = itemView.findViewById(R.id.delete_button);
            }

            void bind(PriceAlert alert) {
                String alertDescription = String.format(Locale.US,
                        "Notify when HBAR is %s %s%.2f",
                        alert.getDirection().equals("ABOVE") ? "above" : "below",
                        getCurrencySymbolForCode(alert.getFiatCurrency()),
                        alert.getTargetPrice()
                );
                alertText.setText(alertDescription);

                statusText.setText(alert.isActive() ? "Active" : "Inactive");
                activeSwitch.setChecked(alert.isActive());

                activeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    PriceAlertService.toggleAlert(PriceAlertsActivity.this, alert.getId());
                    statusText.setText(isChecked ? "Active" : "Inactive");
                });

                deleteButton.setOnClickListener(v -> {
                    new AlertDialog.Builder(PriceAlertsActivity.this)
                            .setTitle("Delete Alert")
                            .setMessage("Are you sure you want to delete this price alert?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                PriceAlertService.deleteAlert(PriceAlertsActivity.this, alert.getId());
                                Toast.makeText(PriceAlertsActivity.this, "Alert deleted", Toast.LENGTH_SHORT).show();
                                loadPriceAlerts();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            }

            private String getCurrencySymbolForCode(String code) {
                for (int i = 0; i < CurrencyPreferenceService.SUPPORTED_CURRENCIES.length; i++) {
                    if (CurrencyPreferenceService.SUPPORTED_CURRENCIES[i].equalsIgnoreCase(code)) {
                        return CurrencyPreferenceService.CURRENCY_SYMBOLS[i];
                    }
                }
                return "$";
            }
        }
    }
}
