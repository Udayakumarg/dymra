package com.tirupurconnect.service;

import com.tirupurconnect.dto.SupplierCreateRequest;
import com.tirupurconnect.dto.SupplierResponse;
import com.tirupurconnect.event.OutboxEventPublisher;
import com.tirupurconnect.event.SupplierProfileUpdatedEvent;
import com.tirupurconnect.exception.BadRequestException;
import com.tirupurconnect.exception.ResourceNotFoundException;
import com.tirupurconnect.model.Supplier;
import com.tirupurconnect.model.User;
import com.tirupurconnect.repository.SupplierRepository;
import com.tirupurconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierService {

    private final SupplierRepository   supplierRepository;
    private final UserRepository       userRepository;
    private final OutboxEventPublisher eventPublisher;

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    @Transactional
    public SupplierResponse createProfile(SupplierCreateRequest req, UUID userId, String tenantSlug) {
        supplierRepository.findByUserId(userId)
            .ifPresent(s -> { throw new BadRequestException("Supplier profile already exists"); });

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Supplier supplier = new Supplier();
        supplier.setTenant(user.getTenant());
        supplier.setUser(user);
        supplier.setBusinessName(req.businessName());
        supplier.setOwnerPhone(user.getPhone());
        supplier.setGstNumber(req.gstNumber());

        // FIX #26: null guard on lat/lon — location is optional at profile creation
        if (req.latitude() != null && req.longitude() != null) {
            supplier.setLocation(buildPoint(req.longitude(), req.latitude()));
        }

        supplier.setProfileCompletePct(computeCompleteness(req));
        supplier.recomputeTrustScore();

        supplier = supplierRepository.save(supplier);

        SupplierProfileUpdatedEvent event = SupplierProfileUpdatedEvent.of(
            supplier.getId(), tenantSlug, "CATALOGUE_UPDATED", supplier.getProfileCompletePct()
        );
        eventPublisher.saveToOutbox(supplier.getId().toString(),
            SupplierProfileUpdatedEvent.TYPE, tenantSlug, event);

        log.info("Supplier profile created: id={} tenant={}", supplier.getId(), tenantSlug);
        return SupplierResponse.from(supplier);
    }

    public SupplierResponse getProfile(UUID userId) {
        Supplier supplier = supplierRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Supplier profile not found"));
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public SupplierResponse updateProfile(SupplierCreateRequest req, UUID userId, String tenantSlug) {
        Supplier supplier = supplierRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Supplier profile not found"));

        supplier.setBusinessName(req.businessName());
        if (req.gstNumber() != null) supplier.setGstNumber(req.gstNumber());
        if (req.latitude()  != null && req.longitude() != null) {
            supplier.setLocation(buildPoint(req.longitude(), req.latitude()));
        }
        supplier.setProfileCompletePct(computeCompleteness(req));
        supplier.recomputeTrustScore();

        supplier = supplierRepository.save(supplier);

        SupplierProfileUpdatedEvent event = SupplierProfileUpdatedEvent.of(
            supplier.getId(), tenantSlug, "CATALOGUE_UPDATED", supplier.getProfileCompletePct()
        );
        eventPublisher.saveToOutbox(supplier.getId().toString(),
            SupplierProfileUpdatedEvent.TYPE, tenantSlug, event);

        return SupplierResponse.from(supplier);
    }

    private Point buildPoint(double lon, double lat) {
        Point p = GF.createPoint(new Coordinate(lon, lat));
        p.setSRID(4326);
        return p;
    }

    private short computeCompleteness(SupplierCreateRequest req) {
        int score = 0;
        if (req.businessName() != null && !req.businessName().isBlank()) score += 30;
        if (req.gstNumber()    != null && !req.gstNumber().isBlank())    score += 30;
        if (req.latitude()     != null && req.longitude() != null)       score += 40;
        return (short) score;
    }
}
