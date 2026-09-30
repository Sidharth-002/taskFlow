package com.flowdesk.comment.mapper;

import com.flowdesk.comment.dto.CommentResponse;
import com.flowdesk.comment.entity.Comment;
import org.springframework.stereotype.Component;

@Component
public class CommentMapper {

    public CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTicket().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getFullName(),
                comment.getBody(),
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }
}
