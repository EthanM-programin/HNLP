package org.epiccure.mtg.seed;

import org.epiccure.mtg.model.ClientMedicalProfile;
import org.epiccure.mtg.repository.ClientMedicalProfileRepository;
import org.epiccure.mtg.repository.ClientRepository;
import org.epiccure.mtg.repository.DietaryTierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Writes the 5 persona medical profiles through JPA so the PHI columns are AES-GCM encrypted
 * by EncryptedStringConverter. SQL can't do this, because the key only exists in the app.
 * Runs at startup and only fills in profiles that are missing, so it is safe on every boot.
 *
 * Format (plaintext before encryption): comma-separated ICD-10 codes, comma-separated allergen tags
 * using the product.allergens vocabulary (PEANUT, TREE_NUT, MILK, EGG, WHEAT, SOY, FISH, SHELLFISH, SESAME).
 */
@Component
public class ProfileSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProfileSeeder.class);

    private record Persona(String clientCode, String icd10Codes, String allergies, String tierCode, String notes) {}

    private static final List<Persona> PERSONAS = List.of(
        new Persona("CLI-1001", "E11,I10", "",          "DIABETIC_STANDARD", "Type 2 diabetes with hypertension"),
        new Persona("CLI-1002", "N18",     "TREE_NUT",  "RENAL_RESTRICTED",  "CKD stage 3, tree nut allergy"),
        new Persona("CLI-1003", "I10",     "SHELLFISH", "CARDIO_LOW_SODIUM", "Hypertension, shellfish allergy"),
        new Persona("CLI-1004", "E11",     "",          "DIABETIC_STANDARD", "Type 2 diabetes, household of 3"),
        new Persona("CLI-1005", "N18,E11", "MILK",      "RENAL_RESTRICTED",  "CKD with diabetes, dairy allergy")
    );

    private final ClientRepository clients;
    private final ClientMedicalProfileRepository profiles;
    private final DietaryTierRepository tiers;

    public ProfileSeeder(ClientRepository clients, ClientMedicalProfileRepository profiles, DietaryTierRepository tiers) {
        this.clients = clients;
        this.profiles = profiles;
        this.tiers = tiers;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (Persona p : PERSONAS) {
            var client = clients.findByClientCode(p.clientCode()).orElse(null);
            if (client == null || profiles.existsByClientId(client.getId())) continue;

            ClientMedicalProfile profile = new ClientMedicalProfile();
            profile.setClient(client);
            profile.setIcd10Codes(p.icd10Codes());
            profile.setAllergies(p.allergies());
            profile.setNotes(p.notes());
            profile.setDietaryTier(tiers.findByCode(p.tierCode()).orElse(null));
            profiles.save(profile);
            created++;
        }
        if (created > 0) log.info("ProfileSeeder: created {} encrypted persona medical profiles", created);
    }
}
