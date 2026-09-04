package com.stockguard.service;

import com.stockguard.dto.ReservationRequest;
import com.stockguard.entity.Inventory;
import com.stockguard.entity.Reservation;
import com.stockguard.entity.ReservationStatus;
import com.stockguard.exception.OutOfStockException;
import com.stockguard.repository.InventoryRepository;
import com.stockguard.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReservationService} using mocked repositories.
 */
class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductService productService;

    private ReservationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ReservationService(reservationRepository, inventoryRepository, productService);
    }

    @Test
    void reserve_decrementsAvailableAndIncrementsReserved_whenStockSufficient() {
        Inventory inventory = new Inventory(1L, 10);
        when(reservationRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(inventoryRepository.lockByProductId(1L)).thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.reserve(new ReservationRequest(1L, "user-1", 3), "key-1");

        assertEquals(ReservationStatus.PENDING, response.status());
        assertEquals(7, inventory.getAvailableQty());
        assertEquals(3, inventory.getReservedQty());
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void reserve_throwsOutOfStock_whenRequestExceedsAvailable() {
        Inventory inventory = new Inventory(1L, 2);
        when(reservationRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
        when(inventoryRepository.lockByProductId(1L)).thenReturn(Optional.of(inventory));

        assertThrows(OutOfStockException.class,
                () -> service.reserve(new ReservationRequest(1L, "user-1", 5), "key-2"));

        assertEquals(2, inventory.getAvailableQty());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserve_isIdempotent_returnsExistingReservationInsteadOfDecrementingAgain() {
        Reservation existing = new Reservation();
        existing.setId(99L);
        existing.setProductId(1L);
        existing.setUserId("user-1");
        existing.setQty(3);
        existing.setStatus(ReservationStatus.PENDING);
        when(reservationRepository.findByIdempotencyKey("key-3")).thenReturn(Optional.of(existing));

        var response = service.reserve(new ReservationRequest(1L, "user-1", 3), "key-3");

        assertEquals(99L, response.id());
        verifyNoInteractions(inventoryRepository);
    }
}