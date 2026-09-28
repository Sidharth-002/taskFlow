package com.flowdesk.comment.entity;

import com.flowdesk.shared.persistence.BaseEntity;
import com.flowdesk.ticket.entity.Ticket;
import com.flowdesk.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A comment on a ticket. No {@code @Version} here (unlike {@code Ticket}
 * and {@code RefreshToken}): an edit conflict on a single free-text field,
 * by its own author, is low-stakes and low-probability enough that
 * optimistic locking would be solving a problem this entity doesn't
 * really have - last-write-wins is an acceptable outcome for a comment
 * body in a way it isn't for a ticket's workflow state.
 */
@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "text")
    private String body;
}
