package com.berke.orders.orchestrator.repo;

import com.berke.orders.orchestrator.model.ProductOrder;
import com.berke.orders.orchestrator.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductOrderRepository extends JpaRepository<ProductOrder, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ProductOrder o
            SET o.status = :finalizing,
                o.updatedAt = CURRENT_TIMESTAMP
            WHERE o.orderId = :orderId
              AND o.status = :inProgress
            """)
    int claimFinalization(
            @Param("orderId") Long orderId,
            @Param("inProgress") OrderStatus inProgress,
            @Param("finalizing") OrderStatus finalizing
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ProductOrder o
            SET o.status = :completed,
                o.universalProductKey = :universalProductKey,
                o.updatedAt = CURRENT_TIMESTAMP
            WHERE o.orderId = :orderId
              AND o.status = :finalizing
            """)
    int completeFinalization(
            @Param("orderId") Long orderId,
            @Param("universalProductKey") Long universalProductKey,
            @Param("finalizing") OrderStatus finalizing,
            @Param("completed") OrderStatus completed
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ProductOrder o
            SET o.status = :failed,
                o.errorMessage = :error,
                o.updatedAt = CURRENT_TIMESTAMP
            WHERE o.orderId = :orderId
              AND (o.status = :inProgress OR o.status = :finalizing)
            """)
    int failActiveOrder(
            @Param("orderId") Long orderId,
            @Param("error") String error,
            @Param("inProgress") OrderStatus inProgress,
            @Param("finalizing") OrderStatus finalizing,
            @Param("failed") OrderStatus failed
    );
}
