package edu.eci.arsw.pixelplatform.chat.repository;

import edu.eci.arsw.pixelplatform.chat.model.DirectMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {

    @Query("SELECT m FROM DirectMessage m WHERE (m.fromUserId = :userA AND m.toUserId = :userB) " +
           "OR (m.fromUserId = :userB AND m.toUserId = :userA) ORDER BY m.sentAt ASC")
    List<DirectMessage> findConversation(@Param("userA") String userA, @Param("userB") String userB);
}
