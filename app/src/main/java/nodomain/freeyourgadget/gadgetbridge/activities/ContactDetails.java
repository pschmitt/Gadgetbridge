/*  Copyright (C) 2023-2024 Daniel Dakhno, José Rebelo

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.TextInputLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityContactDetailsBinding;
import nodomain.freeyourgadget.gadgetbridge.entities.Contact;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

public class ContactDetails extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(ContactDetails.class);

    private ActivityContactDetailsBinding binding;

    private Contact contact;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityContactDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState != null) {
            contact = (Contact) savedInstanceState.getSerializable(Contact.EXTRA_CONTACT);
        } else {
            contact = (Contact) getIntent().getSerializableExtra(Contact.EXTRA_CONTACT);
        }

        if (contact == null) {
            GB.toast("No contact provided to ContactDetails Activity", Toast.LENGTH_LONG, GB.ERROR);
            finish();
            return;
        }

        if (savedInstanceState == null) {
            binding.contactName.setText(contact.getName());
            binding.contactNumber.setText(contact.getNumber());
        }

        clearErrorOnEdit(binding.contactNameLayout);
        clearErrorOnEdit(binding.contactNumberLayout);

        binding.fabSave.setOnClickListener(view -> {
            final String name = String.valueOf(binding.contactName.getText()).trim();
            final String number = String.valueOf(binding.contactNumber.getText()).trim();

            boolean valid = true;
            if (StringUtils.isNullOrEmpty(name)) {
                binding.contactNameLayout.setError(getString(R.string.contact_missing_name));
                valid = false;
            }
            if (StringUtils.isNullOrEmpty(number)) {
                binding.contactNumberLayout.setError(getString(R.string.contact_missing_number));
                valid = false;
            }
            if (!valid) {
                return;
            }

            contact.setName(name);
            contact.setNumber(number);
            LOG.debug("Storing contact {}", contact.getContactId());
            DBHelper.store(contact);
            setResult(Activity.RESULT_OK);
            finish();
        });
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            // back button
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle state) {
        super.onSaveInstanceState(state);
        state.putSerializable(Contact.EXTRA_CONTACT, contact);
    }

    private static void clearErrorOnEdit(final TextInputLayout layout) {
        Objects.requireNonNull(layout.getEditText()).addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
            }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            }

            @Override
            public void afterTextChanged(final Editable s) {
                layout.setError(null);
            }
        });
    }
}
