package com.flowdesk.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowdesk.auth.entity.RefreshToken;
import com.flowdesk.auth.repository.RefreshTokenRepository;
import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.testsupport.IntegrationTest;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves, with two genuinely overlapping database transactions (not just
 * sequential calls), that {@link RefreshToken}'s {@code @Version} actually
 * closes the race described in its Javadoc: two concurrent attempts to
 * rotate the same refresh token must not both succeed.
 *
 * <p>Deliberately not wrapped in {@code @Transactional} like this
 * project's other integration tests - proving real concurrency requires
 * two independently committed transactions on two separate connections,
 * which a single rolled-back test transaction would not exercise. The
 * handful of rows this leaves behind are harmless test data, isolated by
 * a random organization/user created just for this test.
 */
@IntegrationTest
class RefreshTokenConcurrencyIT {

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentRotation_onlyOneWinnerSucceeds() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long tokenId = tx.execute(status -> {
            Organization org = organizationRepository.save(Organization.builder().name("Race Co").build());
            User user = userRepository.save(User.builder()
                    .organization(org)
                    .email("racer-" + System.nanoTime() + "@acme.test")
                    .passwordHash("hashed")
                    .firstName("Race")
                    .lastName("Condition")
                    .role(Role.ORG_ADMIN)
                    .build());
            RefreshToken token = refreshTokenRepository.save(RefreshToken.builder()
                    .user(user)
                    .tokenHash("concurrency-test-" + System.nanoTime())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .revoked(false)
                    .build());
            return token.getId();
        });

        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Boolean> rotateAttempt = () -> {
            try {
                return tx.execute(status -> {
                    RefreshToken token = refreshTokenRepository.findById(tokenId).orElseThrow();
                    awaitUninterruptibly(barrier); // force both transactions to have read before either writes
                    token.setRevoked(true);
                    refreshTokenRepository.saveAndFlush(token);
                    return true;
                });
            } catch (ObjectOptimisticLockingFailureException ex) {
                return false;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = executor.invokeAll(List.of(rotateAttempt, rotateAttempt));
            long successes = results.stream()
                    .map(RefreshTokenConcurrencyIT::getUnchecked)
                    .filter(Boolean::booleanValue)
                    .count();

            assertThat(successes)
                    .as("exactly one of the two concurrent rotations should win")
                    .isEqualTo(1);

            RefreshToken finalState = refreshTokenRepository.findById(tokenId).orElseThrow();
            assertThat(finalState.isRevoked()).isTrue();
            assertThat(finalState.getVersion()).isEqualTo(1L); // exactly one successful update was applied
        } finally {
            executor.shutdownNow();
        }
    }

    private static void awaitUninterruptibly(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean getUnchecked(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
