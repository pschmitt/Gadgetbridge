/*  Copyright (C) 2023-2024 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.capabilities.loyaltycards;

import static nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsConst.LOYALTY_CARDS_CATIMA_PACKAGE;
import static nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsConst.LOYALTY_CARDS_SYNC_ARCHIVED;
import static nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsConst.LOYALTY_CARDS_SYNC_GROUPS;
import static nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsConst.LOYALTY_CARDS_SYNC_GROUPS_ONLY;
import static nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsConst.LOYALTY_CARDS_SYNC_STARRED;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class CatimaManager {
    private static final Logger LOG = LoggerFactory.getLogger(CatimaManager.class);

    public static final String ACTION_LOYALTY_CARDS_SYNCED = "nodomain.freeyourgadget.gadgetbridge.loyaltycards.action.synced";
    public static final String EXTRA_SUCCESS = "success";

    private final Context context;

    public CatimaManager(final Context context) {
        this.context = context;
    }

    /**
     * Sends the loyalty cards to the device.
     *
     * @return the number of cards to be sent, or -1 if the sync was not triggered.
     */
    public int sync(final GBDevice gbDevice) {
        final ArrayList<LoyaltyCard> cardsToSync = getCardsToSync(gbDevice);
        if (cardsToSync == null) {
            return -1;
        }

        GBApplication.deviceService(gbDevice).onSetLoyaltyCards(cardsToSync);

        return cardsToSync.size();
    }

    /**
     * Returns the cards that match the sync options for the device, or null if the cards cannot be read.
     */
    @Nullable
    public ArrayList<LoyaltyCard> getCardsToSync(final GBDevice gbDevice) {
        final List<CharSequence> installedCatimaPackages = findInstalledCatimaPackages();
        if (installedCatimaPackages.isEmpty()) {
            LOG.warn("Catima is not installed");
            return null;
        }

        final Prefs prefs = new Prefs(GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()));

        final boolean syncGroupsOnly = prefs.getBoolean(LOYALTY_CARDS_SYNC_GROUPS_ONLY, false);
        final Set<String> syncGroups = prefs.getStringSet(LOYALTY_CARDS_SYNC_GROUPS, Collections.emptySet());
        final boolean syncArchived = prefs.getBoolean(LOYALTY_CARDS_SYNC_ARCHIVED, false);
        final boolean syncStarred = prefs.getBoolean(LOYALTY_CARDS_SYNC_STARRED, false);

        final String catimaPackage = prefs.getString(LOYALTY_CARDS_CATIMA_PACKAGE, installedCatimaPackages.get(0).toString());
        final CatimaContentProvider catima = new CatimaContentProvider(context, catimaPackage);

        LOG.debug("Syncing loyalty cards from {}", catimaPackage);

        if (!catima.isCatimaCompatible()) {
            LOG.warn("Catima is not compatible");
            return null;
        }

        // FossWallet does not support starred sync
        final boolean starredSupported = catimaPackage.contains("catima");

        final List<LoyaltyCard> cards = catima.getCards();
        final Map<String, List<Integer>> groupCards = catima.getGroupCards();

        final Set<Integer> cardsInGroupsToSync = new HashSet<>();
        if (syncGroupsOnly) {
            for (final Map.Entry<String, List<Integer>> groupCardsEntry : groupCards.entrySet()) {
                if (syncGroups.contains(groupCardsEntry.getKey())) {
                    cardsInGroupsToSync.addAll(groupCardsEntry.getValue());
                }
            }
        }

        final Set<BarcodeFormat> supportedBarcodeFormats = gbDevice.getDeviceCoordinator().getSupportedBarcodeFormats(gbDevice);

        final ArrayList<LoyaltyCard> cardsToSync = new ArrayList<>();
        for (final LoyaltyCard card : cards) {
            if (!supportedBarcodeFormats.contains(card.getBarcodeFormat())) {
                LOG.debug("Ignoring card {} - unsupported barcode format {}", card.getId(), card.getBarcodeFormat());
                continue;
            }
            if (syncGroupsOnly && !cardsInGroupsToSync.contains(card.getId())) {
                continue;
            }
            if (!syncArchived && card.isArchived()) {
                continue;
            }
            if (starredSupported && syncStarred && !card.isStarred()) {
                continue;
            }
            cardsToSync.add(card);
        }

        Collections.sort(cardsToSync);

        LOG.debug("Will sync cards: {}", cardsToSync);

        return cardsToSync;
    }

    public static void notifySynced(final Context context, final GBDevice device, final boolean success) {
        LOG.debug("Loyalty cards sync to {} finished, success = {}", device.getAddress(), success);
        final Intent intent = new Intent(ACTION_LOYALTY_CARDS_SYNCED);
        intent.putExtra(GBDevice.EXTRA_DEVICE, device);
        intent.putExtra(EXTRA_SUCCESS, success);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    public List<CharSequence> findInstalledCatimaPackages() {
        final List<CharSequence> installedCatimaPackages = new ArrayList<>();
        for (final String knownPackage : CatimaContentProvider.KNOWN_PACKAGES) {
            if (AndroidUtils.isPackageInstalled(knownPackage)) {
                installedCatimaPackages.add(knownPackage);
            }
        }
        return installedCatimaPackages;
    }
}
