package com.flowdesk.auth.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowdesk.auth.repository.RefreshTokenRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupJobTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new RefreshTokenCleanupJob(refreshTokenRepository);
    }

    @Test
    void run_deletesTokensExpiredBeforeNow() {
        when(refreshTokenRepository.deleteExpiredBefore(any())).thenReturn(3);

        job.run();

        var captor = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenRepository).deleteExpiredBefore(captor.capture());
        // The cutoff passed is "now" at call time - within a generous
        // tolerance rather than asserting exact equality with a
        // separately-captured Instant.now(), which would be flaky.
        assertThatCutoffIsRecent(captor.getValue());
    }

    private void assertThatCutoffIsRecent(Instant cutoff) {
        Instant now = Instant.now();
        org.assertj.core.api.Assertions.assertThat(cutoff).isBetween(now.minusSeconds(5), now.plusSeconds(5));
    }

    @Test
    void run_noExpiredTokens_completesWithoutError() {
        when(refreshTokenRepository.deleteExpiredBefore(any())).thenReturn(0);

        job.run();

        verify(refreshTokenRepository).deleteExpiredBefore(any());
    }
}
