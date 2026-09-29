package com.flowdesk.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowdesk.organization.entity.Organization;
import com.flowdesk.organization.repository.OrganizationRepository;
import com.flowdesk.project.entity.Project;
import com.flowdesk.project.entity.ProjectStatus;
import com.flowdesk.project.repository.ProjectRepository;
import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;
import com.flowdesk.testsupport.IntegrationTest;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.entity.Role;
import com.flowdesk.user.entity.User;
import com.flowdesk.user.repository.UserRepository;
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
 * Formalizes {@link Ticket}'s {@code @Version} guarantee (see its Javadoc)
 * with the same real, two-thread, two-transaction proof
 * {@code RefreshTokenConcurrencyTest} uses for refresh token rotation:
 * two agents concurrently editing the same ticket (one reassigning it,
 * one changing its status) must not silently overwrite one another - the
 * second writer must lose with an optimistic locking failure instead of
 * both succeeding.
 *
 * <p>Deliberately not wrapped in {@code @Transactional} for the same
 * reason as {@code RefreshTokenConcurrencyTest}: exercising a genuine race
 * needs two independently committed transactions on two separate
 * connections. Left-over rows are harmless, isolated by a random
 * organization/users created just for this test.
 */
@IntegrationTest
class TicketConcurrencyTest {

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentUpdates_onlyOneWinnerSucceeds() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long ticketId = tx.execute(status -> {
            Organization org = organizationRepository.save(Organization.builder().name("Race Co").build());
            User creator = userRepository.save(User.builder()
                    .organization(org)
                    .email("creator-" + System.nanoTime() + "@acme.test")
                    .passwordHash("hashed")
                    .firstName("Cre")
                    .lastName("Ator")
                    .role(Role.ORG_ADMIN)
                    .build());
            Project project = projectRepository.save(Project.builder()
                    .organization(org)
                    .name("Race Project")
                    .status(ProjectStatus.ACTIVE)
                    .build());
            Ticket ticket = ticketRepository.save(Ticket.builder()
                    .organization(org)
                    .project(project)
                    .createdBy(creator)
                    .title("Race ticket")
                    .status(TicketStatus.OPEN)
                    .priority(TicketPriority.MEDIUM)
                    .build());
            return ticket.getId();
        });

        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Boolean> updateAttempt = () -> {
            try {
                return tx.execute(status -> {
                    Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
                    awaitUninterruptibly(barrier); // force both transactions to have read before either writes
                    ticket.setStatus(TicketStatus.IN_PROGRESS);
                    ticketRepository.saveAndFlush(ticket);
                    return true;
                });
            } catch (ObjectOptimisticLockingFailureException ex) {
                return false;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = executor.invokeAll(List.of(updateAttempt, updateAttempt));
            long successes = results.stream()
                    .map(TicketConcurrencyTest::getUnchecked)
                    .filter(Boolean::booleanValue)
                    .count();

            assertThat(successes)
                    .as("exactly one of the two concurrent updates should win")
                    .isEqualTo(1);

            Ticket finalState = ticketRepository.findById(ticketId).orElseThrow();
            assertThat(finalState.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
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
