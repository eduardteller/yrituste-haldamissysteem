package com.rik.prooviylesanne.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.rik.prooviylesanne.model.FyysilisedIsikud;
import com.rik.prooviylesanne.model.JuriidilisedIsikud;
import com.rik.prooviylesanne.model.MaksmiseViisid;
import com.rik.prooviylesanne.model.Yritused;
import com.rik.prooviylesanne.model.YritusedIsikud;
import com.rik.prooviylesanne.repository.FyysilisedIsikudRepository;
import com.rik.prooviylesanne.repository.JuriidilisedIsikudRepository;
import com.rik.prooviylesanne.repository.MaksmiseViisidRepository;
import com.rik.prooviylesanne.repository.YritusedIsikudRepository;
import com.rik.prooviylesanne.repository.YritusedRepository;

import jakarta.transaction.Transactional;

/**
 * Teenuse klass isikute haldamiseks üritustel.
 * Tegeleb nii füüsiliste kui ka juriidiliste isikutega - nende lisamise,
 * kustutamise, uuendamise ja päringutega.
 */
@Service
public class IsikudService {

    private final FyysilisedIsikudRepository fyysilisedIsikudRepository;
    private final JuriidilisedIsikudRepository juriidilisedIsikudRepository;
    private final YritusedRepository yritusedRepository;
    private final YritusedIsikudRepository yritusedIsikudRepository;
    private final MaksmiseViisidRepository maksmiseViisidRepository;

    public IsikudService(
            FyysilisedIsikudRepository fyysilisedIsikudRepository,
            JuriidilisedIsikudRepository juriidilisedIsikudRepository,
            YritusedRepository yritusedRepository,
            YritusedIsikudRepository yritusedIsikudRepository,
            MaksmiseViisidRepository maksmiseViisidRepository) {
        this.fyysilisedIsikudRepository = fyysilisedIsikudRepository;
        this.juriidilisedIsikudRepository = juriidilisedIsikudRepository;
        this.yritusedRepository = yritusedRepository;
        this.yritusedIsikudRepository = yritusedIsikudRepository;
        this.maksmiseViisidRepository = maksmiseViisidRepository;
    }

    /**
     * Kontrollimise meetodid - kas isik juba olemas andmebaasis
     */
    public boolean fyysilineIsikExists(FyysilisedIsikud isik) {
        return fyysilisedIsikudRepository.existsByIsikukood(
                isik.getIsikukood());
    }

    public boolean juriidilineIsikExists(JuriidilisedIsikud isik) {
        return juriidilisedIsikudRepository.existsByRegistrikood(
                isik.getRegistrikood());
    }

    /**
     * Uue füüsilise isiku lisamine üritusele.
     * Kontrollib dubleerimist, leidub maksmise viisi ja salvestab isiku koos
     * seosega.
     */
    @Transactional
    public FyysilisedIsikud addFyysilineIsikToYritus(FyysilisedIsikud isik, Long yritusId, String maksmiseViisString) {
        // Kontrolli, et sama isik pole juba olemas
        if (fyysilineIsikExists(isik)) {
            throw new IllegalArgumentException(
                    "Duplicate person: A physical person with the same properties already exists");
        }

        // Leia üritus ID järgi
        Optional<Yritused> yritusOpt = yritusedRepository.findById(yritusId);
        if (!yritusOpt.isPresent()) {
            throw new RuntimeException("Yritus with ID " + yritusId + " not found");
        }

        // Seadista maksmise viis
        MaksmiseViisid maksmiseViis = maksmiseViisidRepository.findByMaksmiseViis(maksmiseViisString)
                .orElseThrow(() -> new IllegalArgumentException("Payment method not found: " + maksmiseViisString));

        isik.setMaksmiseViis(maksmiseViis);

        // Salvesta isik ja loo seos üritusega
        FyysilisedIsikud savedIsik = fyysilisedIsikudRepository.save(isik);

        YritusedIsikud yritusedIsik = new YritusedIsikud();
        yritusedIsik.setYritus(yritusOpt.get());
        yritusedIsik.setFyysilineIsikId(savedIsik);
        yritusedIsikudRepository.save(yritusedIsik);

        return savedIsik;
    }

    /**
     * Uue juriidilise isiku lisamine üritusele.
     * Töötab sarnaselt füüsilise isiku lisamisega.
     */
    @Transactional
    public JuriidilisedIsikud addJuriidilineIsikToYritus(JuriidilisedIsikud isik, Long yritusId,
            String maksmiseViisString) {
        // Kontrolli, et sama ettevõte pole juba olemas
        if (juriidilineIsikExists(isik)) {
            throw new IllegalArgumentException(
                    "Duplicate entity: A legal entity with the same properties already exists");
        }

        // Leia üritus ja seadista maksmise viis
        Optional<Yritused> yritusOpt = yritusedRepository.findById(yritusId);
        if (!yritusOpt.isPresent()) {
            throw new RuntimeException("Yritus with ID " + yritusId + " not found");
        }

        MaksmiseViisid maksmiseViis = maksmiseViisidRepository.findByMaksmiseViis(maksmiseViisString)
                .orElseThrow(() -> new IllegalArgumentException("Payment method not found: " + maksmiseViisString));

        isik.setMaksmiseViis(maksmiseViis);

        // Salvesta isik ja loo seos üritusega
        JuriidilisedIsikud savedIsik = juriidilisedIsikudRepository.save(isik);

        YritusedIsikud yritusedIsik = new YritusedIsikud();
        yritusedIsik.setYritus(yritusOpt.get());
        yritusedIsik.setJuriidilineIsikId(savedIsik);
        yritusedIsikudRepository.save(yritusedIsik);

        return savedIsik;
    }

    /**
     * Kustutamise meetodid.
     * Eemaldab isiku kõikidest üritustest ja kustutab seejärel isiku enda.
     */
    @Transactional
    public boolean deleteFyysilineIsik(Long id) {
        Optional<FyysilisedIsikud> isikOpt = fyysilisedIsikudRepository.findById(id);

        if (isikOpt.isPresent()) {
            FyysilisedIsikud isik = isikOpt.get();

            // Eemalda kõik seosed üritustega
            yritusedIsikudRepository.deleteByFyysilineIsikId(isik);

            // Kustuta isik
            fyysilisedIsikudRepository.delete(isik);
            return true;
        }

        return false;
    }

    @Transactional
    public boolean deleteJuriidilineIsik(Long id) {
        Optional<JuriidilisedIsikud> isikOpt = juriidilisedIsikudRepository.findById(id);

        if (isikOpt.isPresent()) {
            JuriidilisedIsikud isik = isikOpt.get();

            // Eemalda kõik seosed üritustega
            yritusedIsikudRepository.deleteByJuriidilineIsikId(isik);

            // Kustuta isik
            juriidilisedIsikudRepository.delete(isik);
            return true;
        }

        return false;
    }

    /**
     * Tagastab kõik isikud, kes on kindlal üritusel registreeritud.
     * Eraldab füüsilised ja juriidilised isikud.
     */
    public Map<String, Object> getAllIsikudForYritus(Long yritusId) {
        Optional<Yritused> yritusOpt = yritusedRepository.findById(yritusId);
        if (!yritusOpt.isPresent()) {
            throw new RuntimeException("Event with ID " + yritusId + " not found");
        }

        // Leia kõik üritusele registreeritud isikud
        Yritused yritus = yritusOpt.get();
        List<YritusedIsikud> yritusedIsikud = yritusedIsikudRepository.findByYritus(yritus);

        // Eralda füüsilised ja juriidilised isikud
        List<FyysilisedIsikud> fyysilisedIsikud = new ArrayList<>();
        List<JuriidilisedIsikud> juriidilisedIsikud = new ArrayList<>();

        for (YritusedIsikud yritusIsik : yritusedIsikud) {
            if (yritusIsik.getFyysilineIsikId() != null) {
                fyysilisedIsikud.add(yritusIsik.getFyysilineIsikId());
            } else if (yritusIsik.getJuriidilineIsikId() != null) {
                juriidilisedIsikud.add(yritusIsik.getJuriidilineIsikId());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fyysilisedIsikud", fyysilisedIsikud);
        result.put("juriidilisedIsikud", juriidilisedIsikud);

        return result;
    }

    /**
     * Tagastab kõik isikud, kes veel kindlal üritusel registreeritud ei ole.
     * Kasutatakse olemasolevate isikute lisamise valikute näitamiseks.
     */
    public Map<String, Object> getAvailableIsikudForYritus(Long yritusId) {
        Yritused yritus = yritusedRepository.findById(yritusId)
                .orElseThrow(() -> new RuntimeException("Event with ID " + yritusId + " not found"));

        // Leia juba registreeritud isikud
        List<YritusedIsikud> yritusedIsikud = yritusedIsikudRepository.findByYritus(yritus);

        List<Long> existingFyysilisedIsikudIds = yritusedIsikud.stream()
                .filter(yi -> yi.getFyysilineIsikId() != null)
                .map(yi -> yi.getFyysilineIsikId().getId())
                .toList();

        List<Long> existingJuriidilisedIsikudIds = yritusedIsikud.stream()
                .filter(yi -> yi.getJuriidilineIsikId() != null)
                .map(yi -> yi.getJuriidilineIsikId().getId())
                .toList();

        // Leia kõik isikud ja filtreeri välja juba registreeritud
        List<FyysilisedIsikud> allFyysilisedIsikud = fyysilisedIsikudRepository.findAll();
        List<JuriidilisedIsikud> allJuriidilisedIsikud = juriidilisedIsikudRepository.findAll();

        List<FyysilisedIsikud> availableFyysilisedIsikud = allFyysilisedIsikud.stream()
                .filter(isik -> !existingFyysilisedIsikudIds.contains(isik.getId()))
                .toList();

        List<JuriidilisedIsikud> availableJuriidilisedIsikud = allJuriidilisedIsikud.stream()
                .filter(isik -> !existingJuriidilisedIsikudIds.contains(isik.getId()))
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("fyysilisedIsikud", availableFyysilisedIsikud);
        result.put("juriidilisedIsikud", availableJuriidilisedIsikud);

        return result;
    }

    /**
     * Uuendamise meetodid.
     * Võimaldab muuta olemasoleva isiku andmeid.
     */
    @Transactional
    public FyysilisedIsikud updateFyysilineIsik(Long id, FyysilisedIsikud updatedIsik, String maksmiseViisString) {
        Optional<FyysilisedIsikud> isikOpt = fyysilisedIsikudRepository.findById(id);
        if (!isikOpt.isPresent()) {
            throw new RuntimeException("Physical person with ID " + id + " not found");
        }

        FyysilisedIsikud existingIsik = isikOpt.get();

        // Uuenda kõik väljad
        existingIsik.setEesnimi(updatedIsik.getEesnimi());
        existingIsik.setPerekonnanimi(updatedIsik.getPerekonnanimi());
        existingIsik.setIsikukood(updatedIsik.getIsikukood());
        existingIsik.setLisainfo(updatedIsik.getLisainfo());

        // Uuenda maksmise viis kui vajalaik
        if (maksmiseViisString != null && !maksmiseViisString.isEmpty()) {
            MaksmiseViisid maksmiseViis = maksmiseViisidRepository.findByMaksmiseViis(maksmiseViisString)
                    .orElseThrow(() -> new IllegalArgumentException("Payment method not found: " + maksmiseViisString));
            existingIsik.setMaksmiseViis(maksmiseViis);
        }

        return fyysilisedIsikudRepository.save(existingIsik);
    }

    @Transactional
    public JuriidilisedIsikud updateJuriidilineIsik(Long id, JuriidilisedIsikud updatedIsik,
            String maksmiseViisString) {
        Optional<JuriidilisedIsikud> isikOpt = juriidilisedIsikudRepository.findById(id);
        if (!isikOpt.isPresent()) {
            throw new RuntimeException("Legal entity with ID " + id + " not found");
        }

        JuriidilisedIsikud existingIsik = isikOpt.get();

        // Uuenda kõik väljad
        existingIsik.setNimi(updatedIsik.getNimi());
        existingIsik.setRegistrikood(updatedIsik.getRegistrikood());
        existingIsik.setOsavotjateArv(updatedIsik.getOsavotjateArv());
        existingIsik.setLisainfo(updatedIsik.getLisainfo());

        // Uuenda maksmise viis kui vajalaik
        if (maksmiseViisString != null && !maksmiseViisString.isEmpty()) {
            MaksmiseViisid maksmiseViis = maksmiseViisidRepository.findByMaksmiseViis(maksmiseViisString)
                    .orElseThrow(() -> new IllegalArgumentException("Payment method not found: " + maksmiseViisString));
            existingIsik.setMaksmiseViis(maksmiseViis);
        }

        return juriidilisedIsikudRepository.save(existingIsik);
    }

    /**
     * Lihtsad pärimise meetodid ID järgi.
     */
    public FyysilisedIsikud getFyysilineIsikById(Long id) {
        return fyysilisedIsikudRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Physical person with ID " + id + " not found"));
    }

    public JuriidilisedIsikud getJuriidilineIsikById(Long id) {
        return juriidilisedIsikudRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Legal entity with ID " + id + " not found"));
    }

    /**
     * Olemasoleva isiku lisamine üritusele.
     * Kasutatakse juba andmebaasis olevate isikute lisamiseks uutele üritustele.
     */
    @Transactional
    public FyysilisedIsikud addExistingFyysilineIsikToYritus(Long isikId, Long yritusId) {
        // Leia isik ja üritus
        FyysilisedIsikud existingIsik = fyysilisedIsikudRepository.findById(isikId)
                .orElseThrow(() -> new RuntimeException("Physical person with ID " + isikId + " not found"));

        Yritused yritus = yritusedRepository.findById(yritusId)
                .orElseThrow(() -> new RuntimeException("Event with ID " + yritusId + " not found"));

        // Kontrolli, et isik ei ole juba sellel üritusel
        boolean alreadyAdded = yritusedIsikudRepository.findByYritus(yritus).stream()
                .anyMatch(yi -> yi.getFyysilineIsikId() != null &&
                        yi.getFyysilineIsikId().getId().equals(existingIsik.getId()));

        if (alreadyAdded) {
            throw new IllegalArgumentException("This physical person is already added to this event");
        }

        // Loo seos üritusega
        YritusedIsikud yritusedIsik = new YritusedIsikud();
        yritusedIsik.setYritus(yritus);
        yritusedIsik.setFyysilineIsikId(existingIsik);
        yritusedIsikudRepository.save(yritusedIsik);

        return existingIsik;
    }

    @Transactional
    public JuriidilisedIsikud addExistingJuriidilineIsikToYritus(Long isikId, Long yritusId) {
        // Leia isik ja üritus
        JuriidilisedIsikud existingIsik = juriidilisedIsikudRepository.findById(isikId)
                .orElseThrow(() -> new RuntimeException("Legal entity with ID " + isikId + " not found"));

        Yritused yritus = yritusedRepository.findById(yritusId)
                .orElseThrow(() -> new RuntimeException("Event with ID " + yritusId + " not found"));

        // Kontrolli, et isik ei ole juba sellel üritusel
        boolean alreadyAdded = yritusedIsikudRepository.findByYritus(yritus).stream()
                .anyMatch(yi -> yi.getJuriidilineIsikId() != null &&
                        yi.getJuriidilineIsikId().getId().equals(existingIsik.getId()));

        if (alreadyAdded) {
            throw new IllegalArgumentException("This legal entity is already added to this event");
        }

        // Loo seos üritusega
        YritusedIsikud yritusedIsik = new YritusedIsikud();
        yritusedIsik.setYritus(yritus);
        yritusedIsik.setJuriidilineIsikId(existingIsik);
        yritusedIsikudRepository.save(yritusedIsik);

        return existingIsik;
    }
}
