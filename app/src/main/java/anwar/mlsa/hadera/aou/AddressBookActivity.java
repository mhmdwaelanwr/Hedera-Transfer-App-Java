package anwar.mlsa.hadera.aou;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class AddressBookActivity extends AppCompatActivity {

    private RecyclerView contactsRecyclerView;
    private ContactAdapter adapter;
    private TextView emptyView;
    private List<WalletStorage.Contact> contactList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address_book);

        setupToolbar();
        initializeViews();
        setupRecyclerView();
        setupFab();
        loadContacts();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.address_book_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initializeViews() {
        contactsRecyclerView = findViewById(R.id.contacts_recyclerview);
        emptyView = findViewById(R.id.empty_address_book_text);
    }

    private void setupRecyclerView() {
        adapter = new ContactAdapter(contactList,
                this::onContactSelected,
                this::onDeleteContact
        );
        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        contactsRecyclerView.setAdapter(adapter);
    }

    private void setupFab() {
        FloatingActionButton fab = findViewById(R.id.fab_add_contact);
        fab.setOnClickListener(view -> showAddContactDialog());
    }

    private void loadContacts() {
        contactList.clear();
        contactList.addAll(WalletStorage.getContacts(this));
        adapter.notifyDataSetChanged();
        checkIfEmpty();
    }

    private void checkIfEmpty() {
        if (contactList.isEmpty()) {
            contactsRecyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            contactsRecyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    private void onContactSelected(WalletStorage.Contact contact) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("SELECTED_ACCOUNT_ID", contact.getAccountId());
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    private void onDeleteContact(WalletStorage.Contact contact) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Contact")
                .setMessage("Are you sure you want to delete '" + contact.getName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    WalletStorage.deleteContact(this, contact);
                    loadContacts();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_contact, null);
        final EditText nameEditText = view.findViewById(R.id.edit_text_name);
        final EditText accountIdEditText = view.findViewById(R.id.edit_text_account_id);

        builder.setView(view)
                .setTitle("Add New Contact")
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = nameEditText.getText().toString().trim();
                    String accountId = accountIdEditText.getText().toString().trim();

                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(accountId)) {
                        Toast.makeText(this, "Name and Account ID cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!accountId.matches("^0\\.0\\.[0-9]+$")) {
                        Toast.makeText(this, "Invalid Account ID format", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (WalletStorage.addContact(this, name, accountId)) {
                        Toast.makeText(this, "Contact saved", Toast.LENGTH_SHORT).show();
                        loadContacts();
                    } else {
                        Toast.makeText(this, "A contact with this Account ID already exists", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.create().show();
    }

    // Adapter Class
    private static class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {
        private final List<WalletStorage.Contact> contacts;
        private final OnContactClickListener clickListener;
        private final OnDeleteClickListener deleteClickListener;

        interface OnContactClickListener {
            void onContactClick(WalletStorage.Contact contact);
        }

        interface OnDeleteClickListener {
            void onDeleteClick(WalletStorage.Contact contact);
        }

        ContactAdapter(List<WalletStorage.Contact> contacts, OnContactClickListener clickListener, OnDeleteClickListener deleteClickListener) {
            this.contacts = contacts;
            this.clickListener = clickListener;
            this.deleteClickListener = deleteClickListener;
        }

        @NonNull
        @Override
        public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.contact_item, parent, false);
            return new ContactViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
            holder.bind(contacts.get(position), clickListener, deleteClickListener);
        }

        @Override
        public int getItemCount() {
            return contacts.size();
        }

        static class ContactViewHolder extends RecyclerView.ViewHolder {
            TextView nameTextView, accountIdTextView;
            ImageButton deleteButton;

            ContactViewHolder(@NonNull View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.contact_name);
                accountIdTextView = itemView.findViewById(R.id.contact_account_id);
                deleteButton = itemView.findViewById(R.id.delete_contact_button);
            }

            void bind(final WalletStorage.Contact contact, final OnContactClickListener clickListener, final OnDeleteClickListener deleteClickListener) {
                nameTextView.setText(contact.getName());
                accountIdTextView.setText(contact.getAccountId());
                itemView.setOnClickListener(v -> clickListener.onContactClick(contact));
                deleteButton.setOnClickListener(v -> deleteClickListener.onDeleteClick(contact));
            }
        }
    }
}
