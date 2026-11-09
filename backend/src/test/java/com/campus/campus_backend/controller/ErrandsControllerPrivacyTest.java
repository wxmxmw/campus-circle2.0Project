package com.campus.campus_backend.controller;

import com.campus.campus_backend.domain.Errand;
import com.campus.campus_backend.domain.User;
import com.campus.campus_backend.repository.ErrandRepository;
import com.campus.campus_backend.repository.PointTransactionRepository;
import com.campus.campus_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrandsControllerPrivacyTest {
    @Test
    @SuppressWarnings("unchecked")
    void privatePickupDetailsAreVisibleOnlyToParticipants() {
        ErrandRepository errands = mock(ErrandRepository.class);
        ErrandsController controller = new ErrandsController(errands, mock(UserRepository.class),
                mock(PointTransactionRepository.class));
        User publisher = new User();
        publisher.setUsername("publisher");
        User runner = new User();
        runner.setUsername("runner");
        Errand errand = new Errand();
        errand.setId(7L);
        errand.setPublisher(publisher);
        errand.setRunner(runner);
        errand.setHiddenInfo("pickup code 1234");
        when(errands.findByIdWithPublisher(7L)).thenReturn(Optional.of(errand));

        UserDetails stranger = mock(UserDetails.class);
        when(stranger.getUsername()).thenReturn("stranger");
        Map<String, Object> strangerResult = (Map<String, Object>) controller.getErrandDetail(stranger, 7L).getData();
        assertNull(strangerResult.get("hiddenInfo"));

        UserDetails participant = mock(UserDetails.class);
        when(participant.getUsername()).thenReturn("runner");
        Map<String, Object> participantResult = (Map<String, Object>) controller.getErrandDetail(participant, 7L).getData();
        assertEquals("pickup code 1234", participantResult.get("hiddenInfo"));
    }
}
